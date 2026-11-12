package ai.hey.feature.settings

import ai.hey.designsystem.component.CarFrame
import ai.hey.designsystem.component.HeyButton
import ai.hey.designsystem.component.HeyStatusPill
import ai.hey.designsystem.component.HeyStatusTone
import ai.hey.designsystem.icon.HeyIcons
import ai.hey.designsystem.component.HeyButtonSize
import ai.hey.designsystem.component.HeyButtonVariant
import ai.hey.designsystem.component.HeyConnection
import ai.hey.designsystem.component.HeyKeyboard
import ai.hey.designsystem.component.HeyScrollColumn
import ai.hey.designsystem.component.HeySection
import ai.hey.designsystem.component.HeySegmentOption
import ai.hey.designsystem.component.HeySegmented
import ai.hey.designsystem.component.HeyStepperRow
import ai.hey.designsystem.component.HeyText
import ai.hey.designsystem.component.HeyTextField
import ai.hey.designsystem.component.StatusStrip
import ai.hey.designsystem.layout.HStack
import ai.hey.designsystem.layout.HeyAlign
import ai.hey.designsystem.layout.HeyArrange
import ai.hey.designsystem.layout.VStack
import ai.hey.designsystem.layout.fillSize
import ai.hey.designsystem.layout.fillWidth
import ai.hey.designsystem.theme.HeyGap
import ai.hey.designsystem.theme.HeyTextVariant
import ai.hey.designsystem.theme.HeyTone
import ai.hey.model.CarSettings
import ai.hey.model.HeyConfig
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
 * D8. Values are saved the moment they change ([onChange]), except the two text values: the plate suffix is checked as
 * it is typed and saved when valid; the relay address is only applied with its own button, because applying it
 * reconnects (and, for an address never used before, registers the car there).
 * [connection], [wordmarkParty] and [clock] only dress the status strip.
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
    /** The device could not keep the last change: D8 says so and keeps the value on screen. */
    saveFailed: Boolean = false,
) {
    val context = LocalContext.current
    CarFrame(
        statusStrip = {
            StatusStrip(
                wordmarkParty = wordmarkParty,
                drivingLabel = null,
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
        // One page at 1280x720: title row, then two columns (what the driver hears and sees | the vehicle).
        // A failed save shows in the title row (pill + one short line) so it never pushes the page into a scroll.
        HeyScrollColumn(HeyGap.G24, modifier = Modifier.fillSize()) {
            HStack(HeyGap.G16, modifier = Modifier.fillWidth()) {
                HeyText(stringResource(R.string.settings_title), variant = HeyTextVariant.Title, maxLines = 1)
                if (saveFailed) {
                    HeyStatusPill(stringResource(R.string.settings_save_failed), HeyStatusTone.Danger, icon = HeyIcons.Error)
                    HeyText(stringResource(R.string.settings_save_failed_message), Modifier.grow(), variant = HeyTextVariant.Caption, tone = HeyTone.Muted, maxLines = 2)
                } else {
                    HeyText(stringResource(R.string.settings_subtitle), tone = HeyTone.Muted, maxLines = 1)
                }
            }
            HStack(HeyGap.G32, align = HeyAlign.Start, modifier = Modifier.fillWidth()) {
                VStack(HeyGap.G24, modifier = Modifier.grow()) {
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
                }
                VStack(HeyGap.G24, modifier = Modifier.grow()) {
                    HeySection(stringResource(R.string.settings_vehicle)) {
                        PlateSetting(settings.plateSuffix) { value -> onChange { it.copy(plateSuffix = value) } }
                        RelaySetting(settings.relayUrl) { value -> onChange { it.copy(relayUrl = value) } }
                    }
                }
            }
        }
    }
}

/** The stepper's formatter is not composable, so it gets a Context resolved by the caller. */
private fun gapText(context: Context, seconds: Int): String = context.getString(R.string.settings_gap_value, seconds)

private fun validPlate(value: String) = value.length in 1..8 && value.all(Char::isLetterOrDigit)

/** http(s) with a host; anything else is rejected before it can trigger a connection. */
private fun validRelay(value: String): Boolean {
    val rest = when {
        value.startsWith("http://") -> value.removePrefix("http://")
        value.startsWith("https://") -> value.removePrefix("https://")
        else -> return false
    }
    val host = rest.substringBefore('/').substringBefore(':')
    return host.length >= 3 && host.all { it.isLetterOrDigit() || it == '.' || it == '-' }
}

@Composable
private fun PlateSetting(saved: String, onValid: (String) -> Unit) {
    var draft by remember(saved) { mutableStateOf(saved) }
    HeyTextField(
        label = stringResource(R.string.settings_plate),
        value = draft,
        onValueChange = {
            draft = it
            if (validPlate(it) && it != saved) onValid(it)
        },
        modifier = Modifier.fillWidth(),
        error = if (validPlate(draft)) null else stringResource(R.string.settings_plate_error),
    )
}

@Composable
private fun RelaySetting(saved: String, onApply: (String) -> Unit) {
    var draft by remember(saved) { mutableStateOf(saved) }
    val value = draft.trim().trimEnd('/')
    // The apply button sits beside the field so the vehicle column stays two rows tall.
    HStack(HeyGap.G16, align = HeyAlign.End, modifier = Modifier.fillWidth()) {
        HeyTextField(
            label = stringResource(R.string.settings_relay),
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.grow(),
            keyboard = HeyKeyboard.Uri,
            placeholder = stringResource(R.string.settings_relay_hint),
            error = if (validRelay(value)) null else stringResource(R.string.settings_relay_error),
        )
        HeyButton(
            stringResource(R.string.settings_relay_apply),
            { onApply(value) },
            variant = HeyButtonVariant.Secondary,
            enabled = validRelay(value) && value != saved,
        )
    }
}
