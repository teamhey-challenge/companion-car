package ai.hey.designsystem.component

import ai.hey.designsystem.R
import ai.hey.designsystem.theme.HeyTextVariant
import ai.hey.designsystem.theme.HeyTheme
import ai.hey.designsystem.theme.HeyTone
import ai.hey.designsystem.theme.resolve
import ai.hey.designsystem.theme.style
import ai.hey.designsystem.theme.cssLeading
import ai.hey.designsystem.token.HeyTokens
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle

enum class HeyTextAlign { Start, Center, End }

private fun HeyTextAlign.toCompose(): TextAlign = when (this) {
    HeyTextAlign.Start -> TextAlign.Start
    HeyTextAlign.Center -> TextAlign.Center
    HeyTextAlign.End -> TextAlign.End
}

/**
 * Korean must break between words, never inside one ("준비되지 않았어 / 요"). Android's line breaker may break between
 * any two Hangul syllables, so a WORD JOINER (U+2060, zero width, default-ignorable) is put between adjacent syllables;
 * the only break opportunities left in Korean text are the spaces.
 */
internal fun keepKoreanWords(text: String): String {
    if (text.length < 2) return text
    val out = StringBuilder(text.length * 2)
    for (i in text.indices) {
        val c = text[i]
        out.append(c)
        if (i + 1 < text.length && c.isHangulSyllable() && text[i + 1].isHangulSyllable()) out.append('\u2060')
    }
    return out.toString()
}

private fun Char.isHangulSyllable() = this in '\uAC00'..'\uD7A3'

/**
 * A size that is not one of the tokens, with the line height it needs. Both values are required together: a size alone
 * used to bring a derived line height with it (x 1.4), which silently differed from the studio CSS.
 */
data class HeyTextMetrics(val size: androidx.compose.ui.unit.TextUnit, val lineHeight: androidx.compose.ui.unit.TextUnit)

/** Text in an arbitrary resolved colour; used by components whose ink is not one of the [HeyTone]s. */
@Composable
internal fun InkText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    variant: HeyTextVariant = HeyTextVariant.Body,
    maxLines: Int = Int.MAX_VALUE,
    align: HeyTextAlign = HeyTextAlign.Start,
    /** Overrides the token weight (design headings are regular, not bold). */
    weight: androidx.compose.ui.text.font.FontWeight? = null,
    /** Overrides the token tracking, in em. */
    trackingEm: Float? = null,
    /** Overrides the token size and line height where the mockup measures values between two tokens. */
    metrics: HeyTextMetrics? = null,
    /** Overrides only the line height (token size kept). */
    lineHeight: androidx.compose.ui.unit.TextUnit? = null,
) {
    val base = variant.spec.style(color).copy(textAlign = align.toCompose())
    val style = base.copy(
        fontWeight = weight ?: base.fontWeight,
        letterSpacing = if (trackingEm != null) androidx.compose.ui.unit.TextUnit(trackingEm, androidx.compose.ui.unit.TextUnitType.Em) else base.letterSpacing,
    ).let { if (metrics != null) it.copy(fontSize = metrics.size, lineHeight = metrics.lineHeight) else it }
        .let { if (lineHeight != null) it.copy(lineHeight = lineHeight) else it }
        .cssLeading()
    val probe = LocalHeyTextProbe.current
    val layout = remember { arrayOfNulls<androidx.compose.ui.text.TextLayoutResult>(1) }
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    BasicText(
        text = remember(text) { keepKoreanWords(text) },
        modifier = modifier.probeText(probe, text, style, maxLines, density, layout),
        style = style,
        overflow = TextOverflow.Ellipsis,
        maxLines = maxLines,
        onTextLayout = { if (probe != null) layout[0] = it },
    )
}

/**
 * What one text node looks like after layout, in dp (px at the 160 dpi car display). [x]/[w] are the ink extent of the lines (not the
 * box a parent gave), [baselineY] is where the first line's baseline sits, [lines] the number of lines. Consumed only by the
 * design-conformance dump (PLEOS-199).
 */
class HeyTextRecord(
    val text: String,
    val x: Float, val y: Float, val w: Float, val baselineY: Float, val lines: Int,
    val fontSizeSp: Float, val fontWeight: Int,
    val letterSpacing: Float, val letterSpacingUnit: String,
    val lineHeightSp: Float, val colorHex: String, val maxLines: Int,
)

fun interface HeyTextProbe { fun onText(record: HeyTextRecord) }

/** Null in the app. A test installs a probe to read back every HeyText/InkText as laid out; nothing else reads it. */
val LocalHeyTextProbe = androidx.compose.runtime.staticCompositionLocalOf<HeyTextProbe?> { null }

private fun Modifier.probeText(
    probe: HeyTextProbe?, text: String, style: androidx.compose.ui.text.TextStyle, maxLines: Int, density: Float,
    layout: Array<androidx.compose.ui.text.TextLayoutResult?>,
): Modifier = if (probe == null) this else onGloballyPositioned { c ->
    val t = layout[0] ?: return@onGloballyPositioned
    val b = c.boundsInRoot()
    val left = (0 until t.lineCount).minOf { t.getLineLeft(it) }
    val right = (0 until t.lineCount).maxOf { t.getLineRight(it) }
    probe.onText(
        HeyTextRecord(
            text, (b.left + left) / density, b.top / density, (right - left) / density, (b.top + t.firstBaseline) / density, t.lineCount,
            style.fontSize.value, style.fontWeight?.weight ?: 400,
            style.letterSpacing.value, if (style.letterSpacing.isEm) "em" else "sp",
            style.lineHeight.value, "#%06X".format(style.color.toArgb() and 0xFFFFFF), maxLines,
        ),
    )
}

@Composable
fun HeyText(
    text: String,
    modifier: Modifier = Modifier,
    variant: HeyTextVariant = HeyTextVariant.Body,
    tone: HeyTone = HeyTone.Default,
    maxLines: Int = Int.MAX_VALUE,
    align: HeyTextAlign = HeyTextAlign.Start,
) {
    InkText(text, tone.resolve(HeyTheme.colors), modifier, variant, maxLines, align)
}

enum class HeyIconSize { Regular, Large }

@Composable
fun HeyIcon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: HeyIconSize = HeyIconSize.Regular,
    tone: HeyTone = HeyTone.Default,
) {
    InkIcon(icon, contentDescription, tone.resolve(HeyTheme.colors), modifier, size)
}

@Composable
internal fun InkIcon(
    icon: ImageVector,
    contentDescription: String?,
    color: Color,
    modifier: Modifier = Modifier,
    size: HeyIconSize = HeyIconSize.Regular,
) {
    val edge = when (size) {
        HeyIconSize.Regular -> HeyTokens.Size.iconCar
        HeyIconSize.Large -> HeyTokens.Size.iconCar * 2
    }
    Image(
        painter = rememberVectorPainter(icon),
        contentDescription = contentDescription,
        modifier = modifier.size(edge),
        colorFilter = ColorFilter.tint(color),
    )
}

@Composable
fun HeyDivider(modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier
            .fillMaxWidth()
            .height(HeyTokens.Border.hairline)
            .background(HeyTheme.colors.borderDefault),
    )
}

@Composable
internal fun HeySpinner(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing), RepeatMode.Restart),
        label = "spinnerAngle",
    )
    Canvas(modifier.size(HeyTokens.Size.iconCar)) {
        val stroke = HeyTokens.Border.focus.toPx()
        val inset = stroke / 2f
        drawArc(
            color = color,
            startAngle = angle,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
            size = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}
