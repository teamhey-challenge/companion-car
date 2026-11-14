package ai.hey.designsystem.component

import ai.hey.designsystem.icon.HeyIcons
import ai.hey.designsystem.layout.HStack
import ai.hey.designsystem.layout.HeyStackScope
import ai.hey.designsystem.layout.VStack
import ai.hey.designsystem.theme.HeyGap
import ai.hey.designsystem.theme.HeyTextVariant
import ai.hey.designsystem.theme.HeyTheme
import ai.hey.designsystem.token.HeyTokens
import android.provider.Settings
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

private const val NOTICE_VISIBLE_MS = 4000L

/**
 * Vertical scroll for a list zone inside a frame. The scrollbar is always drawn when the content overflows,
 * so a driver can see that more rows exist below.
 */
@Composable
fun HeyScrollColumn(
    gap: HeyGap,
    modifier: Modifier = Modifier,
    content: @Composable HeyStackScope.() -> Unit,
) {
    val state = rememberScrollState()
    val track = HeyTheme.colors.borderDefault
    val thumb = HeyTheme.colors.fgSubtle
    Box(
        modifier.drawWithContent {
            drawContent()
            val max = state.maxValue
            if (max > 0) {
                val bar = HeyTokens.Space.s8.toPx()
                val viewport = size.height
                val total = viewport + max
                val thumbHeight = (viewport * viewport / total).coerceAtLeast(HeyTokens.Size.touchCar.toPx())
                val top = (viewport - thumbHeight) * state.value / max
                val x = size.width - bar
                val radius = CornerRadius(bar / 2f)
                drawRoundRect(track, Offset(x, 0f), Size(bar, viewport), radius)
                drawRoundRect(thumb, Offset(x, top), Size(bar, thumbHeight), radius)
            }
        },
    ) {
        VStack(gap = gap, modifier = Modifier.fillMaxWidth().verticalScroll(state).padding(end = HeyTokens.Space.s24), content = content)
    }
}

/** Title plus its group; the parent puts 24 between groups. */
@Composable
fun HeySection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable HeyStackScope.() -> Unit,
) {
    VStack(gap = HeyGap.G16, modifier = modifier) {
        HeyText(title, variant = HeyTextVariant.Heading, tone = ai.hey.designsystem.theme.HeyTone.Muted, maxLines = 1)
        VStack(gap = HeyGap.G8, content = content)
    }
}

/** Reduced motion on Android is the system animator scale being 0. */
@Composable
internal fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** Short one-off notice at the top of a screen. Disappears after 4 seconds, instantly under reduced motion. */
@Composable
fun HeyNotice(
    text: String,
    tone: HeyStatusTone,
    modifier: Modifier = Modifier,
) {
    var visible by remember(text) { mutableStateOf(true) }
    val reduced = rememberReducedMotion()
    LaunchedEffect(text) {
        delay(NOTICE_VISIBLE_MS)
        visible = false
    }
    val ink = HeyTheme.colors.ink(tone)
    val shape = RoundedCornerShape(HeyTokens.Radius.lg)
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = EnterTransition.None,
        exit = if (reduced) ExitTransition.None else fadeOut(tween(HeyTokens.Motion.base)),
    ) {
        HStack(
            gap = HeyGap.G16,
            modifier = Modifier
                .clip(shape)
                .background(ink.container, shape)
                .padding(horizontal = HeyTokens.Space.s24, vertical = HeyTokens.Space.s16),
        ) {
            InkIcon(
                when (tone) {
                    HeyStatusTone.Success -> HeyIcons.Check
                    HeyStatusTone.Warning -> HeyIcons.Warning
                    HeyStatusTone.Danger -> HeyIcons.Error
                    else -> HeyIcons.Info
                },
                null,
                ink.content,
            )
            InkText(text, ink.content, Modifier.grow(), HeyTextVariant.Heading, maxLines = 2)
        }
    }
}
