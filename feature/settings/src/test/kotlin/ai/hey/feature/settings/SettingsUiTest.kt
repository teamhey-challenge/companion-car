package ai.hey.feature.settings

import ai.hey.model.CompanionSettings
import ai.hey.model.ProactiveLevel
import ai.hey.model.SpeechStyle
import ai.hey.session.CompanionState
import ai.hey.session.PairingUi
import ai.hey.session.SettingsAction
import ai.hey.session.SettingsSave
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsUiTest {
    @Test fun `levels round-trip and keep the contract minutes`() {
        ProactiveLevel.entries.forEach { assertEquals(it, it.ui().level()) }
        assertEquals(listOf(null, 20, 10, 5), ProactiveLevel.entries.map { it.intervalMin })
    }

    @Test fun `unpair exists only while a phone is linked`() {
        assertFalse(settingsUi(CompanionState(pairing = PairingUi.None), null).phonePaired)
        assertFalse(settingsUi(CompanionState(pairing = PairingUi.Expired), null).phonePaired)
        assertTrue(settingsUi(CompanionState(pairing = PairingUi.Claimed), null).phonePaired)
    }

    @Test fun `saving marks only the field changed last`() {
        val saving = CompanionState(settingsSave = SettingsSave.Saving)
        assertEquals(setOf(SettingField.Memory), settingsUi(saving, SettingField.Memory).saving)
        assertEquals(emptySet<SettingField>(), settingsUi(CompanionState(settingsSave = SettingsSave.Idle), SettingField.Memory).saving)
        assertEquals(emptySet<SettingField>(), settingsUi(saving, null).saving)
    }

    @Test fun `values come from the server settings`() {
        val ui = settingsUi(CompanionState(settings = CompanionSettings(ProactiveLevel.Off, false, SpeechStyle.Jondaetmal)), null)
        assertEquals(ProactiveUi.Off, ui.proactive)
        assertFalse(ui.memoryEnabled)
        assertEquals(SpeechUi.Jondaetmal, ui.speech)
    }

    @Test fun `the look comes from the server settings and an unset body colour follows the theme`() {
        val def = settingsUi(CompanionState(), null).look
        assertEquals(ai.hey.designsystem.component.CharacterLook(null, 1, 0), def)
        assertEquals(0, def.body(dark = false))
        assertEquals(1, def.body(dark = true))
        val picked = settingsUi(CompanionState(settings = CompanionSettings(bodyColor = 0, hat = 2, neck = 1)), null).look
        assertEquals(ai.hey.designsystem.component.CharacterLook(0, 2, 1), picked)
        assertEquals(0, picked.body(dark = true))
    }

    @Test fun `the look is read from and written to the settings wire format`() {
        val o = ai.hey.model.HeyJson.parseToJsonElement(
            """{"proactive":"normal","memoryEnabled":true,"speech":"banmal","nickname":null,"bodyColor":1,"hat":2,"neck":1}""",
        ) as kotlinx.serialization.json.JsonObject
        val s = ai.hey.model.parseCompanionSettings(o)!!
        assertEquals(CompanionSettings(bodyColor = 1, hat = 2, neck = 1), s)
        // an older server without the fields, or a null bodyColor, means the default look
        val old = ai.hey.model.HeyJson.parseToJsonElement("""{"proactive":"normal","memoryEnabled":true,"speech":"banmal","nickname":null,"bodyColor":null}""")
        assertEquals(CompanionSettings(), ai.hey.model.parseCompanionSettings(old as kotlinx.serialization.json.JsonObject))
        // out-of-range values are ignored, never drawn
        val bad = ai.hey.model.HeyJson.parseToJsonElement("""{"proactive":"normal","memoryEnabled":true,"speech":"banmal","hat":9,"neck":-1,"bodyColor":5}""")
        assertEquals(CompanionSettings(), ai.hey.model.parseCompanionSettings(bad as kotlinx.serialization.json.JsonObject))
        // PATCH carries only what changed; going back to the theme sends bodyColor null
        assertEquals("""{"hat":2}""", ai.hey.model.settingsPatch(CompanionSettings(), CompanionSettings(hat = 2)).toString())
        assertEquals("""{"bodyColor":null}""", ai.hey.model.settingsPatch(CompanionSettings(bodyColor = 1), CompanionSettings()).toString())
    }

    @Test fun `a failed withdraw or unpair reaches the screen state`() {
        assertEquals(AccountFailureUi.Withdraw, settingsUi(CompanionState(accountFailure = ai.hey.session.AccountFailure.Withdraw), null).accountFailure)
        assertEquals(AccountFailureUi.Unpair, settingsUi(CompanionState(accountFailure = ai.hey.session.AccountFailure.Unpair), null).accountFailure)
        assertEquals(AccountFailureUi.None, settingsUi(CompanionState(), null).accountFailure)
    }

    @Test fun `a running unpair or withdraw reaches the screen as pending`() {
        assertEquals(SettingsPending.None, settingsUi(CompanionState(), null).pending)
        assertEquals(SettingsPending.Unpair, settingsUi(CompanionState(settingsAction = SettingsAction.Unpairing), null).pending)
        assertEquals(SettingsPending.Withdraw, settingsUi(CompanionState(settingsAction = SettingsAction.Withdrawing), null).pending)
    }
}
