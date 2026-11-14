package ai.hey.designsystem.layout

import ai.hey.designsystem.theme.HeyGap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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

/** Mockup car outer margin (ui-spec "차량 외곽": 80 left/right, 40 top/bottom). */
fun Modifier.carMargin(): Modifier = padding(horizontal = 80.dp, vertical = 40.dp)

/** The home greeting wraps in a 460 px column (mockup C3 breaks "오늘은 어떤 / 하루였어요?"). */
fun Modifier.heroWidth(): Modifier = widthIn(max = 460.dp)
