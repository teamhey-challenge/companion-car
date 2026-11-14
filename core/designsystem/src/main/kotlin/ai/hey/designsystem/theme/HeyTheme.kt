package ai.hey.designsystem.theme

import ai.hey.tokens.R as TokensR
import ai.hey.designsystem.token.HeyColors
import ai.hey.designsystem.token.HeyDarkColors
import ai.hey.designsystem.token.HeyLightColors
import ai.hey.designsystem.token.HeyTokens
import ai.hey.designsystem.token.HeyTypeSpec
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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

/**
 * Design canvas of the mockups: 1600x900 CSS px (docs/ui-spec.md "차량 캔버스"; the delivery PNGs are that at 2x).
 * One mockup px is one dp on a 1600dp-wide window. Wider windows (the 2560x1440 @160dpi car/emulator is 2560dp wide)
 * scale dp and sp together by width/1600, so every token keeps its share of the screen; the 1600dp and 1280dp
 * Paparazzi frames stay 1:1. The 720dp floor keeps a short window from scaling content past its height.
 */
private const val CANVAS_WIDTH_DP = 1600f
private const val CANVAS_HEIGHT_DP = 720f

/** Car theme: [dark] null follows the system setting (D-26). */
@Composable
fun HeyTheme(dark: Boolean? = null, content: @Composable () -> Unit) {
    val isDark = dark ?: isSystemInDarkTheme()
    val config = LocalConfiguration.current
    val base = LocalDensity.current
    val scale = minOf(config.screenWidthDp / CANVAS_WIDTH_DP, config.screenHeightDp / CANVAS_HEIGHT_DP).coerceAtLeast(1f)
    CompositionLocalProvider(
        LocalHeyColors provides if (isDark) HeyDarkColors else HeyLightColors,
        LocalHeyDark provides isDark,
        LocalDensity provides Density(base.density * scale, base.fontScale),
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
