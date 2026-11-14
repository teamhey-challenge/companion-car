package ai.hey.designsystem.component

import ai.hey.designsystem.layout.VStack
import ai.hey.designsystem.theme.HeyGap
import ai.hey.designsystem.theme.HeyTextVariant
import ai.hey.designsystem.theme.HeyTheme
import ai.hey.designsystem.theme.HeyTone
import ai.hey.designsystem.theme.style
import ai.hey.designsystem.token.HeyTokens
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

enum class HeyKeyboard { Text, Number, Uri }

/**
 * One-line text input with its label above. The screen decides whether it is editable by passing [enabled]; a
 * disabled field looks like the disabled switch row. Errors are shown under the field.
 */
@Composable
fun HeyTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboard: HeyKeyboard = HeyKeyboard.Text,
    enabled: Boolean = true,
    placeholder: String? = null,
    error: String? = null,
) {
    val colors = HeyTheme.colors
    val shape = RoundedCornerShape(HeyTokens.Radius.md)
    VStack(gap = HeyGap.G8, modifier = modifier) {
        HeyText(label, variant = HeyTextVariant.Label, tone = HeyTone.Muted, maxLines = 1)
        Box(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(if (enabled) colors.bgSurface else colors.bgSunken, shape)
                .border(HeyTokens.Border.strong, if (error != null) colors.danger else if (enabled) colors.borderStrong else colors.borderDefault, shape)
                .heightIn(min = HeyTokens.Size.touchCar)
                .padding(horizontal = HeyTokens.Space.s24, vertical = HeyTokens.Space.s8),
            contentAlignment = Alignment.CenterStart,
        ) {
            val textColor = if (enabled) colors.fgDefault else colors.fgDisabled
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = HeyTextVariant.Body.spec.style(textColor),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = when (keyboard) {
                        HeyKeyboard.Text -> KeyboardType.Text
                        HeyKeyboard.Number -> KeyboardType.Number
                        HeyKeyboard.Uri -> KeyboardType.Uri
                    },
                ),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (value.isEmpty() && placeholder != null) {
                        InkText(placeholder, colors.fgSubtle, variant = HeyTextVariant.Body, maxLines = 1)
                    }
                    inner()
                },
            )
        }
        if (error != null) HeyText(error, variant = HeyTextVariant.Caption, tone = HeyTone.Danger, maxLines = 2)
    }
}

/**
 * Puts a modal overlay (a [HeyDialog], filling the box) and a short notice ([HeyNotice], under the status strip)
 * over [content]. A slot that is null is not composed at all.
 */
@Composable
fun HeyLayered(
    modifier: Modifier = Modifier,
    overlay: (@Composable () -> Unit)? = null,
    notice: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(modifier) {
        content()
        if (notice != null) {
            Box(
                Modifier.align(Alignment.TopCenter).padding(top = HeyTokens.Space.s32 * 3),
            ) { notice() }
        }
        overlay?.invoke()
    }
}
