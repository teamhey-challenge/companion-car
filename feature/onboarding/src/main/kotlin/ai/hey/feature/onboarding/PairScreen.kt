package ai.hey.feature.onboarding

import ai.hey.designsystem.component.CarFrame
import ai.hey.designsystem.component.CharacterStill
import ai.hey.designsystem.component.HeaderBar
import ai.hey.designsystem.component.HeyButton
import ai.hey.designsystem.component.HeyButtonVariant
import ai.hey.designsystem.component.HeyText
import ai.hey.designsystem.component.QrPanel
import ai.hey.designsystem.layout.HStack
import ai.hey.designsystem.layout.HeyAlign
import ai.hey.designsystem.layout.VStack
import ai.hey.designsystem.layout.fillWidth
import ai.hey.designsystem.theme.HeyGap
import ai.hey.designsystem.theme.HeyTextVariant
import ai.hey.designsystem.theme.HeyTone
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource

/** The base address a phone opens; the pair code is appended (`/p/<pairCode>`, PRD 4.2, contract.md). */
const val PAIR_URL_BASE = "https://hey.lastmile.ltd/p/"

fun pairUrl(pairCode: String): String = PAIR_URL_BASE + pairCode

/** What C2 shows. [Pending.phoneEntered] is the "phone opened W1 but has not consented yet" partial state. */
sealed interface PairUi {
    /** The code is being issued: the QR place stays empty. */
    data object Loading : PairUi
    data class Pending(val code: String, val phoneEntered: Boolean = false) : PairUi
    data object Claimed : PairUi
    data class Expired(val code: String) : PairUi
    data object Failed : PairUi
}

/** C2 phone pairing. Pure UI. The owner navigates away on [PairUi.Claimed] and while driving (QR never shows then). */
@Composable
fun PairScreen(
    ui: PairUi,
    onBack: () -> Unit,
    onLater: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CarFrame(
        modifier = modifier,
        statusStrip = {
            HeaderBar(
                title = stringResource(R.string.onboard_title_bar),
                onBack = onBack,
                backLabel = stringResource(R.string.pair_header_back),
            )
        },
        driving = false,
        actionRail = {
            HStack(HeyGap.G16) {
                if (ui is PairUi.Expired || ui is PairUi.Failed) {
                    HeyButton(stringResource(R.string.pair_retry), onRetry)
                }
                HeyButton(
                    stringResource(R.string.pair_later),
                    onLater,
                    variant = if (ui is PairUi.Expired || ui is PairUi.Failed) HeyButtonVariant.Ghost else HeyButtonVariant.Primary,
                )
            }
        },
    ) {
        HStack(HeyGap.G32, align = HeyAlign.Center, modifier = Modifier.fillWidth()) {
            VStack(HeyGap.G16, modifier = Modifier.grow(1.3f)) {
                HeyText(stringResource(R.string.pair_title), variant = HeyTextVariant.Title)
                HeyText(stringResource(R.string.pair_subtitle), tone = HeyTone.Muted)
                val code = when (ui) {
                    is PairUi.Pending -> ui.code
                    is PairUi.Expired -> ui.code
                    else -> null
                }
                if (code != null) {
                    QrPanel(
                        content = pairUrl(code),
                        code = stringResource(R.string.pair_code_label, code),
                        status = statusText(ui),
                        modifier = if (ui is PairUi.Expired) Modifier.alpha(0.35f) else Modifier,
                    )
                }
                if (code == null || ui is PairUi.Expired) {
                    HeyText(
                        statusText(ui),
                        variant = HeyTextVariant.Heading,
                        tone = when (ui) {
                            PairUi.Failed -> HeyTone.Danger
                            PairUi.Claimed -> HeyTone.Success
                            else -> HeyTone.Muted
                        },
                    )
                }
                if (ui is PairUi.Pending) HeyText(stringResource(R.string.pair_guide), tone = HeyTone.Muted)
            }
            CharacterStill(
                description = stringResource(R.string.onboard_character),
                caption = stringResource(R.string.onboard_caption),
                modifier = Modifier.grow(1f),
            )
        }
    }
}

@Composable
private fun statusText(ui: PairUi): String = stringResource(
    when (ui) {
        PairUi.Loading -> R.string.pair_status_waiting
        is PairUi.Pending -> if (ui.phoneEntered) R.string.pair_status_partial else R.string.pair_status_waiting
        PairUi.Claimed -> R.string.pair_status_claimed
        is PairUi.Expired -> R.string.pair_status_expired
        PairUi.Failed -> R.string.pair_status_failed
    },
)
