package ai.hey.designsystem.component

import ai.hey.designsystem.R
import ai.hey.designsystem.icon.HeyIcons
import ai.hey.designsystem.layout.HStack
import ai.hey.designsystem.layout.HeyAlign
import ai.hey.designsystem.layout.HeyArrange
import ai.hey.designsystem.layout.VStack
import ai.hey.designsystem.theme.HeyGap
import ai.hey.designsystem.theme.HeyTextVariant
import ai.hey.designsystem.theme.HeyTheme
import ai.hey.designsystem.theme.HeyTone
import ai.hey.designsystem.token.HeyTokens
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.sin

// ---------------------------------------------------------------- choice row

/**
 * 2-4 way pill group for a single choice (먼저 말 걸기 적게/보통/자주, 반말/존댓말), the mockup's `.frequency-options`:
 * 144dp-wide pills, 76dp high, 16 apart; the chosen pill has the accent container and the focus-ring border, so it reads
 * without colour alone. The label above is 24sp muted.
 */
@Composable
fun <T> ChoiceRow(
    options: List<HeySegmentOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
) {
    val colors = HeyTheme.colors
    VStack(HeyGap.G16, modifier = modifier) {
        if (label != null) HeyText(label, variant = HeyTextVariant.Label, tone = HeyTone.Muted, maxLines = 1)
        HStack(HeyGap.G16) {
            options.forEach { option ->
                val isSelected = option.value == selected
                val shape = RoundedCornerShape(HeyTokens.Radius.full)
                Box(
                    Modifier
                        .widthIn(min = 144.dp)
                        .heightIn(min = HeyTokens.Size.touchCar)
                        .clip(shape)
                        .background(if (isSelected) colors.accentContainer else colors.bgRaised, shape)
                        .border(HeyTokens.Border.strong, if (isSelected) colors.focusRing else Color.Transparent, shape)
                        .semantics { this.selected = isSelected }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = enabled,
                            role = Role.RadioButton,
                        ) { onSelect(option.value) }
                        .padding(horizontal = 28.dp, vertical = HeyTokens.Space.s16),
                    contentAlignment = Alignment.Center,
                ) {
                    InkText(
                        option.label,
                        if (enabled) colors.fgDefault else colors.fgDisabled,
                        variant = HeyTextVariant.Label,
                        maxLines = 2,
                        align = HeyTextAlign.Center,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- memory

/**
 * One remembered item: kind label, the text, date, and a 76dp delete button. [tint] 0..2 rotates the card colour as the
 * design does; [deleteLabel] is the accessibility name and should include the kind ("이 기억 지우기").
 */
@Composable
fun MemoryRow(
    kind: String,
    text: String,
    onDelete: () -> Unit,
    deleteLabel: String,
    modifier: Modifier = Modifier,
    date: String? = null,
    tint: Int = 0,
    enabled: Boolean = true,
) {
    val colors = HeyTheme.colors
    val surface = when (((tint % 3) + 3) % 3) {
        0 -> colors.secondaryContainer
        1 -> colors.infoContainer
        else -> colors.sceneSand
    }
    val shape = RoundedCornerShape(HeyTokens.Space.s24 - 4.dp)
    HStack(
        gap = HeyGap.G16,
        modifier = modifier
            .clip(shape)
            .background(surface, shape)
            .heightIn(min = HeyTokens.Size.touchCarPrimary)
            // Mockup C6: card top to label top 39 dp (was 27), card pitch 198 dp (was 194).
            .padding(start = HeyTokens.Space.s24, end = HeyTokens.Space.s24, top = HeyTokens.Space.s32 + 4.dp, bottom = HeyTokens.Space.s16),
    ) {
        // Mockup C6: kind→body 8 dp further apart (body y 427), body→date 8 dp closer (date y 476 unchanged),
        // delete glyph 6 dp higher and 9 dp further right.
        VStack(HeyGap.G16, modifier = Modifier.grow()) {
            VStack(HeyGap.G16) {
                InkText(kind, colors.fgDefault, size = 22.sp, maxLines = 1)
                InkText(text, colors.fgDefault, size = 28.sp, maxLines = 3)
            }
            if (date != null) InkText(date, colors.fgMuted, size = 20.sp, maxLines = 1)
        }
        HeyIconButton(
            HeyIcons.Trash, deleteLabel, onDelete, enabled = enabled, bare = true,
            modifier = Modifier.offset(x = 9.dp, y = (-6).dp),
        )
    }
}

/** Loading placeholder for [MemoryRow]; static when the system reduces motion. */
@Composable
fun SkeletonRow(modifier: Modifier = Modifier) {
    val colors = HeyTheme.colors
    val moving = !rememberReducedMotion()
    val alpha = if (moving) {
        val a by rememberInfiniteTransition(label = "skeleton").animateFloat(
            initialValue = 0.5f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
            label = "skeletonAlpha",
        )
        a
    } else {
        1f
    }
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = HeyTokens.Size.touchCarPrimary)
            .clip(RoundedCornerShape(HeyTokens.Radius.md))
            .background(colors.bgSunken.copy(alpha = alpha)),
    )
}

// ---------------------------------------------------------------- confirm dialog

private val ConfirmButtonHeight = 104.dp

/**
 * Modal confirmation (delete memory, withdraw consent, unpair). Drawn in the composition (scrim + card); place it last
 * inside a full-size Box. Buttons are 104dp high. While [busy] both buttons are disabled and the confirm shows a spinner.
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    busy: Boolean = false,
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
                .padding(HeyTokens.Space.s32),
            align = HeyAlign.Start,
        ) {
            VStack(HeyGap.G8) {
                HeyText(title, variant = HeyTextVariant.Title, maxLines = 3)
                HeyText(message, tone = HeyTone.Muted, maxLines = 6)
            }
            HStack(HeyGap.G16, modifier = Modifier.fillMaxWidth()) {
                HeyButton(
                    dismissText, onDismiss, Modifier.grow().heightIn(min = ConfirmButtonHeight),
                    variant = HeyButtonVariant.Secondary, size = HeyButtonSize.Large, enabled = !busy,
                )
                HeyButton(
                    confirmText, onConfirm, Modifier.grow().heightIn(min = ConfirmButtonHeight),
                    variant = if (danger) HeyButtonVariant.Danger else HeyButtonVariant.Primary,
                    size = HeyButtonSize.Large, loading = busy,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- status badge

/** The 12-bar waveform of the design; heights are fixed per bar and animated by a shared phase. */
@Composable
private fun Waveform(color: Color, moving: Boolean, speaking: Boolean, modifier: Modifier = Modifier) {
    val phase: Float = if (moving) {
        val p by rememberInfiniteTransition(label = "wave").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(if (speaking) 1800 else 1100, easing = LinearEasing), RepeatMode.Restart),
            label = "wavePhase",
        )
        p
    } else {
        0f
    }
    Canvas(modifier.size(172.dp, 40.dp)) {
        val bars = 12
        val barW = 8.dp.toPx()
        val gap = (size.width - barW * bars) / (bars - 1)
        repeat(bars) { i ->
            val base = 8 + (sin(i * 1.73) * .5 + .5) * (1 - abs(i - 5.5) / 8) * 28
            val wobble = if (moving) 0.65 + 0.35 * sin(2 * PI * (phase + i * 0.137)) else 1.0
            val h = (base * wobble).toFloat().dp.toPx().coerceAtLeast(barW)
            drawRoundRect(
                color,
                Offset(i * (barW + gap), (size.height - h) / 2f),
                Size(barW, h),
                CornerRadius(barW / 2f),
            )
        }
    }
}

/**
 * Phase indicator. Listening/Speaking draw a waveform (static under reduced motion) with the label read aloud only;
 * Thinking is dot + text. While driving the label stays short ("잠깐만요") and Idle draws nothing. A connection other
 * than Online replaces the phase ("연결 중" / "오프라인").
 */
@Composable
fun StatusBadge(
    phase: TalkPhase,
    modifier: Modifier = Modifier,
    driving: Boolean = false,
    connection: HeyConnection = HeyConnection.Online,
    animated: Boolean = true,
) {
    val colors = HeyTheme.colors
    val moving = animated && !rememberReducedMotion()
    val label: String? = when {
        connection == HeyConnection.Reconnecting -> stringResource(R.string.hey_status_connecting)
        connection == HeyConnection.Offline -> stringResource(R.string.hey_status_offline)
        phase == TalkPhase.Listening -> stringResource(R.string.hey_status_listening)
        phase == TalkPhase.Thinking -> stringResource(if (driving) R.string.hey_status_thinking_driving else R.string.hey_status_thinking)
        phase == TalkPhase.Speaking -> stringResource(R.string.hey_status_speaking)
        driving -> null
        else -> stringResource(R.string.hey_status_ready)
    }
    if (label == null) return
    val online = connection == HeyConnection.Online
    val color = when {
        !online -> if (connection == HeyConnection.Offline) colors.danger else colors.warning
        phase == TalkPhase.Listening -> colors.info
        phase == TalkPhase.Thinking -> colors.warning
        phase == TalkPhase.Speaking -> colors.accent
        else -> colors.fgMuted
    }
    // Mockup `.status`: 24 label, 9 dot, 12 apart, the phase colour. On the driving screen (C5) the label is default ink at 24
    // while thinking and a 20 muted word while listening/speaking: no waveform there (`.car[data-driving=true] .status-motion{display:none}`).
    HStack(
        gap = HeyGap.G8,
        modifier = modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        if (online && !driving && (phase == TalkPhase.Listening || phase == TalkPhase.Speaking)) {
            // Design `.status[data-visual=waveform]`: the wave, then the small phase word (PLEOS-194 "파형 + 듣는 중/말하는 중").
            Waveform(color, moving, speaking = phase == TalkPhase.Speaking)
            InkText(
                label, colors.fgMuted, Modifier.padding(start = 16.dp),
                weight = androidx.compose.ui.text.font.FontWeight.Normal, size = 20.sp, lineHeight = 30.sp, maxLines = 1,
            )
        } else if (driving && online && (phase == TalkPhase.Listening || phase == TalkPhase.Speaking)) {
            InkText(label, if (phase == TalkPhase.Listening) colors.fgDefault else colors.fgMuted, size = 20.sp, lineHeight = 30.sp, maxLines = 1)
        } else {
            Box(Modifier.size(9.dp).clip(RoundedCornerShape(HeyTokens.Radius.full)).background(if (driving) colors.fgDefault else color))
            InkText(
                label, if (driving) colors.fgDefault else color, Modifier.padding(start = 4.dp),
                weight = androidx.compose.ui.text.font.FontWeight.Normal, size = 24.sp, lineHeight = 36.sp, maxLines = 1,
            )
        }
    }
}

// ---------------------------------------------------------------- error panel

enum class ErrorKind { Offline, Connecting, Microphone, Server, Recognition, Audio }

private class ErrorCopy(val title: Int, val message: Int, val action: Int)

private fun ErrorKind.copy() = when (this) {
    ErrorKind.Offline -> ErrorCopy(R.string.hey_error_offline_title, R.string.hey_error_offline_message, R.string.hey_error_offline_action)
    ErrorKind.Connecting -> ErrorCopy(R.string.hey_error_connecting_title, R.string.hey_error_connecting_message, R.string.hey_error_connecting_action)
    ErrorKind.Microphone -> ErrorCopy(R.string.hey_error_microphone_title, R.string.hey_error_microphone_message, R.string.hey_error_microphone_action)
    ErrorKind.Server -> ErrorCopy(R.string.hey_error_server_title, R.string.hey_error_server_message, R.string.hey_error_server_action)
    ErrorKind.Recognition -> ErrorCopy(R.string.hey_error_recognition_title, R.string.hey_error_recognition_message, R.string.hey_error_recognition_action)
    ErrorKind.Audio -> ErrorCopy(R.string.hey_error_audio_title, R.string.hey_error_audio_message, R.string.hey_error_audio_action)
}

/**
 * Error text column: title, message and the recovery action, with default copy per [kind]. [onAction] null or kind =
 * Connecting draws the action disabled ("연결 중"). The caller places the sorry character beside it.
 * [parked] is the C8 look of the mockup (48 title, 24 message, glass action pinned to the column bottom); without it the
 * panel is the compact inline one (C6 load failure).
 */
@Composable
fun ErrorPanel(
    kind: ErrorKind,
    onAction: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    title: String? = null,
    message: String? = null,
    parked: Boolean = false,
) {
    val copy = kind.copy()
    val alertMode = if (kind == ErrorKind.Connecting) LiveRegionMode.Polite else LiveRegionMode.Assertive
    if (parked) {
        // Mockup parked C8: title 48/450 (-0.05em, line 1.4), message 24 under it (+24, line 1.6), the one button bottom-aligned.
        VStack(HeyGap.G24, modifier = modifier, arrange = HeyArrange.SpaceBetween) {
            VStack(HeyGap.G24, modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = alertMode }) {
                InkText(
                    title ?: stringResource(copy.title), HeyTheme.colors.fgDefault, maxLines = 3,
                    weight = androidx.compose.ui.text.font.FontWeight.Normal, trackingEm = -0.055f, size = 48.sp, lineHeight = 67.sp,
                )
                InkText(
                    message ?: stringResource(copy.message), HeyTheme.colors.fgMuted, Modifier.padding(top = 8.dp), maxLines = 4,
                    weight = androidx.compose.ui.text.font.FontWeight.Normal, size = 24.sp, lineHeight = 38.sp,
                )
            }
            if (onAction != null || kind == ErrorKind.Connecting) {
                GlassButton(
                    actionLabel ?: stringResource(copy.action),
                    onAction ?: {},
                    icon = if (kind == ErrorKind.Microphone) HeyIcons.Mic else HeyIcons.Link,
                    enabled = onAction != null && kind != ErrorKind.Connecting,
                )
            }
        }
        return
    }
    VStack(HeyGap.G24, modifier = modifier) {
        VStack(HeyGap.G16, modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = alertMode }) {
            HeyText(title ?: stringResource(copy.title), variant = HeyTextVariant.Title, maxLines = 3)
            HeyText(message ?: stringResource(copy.message), tone = HeyTone.Muted, maxLines = 4)
        }
        if (onAction != null || kind == ErrorKind.Connecting) {
            HeyButton(
                actionLabel ?: stringResource(copy.action),
                onAction ?: {},
                variant = HeyButtonVariant.Secondary,
                size = HeyButtonSize.Large,
                icon = if (kind == ErrorKind.Microphone) HeyIcons.Mic else HeyIcons.Link,
                enabled = onAction != null && kind != ErrorKind.Connecting,
            )
        }
    }
}

// ---------------------------------------------------------------- subtitle

/**
 * Caption of what is being said/heard: at most 3 lines, ellipsis; composes nothing while [driving] (no captions on the road).
 * Mockup `.caption`: 32 regular, line height 1.5, default ink. [hint] is the small muted line under it ("말을 마치면 알아서 보내요").
 */
@Composable
fun Subtitle(
    text: String,
    modifier: Modifier = Modifier,
    driving: Boolean = false,
    hint: String? = null,
) {
    if (driving) return
    VStack(HeyGap.G16, modifier = modifier) {
        InkText(
            text, HeyTheme.colors.fgDefault, maxLines = 3,
            weight = androidx.compose.ui.text.font.FontWeight.Normal, size = 32.sp, lineHeight = 48.sp,
        )
        if (hint != null) {
            InkText(
                hint, HeyTheme.colors.fgMuted, maxLines = 2,
                weight = androidx.compose.ui.text.font.FontWeight.Normal, size = 23.sp, lineHeight = 37.sp,
            )
        }
    }
}

// ---------------------------------------------------------------- QR panel

/** Pairing QR with its code in text (for typing it on the phone) and the current status line. */
@Composable
fun QrPanel(
    content: String,
    code: String,
    status: String,
    modifier: Modifier = Modifier,
    qrSize: Dp = 320.dp,
    /** 1/2 and 1/3 windows: a 240 dp QR (still large enough to scan across the dashboard). */
    compact: Boolean = false,
) {
    VStack(HeyGap.G16, modifier = modifier, align = HeyAlign.Center) {
        HeyQrCode(content, if (compact) 240.dp else qrSize)
        HeyText(code, variant = HeyTextVariant.Title, maxLines = 1, align = HeyTextAlign.Center)
        HeyText(status, tone = HeyTone.Muted, maxLines = 2, align = HeyTextAlign.Center)
    }
}
