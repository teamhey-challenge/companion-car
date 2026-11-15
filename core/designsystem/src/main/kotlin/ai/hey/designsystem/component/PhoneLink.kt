package ai.hey.designsystem.component

import ai.hey.designsystem.R
import ai.hey.designsystem.icon.HeyIcons
import ai.hey.designsystem.layout.HStack
import ai.hey.designsystem.layout.HeyAlign
import ai.hey.designsystem.layout.VStack
import ai.hey.designsystem.theme.HeyGap
import ai.hey.designsystem.theme.HeyTextVariant
import ai.hey.designsystem.theme.HeyTheme
import ai.hey.designsystem.theme.HeyTone
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

/**
 * Studio `car/settings/phone` pairing card (wide windows): a white 300 QR tile on the left, the title, the instruction, the code in
 * 40/700 and the "새 QR 보기" action on the right. Drawn only for [PhoneLinkUi.Code], i.e. while parked.
 */
@Composable
fun PhoneLinkCodeCard(code: String, onNewCode: () -> Unit, modifier: Modifier = Modifier) {
    val colors = HeyTheme.colors
    HStack(HeyGap.G32, modifier = modifier.fillMaxWidth().padding(start = 217.dp), align = HeyAlign.Center) {
        Box(
            Modifier.size(300.dp).clip(RoundedCornerShape(26.dp)).background(colors.bgRaised).padding(22.dp),
            contentAlignment = Alignment.Center,
        ) { HeyQrCode(PHONE_LINK_URL_BASE + code, 256.dp) }
        Spacer(Modifier.width(48.dp))
        VStack(HeyGap.G24) {
            InkText(
                stringResource(R.string.phone_link_card_title), colors.fgDefault,
                weight = FontWeight.SemiBold, trackingEm = -0.025f, metrics = HeyTextMetrics(30.sp, 43.5.sp), maxLines = 1,
            )
            InkText(stringResource(R.string.phone_link_card_body), colors.fgDefault, metrics = HeyTextMetrics(26.sp, 44.2.sp), maxLines = 3)
            InkText(
                code.chunked(4).joinToString(" "), colors.fgDefault,
                weight = FontWeight.Bold, trackingEm = 0.12f, metrics = HeyTextMetrics(40.sp, 60.sp), maxLines = 1,
            )
            HeyButton(stringResource(R.string.phone_link_new_qr), onNewCode, variant = HeyButtonVariant.Secondary, icon = HeyIcons.Refresh)
        }
    }
}

@Composable
private fun accountLine(name: String?): String =
    if (name.isNullOrBlank()) stringResource(R.string.phone_link_account_unknown) else stringResource(R.string.phone_link_account, name)
