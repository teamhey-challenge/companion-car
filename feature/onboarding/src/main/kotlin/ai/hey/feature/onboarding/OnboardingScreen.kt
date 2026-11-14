package ai.hey.feature.onboarding

import ai.hey.designsystem.component.CarFrame
import ai.hey.designsystem.component.CharacterStill
import ai.hey.designsystem.component.CheckRow
import ai.hey.designsystem.component.HeaderBar
import ai.hey.designsystem.component.HeyButton
import ai.hey.designsystem.component.HeyButtonSize
import ai.hey.designsystem.component.HeyButtonVariant
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

/** What the server round trip of "동의하고 시작" is doing. */
enum class ConsentPhase { Idle, Saving, NetworkError }

/**
 * The C1 form. [voice] and [memory] both start off (PRD 4.1: nothing is pre-ticked); the screen owner keeps them so
 * they survive recomposition and a failed save keeps the ticks (PRD 4.1.5 Error).
 */
data class OnboardingUi(
    val voice: Boolean = false,
    val memory: Boolean = false,
    val phase: ConsentPhase = ConsentPhase.Idle,
    /** The required consent was refused (start pressed unticked, or "동의하지 않기"): the screen stays and says why. */
    val voiceRequiredShown: Boolean = false,
)

/**
 * C1 first run. Pure UI: every press is a callback. [onStart] is only called with the required consent ticked
 * (otherwise the screen itself shows the required-consent message and nothing is asked or sent).
 */
@Composable
fun OnboardingScreen(
    ui: OnboardingUi,
    onVoiceChange: (Boolean) -> Unit,
    onMemoryChange: (Boolean) -> Unit,
    onStart: () -> Unit,
    onStartWithoutVoice: () -> Unit,
    onDecline: () -> Unit,
    onPolicy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val saving = ui.phase == ConsentPhase.Saving
    CarFrame(
        modifier = modifier,
        statusStrip = {
            HeaderBar(title = stringResource(R.string.onboard_title_bar), backLabel = stringResource(R.string.pair_header_back))
        },
        driving = false,
        actionRail = {
            VStack(HeyGap.G8, modifier = Modifier.fillWidth()) {
                if (saving) HeyText(stringResource(R.string.onboard_saving), tone = HeyTone.Muted)
                if (ui.phase == ConsentPhase.NetworkError) {
                    HeyText(stringResource(R.string.onboard_error_network), tone = HeyTone.Danger)
                }
                HStack(HeyGap.G16) {
                    HeyButton(
                        stringResource(R.string.onboard_start),
                        if (ui.voice) onStart else onStartWithoutVoice,
                        size = HeyButtonSize.Large,
                        enabled = !saving,
                        loading = saving,
                    )
                    HeyButton(
                        stringResource(R.string.onboard_decline),
                        onDecline,
                        variant = HeyButtonVariant.Ghost,
                        enabled = !saving,
                    )
                }
            }
        },
    ) {
        HStack(HeyGap.G32, align = HeyAlign.Center, modifier = Modifier.fillWidth()) {
            HeyScrollColumn(HeyGap.G16, modifier = Modifier.grow(1.3f)) {
                HeyText(stringResource(R.string.onboard_title), variant = HeyTextVariant.Title)
                HeyText(stringResource(R.string.onboard_notice_collect), tone = HeyTone.Muted)
                HeyText(stringResource(R.string.onboard_notice_purpose), tone = HeyTone.Muted)
                HeyText(stringResource(R.string.onboard_notice_retention), tone = HeyTone.Muted)
                HeyText(stringResource(R.string.onboard_notice_refuse), tone = HeyTone.Muted)
                HeyText(stringResource(R.string.onboard_notice_providers), tone = HeyTone.Muted)
                CheckRow(
                    label = stringResource(R.string.onboard_check_voice),
                    checked = ui.voice,
                    onCheckedChange = onVoiceChange,
                    enabled = !saving,
                    error = if (ui.voiceRequiredShown && !ui.voice) stringResource(R.string.onboard_error_voice_required) else null,
                )
                CheckRow(
                    label = stringResource(R.string.onboard_check_memory),
                    checked = ui.memory,
                    onCheckedChange = onMemoryChange,
                    enabled = !saving,
                )
                HeyButton(stringResource(R.string.onboard_policy), onPolicy, variant = HeyButtonVariant.Secondary)
            }
            CharacterStill(
                description = stringResource(R.string.onboard_character),
                caption = stringResource(R.string.onboard_caption),
                modifier = Modifier.grow(1f),
            )
        }
    }
}
