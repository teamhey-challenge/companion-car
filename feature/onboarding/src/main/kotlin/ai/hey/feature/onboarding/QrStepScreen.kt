package ai.hey.feature.onboarding

import ai.hey.designsystem.component.CharacterLook
import ai.hey.designsystem.component.HeyButton
import ai.hey.designsystem.component.HeyButtonVariant
import ai.hey.designsystem.component.HeyText
import ai.hey.designsystem.component.OnbAlign
import ai.hey.designsystem.component.OnbCaption
import ai.hey.designsystem.component.OnbPanelText
import ai.hey.designsystem.component.OnbPanelTitle
import ai.hey.designsystem.component.OnbScene
import ai.hey.designsystem.component.OnbShot
import ai.hey.designsystem.component.OnbSide
import ai.hey.designsystem.component.OnbSidePanel
import ai.hey.designsystem.component.CharacterSize
import ai.hey.designsystem.component.CharacterSlot
import ai.hey.designsystem.component.GlassButton
import ai.hey.designsystem.component.HeyFadeIn
import ai.hey.designsystem.component.QrPanel
import ai.hey.designsystem.layout.HStack
import ai.hey.designsystem.layout.HeyAlign
import ai.hey.designsystem.layout.HeyArrange
import ai.hey.designsystem.layout.HeyWindow
import ai.hey.designsystem.layout.VStack
import ai.hey.designsystem.layout.carCharacterBand
import ai.hey.designsystem.layout.fillSize
import ai.hey.designsystem.layout.fillWidth
import ai.hey.designsystem.layout.isCompactWindow
import ai.hey.designsystem.theme.HeyGap
import ai.hey.designsystem.theme.HeyTextVariant
import ai.hey.designsystem.theme.HeyTone
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource

/**
 * O8 phone link (parked only), on the scene: heyri steps to the left and the QR panel opens on the right. In a 1/2 or 1/3
 * window the panel takes the full width under a band with the small character. [onLater] is "나중에 할게", [onDone] is
 * "연결했어" (offered once the phone has claimed the code, or any time as the studio has it).
 */
@Composable
fun QrStepScreen(
    ui: PairUi,
    onLater: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    look: CharacterLook = CharacterLook(),
    line: String = "",
) {
    HeyWindow(modifier) { width, height ->
        val compact = isCompactWindow(width, height)
        OnbScene(OnbShot.Sea, Modifier.fillSize()) {
            if (compact) {
                VStack(HeyGap.G16, modifier = Modifier.place(OnbAlign.TopCenter).fillWidth(), align = HeyAlign.Center) {
                    HeyFadeIn { CharacterSlot(CharacterSize.Small, look = look, contentDescription = stringResource(R.string.onboard_character)) }
                }
            } else {
                HStack(HeyGap.G32, modifier = Modifier.place(OnbAlign.Center).fillSize()) {
                    VStack(HeyGap.G16, modifier = Modifier.grow(), align = HeyAlign.Center, arrange = HeyArrange.Center) {
                        HeyFadeIn { CharacterSlot(CharacterSize.Stage, look = look, contentDescription = stringResource(R.string.onboard_character)) }
                    }
                    VStack(HeyGap.G16, modifier = Modifier.grow()) {}
                }
            }
            OnbSidePanel(
                side = OnbSide.End,
                compact = compact,
                modifier = if (compact) Modifier.carCharacterBand() else Modifier,
                footer = {
                    if (ui is PairUi.Expired || ui is PairUi.Failed) {
                        GlassButton(stringResource(R.string.pair_retry), onRetry)
                    }
                    HStack(HeyGap.G16) {
                        GlassButton(stringResource(R.string.onb_qr_connected), onLater, enabled = ui is PairUi.Claimed, dimmed = ui !is PairUi.Claimed)
                        HeyButton(stringResource(R.string.onb_qr_later), onLater, variant = HeyButtonVariant.Ghost)
                    }
                },
            ) {
                OnbPanelTitle(stringResource(R.string.onb_qr_title), compact = compact)
                OnbPanelText(stringResource(R.string.onb_qr_body))
                val code = when (ui) {
                    is PairUi.Pending -> ui.code
                    is PairUi.Expired -> ui.code
                    else -> null
                }
                if (code != null) {
                    QrPanel(
                        content = pairUrl(code),
                        code = code,
                        status = if (ui is PairUi.Expired) stringResource(R.string.pair_status_expired) else statusOf(ui),
                        modifier = if (ui is PairUi.Expired) Modifier.alpha(0.35f) else Modifier,
                        compact = compact,
                    )
                } else {
                    HeyText(
                        statusOf(ui),
                        variant = HeyTextVariant.Heading,
                        tone = if (ui == PairUi.Failed) HeyTone.Danger else if (ui == PairUi.Claimed) HeyTone.Success else HeyTone.Muted,
                    )
                }
            }
            if (line.isNotBlank()) OnbCaption(line, Modifier.place(OnbAlign.BottomCenter), narrow = !compact, compact = compact)
        }
    }
}

@Composable
private fun statusOf(ui: PairUi): String = stringResource(
    when (ui) {
        PairUi.Loading -> R.string.pair_status_waiting
        is PairUi.Pending -> if (ui.phoneEntered) R.string.pair_status_partial else R.string.pair_status_waiting
        PairUi.Claimed -> R.string.pair_status_claimed
        is PairUi.Expired -> R.string.pair_status_expired
        PairUi.Failed -> R.string.pair_status_failed
    },
)
