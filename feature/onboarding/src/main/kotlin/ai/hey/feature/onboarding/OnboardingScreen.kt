package ai.hey.feature.onboarding

import ai.hey.designsystem.component.CarFrame
import ai.hey.designsystem.component.CharacterStill
import ai.hey.designsystem.component.CheckRow
import ai.hey.designsystem.component.HeaderBar
import ai.hey.designsystem.component.HeyButton
import ai.hey.designsystem.component.GlassButton
import ai.hey.designsystem.component.HeyButtonVariant
import ai.hey.designsystem.component.HeyIcon
import ai.hey.designsystem.component.HeySection
import ai.hey.designsystem.component.PageHeading
import ai.hey.designsystem.component.ToggleRow
import ai.hey.designsystem.icon.HeyIcons
import androidx.compose.ui.graphics.vector.ImageVector
import ai.hey.designsystem.component.HeyScrollColumn
import ai.hey.designsystem.component.HeyText
import ai.hey.designsystem.layout.HStack
import ai.hey.designsystem.layout.HeyAlign
import ai.hey.designsystem.layout.VStack
import ai.hey.designsystem.layout.fillWidth
import ai.hey.designsystem.theme.HeyGap
import ai.hey.designsystem.theme.HeyTextVariant
import ai.hey.designsystem.theme.HeyTone
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/**
 * The C1 form. [voice] and [memory] both start off (PRD 4.1: nothing is pre-ticked); the screen owner keeps them so
 * they survive recomposition and a failed save keeps the ticks (PRD 4.1.5 Error).
 */
data class ConsentFormUi(
    val voice: Boolean = false,
    val memory: Boolean = false,
    /** The consents are being recorded on the server (`txt-onboard-saving`). */
    val saving: Boolean = false,
    /** The last save failed: "연결이 안 돼요. 다시 눌러 주세요". */
    val failed: Boolean = false,
    /** Start was pressed without the required consent: the screen stays and says why. */
    val voiceRequiredShown: Boolean = false,
)

/**
 * C1 first run. Pure UI: every press is a callback. Pressing start without the required consent calls
 * [onStartWithoutVoice] (the owner only shows the message; nothing is asked or sent), otherwise [onStart].
 */
@Composable
fun OnboardingScreen(
    ui: ConsentFormUi,
    onVoiceChange: (Boolean) -> Unit,
    onMemoryChange: (Boolean) -> Unit,
    onStart: () -> Unit,
    onStartWithoutVoice: () -> Unit,
    onDecline: () -> Unit,
    onPolicy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CarFrame(
        modifier = modifier,
        statusStrip = {
            HeaderBar(
                title = stringResource(R.string.onboard_title_bar),
                stateLabel = stringResource(R.string.onboard_header),
            )
        },
        driving = false,
        actionRail = {
            VStack(HeyGap.G8, modifier = Modifier.fillWidth()) {
                if (ui.saving) HeyText(stringResource(R.string.onboard_saving), tone = HeyTone.Muted)
                if (ui.failed) HeyText(stringResource(R.string.onboard_error_network), tone = HeyTone.Danger)
                HStack(HeyGap.G16) {
                    GlassButton(
                        stringResource(R.string.onboard_start),
                        if (ui.voice) onStart else onStartWithoutVoice,
                        enabled = !ui.saving,
                        dimmed = !ui.voice,
                    )
                    HeyButton(
                        stringResource(R.string.onboard_decline),
                        onDecline,
                        variant = HeyButtonVariant.Ghost,
                        enabled = !ui.saving,
                    )
                }
            }
        },
    ) {
        HStack(HeyGap.G32, align = HeyAlign.Center, modifier = Modifier.fillWidth()) {
            HeyScrollColumn(HeyGap.G16, modifier = Modifier.grow(1.3f)) {
                PageHeading(
                    title = stringResource(R.string.onboard_title),
                    subtitle = stringResource(R.string.onboard_subtitle),
                )
                InfoRow(HeyIcons.Mic, stringResource(R.string.onboard_point_voice_title), stringResource(R.string.onboard_point_voice_body))
                InfoRow(HeyIcons.Book, stringResource(R.string.onboard_point_memory_title), stringResource(R.string.onboard_point_memory_body))
                ToggleRow(
                    label = stringResource(R.string.onboard_toggle_memory),
                    checked = ui.memory,
                    onCheckedChange = onMemoryChange,
                    description = stringResource(R.string.onboard_toggle_memory_body),
                    enabled = !ui.saving,
                )
                CheckRow(
                    label = stringResource(R.string.onboard_check_voice),
                    checked = ui.voice,
                    onCheckedChange = onVoiceChange,
                    enabled = !ui.saving,
                    error = if (ui.voiceRequiredShown && !ui.voice) stringResource(R.string.onboard_error_voice_required) else null,
                )
                HeySection(stringResource(R.string.onboard_notice_title)) {
                    HeyText(stringResource(R.string.onboard_notice_collect), variant = HeyTextVariant.Caption, tone = HeyTone.Muted)
                    HeyText(stringResource(R.string.onboard_notice_purpose), variant = HeyTextVariant.Caption, tone = HeyTone.Muted)
                    HeyText(stringResource(R.string.onboard_notice_retention), variant = HeyTextVariant.Caption, tone = HeyTone.Muted)
                    HeyText(stringResource(R.string.onboard_notice_refuse), variant = HeyTextVariant.Caption, tone = HeyTone.Muted)
                    HeyText(stringResource(R.string.onboard_notice_providers), variant = HeyTextVariant.Caption, tone = HeyTone.Muted)
                }
                HeyButton(stringResource(R.string.onboard_policy), onPolicy, variant = HeyButtonVariant.Ghost)
            }
            CharacterStill(
                description = stringResource(R.string.onboard_character),
                caption = stringResource(R.string.onboard_caption),
                modifier = Modifier.grow(1f),
            )
        }
    }
}

/** One line of the "what this is" list: an icon, a heading and one explanatory line (C1 design). */
@Composable
private fun InfoRow(icon: ImageVector, title: String, body: String) {
    HStack(HeyGap.G16, align = HeyAlign.Start, modifier = Modifier.fillWidth()) {
        HeyIcon(icon, null)
        VStack(HeyGap.G4, modifier = Modifier.grow()) {
            HeyText(title, variant = HeyTextVariant.Heading)
            HeyText(body, variant = HeyTextVariant.Caption, tone = HeyTone.Muted, maxLines = 2)
        }
    }
}
