package ai.hey.feature.onboarding

import ai.hey.designsystem.component.CheckRow
import ai.hey.designsystem.component.GlassButton
import ai.hey.designsystem.component.HeyButton
import ai.hey.designsystem.component.HeyButtonVariant
import ai.hey.designsystem.component.HeyText
import ai.hey.designsystem.component.OnbPanelText
import ai.hey.designsystem.component.OnbPanelTitle
import ai.hey.designsystem.component.OnbScene
import ai.hey.designsystem.component.OnbShot
import ai.hey.designsystem.component.OnbSide
import ai.hey.designsystem.component.OnbSidePanel
import ai.hey.designsystem.component.ToggleRow
import ai.hey.designsystem.layout.HStack
import ai.hey.designsystem.layout.HeyWindow
import ai.hey.designsystem.layout.VStack
import ai.hey.designsystem.layout.fillSize
import ai.hey.designsystem.layout.fillWidth
import ai.hey.designsystem.layout.isCompactWindow
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
 * The consent (D-48 O2, studio PLEOS-190): a full-height panel on the left half over the living whale scene; in a 1/2 or
 * 1/3 window the panel takes the whole width. Pure UI: every press is a callback. Pressing start without the required
 * consent calls [onStartWithoutVoice] (the owner only shows the message; nothing is asked or sent), otherwise [onStart].
 * The notice texts stay in full (collection, purpose, retention, refusal, providers): the law asks for them, the studio's
 * short three-line version is not enough.
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
    HeyWindow(modifier) { width, height ->
        val compact = isCompactWindow(width, height)
        OnbScene(OnbShot.Whale, Modifier.fillSize()) {
            OnbSidePanel(
                side = OnbSide.Start,
                compact = compact,
                footer = {
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
                OnbPanelTitle(stringResource(R.string.onboard_promise_title), compact = compact)
                OnbPanelText(stringResource(R.string.onboard_point_voice_body))
                OnbPanelText(stringResource(R.string.onboard_point_memory_body))
                ToggleRow(
                    label = stringResource(R.string.onboard_toggle_memory),
                    checked = ui.memory,
                    onCheckedChange = onMemoryChange,
                    description = stringResource(R.string.onboard_toggle_memory_body),
                    enabled = !ui.saving,
                    modifier = Modifier.fillWidth(),
                )
                CheckRow(
                    label = stringResource(R.string.onboard_check_voice),
                    checked = ui.voice,
                    onCheckedChange = onVoiceChange,
                    enabled = !ui.saving,
                    error = if (ui.voiceRequiredShown && !ui.voice) stringResource(R.string.onboard_error_voice_required) else null,
                    modifier = Modifier.fillWidth(),
                )
                HeyText(stringResource(R.string.onboard_notice_title), variant = HeyTextVariant.Heading, tone = HeyTone.Muted)
                HeyText(stringResource(R.string.onboard_notice_collect), variant = HeyTextVariant.Caption, tone = HeyTone.Muted)
                HeyText(stringResource(R.string.onboard_notice_purpose), variant = HeyTextVariant.Caption, tone = HeyTone.Muted)
                HeyText(stringResource(R.string.onboard_notice_retention), variant = HeyTextVariant.Caption, tone = HeyTone.Muted)
                HeyText(stringResource(R.string.onboard_notice_refuse), variant = HeyTextVariant.Caption, tone = HeyTone.Muted)
                HeyText(stringResource(R.string.onboard_notice_providers), variant = HeyTextVariant.Caption, tone = HeyTone.Muted)
                HeyButton(stringResource(R.string.onboard_policy), onPolicy, variant = HeyButtonVariant.Ghost)
            }
        }
    }
}
