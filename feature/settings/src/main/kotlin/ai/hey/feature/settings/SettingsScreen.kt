package ai.hey.feature.settings

import ai.hey.designsystem.component.CarFrame
import ai.hey.designsystem.component.HeyButton
import ai.hey.designsystem.component.HeyButtonSize
import ai.hey.designsystem.component.HeyButtonVariant
import ai.hey.designsystem.component.HeyConnection
import ai.hey.designsystem.component.HeyKeyboard
import ai.hey.designsystem.component.HeyScrollColumn
import ai.hey.designsystem.component.HeySection
import ai.hey.designsystem.component.HeySegmentOption
import ai.hey.designsystem.component.HeySegmented
import ai.hey.designsystem.component.HeyStepperRow
import ai.hey.designsystem.component.HeySwitchRow
import ai.hey.designsystem.component.HeyText
import ai.hey.designsystem.component.HeyTextField
import ai.hey.designsystem.component.StatusStrip
import ai.hey.designsystem.layout.HStack
import ai.hey.designsystem.layout.HeyArrange
import ai.hey.designsystem.layout.VStack
import ai.hey.designsystem.layout.fillSize
import ai.hey.designsystem.layout.fillWidth
import ai.hey.designsystem.theme.HeyGap
import ai.hey.designsystem.theme.HeyTextVariant
import ai.hey.designsystem.theme.HeyTone
import ai.hey.model.CarSettings
import ai.hey.model.HeyConfig
import ai.hey.model.PresetId
import ai.hey.model.SpeechRate
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

/**
 * D8. Values are saved the moment they change ([onChange]). The two text values are validated; an invalid draft stays
 * on screen with its error and is not saved. [connection], [wordmarkParty] and [clock] only dress the status strip.
 */
@Composable
fun SettingsScreen(
    settings: CarSettings,
    onChange: ((CarSettings) -> CarSettings) -> Unit,
    onClose: () -> Unit,
    connection: HeyConnection,
    wordmarkParty: Int,
    clock: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    CarFrame(
        statusStrip = {
            StatusStrip(
                wordmarkParty = wordmarkParty,
                drivingLabel = stringResource(R.string.settings_title),
                connection = connection,
                clock = clock,
            )
        },
        driving = false,
        modifier = modifier,
        actionRail = {
            HStack(HeyGap.G16, arrange = HeyArrange.End, modifier = Modifier.fillWidth()) {
                HeyButton(stringResource(R.string.settings_close), onClose, variant = HeyButtonVariant.Secondary, size = HeyButtonSize.Large)
            }
        },
    ) {
        HeyScrollColumn(HeyGap.G24, modifier = Modifier.fillSize()) {
            VStack(HeyGap.G8) {
                HeyText(stringResource(R.string.settings_title), variant = HeyTextVariant.Title, maxLines = 1)
                HeyText(stringResource(R.string.settings_subtitle), tone = HeyTone.Muted, maxLines = 1)
            }
            HeySection(stringResource(R.string.settings_presets)) {
                PresetId.entries.forEach { id ->
                    HeySwitchRow(
                        label = stringResource(presetLabel(id)),
                        checked = id in settings.enabledPresets,
                        onCheckedChange = { on -> onChange { it.withPreset(id, on) } },
                        modifier = Modifier.fillWidth(),
                        enabled = id.driverCanDisable,
                    )
                }
            }
            HeySection(stringResource(R.string.settings_speech)) {
                HeyStepperRow(
                    label = stringResource(R.string.settings_gap),
                    value = settings.speechGapSec,
                    range = HeyConfig.SPEECH_GAP_MIN_SEC..HeyConfig.SPEECH_GAP_MAX_SEC,
                    onChange = { value ->
                        onChange { it.copy(speechGapSec = value.coerceIn(HeyConfig.SPEECH_GAP_MIN_SEC, HeyConfig.SPEECH_GAP_MAX_SEC)) }
                    },
                    modifier = Modifier.fillWidth(),
                    format = { gapText(context, it) },
                )
                HeyText(stringResource(R.string.settings_rate), variant = HeyTextVariant.Label, tone = HeyTone.Muted, maxLines = 1)
                HeySegmented(
                    options = listOf(
                        HeySegmentOption(SpeechRate.Slow, stringResource(R.string.settings_rate_slow)),
                        HeySegmentOption(SpeechRate.Normal, stringResource(R.string.settings_rate_normal)),
                        HeySegmentOption(SpeechRate.Fast, stringResource(R.string.settings_rate_fast)),
                    ),
                    selected = settings.speechRate,
                    onSelect = { rate -> onChange { it.copy(speechRate = rate) } },
                    modifier = Modifier.fillWidth(),
                )
            }
            HeySection(stringResource(R.string.settings_theme)) {
                HeySegmented(
                    options = listOf(
                        HeySegmentOption(true, stringResource(R.string.settings_theme_dark)),
                        HeySegmentOption(false, stringResource(R.string.settings_theme_light)),
                    ),
                    selected = settings.dark,
                    onSelect = { dark -> onChange { it.copy(dark = dark) } },
                    modifier = Modifier.fillWidth(),
                )
            }
            HeySection(stringResource(R.string.settings_vehicle)) {
                TextSetting(
                    label = stringResource(R.string.settings_plate),
                    saved = settings.plateSuffix,
                    valid = { it.length in 1..8 && it.all(Char::isLetterOrDigit) },
                    errorText = stringResource(R.string.settings_plate_error),
                    onValid = { value -> onChange { it.copy(plateSuffix = value) } },
                )
                TextSetting(
                    label = stringResource(R.string.settings_relay),
                    saved = settings.relayUrl,
                    keyboard = HeyKeyboard.Uri,
                    placeholder = stringResource(R.string.settings_relay_hint),
                    valid = { it.startsWith("http://") || it.startsWith("https://") },
                    errorText = stringResource(R.string.settings_relay_error),
                    onValid = { value -> onChange { it.copy(relayUrl = value.trimEnd('/')) } },
                )
                HeyText(stringResource(R.string.settings_device_note), variant = HeyTextVariant.Caption, tone = HeyTone.Muted, maxLines = 2)
            }
        }
    }
}

/** The stepper's formatter is not composable, so it gets a Context resolved by the caller. */
private fun gapText(context: Context, seconds: Int): String = context.getString(R.string.settings_gap_value, seconds)

/** A text value with a local draft: typing never touches the saved value until the draft is valid. */
@Composable
private fun TextSetting(
    label: String,
    saved: String,
    valid: (String) -> Boolean,
    errorText: String,
    onValid: (String) -> Unit,
    keyboard: HeyKeyboard = HeyKeyboard.Text,
    placeholder: String? = null,
) {
    var draft by remember(saved) { mutableStateOf(saved) }
    HeyTextField(
        label = label,
        value = draft,
        onValueChange = {
            draft = it
            if (valid(it) && it != saved) onValid(it)
        },
        modifier = Modifier.fillWidth(),
        keyboard = keyboard,
        placeholder = placeholder,
        error = if (valid(draft)) null else errorText,
    )
}

private fun presetLabel(id: PresetId): Int = when (id) {
    PresetId.Slower -> R.string.settings_preset_slower
    PresetId.Cooler -> R.string.settings_preset_cooler
    PresetId.Warmer -> R.string.settings_preset_warmer
    PresetId.Window -> R.string.settings_preset_window
    PresetId.Card -> R.string.settings_preset_card
    PresetId.HowLong -> R.string.settings_preset_how_long
    PresetId.StopHere -> R.string.settings_preset_stop_here
    PresetId.Trunk -> R.string.settings_preset_trunk
}
