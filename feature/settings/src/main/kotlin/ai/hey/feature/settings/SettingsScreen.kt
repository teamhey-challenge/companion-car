package ai.hey.feature.settings

import ai.hey.designsystem.component.CarFrame
import ai.hey.designsystem.component.ChoiceRow
import ai.hey.designsystem.component.ConfirmDialog
import ai.hey.designsystem.component.HeaderBar
import ai.hey.designsystem.component.HeyButton
import ai.hey.designsystem.component.HeyButtonVariant
import ai.hey.designsystem.component.HeyKeyboard
import ai.hey.designsystem.component.HeyLayered
import ai.hey.designsystem.component.HeyNotice
import ai.hey.designsystem.component.HeyScrollColumn
import ai.hey.designsystem.component.HeySegmentOption
import ai.hey.designsystem.component.HeyStatusTone
import ai.hey.designsystem.component.HeyTextField
import ai.hey.designsystem.component.PageHeading
import ai.hey.designsystem.component.ToggleRow
import ai.hey.designsystem.icon.HeyIcons
import ai.hey.designsystem.layout.HStack
import ai.hey.designsystem.layout.HeyAlign
import ai.hey.designsystem.layout.fillSize
import ai.hey.designsystem.layout.fillWidth
import ai.hey.designsystem.theme.HeyGap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/** 먼저 말 걸기 단계. Off hides the frequency chips; the minutes are the contract values (20 / 10 / 5). */
enum class ProactiveUi { Off, Low, Normal, High }

enum class SpeechUi { Banmal, Jondaetmal }

/** The settings fields that can have a save in flight (each saves on its own, PRD 4.7.5 Partial = n/a). */
enum class SettingField { Proactive, Memory, Speech }

data class SettingsUi(
    val proactive: ProactiveUi = ProactiveUi.Normal,
    val memoryEnabled: Boolean = true,
    val speech: SpeechUi = SpeechUi.Banmal,
    /** The phone is linked: only then "폰 연결 해제" exists (AC-4.7-09). */
    val phonePaired: Boolean = false,
    /** Fields whose save request has been running; the toggle shows work after 2 s, the screen blocks that field. */
    val saving: Set<SettingField> = emptySet(),
)

/** The debug-only server address field; null in release builds, which never show it. */
class ServerAddress(val saved: String, val onApply: (String) -> Unit)

/** The confirmation that is open; the screen owns it, the caller learns only the final answer. */
private enum class Confirm { None, Unpair, Withdraw }

/**
 * C7 (PRD 4.7). Every change goes to [onChange] at once; a failed save is reported by the caller as [notice] and as the
 * old value coming back in [settings]. Dialogs: unpair (memory stays) and withdraw (memory is deleted at once).
 * The theme is not a setting: it follows the system.
 */
@Composable
fun SettingsScreen(
    settings: SettingsUi,
    onBack: () -> Unit,
    onProactive: (ProactiveUi) -> Unit,
    onMemoryEnabled: (Boolean) -> Unit,
    onSpeech: (SpeechUi) -> Unit,
    onOpenMemory: () -> Unit,
    /** C7 is the only way to C2 (D-33): shown while no phone is linked. */
    onOpenPair: () -> Unit,
    onUnpair: () -> Unit,
    onWithdraw: () -> Unit,
    modifier: Modifier = Modifier,
    notice: String? = null,
    server: ServerAddress? = null,
) {
    var confirm by remember { mutableStateOf(Confirm.None) }
    if (confirm == Confirm.Unpair && !settings.phonePaired) confirm = Confirm.None
    HeyLayered(
        modifier = modifier.fillSize(),
        overlay = {
            when (confirm) {
                Confirm.Unpair -> ConfirmDialog(
                    title = stringResource(R.string.settings_unpair_title),
                    message = stringResource(R.string.settings_unpair_message),
                    confirmText = stringResource(R.string.settings_unpair_confirm),
                    dismissText = stringResource(R.string.settings_cancel),
                    onConfirm = { confirm = Confirm.None; onUnpair() },
                    onDismiss = { confirm = Confirm.None },
                    modifier = Modifier.fillSize(),
                )
                Confirm.Withdraw -> ConfirmDialog(
                    title = stringResource(R.string.settings_withdraw_title),
                    message = stringResource(R.string.settings_withdraw_message),
                    confirmText = stringResource(R.string.settings_withdraw_confirm),
                    dismissText = stringResource(R.string.settings_cancel),
                    onConfirm = { confirm = Confirm.None; onWithdraw() },
                    onDismiss = { confirm = Confirm.None },
                    modifier = Modifier.fillSize(),
                    danger = true,
                )
                Confirm.None -> Unit
            }
        },
        notice = notice?.let { text -> { HeyNotice(text, HeyStatusTone.Danger) } },
    ) {
        CarFrame(
            statusStrip = {
                HeaderBar(
                    title = stringResource(R.string.settings_app_name),
                    onBack = onBack,
                    backLabel = stringResource(R.string.settings_back),
                    stateLabel = stringResource(R.string.settings_parked),
                )
            },
            driving = false,
        ) {
            HeyScrollColumn(HeyGap.G24, modifier = Modifier.fillSize()) {
                PageHeading(stringResource(R.string.settings_title), subtitle = stringResource(R.string.settings_subtitle))
                val proactiveSaving = SettingField.Proactive in settings.saving
                ToggleRow(
                    label = stringResource(R.string.settings_proactive),
                    description = stringResource(R.string.settings_proactive_desc),
                    checked = settings.proactive != ProactiveUi.Off,
                    // Turning it back on returns to the usual step, not to a step the driver never picked.
                    onCheckedChange = { on -> onProactive(if (on) ProactiveUi.Normal else ProactiveUi.Off) },
                    saving = proactiveSaving,
                    modifier = Modifier.fillWidth(),
                )
                if (settings.proactive != ProactiveUi.Off) {
                    ChoiceRow(
                        options = listOf(
                            HeySegmentOption(ProactiveUi.Low, stringResource(R.string.settings_freq_low)),
                            HeySegmentOption(ProactiveUi.Normal, stringResource(R.string.settings_freq_normal)),
                            HeySegmentOption(ProactiveUi.High, stringResource(R.string.settings_freq_high)),
                        ),
                        selected = settings.proactive,
                        onSelect = onProactive,
                        label = stringResource(R.string.settings_freq),
                        enabled = !proactiveSaving,
                        modifier = Modifier.fillWidth(),
                    )
                }
                ToggleRow(
                    label = stringResource(R.string.settings_memory),
                    description = stringResource(R.string.settings_memory_desc),
                    checked = settings.memoryEnabled,
                    onCheckedChange = onMemoryEnabled,
                    saving = SettingField.Memory in settings.saving,
                    modifier = Modifier.fillWidth(),
                )
                ChoiceRow(
                    options = listOf(
                        HeySegmentOption(SpeechUi.Banmal, stringResource(R.string.settings_speech_banmal)),
                        HeySegmentOption(SpeechUi.Jondaetmal, stringResource(R.string.settings_speech_jondaetmal)),
                    ),
                    selected = settings.speech,
                    onSelect = onSpeech,
                    label = stringResource(R.string.settings_speech),
                    enabled = SettingField.Speech !in settings.saving,
                    modifier = Modifier.fillWidth(),
                )
                HStack(HeyGap.G16, align = HeyAlign.Center) {
                    HeyButton(
                        stringResource(R.string.settings_open_memory),
                        onOpenMemory,
                        variant = HeyButtonVariant.Secondary,
                        icon = HeyIcons.Book,
                    )
                    if (settings.phonePaired) {
                        HeyButton(
                            stringResource(R.string.settings_unpair),
                            { confirm = Confirm.Unpair },
                            variant = HeyButtonVariant.Secondary,
                            icon = HeyIcons.Link,
                        )
                    } else {
                        HeyButton(
                            stringResource(R.string.settings_pair),
                            onOpenPair,
                            variant = HeyButtonVariant.Secondary,
                            icon = HeyIcons.Qr,
                        )
                    }
                }
                if (server != null) ServerSetting(server.saved, server.onApply)
                HeyButton(
                    stringResource(R.string.settings_withdraw),
                    { confirm = Confirm.Withdraw },
                    variant = HeyButtonVariant.Danger,
                    icon = HeyIcons.Trash,
                )
            }
        }
    }
}

/** http(s) with a host; anything else is rejected before it can trigger a connection. */
internal fun validServer(value: String): Boolean {
    val rest = when {
        value.startsWith("http://") -> value.removePrefix("http://")
        value.startsWith("https://") -> value.removePrefix("https://")
        else -> return false
    }
    val host = rest.substringBefore('/').substringBefore(':')
    return host.length >= 3 && host.all { it.isLetterOrDigit() || it == '.' || it == '-' }
}

@Composable
private fun ServerSetting(saved: String, onApply: (String) -> Unit) {
    var draft by remember(saved) { mutableStateOf(saved) }
    val value = draft.trim().trimEnd('/')
    HStack(HeyGap.G16, align = HeyAlign.End, modifier = Modifier.fillWidth()) {
        HeyTextField(
            label = stringResource(R.string.settings_server),
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.grow(),
            keyboard = HeyKeyboard.Uri,
            placeholder = stringResource(R.string.settings_server_hint),
            error = if (validServer(value)) null else stringResource(R.string.settings_server_error),
        )
        HeyButton(
            stringResource(R.string.settings_server_apply),
            { onApply(value) },
            variant = HeyButtonVariant.Secondary,
            enabled = validServer(value) && value != saved,
        )
    }
}
