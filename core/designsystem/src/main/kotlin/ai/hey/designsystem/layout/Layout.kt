package ai.hey.designsystem.layout

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

/** The window's own size in dp (as seen by the design scale), for screens that switch layout in Pleos multi-window. */
@Composable
fun HeyWindow(modifier: Modifier = Modifier, content: @Composable (width: Dp, height: Dp) -> Unit) {
    BoxWithConstraints(modifier) { content(maxWidth, maxHeight) }
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

/** Mockup parked grid right column: (1440 - 48) / 2.05 = 679 wide; the character stage is centred in it. */
fun Modifier.carSideColumn(): Modifier = width(679.dp)

/** The home greeting wraps in a 460 px column (mockup C3 breaks "오늘은 어떤 / 하루였어요?"). */
fun Modifier.heroWidth(): Modifier = widthIn(max = 460.dp)

/** Mockup C5: the stage's top edge is at 178 whatever the phase (character ink 224 on C5, measured on C5, -listening, -thinking, -speaking). */
fun Modifier.driveTop(): Modifier = padding(top = 178.dp)

/** Mockup C5: the status line has a fixed 87 dp slot (label top 33 into it, 4 gap each side in the stack) so the button stays at 573. */
fun Modifier.driveStatusSlot(): Modifier = height(87.dp).padding(top = 33.dp)
