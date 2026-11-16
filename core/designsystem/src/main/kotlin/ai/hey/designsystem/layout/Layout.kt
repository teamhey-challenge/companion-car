package ai.hey.designsystem.layout

import ai.hey.designsystem.token.HeyTokens
import ai.hey.designsystem.theme.HeyGap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.layout

/** Width in dp of the 1600x900 design canvas every car mockup (hey-companion-ui) is drawn on. */
private const val DESIGN_WIDTH_DP = 1600f
private const val DESIGN_HEIGHT_DP = 900f

/** Pixels per design dp relative to the system dp: full width for 16:9 and wider windows, full 900 dp height for narrower ones. */
fun designScale(widthDp: Int, heightDp: Int): Float =
    if (widthDp * DESIGN_HEIGHT_DP >= heightDp * DESIGN_WIDTH_DP) widthDp / DESIGN_WIDTH_DP else heightDp / DESIGN_HEIGHT_DP

/**
 * The density every car screen is drawn with in a window of [widthDp] x [heightDp] system dp. The one definition: MainActivity
 * provides it to the app and the Paparazzi helper provides it to snapshots, so a snapshot is the window the car shows.
 */
fun designDensity(system: Density, widthDp: Int, heightDp: Int): Density =
    Density(density = designScale(widthDp, heightDp) * system.density, fontScale = 1f)

/** The window's own size in dp (as seen by the design scale), for screens that switch layout in Pleos multi-window. */
@Composable
fun HeyWindow(modifier: Modifier = Modifier, content: @Composable (width: Dp, height: Dp) -> Unit) {
    BoxWithConstraints(modifier) { content(maxWidth, maxHeight) }
}

/**
 * Pixels per design dp that fit the 1600x900 design canvas into a [widthPx] x [heightPx] window without stretching or clipping:
 * the smaller of the two axis ratios. It is the final Compose density, whatever density the window was laid out with.
 */
fun fitScale(widthPx: Int, heightPx: Int): Float = minOf(widthPx / DESIGN_WIDTH_DP, heightPx / DESIGN_HEIGHT_DP)

/**
 * Screens that exist only as the 16:9 mockup (onboarding, driving C5): drawn on a fixed 1600x900 dp canvas that is scaled to fit
 * the window and centred, the rest filled with [background]. A 4:3-ish Pleos window thus shows the mockup letterboxed instead of a
 * squashed 1251 x 900 re-layout. Touch input follows the scaled density.
 */
@Composable
fun WideCanvas(background: Color, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier.fillMaxSize().background(background), contentAlignment = Alignment.Center) {
        val fit = fitScale(constraints.maxWidth, constraints.maxHeight)
        CompositionLocalProvider(LocalDensity provides Density(density = fit, fontScale = 1f)) {
            Box(Modifier.size(DESIGN_WIDTH_DP.dp, DESIGN_HEIGHT_DP.dp)) { content() }
        }
    }
}

/** Multi-window outer margin (1/2 and 1/3 width): 32 all round instead of the 80/64 of the full-width mockup. */
fun Modifier.carMarginCompact(): Modifier = padding(32.dp)

/** Window sizes (dp, after the design scale) under which screens switch to the stacked multi-window layout. */
private val CompactBelow = 1000.dp
private val NarrowBelow = 700.dp

/** Compact = narrower than 1000 dp, or clearly portrait-ish (taller than 0.8 x its width, e.g. a 1/3 width window). */
fun isCompactWindow(width: Dp, height: Dp): Boolean = width < CompactBelow || height > width * 0.8f

/** Narrower than 700 dp: headers drop their state label. */
fun isNarrowWindow(width: Dp): Boolean = width < NarrowBelow

/** Cross-axis alignment for [VStack] (horizontal) and [HStack] (vertical). */
enum class HeyAlign { Start, Center, End }

/** Main-axis distribution. */
enum class HeyArrange { Start, Center, End, SpaceBetween }

/** What a stack exposes to its children: only a share of the remaining space. */
interface HeyStackScope {
    fun Modifier.grow(weight: Float = 1f): Modifier
}

private class ColumnStackScope(private val scope: ColumnScope) : HeyStackScope {
    override fun Modifier.grow(weight: Float): Modifier = with(scope) { this@grow.weight(weight) }
}

private class RowStackScope(private val scope: RowScope) : HeyStackScope {
    override fun Modifier.grow(weight: Float): Modifier = with(scope) { this@grow.weight(weight) }
}

private fun HeyArrange.vertical(gap: HeyGap): Arrangement.Vertical = when (this) {
    HeyArrange.Start -> Arrangement.spacedBy(gap.value, Alignment.Top)
    HeyArrange.Center -> Arrangement.spacedBy(gap.value, Alignment.CenterVertically)
    HeyArrange.End -> Arrangement.spacedBy(gap.value, Alignment.Bottom)
    HeyArrange.SpaceBetween -> Arrangement.SpaceBetween
}

private fun HeyArrange.horizontal(gap: HeyGap): Arrangement.Horizontal = when (this) {
    HeyArrange.Start -> Arrangement.spacedBy(gap.value, Alignment.Start)
    HeyArrange.Center -> Arrangement.spacedBy(gap.value, Alignment.CenterHorizontally)
    HeyArrange.End -> Arrangement.spacedBy(gap.value, Alignment.End)
    HeyArrange.SpaceBetween -> Arrangement.SpaceBetween
}

/** Vertical container. The parent owns the gap between direct children; children carry no outer margin. */
@Composable
fun VStack(
    gap: HeyGap,
    modifier: Modifier = Modifier,
    align: HeyAlign = HeyAlign.Start,
    arrange: HeyArrange = HeyArrange.Start,
    content: @Composable HeyStackScope.() -> Unit,
) {
    val horizontal = when (align) {
        HeyAlign.Start -> Alignment.Start
        HeyAlign.Center -> Alignment.CenterHorizontally
        HeyAlign.End -> Alignment.End
    }
    Column(
        modifier = modifier,
        verticalArrangement = arrange.vertical(gap),
        horizontalAlignment = horizontal,
    ) { ColumnStackScope(this).content() }
}

/** Horizontal container. The parent owns the gap between direct children. */
@Composable
fun HStack(
    gap: HeyGap,
    modifier: Modifier = Modifier,
    align: HeyAlign = HeyAlign.Center,
    arrange: HeyArrange = HeyArrange.Start,
    content: @Composable HeyStackScope.() -> Unit,
) {
    val vertical = when (align) {
        HeyAlign.Start -> Alignment.Top
        HeyAlign.Center -> Alignment.CenterVertically
        HeyAlign.End -> Alignment.Bottom
    }
    Row(
        modifier = modifier,
        horizontalArrangement = arrange.horizontal(gap),
        verticalAlignment = vertical,
    ) { RowStackScope(this).content() }
}

/** Takes the remaining space inside a stack. */
@Composable
fun HeyStackScope.HeyFiller() {
    Spacer(Modifier.grow())
}

fun Modifier.fillWidth(): Modifier = fillMaxWidth()
fun Modifier.fillHeight(): Modifier = fillMaxHeight()
fun Modifier.fillSize(): Modifier = fillMaxSize()

/** Padding is the parent's decision; it can only pick a step. */
fun Modifier.inset(gap: HeyGap): Modifier = padding(gap.value)
fun Modifier.insetHorizontal(gap: HeyGap): Modifier = padding(horizontal = gap.value)
fun Modifier.insetTop(gap: HeyGap): Modifier = padding(top = gap.value)
fun Modifier.insetBottom(gap: HeyGap): Modifier = padding(bottom = gap.value)

/** The onboarding hold ring: a square as tall as 69% of the scene (620 of 900 in the studio), centred by its parent. */
fun Modifier.sceneRing(): Modifier = fillMaxHeight(0.69f).aspectRatio(1f)

/** The left half of a scene (the character steps aside while a panel is open on the right). */
fun Modifier.fillHalf(): Modifier = fillMaxWidth(0.5f)

/** The onboarding "Hey" title block starts 120 dp under the top edge (studio .onb-title padding-top). */
fun Modifier.carTitleTop(): Modifier = padding(top = 120.dp)

/** 1/2 and 1/3 windows: a 192 dp band at the top keeps the small character in view above a full-width panel. */
fun Modifier.carCharacterBand(): Modifier = padding(top = 192.dp)

/** Mockup car outer margin (ui-spec "차량 외곽", styles.css `--ui-shell-pad:80px;--ui-edge-y:64px`): 80 left/right, 64 top/bottom at 1600x900. */
fun Modifier.carMargin(): Modifier = padding(horizontal = 80.dp, vertical = 64.dp)

/** Mockup `.conversation` padding-top: the C3/C4/C8 body starts 54 below the 24 row gap under the header (measured: hero ink top at 236 on C3). */
fun Modifier.carBodyTop(): Modifier = padding(top = 54.dp)

/** Mockup C4: the caption ink starts 50 under the bottom of the status row's ink (251 -> 301); the stack gap gives 37, so 13 more. */
fun Modifier.carCaptionTop(): Modifier = padding(top = 13.dp)

/** Mockup parked grid: columns 1.05fr / 1fr with a 48 gutter. [HeyGap] stops at 32, so the left column adds the missing 16 here. */
fun Modifier.carGutter(): Modifier = padding(end = 16.dp)

/** Mockup parked grid right column: (window - 160 margins - 48 gutter) / 2.05 wide (679 at 1600, 509 at 1251); the character stage is centred in it. */
fun carSideColumnWidth(windowWidth: Dp): Dp = (windowWidth - 208.dp) / 2.05f

fun Modifier.carSideColumn(windowWidth: Dp): Modifier = width(carSideColumnWidth(windowWidth))

/** The character stage never wider than its column (580 at 1600, 509 at 1251). */
fun Modifier.carStageMax(columnWidth: Dp): Modifier = widthIn(max = columnWidth)

/** The home greeting wraps in a 460 px column (mockup C3 breaks "오늘은 어떤 / 하루였어요?"). */
fun Modifier.heroWidth(): Modifier = widthIn(max = 460.dp)

/** Studio C5 (spec car-drive@1251x900): the 480 dp character canvas starts at y=87 whatever the phase. */
fun Modifier.driveTop(): Modifier = padding(top = 87.dp)

/** Studio C5: canvas bottom 567 to button top 661 is 94 = 4 gap + this 86 dp status slot + 4 gap, so the button stays at 661; the label sits 33 into it. */
fun Modifier.driveStatusSlot(): Modifier = height(106.dp).padding(top = 33.dp)

/**
 * Studio list pages (car-memory @1600x900): content starts 91 under the frame's content top (title at y 145), runs from x 64 to 1536
 * (the frame gives 80), and keeps the viewport size the frame gave. Apply to the scroll column.
 */
fun Modifier.carStudioPage(): Modifier = layout { m, c ->
    val left = HeyTokens.Space.s16.roundToPx()
    val up = (HeyTokens.Space.s32 * 3 - HeyTokens.Space.s4 - HeyTokens.Border.hairline).roundToPx()
    val w = c.maxWidth + left + (HeyTokens.Space.s32 + HeyTokens.Space.s8).roundToPx()
    val h = c.maxHeight + up
    val p = m.measure(c.copy(minWidth = w, maxWidth = w, minHeight = h, maxHeight = h))
    layout(c.maxWidth, c.maxHeight) { p.place(-left, -up) }
}

/** The studio header row sits 45.5 above where the frame's top margin puts it. */
fun Modifier.carStudioHeader(): Modifier = layout { m, c ->
    val p = m.measure(c)
    layout(p.width, p.height) { p.place(0, (-45.5f).dp.roundToPx()) }
}

/** A trailing glyph of a list card sits 24 right of its 76 dp touch target's slot, on the card's inner edge. */
fun Modifier.carCardTrailing(): Modifier = layout { m, c ->
    val p = m.measure(c)
    layout(p.width, p.height) { p.place(HeyTokens.Space.s24.roundToPx(), 0) }
}
