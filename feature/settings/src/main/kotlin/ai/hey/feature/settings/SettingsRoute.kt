package ai.hey.feature.settings

import ai.hey.designsystem.component.CharacterLook
import ai.hey.model.ProactiveLevel
import ai.hey.model.SpeechStyle
import ai.hey.session.CompanionCommands
import ai.hey.session.SettingsAction
import ai.hey.session.AccountFailure
import ai.hey.session.CompanionState
import ai.hey.session.SettingsSave
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

internal fun ProactiveLevel.ui() = when (this) {
    ProactiveLevel.Off -> ProactiveUi.Off
    ProactiveLevel.Low -> ProactiveUi.Low
    ProactiveLevel.Normal -> ProactiveUi.Normal
    ProactiveLevel.High -> ProactiveUi.High
}

internal fun ProactiveUi.level() = when (this) {
    ProactiveUi.Off -> ProactiveLevel.Off
    ProactiveUi.Low -> ProactiveLevel.Low
    ProactiveUi.Normal -> ProactiveLevel.Normal
    ProactiveUi.High -> ProactiveLevel.High
}

/**
 * [state] to what C7 draws. [lastField] is the field the driver changed last: the repository saves one change at a
 * time, so that is the field whose save is running.
 */
fun settingsUi(state: CompanionState, lastField: SettingField?): SettingsUi = SettingsUi(
    name = state.settings.nickname,
    proactive = state.settings.proactive.ui(),
    memoryEnabled = state.settings.memoryEnabled,
    speech = if (state.settings.speech == SpeechStyle.Jondaetmal) SpeechUi.Jondaetmal else SpeechUi.Banmal,
    phonePaired = state.paired,
    look = CharacterLook(state.settings.bodyColor, state.settings.hat, state.settings.neck),
    saving = if (state.settingsSave == SettingsSave.Saving && lastField != null) setOf(lastField) else emptySet(),
    accountFailure = if (state.accountFailure == AccountFailure.Withdraw) AccountFailureUi.Withdraw else AccountFailureUi.None,
    pending = if (state.settingsAction == SettingsAction.Withdrawing) SettingsPending.Withdraw else SettingsPending.None,
)

/**
 * C7 wired to the companion repository. Opening it refreshes the server settings and whether a phone is linked.
 * A failed save puts the old value back (repository) and shows "저장하지 못했어요" until the next change.
 */
@Composable
fun SettingsRoute(
    state: CompanionState,
    commands: CompanionCommands,
    onBack: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenPair: () -> Unit,
    modifier: Modifier = Modifier,
    /** Debug builds only; release passes null and the field does not exist. */
    server: ServerAddress? = null,
) {
    LaunchedEffect(Unit) { commands.refresh() }
    var last by remember { mutableStateOf<SettingField?>(null) }
    SettingsScreen(
        settings = settingsUi(state, last),
        onBack = onBack,
        onProactive = { level -> last = SettingField.Proactive; commands.updateSettings { it.copy(proactive = level.level()) } },
        onMemoryEnabled = { on -> last = SettingField.Memory; commands.updateSettings { it.copy(memoryEnabled = on) } },
        onSpeech = { style ->
            last = SettingField.Speech
            commands.updateSettings { it.copy(speech = if (style == SpeechUi.Jondaetmal) SpeechStyle.Jondaetmal else SpeechStyle.Banmal) }
        },
        onName = { name -> last = SettingField.Name; commands.updateSettings { it.copy(nickname = name) } },
        onLook = { look ->
            last = SettingField.Look
            commands.updateSettings { it.copy(bodyColor = look.bodyColor, hat = look.hat, neck = look.neck) }
        },
        onOpenMemory = onOpenMemory,
        onOpenPair = onOpenPair,
        onWithdraw = commands::withdrawRecordConsent,
        modifier = modifier,
        notice = if (state.settingsSave == SettingsSave.Failed) stringResource(R.string.settings_save_failed) else null,
        server = server,
    )
}
