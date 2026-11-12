package ai.hey.designsystem.component

import ai.hey.designsystem.R
import ai.hey.designsystem.theme.HeyTextVariant
import ai.hey.designsystem.theme.HeyTheme
import ai.hey.designsystem.theme.HeyTone
import ai.hey.designsystem.theme.resolve
import ai.hey.designsystem.theme.style
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

/** Text in an arbitrary resolved colour; used by components whose ink is not one of the [HeyTone]s. */
@Composable
internal fun InkText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    variant: HeyTextVariant = HeyTextVariant.Body,
    maxLines: Int = Int.MAX_VALUE,
    align: HeyTextAlign = HeyTextAlign.Start,
) {
    BasicText(
        text = remember(text) { keepKoreanWords(text) },
        modifier = modifier,
        style = variant.spec.style(color).copy(textAlign = align.toCompose()),
        overflow = TextOverflow.Ellipsis,
        maxLines = maxLines,
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

/** HEY with one Y per passenger (HEY, HEYY, HEYYY, HEYYYY). */
@Composable
fun HeyWordmark(partySize: Int, modifier: Modifier = Modifier, large: Boolean = false) {
    val colors = HeyTheme.colors
    val spec = if (large) HeyTextVariant.Hero.spec else HeyTextVariant.Title.spec
    val description = stringResource(R.string.hey_wordmark_description)
    val styled = buildAnnotatedString {
        append("HE")
        withStyle(SpanStyle(color = colors.primary)) { append("Y".repeat(partySize.coerceIn(1, 4))) }
    }
    BasicText(
        text = styled,
        modifier = modifier.semantics { contentDescription = description },
        style = spec.style(colors.fgDefault),
        maxLines = 1,
    )
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
