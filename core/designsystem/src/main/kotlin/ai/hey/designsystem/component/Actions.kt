package ai.hey.designsystem.component

import ai.hey.designsystem.icon.HeyIcons
import ai.hey.designsystem.layout.HStack
import ai.hey.designsystem.layout.HeyAlign
import ai.hey.designsystem.layout.VStack
import ai.hey.designsystem.theme.HeyGap
import ai.hey.designsystem.theme.HeyTextVariant
import ai.hey.designsystem.theme.HeyTheme
import ai.hey.designsystem.theme.HeyTone
import ai.hey.designsystem.token.HeyColors
import ai.hey.designsystem.token.HeyTokens
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** Soft = filled, outline-free surface (header 기억/설정 in the design); the others are unchanged. */
enum class HeyButtonVariant { Primary, Accent, Secondary, Danger, Ghost, Soft }
/** [Header] is the C3 top-bar pill (mockup `.app-header nav .control`: 84 high, 30sp label, 36dp icon, 30dp side padding). */
enum class HeyButtonSize { Regular, Large, Header }

private class ButtonInk(val container: Color, val content: Color, val border: Color?)

private fun HeyColors.ink(variant: HeyButtonVariant, pressed: Boolean, enabled: Boolean): ButtonInk {
    if (!enabled) return ButtonInk(bgSunken, fgDisabled, borderDefault)
    return when (variant) {
        HeyButtonVariant.Primary -> ButtonInk(if (pressed) primaryPressed else primary, fgOnPrimary, null)
        HeyButtonVariant.Accent -> ButtonInk(if (pressed) accentPressed else accent, fgOnAccent, null)
        HeyButtonVariant.Secondary -> ButtonInk(if (pressed) bgSunken else bgRaised, fgDefault, borderStrong)
        HeyButtonVariant.Danger -> ButtonInk(if (pressed) danger.copy(alpha = 0.32f) else dangerContainer, danger, danger)
        HeyButtonVariant.Ghost -> ButtonInk(if (pressed) bgSunken else Color.Transparent, fgDefault, borderDefault)
        HeyButtonVariant.Soft -> ButtonInk(if (pressed) bgSunken else bgRaised, fgDefault, null)
    }
}

/** Shared press surface: one place that decides role, ripple-less pressed state and the 76dp floor. */
@Composable
private fun PressSurface(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier,
    contentAlignment: Alignment = Alignment.Center,
    ink: @Composable (pressed: Boolean) -> ButtonInk,
    radius: androidx.compose.ui.unit.Dp = HeyTokens.Radius.md,
    content: @Composable (ButtonInk) -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val resolved = ink(pressed)
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(resolved.container, shape)
            .let { if (resolved.border != null) it.border(BorderStroke(HeyTokens.Border.strong, resolved.border), shape) else it }
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = contentAlignment,
    ) { content(resolved) }
}

@Composable
fun HeyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: HeyButtonVariant = HeyButtonVariant.Primary,
    size: HeyButtonSize = HeyButtonSize.Regular,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val colors = HeyTheme.colors
    val soft = variant == HeyButtonVariant.Soft
    val floor = when {
        soft -> HeaderTouch
        size == HeyButtonSize.Large -> HeyTokens.Size.touchCarPrimary
        else -> HeyTokens.Size.touchCar
    }
    val header = size == HeyButtonSize.Header
    val textVariant = if (size == HeyButtonSize.Large) HeyTextVariant.Heading else HeyTextVariant.Label
    PressSurface(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.heightIn(min = floor).widthIn(min = floor),
        ink = { pressed -> colors.ink(variant, pressed, enabled) },
        radius = if (soft) HeyTokens.Radius.full else HeyTokens.Radius.md,
    ) { ink ->
        HStack(
            gap = HeyGap.G8,
            modifier = Modifier.padding(
                horizontal = if (header) 32.dp else HeyTokens.Space.s24,
                vertical = if (header) 18.dp else HeyTokens.Space.s8,
            ),
        ) {
            if (loading) {
                HeySpinner(ink.content)
            } else if (icon != null) {
                InkIcon(icon, null, ink.content, if (header) Modifier.size(36.dp) else Modifier)
            }
            if (header) {
                InkText(
                    text, ink.content, Modifier.padding(start = 4.dp), variant = textVariant, maxLines = 1,
                    align = HeyTextAlign.Center, weight = androidx.compose.ui.text.font.FontWeight.Medium, metrics = HeyTextMetrics(30.sp, 45.sp),
                )
            } else {
                InkText(text, ink.content, variant = textVariant, maxLines = 2, align = HeyTextAlign.Center)
            }
        }
    }
}

/** Icon-only button. [bare] draws just the glyph (mockup C6 delete: no fill, no outline, 56dp glyph in a 76dp target). */
@Composable
fun HeyIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    bare: Boolean = false,
) {
    val colors = HeyTheme.colors
    PressSurface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(HeyTokens.Size.touchCar)
            .semantics { this.contentDescription = contentDescription },
        ink = { pressed ->
            if (bare) ButtonInk(Color.Transparent, if (enabled) (if (pressed) colors.fgMuted else colors.fgDefault) else colors.fgDisabled, null)
            else colors.ink(HeyButtonVariant.Secondary, pressed, enabled)
        },
        radius = if (bare) HeyTokens.Radius.full else HeyTokens.Radius.md,
    ) { ink -> InkIcon(icon, null, ink.content, if (bare) Modifier.size(56.dp) else Modifier) }
}

/** Header touch size of the mockups (ui-spec "헤더 84"): header pills are 84 high, the back button is an 84 circle. */
internal val HeaderTouch = 84.dp

/** Header back button: white circle (mockup C2/C6/C7). */
@Composable
fun HeyBackButton(contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier, ghost: Boolean = false) {
    val colors = HeyTheme.colors
    PressSurface(
        onClick = onClick,
        enabled = true,
        modifier = modifier
            .size(HeaderTouch)
            .semantics { this.contentDescription = contentDescription },
        ink = { pressed -> colors.ink(if (ghost) HeyButtonVariant.Ghost else HeyButtonVariant.Soft, pressed, true) },
        radius = HeyTokens.Radius.full,
    ) { ink -> InkIcon(HeyIcons.ArrowLeft, null, ink.content) }
}

@Composable
fun HeySwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = HeyTheme.colors
    val source = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(HeyTokens.Radius.md)
    HStack(
        gap = HeyGap.G16,
        modifier = modifier
            .clip(shape)
            .background(colors.bgSurface, shape)
            .border(HeyTokens.Border.hairline, colors.borderDefault, shape)
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) }
            .heightIn(min = HeyTokens.Size.touchCarPrimary)
            .padding(horizontal = HeyTokens.Space.s24, vertical = HeyTokens.Space.s16),
    ) {
        HeyText(label, Modifier.grow(), tone = if (enabled) HeyTone.Default else HeyTone.Disabled)
        val track = when {
            !enabled -> colors.bgSunken
            checked -> colors.primary
            else -> colors.borderStrong
        }
        val thumb = if (checked && enabled) colors.fgOnPrimary else colors.bgRaised
        Box(
            Modifier
                .size(width = HeyTokens.Size.touchCarPrimary, height = HeyTokens.Size.touchCar / 2 + HeyTokens.Space.s16)
                .clip(RoundedCornerShape(HeyTokens.Radius.full))
                .background(track),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .padding(HeyTokens.Space.s4)
                    .size(HeyTokens.Size.touchCar / 2 + HeyTokens.Space.s8)
                    .clip(RoundedCornerShape(HeyTokens.Radius.full))
                    .background(thumb),
            )
        }
    }
}

@Composable
fun HeyStepperRow(
    label: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    format: (Int) -> String = { it.toString() },
) {
    val colors = HeyTheme.colors
    val shape = RoundedCornerShape(HeyTokens.Radius.md)
    val minusLabel = androidx.compose.ui.res.stringResource(ai.hey.designsystem.R.string.hey_stepper_decrease)
    val plusLabel = androidx.compose.ui.res.stringResource(ai.hey.designsystem.R.string.hey_stepper_increase)
    HStack(
        gap = HeyGap.G16,
        modifier = modifier
            .clip(shape)
            .background(colors.bgSurface, shape)
            .border(HeyTokens.Border.hairline, colors.borderDefault, shape)
            .heightIn(min = HeyTokens.Size.touchCarPrimary)
            .padding(horizontal = HeyTokens.Space.s24, vertical = HeyTokens.Space.s8),
    ) {
        HeyText(label, Modifier.grow())
        HeyIconButton(HeyIcons.Minus, minusLabel, onClick = { onChange(value - 1) }, enabled = value > range.first)
        HeyText(
            text = format(value),
            modifier = Modifier.widthIn(min = HeyTokens.Size.touchCarPrimary + HeyTokens.Space.s32),
            variant = HeyTextVariant.Heading,
            align = HeyTextAlign.Center,
        )
        HeyIconButton(HeyIcons.Plus, plusLabel, onClick = { onChange(value + 1) }, enabled = value < range.last)
    }
}

data class HeySegmentOption<T>(val value: T, val label: String)

@Composable
fun <T> HeySegmented(
    options: List<HeySegmentOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HeyTheme.colors
    val shape = RoundedCornerShape(HeyTokens.Radius.md)
    HStack(
        gap = HeyGap.G4,
        modifier = modifier
            .clip(shape)
            .background(colors.bgSunken, shape)
            .border(HeyTokens.Border.hairline, colors.borderDefault, shape)
            .padding(HeyTokens.Space.s4),
    ) {
        options.forEach { option ->
            val isSelected = option.value == selected
            val inner = RoundedCornerShape(HeyTokens.Radius.sm)
            Box(
                Modifier
                    .grow()
                    .heightIn(min = HeyTokens.Size.touchCar - HeyTokens.Space.s8)
                    .clip(inner)
                    .background(if (isSelected) colors.primary else Color.Transparent, inner)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, role = Role.RadioButton) { onSelect(option.value) }
                    .padding(horizontal = HeyTokens.Space.s16, vertical = HeyTokens.Space.s8),
                contentAlignment = Alignment.Center,
            ) {
                InkText(
                    text = option.label,
                    color = if (isSelected) colors.fgOnPrimary else colors.fgDefault,
                    variant = HeyTextVariant.Label,
                    maxLines = 2,
                    align = HeyTextAlign.Center,
                )
            }
        }
    }
}

data class HeyDialogAction(val text: String, val onClick: () -> Unit)

/**
 * Modal confirmation drawn in the composition (scrim + centred card) so it previews and snapshots like any part.
 * The caller shows it by placing it last inside a full-size Box.
 */
@Composable
fun HeyDialog(
    title: String,
    message: String,
    confirm: HeyDialogAction,
    dismiss: HeyDialogAction,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
) {
    val colors = HeyTheme.colors
    val shape = RoundedCornerShape(HeyTokens.Radius.xl)
    Box(
        modifier
            .background(colors.scrim)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
        contentAlignment = Alignment.Center,
    ) {
        VStack(
            gap = HeyGap.G24,
            modifier = Modifier
                .widthIn(max = HeyTokens.Sheet.maxWidth + HeyTokens.Space.s32 * 4)
                .clip(shape)
                .background(colors.bgRaised, shape)
                .border(HeyTokens.Border.strong, colors.borderStrong, shape)
                .padding(HeyTokens.Space.s32),
            align = HeyAlign.Start,
        ) {
            VStack(gap = HeyGap.G8) {
                HeyText(title, variant = HeyTextVariant.Title, maxLines = 2)
                HeyText(message, tone = HeyTone.Muted, maxLines = 5)
            }
            HStack(gap = HeyGap.G16) {
                HeyButton(
                    text = dismiss.text,
                    onClick = dismiss.onClick,
                    modifier = Modifier.grow(),
                    variant = HeyButtonVariant.Secondary,
                    size = HeyButtonSize.Large,
                )
                HeyButton(
                    text = confirm.text,
                    onClick = confirm.onClick,
                    modifier = Modifier.grow(),
                    variant = if (danger) HeyButtonVariant.Danger else HeyButtonVariant.Primary,
                    size = HeyButtonSize.Large,
                )
            }
        }
    }
}
