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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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

internal val HeyFontFamily = FontFamily(
    Font(TokensR.font.chiron_goround_tc_regular, FontWeight.Normal),
    Font(TokensR.font.chiron_goround_tc_medium, FontWeight.Medium),
    Font(TokensR.font.chiron_goround_tc_semibold, FontWeight.SemiBold),
    Font(TokensR.font.chiron_goround_tc_bold, FontWeight.Bold),
)

internal val LocalHeyColors = staticCompositionLocalOf<HeyColors> { error("HeyTheme is missing above this composable") }
internal val LocalHeyDark = staticCompositionLocalOf { true }

object HeyTheme {
    val colors: HeyColors
        @Composable @ReadOnlyComposable get() = LocalHeyColors.current
    val dark: Boolean
        @Composable @ReadOnlyComposable get() = LocalHeyDark.current
}

/** Car default is dark (night driving, glare). */
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
)
