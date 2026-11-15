package ai.hey.designsystem.theme

import ai.hey.tokens.R as TokensR
import ai.hey.designsystem.token.HeyColors
import ai.hey.designsystem.token.HeyDarkColors
import ai.hey.designsystem.token.HeyLightColors
import ai.hey.designsystem.token.HeyTokens
import ai.hey.designsystem.token.HeyTypeSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.em

/** The only spacing steps. Containers pass one of these as `gap`; components never carry outer margin. */
enum class HeyGap(val value: Dp) {
    G4(HeyTokens.Space.s4),
    G8(HeyTokens.Space.s8),
    G16(HeyTokens.Space.s16),
    G24(HeyTokens.Space.s24),
    G32(HeyTokens.Space.s32),
}

enum class HeyTextVariant(internal val spec: HeyTypeSpec) {
    Hero(HeyTokens.CarType.hero),
    Title(HeyTokens.CarType.title),
    Heading(HeyTokens.CarType.heading),
    Body(HeyTokens.CarType.body),
    Label(HeyTokens.CarType.label),
    Caption(HeyTokens.CarType.caption),
}

enum class HeyTone { Default, Muted, Subtle, Disabled, Accent, Danger, Success, OnAccent }

/**
 * One variable Chiron GoRound TC (wght 200-900, all 11,172 Hangul syllables), the same file family the studio loads.
 * One entry per weight the design uses; minSdk is 28, so font variations are always available.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun variableFont(weight: Int) = Font(
    TokensR.font.chiron_goround_tc_variable,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

internal val HeyFontFamily = FontFamily(
    listOf(300, 400, 450, 500, 600, 700, 800).map(::variableFont),
)

/** The font's ascent + descent (970 + 210 on a 1000 em): the height Compose gives a line before any line-height applies. */
private const val NaturalLineEm = 1.18f

/**
 * CSS half-leading: every line box, first and last included, is exactly `line-height` tall with the glyph box centred.
 * Measured in TextMetricsTest on layoutlib: Trim.Both + Mode.Tight is the only style that shrinks the FIRST line when the
 * line height is below the natural height (150/150 text: baseline 132 as in CSS, with Trim.None it stays 145), and it
 * never grows a line; Trim.None grows lines correctly (28/39.2: 30 against CSS 30.2). So the style follows the case, and
 * only here: every text style of the design system goes through [cssLeading].
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
internal fun TextStyle.cssLeading(): TextStyle {
    val shrinks = lineHeight.value < fontSize.value * NaturalLineEm
    return copy(
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = if (shrinks) LineHeightStyle.Trim.Both else LineHeightStyle.Trim.None,
            mode = if (shrinks) LineHeightStyle.Mode.Tight else LineHeightStyle.Mode.Fixed,
        ),
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
}

internal val LocalHeyColors = staticCompositionLocalOf<HeyColors> { error("HeyTheme is missing above this composable") }
internal val LocalHeyDark = staticCompositionLocalOf { true }

object HeyTheme {
    val colors: HeyColors
        @Composable @ReadOnlyComposable get() = LocalHeyColors.current
    val dark: Boolean
        @Composable @ReadOnlyComposable get() = LocalHeyDark.current
}

/** Car default is dark (night driving, glare); [dark] is the driver's choice. Canvas scaling (1600dp design width) is done once in MainActivity. */
@Composable
fun HeyTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalHeyColors provides if (dark) HeyDarkColors else HeyLightColors,
        LocalHeyDark provides dark,
        content = content,
    )
}

internal fun HeyTone.resolve(colors: HeyColors): Color = when (this) {
    HeyTone.Default -> colors.fgDefault
    HeyTone.Muted -> colors.fgMuted
    HeyTone.Subtle -> colors.fgSubtle
    HeyTone.Disabled -> colors.fgDisabled
    HeyTone.Accent -> colors.primary
    HeyTone.Danger -> colors.danger
    HeyTone.Success -> colors.success
    HeyTone.OnAccent -> colors.fgOnAccent
}

internal fun HeyTypeSpec.style(color: Color): TextStyle = TextStyle(
    fontFamily = HeyFontFamily,
    fontSize = size,
    lineHeight = lineHeight,
    fontWeight = FontWeight(weight),
    letterSpacing = trackingEm.em,
    color = color,
    lineBreak = LineBreak.Paragraph,
).cssLeading()
