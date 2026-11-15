package ai.hey.feature.onboarding

import ai.hey.designsystem.component.HeyText
import ai.hey.designsystem.component.OnbSpace
import ai.hey.designsystem.component.OnbBullet
import ai.hey.designsystem.component.OnbCheckRow
import ai.hey.designsystem.component.OnbGap
import ai.hey.designsystem.component.OnbGhostButton
import ai.hey.designsystem.component.OnbLink
import ai.hey.designsystem.component.OnbPanelText
import ai.hey.designsystem.component.OnbPanelTitle
import ai.hey.designsystem.component.OnbPrimaryButton
import ai.hey.designsystem.component.OnbScene
import ai.hey.designsystem.component.OnbShot
import ai.hey.designsystem.component.OnbSide
import ai.hey.designsystem.component.OnbSidePanel
import ai.hey.designsystem.layout.HeyWindow
import ai.hey.designsystem.layout.fillSize
import ai.hey.designsystem.layout.isCompactWindow
import ai.hey.designsystem.theme.HeyTone
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ai.hey.designsystem.component.OnbNoticeScroll

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
 * The consent (O2, studio 4dc8ad3): a full-height panel on the left half over the living whale scene; in a 1/2 or
 * 1/3 window the panel takes the whole width. Pure UI: every press is a callback. Pressing start without the required
 * consent calls [onStartWithoutVoice] (the owner only shows the message; nothing is asked or sent), otherwise [onStart].
 * The studio shows the three promises and the two ticks; the full notice (collection, purpose, retention, refusal, providers)
 * is behind "개인정보처리방침" ([onPolicy]). The law needs the four items visible without a link and the full notice readable
 * inside the panel (it scrolls), so both sit below the promises. The only consent is the required tick plus start;
 * "동의하지 않기" ([onDecline]) records nothing; Back is not consent.
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
            OnbSidePanel(side = OnbSide.Start, compact = compact) {
                OnbPanelTitle(stringResource(R.string.onboard_promise_title), compact = compact)
                OnbGap(OnbSpace.S28)
                OnbBullet(stringResource(R.string.onboard_point_voice))
                OnbGap(OnbSpace.S8)
                OnbBullet(stringResource(R.string.onboard_point_use))
                OnbGap(OnbSpace.S8)
                OnbBullet(stringResource(R.string.onboard_point_memory))
                OnbGap(OnbSpace.S24)
                OnbPanelText(stringResource(R.string.onboard_notice_title))
                OnbGap(OnbSpace.S8)
                OnbPanelText(stringResource(R.string.onboard_short_collect))
                OnbPanelText(stringResource(R.string.onboard_short_purpose))
                OnbPanelText(stringResource(R.string.onboard_short_retention))
                OnbPanelText(stringResource(R.string.onboard_short_refuse))
                OnbGap(OnbSpace.S16)
                OnbNoticeScroll {
                    OnbPanelText(stringResource(R.string.onboard_notice_collect))
                    OnbPanelText(stringResource(R.string.onboard_notice_purpose))
                    OnbPanelText(stringResource(R.string.onboard_notice_retention))
                    OnbPanelText(stringResource(R.string.onboard_notice_refuse))
                    OnbPanelText(stringResource(R.string.onboard_notice_providers))
                }
                OnbGap(OnbSpace.S28)
                OnbCheckRow(
                    label = stringResource(R.string.onboard_check_voice),
                    checked = ui.voice,
                    onCheckedChange = onVoiceChange,
                    enabled = !ui.saving,
                    error = if (ui.voiceRequiredShown && !ui.voice) stringResource(R.string.onboard_error_voice_required) else null,
                )
                OnbCheckRow(
                    label = stringResource(R.string.onboard_check_memory),
                    checked = ui.memory,
                    onCheckedChange = onMemoryChange,
                    enabled = !ui.saving,
                    last = true,
                )
                OnbGap(OnbSpace.S24)
                OnbLink(stringResource(R.string.onboard_policy), onPolicy)
                OnbGap(OnbSpace.S18)
                if (ui.saving) HeyText(stringResource(R.string.onboard_saving), tone = HeyTone.Muted)
                if (ui.failed) HeyText(stringResource(R.string.onboard_error_network), tone = HeyTone.Danger)
                OnbPrimaryButton(
                    stringResource(R.string.onboard_start),
                    if (ui.voice) onStart else onStartWithoutVoice,
                    enabled = !ui.saving,
                    dimmed = !ui.voice,
                )
                OnbGap(OnbSpace.S16)
                OnbGhostButton(stringResource(R.string.onboard_decline), onDecline, enabled = !ui.saving)
            }
        }
    }
}
