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
