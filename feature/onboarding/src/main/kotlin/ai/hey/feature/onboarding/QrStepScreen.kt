package ai.hey.feature.onboarding

import ai.hey.designsystem.component.OnbSpace
import ai.hey.designsystem.component.OnbGap
import ai.hey.designsystem.component.OnbGhostButton
import ai.hey.designsystem.component.OnbPrimaryButton
import androidx.compose.ui.unit.dp
import ai.hey.designsystem.component.CharacterLook
import ai.hey.designsystem.component.CharacterSize
import ai.hey.designsystem.component.CharacterSlot
import ai.hey.designsystem.component.GlassButton
import ai.hey.designsystem.component.HeyButton
import ai.hey.designsystem.component.HeyButtonVariant
import ai.hey.designsystem.component.HeyFadeIn
import ai.hey.designsystem.component.OnbAlign
import ai.hey.designsystem.component.OnbCaption
import ai.hey.designsystem.component.OnbPanelText
import ai.hey.designsystem.component.OnbPanelTitle
import ai.hey.designsystem.component.OnbScene
import ai.hey.designsystem.component.OnbShot
import ai.hey.designsystem.component.OnbSide
import ai.hey.designsystem.component.OnbSidePanel
import ai.hey.designsystem.component.PhoneLinkPanel
import ai.hey.designsystem.component.PhoneLinkUi
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/**
 * O8 phone link (the only place the first phone is linked; later it is settings > 폰 연결), parked only, on the scene:
 * heyri steps to the left and the shared phone-connection panel opens on the right. In a 1/2 or 1/3 window the panel takes the
 * full width under a band with the small character. "나중에 할게" ([onLater]) leaves without a phone; "연결했어" ([onDone]) is
 * pressable once a phone is linked.
 */
@Composable
fun QrStepScreen(
    ui: PhoneLinkUi,
    onLater: () -> Unit,
    onDone: () -> Unit,
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
            val linked = ui is PhoneLinkUi.Linked || ui is PhoneLinkUi.Flash
            OnbSidePanel(
                side = OnbSide.End,
                compact = compact,
                modifier = if (compact) Modifier.carCharacterBand() else Modifier,
            ) {
                OnbPanelTitle(stringResource(R.string.onb_qr_title), compact = compact)
                OnbGap(OnbSpace.S20)
                OnbPanelText(stringResource(R.string.onb_qr_body), large = true)
                OnbGap(OnbSpace.S40)
                PhoneLinkPanel(ui, compact = compact)
                OnbGap(OnbSpace.S40)
                if (ui is PhoneLinkUi.Offline) {
                    OnbPrimaryButton(stringResource(R.string.pair_retry), onRetry)
                    OnbGap(OnbSpace.S16)
                }
                HStack(HeyGap.G16, modifier = Modifier.fillWidth()) {
                    OnbGhostButton(stringResource(R.string.onb_qr_later), onLater, Modifier.grow())
                    OnbPrimaryButton(stringResource(R.string.onb_qr_connected), onDone, Modifier.grow(), enabled = linked, dimmed = !linked)
                }
            }
            if (line.isNotBlank()) OnbCaption(line, Modifier.place(OnbAlign.BottomCenter), narrow = !compact, compact = compact)
        }
    }
}
