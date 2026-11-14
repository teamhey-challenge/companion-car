package ai.hey.designsystem.component

import ai.hey.designsystem.R
import ai.hey.designsystem.icon.HeyIcons
import ai.hey.designsystem.layout.HStack
import ai.hey.designsystem.layout.HeyAlign
import ai.hey.designsystem.layout.HeyArrange
import ai.hey.designsystem.layout.HeyStackScope
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

// ---------------------------------------------------------------- ocean background

private class Bubble(val x: Float, val sizeDp: Float, val durationS: Float, val delayS: Float, val driftDp: Float)

/** The six bubbles of design v0.5 (ui.tsx `oceanBubbles`): x in %, size px, period s, negative delay s, sideways drift px. */
private val Bubbles = listOf(
    Bubble(0.12f, 54f, 34f, -8f, 22f),
    Bubble(0.38f, 88f, 38f, -25f, -28f),
    Bubble(0.62f, 66f, 29f, -17f, 26f),
    Bubble(0.76f, 118f, 40f, -4f, -32f),
    Bubble(0.87f, 46f, 27f, -22f, 18f),
    Bubble(0.95f, 82f, 35f, -13f, -24f),
)

private const val BUBBLE_CLOCK_S = 1200f

/**
 * Sea (light) / deep sea (dark) page background with six faint outline bubbles drifting upward.
 * [animated] = false (reduced motion, or the C5 driving screen) freezes the bubbles in place; pass false there.
 * Decorative only: it takes no input and has no semantics.
 */
@Composable
fun OceanBackground(
    modifier: Modifier = Modifier,
    animated: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = HeyTheme.colors
    val dark = HeyTheme.dark
    val moving = animated && !rememberReducedMotion()
    val brush = remember(colors) { Brush.verticalGradient(listOf(colors.bgPage, colors.sceneSky)) }
    // Soft filled bubbles (design `.ocean-bubbles i`): radial highlight at 32%/28%, faint rim.
    val rimColor = if (dark) Color(0xFF83CEDE).copy(alpha = 0.12f) else Color.White.copy(alpha = 0.32f)
    val hi = if (dark) Color(0xFF91D6E5).copy(alpha = 0.12f) else Color.White.copy(alpha = 0.38f)
    val mid = if (dark) Color(0xFF2C7893).copy(alpha = 0.05f) else Color(0xFFE1F5FC).copy(alpha = 0.10f)
    val edge = if (dark) Color(0xFF49A5B8).copy(alpha = 0.07f) else Color(0xFF93CAE0).copy(alpha = 0.10f)
    val clock: Float = if (moving) {
        val transition = rememberInfiniteTransition(label = "ocean")
        val t by transition.animateFloat(
            initialValue = 0f,
            targetValue = BUBBLE_CLOCK_S,
            animationSpec = infiniteRepeatable(tween((BUBBLE_CLOCK_S * 1000).toInt(), easing = LinearEasing), RepeatMode.Restart),
            label = "oceanClock",
        )
        t
    } else {
        0f
    }
    Box(modifier.background(brush)) {
        Canvas(Modifier.fillMaxSize()) {
            val t = if (moving) clock else 0f
            Bubbles.forEach { b ->
                val phase = (((t + b.delayS) / b.durationS) % 1f + 1f) % 1f
                val d = b.sizeDp.dp.toPx()
                val rise = size.height + d * 2f
                val cx = size.width * b.x + b.driftDp.dp.toPx() * (phase - 0.5f)
                val cy = size.height + d - rise * phase
                val fade = (sin(PI * phase).toFloat() * 0.58f).coerceIn(0f, 0.58f)
                val center = Offset(cx, cy)
                val r = d / 2f
                val fill = Brush.radialGradient(
                    0f to hi, 0.48f to mid, 0.76f to edge, 1f to Color.Transparent,
                    center = Offset(center.x - r * 0.36f, center.y - r * 0.44f), radius = r * 1.4f,
                )
                drawCircle(fill, r, center, alpha = fade)
                drawCircle(rimColor, r, center, alpha = fade, style = Stroke(1.dp.toPx()))
            }
        }
        content()
    }
}

// ---------------------------------------------------------------- character

/** Stage boxes from asset-placement.md; the picture is `contain`ed and centred inside. */
enum class CharacterSize(val width: Dp, val height: Dp) {
    /** C3/C4/C8 right side. */
    Stage(580.dp, 620.dp),

    /** C1/C2 right side. */
    Hero(500.dp, 440.dp),

    /** C5 centre. */
    Driving(280.dp, 300.dp),

    /** Small stage (phone-style cards). */
    Small(150.dp, 160.dp),
}

/** Theme-aware still: aqua in the sea theme, peach in the deep-sea theme (design "헤이파리 테마별 기본 색상"). */
@Composable
fun characterStill(): Painter =
    painterResource(if (HeyTheme.dark) R.drawable.hey_character_still_peach else R.drawable.hey_character_still)

/**
 * Hosts the character. With no [live] view it draws [still]; a Rive view (or any composable) passed as [live] replaces the
 * still. The slot is always [size] big so the layout never shifts when the live view loads or fails back to the still.
 */
@Composable
fun CharacterSlot(
    size: CharacterSize,
    modifier: Modifier = Modifier,
    still: Painter = characterStill(),
    live: (@Composable () -> Unit)? = null,
    contentDescription: String? = null,
) {
    Box(
        modifier
            .size(size.width, size.height)
            .let { if (contentDescription != null) it.semantics { this.contentDescription = contentDescription } else it },
        contentAlignment = Alignment.Center,
    ) {
        if (live != null) {
            live()
        } else {
            Image(still, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
    }
}

/** The still plus an optional caption under it (C1/C2 "당신의 이야기가 머무는 곳"). */
@Composable
fun CharacterStill(
    description: String,
    modifier: Modifier = Modifier,
    size: CharacterSize = CharacterSize.Hero,
    caption: String? = null,
) {
    VStack(HeyGap.G16, modifier = modifier, align = HeyAlign.Center) {
        CharacterSlot(size, contentDescription = description)
        if (caption != null) HeyText(caption, tone = HeyTone.Muted, maxLines = 2, align = HeyTextAlign.Center)
    }
}

// ---------------------------------------------------------------- talk buttons

enum class TalkPhase { Idle, Listening, Thinking, Speaking }

private val TalkMinWidth = 320.dp
private val TalkBigMinWidth = 360.dp

private class GlassInk(val container: Brush, val content: Color, val border: Color, val shadow: Color)

private fun glass(a: Color, b: Color, c: Color? = null): Brush = Brush.linearGradient(listOfNotNull(a, b, c))

/** Soft glass pill of the design (`.talk-button`): translucent tint, light rim, soft shadow; no hard outline. */
@Composable
private fun glassInk(phase: TalkPhase, pressed: Boolean, blocked: Boolean): GlassInk {
    val c = HeyTheme.colors
    val dark = HeyTheme.dark
    val none = Color.Transparent
    return if (dark) {
        val rim = Color(0xFF89D5E1).copy(alpha = 0.30f)
        val shadow = Color(0xFF00070E).copy(alpha = 0.20f)
        when {
            blocked -> GlassInk(glass(c.bgSurface, c.bgSurface), c.fgMuted, c.borderDefault, none)
            phase == TalkPhase.Listening -> GlassInk(glass(Color(0xFF2D7F90).copy(alpha = 0.28f), Color(0xFF2D7F90).copy(alpha = 0.28f)), c.fgDefault, rim, shadow)
            pressed -> GlassInk(glass(c.accentContainer, c.accentContainer), c.fgDefault, rim, shadow)
            else -> GlassInk(
                glass(Color(0xFF72BFD7).copy(alpha = 0.14f), Color(0xFF3E8FA3).copy(alpha = 0.12f), Color(0xFF33A0B1).copy(alpha = 0.22f)),
                c.fgDefault, rim, shadow,
            )
        }
    } else {
        val rim = Color.White.copy(alpha = 0.80f)
        val shadow = Color(0xFF25506C).copy(alpha = 0.16f)
        val blue = Color(0xFF69B4DA)
        when {
            blocked -> GlassInk(glass(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.08f)), c.fgMuted, Color.White.copy(alpha = 0.5f), none)
            phase == TalkPhase.Listening -> GlassInk(glass(Color.White.copy(alpha = 0.12f), Color(0xFF91BED0).copy(alpha = 0.16f)), c.fgDefault, rim, shadow)
            pressed -> GlassInk(glass(blue.copy(alpha = 0.22f), blue.copy(alpha = 0.30f)), c.fgOnAccent, rim, none)
            else -> GlassInk(
                glass(Color.White.copy(alpha = 0.14f), blue.copy(alpha = 0.08f), blue.copy(alpha = 0.20f)),
                c.fgOnAccent, rim, shadow,
            )
        }
    }
}

/** One pill used by [TalkButton] and [StopListeningButton]: 96dp (112dp when [big]), icon + label, tap or press-and-hold. */
@Composable
private fun TalkPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    phase: TalkPhase,
    blocked: Boolean,
    big: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    onHoldStart: (() -> Unit)? = null,
    onHoldEnd: (() -> Unit)? = null,
    /** Blocked look but still pressable. */
    dimmed: Boolean = false,
) {
    var pressed by remember { mutableStateOf(false) }
    val ink = glassInk(phase, pressed, blocked || dimmed)
    val shape = RoundedCornerShape(HeyTokens.Radius.full)
    val hold = onHoldStart != null
    val press = if (blocked) {
        Modifier
    } else {
        Modifier.pointerInput(hold, onClick, onHoldStart, onHoldEnd) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    onHoldStart?.invoke()
                    try {
                        tryAwaitRelease()
                    } finally {
                        pressed = false
                        onHoldEnd?.invoke()
                    }
                },
                onTap = if (hold) null else { _ -> onClick() },
            )
        }
    }
    Box(
        modifier
            .heightIn(min = HeyTokens.Size.touchCarPrimary)
            .widthIn(min = if (big) TalkBigMinWidth else TalkMinWidth)
            .shadow(if (ink.shadow == Color.Transparent) 0.dp else 6.dp, shape, ambientColor = ink.shadow, spotColor = ink.shadow)
            .clip(shape)
            .background(ink.container, shape)
            .border(HeyTokens.Border.hairline, ink.border, shape)
            .then(press)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = label
                if (blocked) disabled() else this.onClick(label) { onClick(); true }
            }
            .padding(horizontal = HeyTokens.Space.s32, vertical = HeyTokens.Space.s8),
        contentAlignment = Alignment.Center,
    ) {
        HStack(HeyGap.G16) {
            if (icon != null) InkIcon(icon, null, ink.content, Modifier.size(HeyTokens.Size.iconCar * 4 / 3))
            InkText(
                label, ink.content,
                variant = HeyTextVariant.Heading,
                maxLines = 2, align = HeyTextAlign.Center,
            )
        }
    }
}

/**
 * The primary action. 96dp high (112dp when [big], the C5 driving variant). Idle/Listening can be pressed; Thinking and
 * Speaking (and [enabled] = false) draw the blocked look and take no input.
 *
 * Parked: pass [onHoldStart]/[onHoldEnd] for press-and-hold ("누르고 말하기"); [onClick] is then only the accessibility
 * action. Driving: leave them null; a tap is [onClick] ("말하기" / "말하기 끝내기").
 */
@Composable
fun TalkButton(
    phase: TalkPhase,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    big: Boolean = false,
    driving: Boolean = false,
    enabled: Boolean = true,
    label: String? = null,
    onHoldStart: (() -> Unit)? = null,
    onHoldEnd: (() -> Unit)? = null,
) {
    val blocked = !enabled || phase == TalkPhase.Thinking || phase == TalkPhase.Speaking
    val text = label ?: stringResource(
        when (phase) {
            TalkPhase.Idle -> if (driving) R.string.hey_talk_idle_driving else R.string.hey_talk_idle
            TalkPhase.Listening -> if (driving) R.string.hey_talk_listening_driving else R.string.hey_talk_listening
            TalkPhase.Thinking -> R.string.hey_talk_thinking
            TalkPhase.Speaking -> R.string.hey_talk_speaking
        },
    )
    TalkPill(
        label = text,
        icon = if (phase == TalkPhase.Listening) HeyIcons.Pause else HeyIcons.Mic,
        phase = phase,
        blocked = blocked,
        big = big,
        onClick = onClick,
        modifier = modifier,
        onHoldStart = if (driving) null else onHoldStart,
        onHoldEnd = if (driving) null else onHoldEnd,
    )
}

/** "그만 듣기": stops the assistant's speech and listens (D-24). Same size and look as the talk button, always pressable. */
@Composable
fun StopListeningButton(onClick: () -> Unit, modifier: Modifier = Modifier, big: Boolean = false) {
    TalkPill(
        label = stringResource(R.string.hey_stop_listening),
        icon = HeyIcons.Pause,
        phase = TalkPhase.Idle,
        blocked = false,
        big = big,
        onClick = onClick,
        modifier = modifier,
    )
}

/**
 * Glass pill for a screen's one main action (C1 "동의하고 시작하기", C2 "연결 완료 상태 보기", C4 "그만 듣기"), same look as
 * the talk button. [dimmed] draws the blocked look while still taking the press.
 */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    enabled: Boolean = true,
    dimmed: Boolean = false,
) {
    TalkPill(
        label = text,
        icon = icon,
        phase = TalkPhase.Idle,
        blocked = !enabled,
        big = false,
        onClick = onClick,
        modifier = modifier,
        dimmed = dimmed,
    )
}

// ---------------------------------------------------------------- headings and rows

/** Page title with an optional line under it. [hero] is the 72sp home greeting. */
@Composable
fun PageHeading(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    hero: Boolean = false,
) {
    VStack(HeyGap.G8, modifier = modifier) {
        // Design: headings are regular weight (450) with -0.055em tracking (`.welcome h1`, `.page-heading h1`).
        InkText(
            title, HeyTheme.colors.fgDefault,
            variant = if (hero) HeyTextVariant.Hero else HeyTextVariant.Title,
            maxLines = if (hero) 3 else 2,
            weight = androidx.compose.ui.text.font.FontWeight.Normal,
            trackingEm = -0.055f,
        )
        if (subtitle != null) HeyText(subtitle, tone = HeyTone.Muted, maxLines = 3)
    }
}

/** Page header: optional back circle (76dp), title, an optional state label ("정차 중"), and trailing actions on the right. */
@Composable
fun HeaderBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backLabel: String = "",
    stateLabel: String? = null,
    trailing: (@Composable HeyStackScope.() -> Unit)? = null,
) {
    HStack(
        gap = HeyGap.G24,
        arrange = HeyArrange.SpaceBetween,
        modifier = modifier.fillMaxWidth().heightIn(min = HeyTokens.Size.touchCar + HeyTokens.Space.s8),
    ) {
        HStack(HeyGap.G16) {
            if (onBack != null) HeyIconButton(HeyIcons.ArrowLeft, backLabel, onBack)
            HeyText(title, variant = HeyTextVariant.Title, maxLines = 1)
            if (stateLabel != null) {
                Box(Modifier.size(HeyTokens.Border.hairline, HeyTokens.Space.s32).background(HeyTheme.colors.borderStrong))
                HeyText(stateLabel, variant = HeyTextVariant.Heading, tone = HeyTone.Muted, maxLines = 1)
            }
        }
        if (trailing != null) HStack(HeyGap.G16, content = trailing)
    }
}

private val RowShape = RoundedCornerShape(HeyTokens.Radius.md)

/** 96dp switch row with an optional description; while [saving] it shows "저장 중" and takes no input. */
@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
    saving: Boolean = false,
) {
    val colors = HeyTheme.colors
    val active = enabled && !saving
    HStack(
        gap = HeyGap.G16,
        modifier = modifier
            .clip(RowShape)
            .background(colors.bgSurface, RowShape)
            .border(HeyTokens.Border.hairline, colors.borderDefault, RowShape)
            .toggleable(
                value = checked,
                enabled = active,
                role = Role.Switch,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onValueChange = onCheckedChange,
            )
            .heightIn(min = HeyTokens.Size.touchCarPrimary)
            .padding(horizontal = HeyTokens.Space.s24, vertical = HeyTokens.Space.s16),
    ) {
        VStack(HeyGap.G4, modifier = Modifier.grow()) {
            HeyText(label, tone = if (enabled) HeyTone.Default else HeyTone.Disabled, maxLines = 2)
            if (description != null) HeyText(description, variant = HeyTextVariant.Caption, tone = HeyTone.Muted, maxLines = 3)
        }
        if (saving) {
            HeySpinner(colors.fgMuted)
        }
        val track = when {
            !enabled -> colors.bgSunken
            checked -> colors.primary
            else -> colors.fgSubtle
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

/** Large checkbox row (C1 consent). [error] is shown under the row in the danger colour. */
@Composable
fun CheckRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    error: String? = null,
) {
    val colors = HeyTheme.colors
    VStack(HeyGap.G8, modifier = modifier) {
        HStack(
            gap = HeyGap.G16,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RowShape)
                .background(colors.bgSurface, RowShape)
                .border(HeyTokens.Border.hairline, if (error != null) colors.danger else colors.borderDefault, RowShape)
                .toggleable(
                    value = checked,
                    enabled = enabled,
                    role = Role.Checkbox,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onValueChange = onCheckedChange,
                )
                .heightIn(min = HeyTokens.Size.touchCarPrimary)
                .padding(horizontal = HeyTokens.Space.s24, vertical = HeyTokens.Space.s16),
        ) {
            val boxShape = RoundedCornerShape(HeyTokens.Radius.sm)
            Box(
                Modifier
                    .size(HeyTokens.Size.touchCar / 2 + HeyTokens.Space.s8)
                    .clip(boxShape)
                    .background(if (checked) colors.primary else Color.Transparent, boxShape)
                    .border(HeyTokens.Border.strong, if (checked) colors.primary else colors.fgSubtle, boxShape),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) InkIcon(HeyIcons.Check, null, colors.fgOnPrimary)
            }
            HeyText(label, Modifier.grow(), tone = if (enabled) HeyTone.Default else HeyTone.Disabled, maxLines = 3)
        }
        if (error != null) HeyText(error, variant = HeyTextVariant.Caption, tone = HeyTone.Danger, maxLines = 2)
    }
}

/** Flat page-colour background for C5 and any screen that must not draw decoration. */
@Composable
fun PageBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.background(HeyTheme.colors.bgPage)) { content() }
}
