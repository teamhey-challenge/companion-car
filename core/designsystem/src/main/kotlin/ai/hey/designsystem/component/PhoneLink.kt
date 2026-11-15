package ai.hey.designsystem.component

import ai.hey.designsystem.R
import ai.hey.designsystem.icon.HeyIcons
import ai.hey.designsystem.layout.HeyAlign
import ai.hey.designsystem.layout.VStack
import ai.hey.designsystem.theme.HeyGap
import ai.hey.designsystem.theme.HeyTextVariant
import ai.hey.designsystem.theme.HeyTone
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private val QrFull = 320.dp
private val QrCompact = 240.dp

/** The address a phone opens from the car's QR; the pairing code is appended (`/p/<pairCode>`, §4.6). */
const val PHONE_LINK_URL_BASE = "https://hey.lastmile.ltd/p/"

/** What the phone-connection panel shows (car settings > 폰 연결 and onboarding O8). The owner maps the session state to one of these. */
sealed interface PhoneLinkUi {
    /** A code is being made: no QR yet. */
    data object Loading : PhoneLinkUi

    /** Parked and linkable: the QR carries [code] and the same [code] is written under it (for typing it on the phone). */
    data class Code(val code: String) : PhoneLinkUi

    /** The moment a phone linked: "연결됐어요!" for a short while. [accountName] = the Google given name, null when unknown. */
    data class Flash(val accountName: String?) : PhoneLinkUi

    /** A phone is linked. */
    data class Linked(val accountName: String?) : PhoneLinkUi

    /** The server cannot be reached: no code can be made, so no QR (§6.6 "연결이 끊겼어요"). */
    data object Offline : PhoneLinkUi
}

/**
 * The phone-connection content, one place for both screens. Never draws a QR unless the owner passes [PhoneLinkUi.Code];
 * the owner passes it only while the car is parked.
 */
@Composable
fun PhoneLinkPanel(ui: PhoneLinkUi, modifier: Modifier = Modifier, compact: Boolean = false) {
    when (ui) {
        PhoneLinkUi.Loading -> VStack(HeyGap.G16, modifier = modifier, align = HeyAlign.Center) {
            HeyText(stringResource(R.string.phone_link_loading), variant = HeyTextVariant.Heading, tone = HeyTone.Muted)
        }
        is PhoneLinkUi.Code -> QrPanel(
            content = PHONE_LINK_URL_BASE + ui.code,
            code = ui.code,
            status = stringResource(R.string.phone_link_scan),
            modifier = modifier,
            qrSize = if (compact) QrCompact else QrFull,
        )
        is PhoneLinkUi.Flash -> VStack(HeyGap.G16, modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite }, align = HeyAlign.Center) {
            HeyIcon(HeyIcons.Check, null, size = HeyIconSize.Large, tone = HeyTone.Success)
            HeyText(stringResource(R.string.phone_link_flash), variant = HeyTextVariant.Title, tone = HeyTone.Success, align = HeyTextAlign.Center)
            HeyText(accountLine(ui.accountName), tone = HeyTone.Muted, maxLines = 2, align = HeyTextAlign.Center)
        }
        is PhoneLinkUi.Linked -> VStack(HeyGap.G16, modifier = modifier, align = HeyAlign.Center) {
            HeyIcon(HeyIcons.Link, null, size = HeyIconSize.Large, tone = HeyTone.Accent)
            HeyText(accountLine(ui.accountName), variant = HeyTextVariant.Heading, maxLines = 2, align = HeyTextAlign.Center)
        }
        PhoneLinkUi.Offline -> VStack(HeyGap.G16, modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite }, align = HeyAlign.Center) {
            HeyIcon(HeyIcons.Error, null, size = HeyIconSize.Large, tone = HeyTone.Danger)
            HeyText(stringResource(R.string.phone_link_offline), variant = HeyTextVariant.Title, tone = HeyTone.Danger, align = HeyTextAlign.Center)
            HeyText(stringResource(R.string.phone_link_offline_hint), tone = HeyTone.Muted, maxLines = 2, align = HeyTextAlign.Center)
        }
    }
}

@Composable
private fun accountLine(name: String?): String =
    if (name.isNullOrBlank()) stringResource(R.string.phone_link_account_unknown) else stringResource(R.string.phone_link_account, name)
