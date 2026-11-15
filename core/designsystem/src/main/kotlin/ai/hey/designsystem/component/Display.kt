package ai.hey.designsystem.component

import ai.hey.designsystem.R
import ai.hey.designsystem.icon.HeyIcons
import ai.hey.designsystem.layout.HStack
import ai.hey.designsystem.layout.HeyAlign
import ai.hey.designsystem.layout.HeyArrange
import ai.hey.designsystem.layout.HeyStackScope
import ai.hey.designsystem.layout.VStack
import ai.hey.designsystem.theme.HeyGap
import ai.hey.designsystem.theme.HeyTextVariant
import ai.hey.designsystem.theme.HeyTheme
import ai.hey.designsystem.theme.HeyTone
import ai.hey.designsystem.token.HeyColors
import ai.hey.designsystem.token.HeyDarkColors
import ai.hey.designsystem.token.HeyTokens
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

// ---------------------------------------------------------------- tone

enum class HeyStatusTone { Neutral, Info, Success, Warning, Danger, Accent }

internal class StatusInk(val container: Color, val content: Color)

internal fun HeyColors.ink(tone: HeyStatusTone): StatusInk = when (tone) {
    HeyStatusTone.Neutral -> StatusInk(bgSunken, fgMuted)
    HeyStatusTone.Info -> StatusInk(infoContainer, info)
    HeyStatusTone.Success -> StatusInk(successContainer, success)
    HeyStatusTone.Warning -> StatusInk(warningContainer, warning)
    HeyStatusTone.Danger -> StatusInk(dangerContainer, danger)
    HeyStatusTone.Accent -> StatusInk(accentContainer, fgDefault)
}

// ---------------------------------------------------------------- card

enum class HeyCardTone { Default, Raised, Accent, Sunken }

/** Surface with a fixed 24dp interior; children are stacked with a 16dp gap. No outer margin. */
@Composable
fun HeyCard(
    modifier: Modifier = Modifier,
    tone: HeyCardTone = HeyCardTone.Default,
    content: @Composable HeyStackScope.() -> Unit,
) {
    val colors = HeyTheme.colors
    val shape = RoundedCornerShape(HeyTokens.Radius.lg)
    val surface = when (tone) {
        HeyCardTone.Default -> colors.bgSurface
        HeyCardTone.Raised -> colors.bgRaised
        HeyCardTone.Accent -> colors.accentContainer
        HeyCardTone.Sunken -> colors.bgSunken
    }
    val edge = when (tone) {
        HeyCardTone.Accent -> colors.accent
        HeyCardTone.Raised -> colors.borderStrong
        else -> colors.borderDefault
    }
    VStack(
        gap = HeyGap.G16,
        modifier = modifier
            .clip(shape)
            .background(surface, shape)
            .border(HeyTokens.Border.hairline, edge, shape)
            .padding(HeyTokens.Space.s24),
        content = content,
    )
}

@Composable
fun HeyStatusPill(
    text: String,
    tone: HeyStatusTone,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val ink = HeyTheme.colors.ink(tone)
    HStack(
        gap = HeyGap.G8,
        modifier = modifier
            .clip(RoundedCornerShape(HeyTokens.Radius.full))
            .background(ink.container)
            .padding(horizontal = HeyTokens.Space.s16, vertical = HeyTokens.Space.s8),
    ) {
        if (icon != null) InkIcon(icon, null, ink.content, Modifier.size(HeyTokens.Size.iconCar * 3 / 4))
        InkText(text, ink.content, variant = HeyTextVariant.Label, maxLines = 1)
    }
}

/**
 * One action a banner offers. The banner renders it itself as a [HeyButton] (Regular, or Large for the [primary] one),
 * so an action can never be smaller than the car touch floor; there is no free content slot to put a chip into.
 */
class HeyBannerAction(
    val text: String,
    val onClick: () -> Unit,
    val primary: Boolean = false,
    val enabled: Boolean = true,
)

@Composable
fun HeyBanner(
    tone: HeyStatusTone,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actions: List<HeyBannerAction> = emptyList(),
) {
    val ink = HeyTheme.colors.ink(tone)
    val icon = when (tone) {
        HeyStatusTone.Success -> HeyIcons.Check
        HeyStatusTone.Warning -> HeyIcons.Warning
        HeyStatusTone.Danger -> HeyIcons.Error
        else -> HeyIcons.Info
    }
    val shape = RoundedCornerShape(HeyTokens.Radius.lg)
    VStack(
        gap = HeyGap.G16,
        modifier = modifier
            .clip(shape)
            .background(ink.container, shape)
            .padding(HeyTokens.Space.s24),
    ) {
        HStack(gap = HeyGap.G16, align = HeyAlign.Start, modifier = Modifier.fillMaxWidth()) {
            InkIcon(icon, null, ink.content)
            VStack(gap = HeyGap.G4, modifier = Modifier.grow()) {
                InkText(title, ink.content, variant = HeyTextVariant.Heading, maxLines = 3)
                if (message != null) HeyText(message, tone = HeyTone.Default, variant = HeyTextVariant.Body, maxLines = 4)
            }
        }
        if (actions.isNotEmpty()) {
            // One row; the primary action is a Large (96dp) button with the bigger share, the others are Regular (76dp).
            HStack(gap = HeyGap.G8, modifier = Modifier.fillMaxWidth()) {
                actions.forEach { a ->
                    HeyButton(
                        a.text,
                        a.onClick,
                        Modifier.grow(if (a.primary) 1.4f else 1f),
                        variant = if (a.primary) HeyButtonVariant.Primary else HeyButtonVariant.Secondary,
                        size = if (a.primary) HeyButtonSize.Large else HeyButtonSize.Regular,
                        enabled = a.enabled,
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyPanel(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
) {
    VStack(
        gap = HeyGap.G16,
        align = HeyAlign.Center,
        arrange = HeyArrange.Center,
        modifier = modifier,
    ) {
        HeyIcon(icon, null, size = HeyIconSize.Large, tone = HeyTone.Subtle)
        HeyText(title, variant = HeyTextVariant.Title, maxLines = 2, align = HeyTextAlign.Center)
        if (message != null) HeyText(message, tone = HeyTone.Muted, maxLines = 3, align = HeyTextAlign.Center)
    }
}

// ---------------------------------------------------------------- status strip + frame

enum class HeyConnection { Online, Reconnecting, Offline }

@Composable
fun StatusStrip(
    /** Null when the screen's own title already says it, so one fact is not on screen twice. */
    drivingLabel: String?,
    connection: HeyConnection,
    modifier: Modifier = Modifier,
    title: String? = stringResource(R.string.hey_app_name),
    clock: String? = null,
) {
    val colors = HeyTheme.colors
    val (tone, label) = when (connection) {
        HeyConnection.Online -> HeyStatusTone.Success to stringResource(R.string.hey_connection_online)
        HeyConnection.Reconnecting -> HeyStatusTone.Warning to stringResource(R.string.hey_connection_reconnecting)
        HeyConnection.Offline -> HeyStatusTone.Danger to stringResource(R.string.hey_connection_offline)
    }
    HStack(gap = HeyGap.G24, arrange = HeyArrange.SpaceBetween, modifier = modifier.fillMaxWidth()) {
        if (title != null) InkText(title, colors.fgDefault, variant = HeyTextVariant.Title, maxLines = 1)
        if (drivingLabel != null) HeyStatusPill(drivingLabel, HeyStatusTone.Neutral, icon = HeyIcons.Car)
        HStack(gap = HeyGap.G16) {
            HeyStatusPill(label, tone)
            if (clock != null) InkText(clock, colors.fgMuted, variant = HeyTextVariant.Heading, maxLines = 1)
        }
    }
}

/**
 * The one page frame. While driving the action rail is not composed at all, so nothing in it can be pressed.
 */
@Composable
fun CarFrame(
    statusStrip: @Composable () -> Unit,
    driving: Boolean,
    modifier: Modifier = Modifier,
    actionRail: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    // A scrolling page (no rail, not driving) lets its scroll viewport reach the window's right and bottom edge,
    // so the scrollbar sits in the outer margin; the content gets the margin back as padding inside the scroll.
    val edgeScroll = actionRail == null && !driving
    val endPad = if (edgeScroll) 0.dp else CarPageX
    VStack(
        gap = HeyGap.G24,
        modifier = modifier
            .fillMaxSize()
            .background(HeyTheme.colors.bgPage)
            .padding(start = CarPageX, end = endPad, top = CarPageTop, bottom = if (actionRail != null) CarPageBottom else 0.dp),
    ) {
        Box(Modifier.fillMaxWidth().padding(end = if (edgeScroll) CarPageX else 0.dp)) { statusStrip() }
        Box(Modifier.grow().fillMaxWidth().padding(top = CarContentGap)) {
            CompositionLocalProvider(
                LocalCarScrollInset provides if (edgeScroll) CarScrollInset(end = CarPageX, bottom = CarPageBottom) else CarScrollInset(0.dp, 0.dp),
                content = content,
            )
        }
        if (!driving && actionRail != null) actionRail()
    }
}

/**
 * Margin a scroll viewport inside [CarFrame] reaches past, and the clearance its last row keeps from the window
 * edge. Zero outside an edge-scrolling frame.
 */
@Immutable
data class CarScrollInset(val end: Dp, val bottom: Dp)

val LocalCarScrollInset = staticCompositionLocalOf { CarScrollInset(0.dp, 0.dp) }

/** Mockup car shell (ui-spec "차량 외곽": 80 left/right, 64 top). */
private val CarPageX = 80.dp
private val CarPageTop = 64.dp
private val CarPageBottom = 64.dp

/** Header bottom (148) to title top (~205) in the mockups is ~56 dp; the frame's G24 supplies 24 of it. */
private val CarContentGap = 32.dp

// ---------------------------------------------------------------- QR + greeting

private val QrPaper = HeyDarkColors.bgInverse
private val QrInk = HeyDarkColors.fgInverse

/** Real QR (zxing). Always dark modules on light paper regardless of theme so every scanner reads it. */
@Composable
fun HeyQrCode(content: String, size: Dp, modifier: Modifier = Modifier) {
    val matrix = remember(content) {
        QRCodeWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            0,
            0,
            mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
        )
    }
    val quiet = HeyTokens.Space.s16
    val shape = RoundedCornerShape(HeyTokens.Radius.md)
    Canvas(
        modifier
            .size(size)
            .clip(shape)
            .background(QrPaper)
            .semantics { contentDescription = content },
    ) {
        val inner = this.size.width - quiet.toPx() * 2
        val cells = matrix.width
        val cell = kotlin.math.floor(inner / cells)
        val origin = (this.size.width - cell * cells) / 2f
        for (y in 0 until cells) {
            for (x in 0 until cells) {
                if (matrix.get(x, y)) {
                    drawRect(QrInk, Offset(origin + x * cell, origin + y * cell), Size(cell, cell))
                }
            }
        }
    }
}
