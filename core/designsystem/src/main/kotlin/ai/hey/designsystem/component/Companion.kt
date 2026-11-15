package ai.hey.designsystem.component

import ai.hey.designsystem.R
import kotlinx.coroutines.withTimeoutOrNull
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.layout.layout
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RectangleShape
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
    // Mockup C3: one flat sky colour (bgPage), no vertical gradient.
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
    Box(modifier.background(colors.bgPage)) {
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

    /** C5 centre: studio `.character-stage` 480×480 (spec car-drive@1251x900 canvas); the rig is `Fit.Contain` + centre inside it. */
    Driving(480.dp, 480.dp),

    /** C3/C4 in a 1/2 or 1/3 width multi-window (stacked layout). */
    Compact(320.dp, 340.dp),

    /** Small stage (phone-style cards). */
    Small(150.dp, 160.dp),
}

/**
 * What the character wears (D-41), in the `hey_companion.riv` Companion view-model values (rive-rig.md): [bodyColor] 0 aqua,
 * 1 peach, null = follow the theme (light 0, dark 1); [hat] 0 none, 1 beanie (the default look), 2 bucket; [neck] 0 none, 1 scarf.
 */
data class CharacterLook(val bodyColor: Int? = null, val hat: Int = 1, val neck: Int = 0) {
    fun body(dark: Boolean): Int = (bodyColor ?: if (dark) 1 else 0).coerceIn(0, 1)
}

private val Stills: Array<IntArray> = arrayOf(
    intArrayOf(R.drawable.hey_character_still_b0_h0_n0, R.drawable.hey_character_still_b0_h0_n1),
    intArrayOf(R.drawable.hey_character_still_b0_h1_n0, R.drawable.hey_character_still_b0_h1_n1),
    intArrayOf(R.drawable.hey_character_still_b0_h2_n0, R.drawable.hey_character_still_b0_h2_n1),
    intArrayOf(R.drawable.hey_character_still_b1_h0_n0, R.drawable.hey_character_still_b1_h0_n1),
    intArrayOf(R.drawable.hey_character_still_b1_h1_n0, R.drawable.hey_character_still_b1_h1_n1),
    intArrayOf(R.drawable.hey_character_still_b1_h2_n0, R.drawable.hey_character_still_b1_h2_n1),
)

/**
 * The still for [look]: one prepared WebP per combination (2 bodies x 3 hats x 2 necks), frames taken from the Rive file itself
 * in its parked pose, so C5 stays a plain image (D-11) and matches what C3 shows. Default look: aqua in the sea theme, peach in
 * the deep-sea theme (design "헤이리 테마별 기본 색상").
 */
@Composable
fun characterStill(look: CharacterLook = CharacterLook()): Painter {
    val row = look.body(HeyTheme.dark) * 3 + look.hat.coerceIn(0, 2)
    return painterResource(Stills[row][look.neck.coerceIn(0, 1)])
}

/**
 * Hosts the character. With no [live] view it draws [still]; a Rive view (or any composable) passed as [live] replaces the
 * still. The slot is always [size] big so the layout never shifts when the live view loads or fails back to the still.
 */
@Composable
fun CharacterSlot(
    size: CharacterSize,
    modifier: Modifier = Modifier,
    look: CharacterLook = CharacterLook(),
    still: Painter = characterStill(look),
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

/**
 * A clearly named, EMPTY place for art that has not arrived yet (D-49: whale picture, backgrounds, new outfits come from the
 * planner later). It draws only a sunken outline and the [name] of the asset, never a stand-in picture, so a screen can swap it
 * for the real art without touching its logic. Same box as [CharacterSlot] of [size].
 */
@Composable
fun AssetSlot(name: String, size: CharacterSize, modifier: Modifier = Modifier, contentDescription: String? = null) {
    val colors = HeyTheme.colors
    val shape = RoundedCornerShape(HeyTokens.Radius.lg)
    Box(
        modifier
            .size(size.width, size.height)
            .clip(shape)
            .background(colors.bgSunken, shape)
            .border(HeyTokens.Border.hairline, colors.borderDefault, shape)
            .let { if (contentDescription != null) it.semantics { this.contentDescription = contentDescription } else it },
        contentAlignment = Alignment.Center,
    ) {
        HeyText(name, tone = HeyTone.Muted, maxLines = 2, align = HeyTextAlign.Center)
    }
}

/** Fades [content] in over [millis] when it first appears (heyri showing up after the long press); instant under reduced motion. */
@Composable
fun HeyFadeIn(modifier: Modifier = Modifier, millis: Int = 900, content: @Composable () -> Unit) {
    val reduced = rememberReducedMotion()
    // Previews and snapshots draw the settled picture, not the first frame of the fade.
    val settled = LocalHeyStill.current || androidx.compose.ui.platform.LocalInspectionMode.current
    var shown by remember { mutableStateOf(settled) }
    LaunchedEffect(Unit) { shown = true }
    val alpha by animateFloatAsState(
        targetValue = if (shown || reduced) 1f else 0f,
        animationSpec = if (reduced) snap() else tween(millis),
        label = "heyFadeIn",
    )
    Box(modifier.graphicsLayer { this.alpha = alpha }) { content() }
}

/** The still plus an optional caption under it (C1/C2 "당신의 이야기가 머무는 곳"). */
@Composable
fun CharacterStill(
    description: String,
    modifier: Modifier = Modifier,
    size: CharacterSize = CharacterSize.Hero,
    caption: String? = null,
    /** C1/C2 mockups seat the stage 100 dp below the content top (character centre sits at y≈545 of 900). */
    seated: Boolean = false,
) {
    VStack(HeyGap.G16, modifier = if (seated) modifier.padding(top = 100.dp) else modifier, align = HeyAlign.Center) {
        CharacterSlot(size, contentDescription = description)
        if (caption != null) HeyText(caption, tone = HeyTone.Muted, maxLines = 2, align = HeyTextAlign.Center)
    }
}

// ---------------------------------------------------------------- talk buttons

enum class TalkPhase { Idle, Listening, Thinking, Speaking }

private val TalkMinWidth = 320.dp

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

/** One pill used by [TalkButton] and [GlassButton]: 96dp x 320dp minimum, icon + label, tap or press-and-hold. */
@Composable
private fun TalkPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    phase: TalkPhase,
    blocked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    onHoldStart: (() -> Unit)? = null,
    onHoldEnd: (() -> Unit)? = null,
    /** Blocked look but still pressable. */
    dimmed: Boolean = false,
    /** A long press (D-48 3c "길게 눌러 헤이리 만나기"): a plain tap does nothing; the accessibility action runs it at once. */
    onLongPress: (() -> Unit)? = null,
    /** How long the press must be held before [onLongPress] fires (planner: 1.8 s). */
    longPressMillis: Long = 1_800L,
) {
    var pressed by remember { mutableStateOf(false) }
    val ink = glassInk(phase, pressed, blocked || dimmed)
    val shape = RoundedCornerShape(HeyTokens.Radius.full)
    val hold = onHoldStart != null
    val latestClick by rememberUpdatedState(onClick)
    val latestStart by rememberUpdatedState(onHoldStart)
    val latestEnd by rememberUpdatedState(onHoldEnd)
    val latestLong by rememberUpdatedState(onLongPress)
    val press = if (blocked) {
        Modifier
    } else {
        Modifier.pointerInput(hold, onLongPress != null, longPressMillis) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    latestStart?.invoke()
                    try {
                        if (latestLong != null) {
                            // Held for [longPressMillis] (the system long-press time is too short for "길게 눌러 만나기"): fires once.
                            if (withTimeoutOrNull(longPressMillis) { tryAwaitRelease() } == null) {
                                latestLong?.invoke()
                                tryAwaitRelease()
                            }
                        } else {
                            tryAwaitRelease()
                        }
                    } finally {
                        pressed = false
                        latestEnd?.invoke()
                    }
                },
                onTap = if (hold || onLongPress != null) null else { _ -> latestClick() },
            )
        }
    }
    Box(
        modifier
            .heightIn(min = HeyTokens.Size.touchCarPrimary)
            .widthIn(min = TalkMinWidth)
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
            // Mockup `.talk-button`: padding 20 48, 28 label, 32 icon, 20 between them.
            .padding(horizontal = 48.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        HStack(HeyGap.G16) {
            if (icon != null) InkIcon(icon, null, ink.content, Modifier.size(HeyTokens.Size.iconCar * 4 / 3))
            InkText(
                label, ink.content,
                modifier = if (icon != null) Modifier.padding(start = 4.dp) else Modifier,
                maxLines = 2, align = HeyTextAlign.Center,
                weight = androidx.compose.ui.text.font.FontWeight.SemiBold, metrics = HeyTextMetrics(28.sp, 42.sp),
            )
        }
    }
}

/**
 * The primary action. 96dp high, 320dp wide at least. Idle is "말하기" and starts a conversation; in every other phase
 * (Listening, Thinking, Speaking) the same button is "대화 마치기" and ends it (PLEOS-194: never a send button). Only
 * [enabled] = false draws the blocked look and takes no input.
 *
 * Parked: pass [onHoldStart]/[onHoldEnd] for press-and-hold; [onClick] is then only the accessibility
 * action. Driving: leave them null; a tap is [onClick].
 */
@Composable
fun TalkButton(
    phase: TalkPhase,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    driving: Boolean = false,
    enabled: Boolean = true,
    label: String? = null,
    onHoldStart: (() -> Unit)? = null,
    onHoldEnd: (() -> Unit)? = null,
) {
    val idle = phase == TalkPhase.Idle
    val text = label ?: stringResource(if (idle) R.string.hey_talk_idle else R.string.hey_talk_end)
    TalkPill(
        label = text,
        icon = if (idle) HeyIcons.Mic else HeyIcons.Close,
        phase = phase,
        blocked = !enabled,
        onClick = onClick,
        modifier = modifier,
        onHoldStart = if (driving) null else onHoldStart,
        onHoldEnd = if (driving) null else onHoldEnd,
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
    onLongPress: (() -> Unit)? = null,
    longPressMillis: Long = 1_800L,
) {
    TalkPill(
        label = text,
        icon = icon,
        phase = TalkPhase.Idle,
        blocked = !enabled,
        onClick = onClick,
        modifier = modifier,
        dimmed = dimmed,
        onLongPress = onLongPress,
        longPressMillis = longPressMillis,
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
    // Mockup C1/C2/C6/C7: title→subtitle 32 dp and subtitle→content +16 dp; the hero greeting keeps its tight stack.
    VStack(
        if (hero) HeyGap.G8 else HeyGap.G24,
        modifier = if (hero) modifier else modifier.padding(bottom = HeyTokens.Space.s8 - 2.dp),
    ) {
        // Design: headings are regular weight (450) with -0.055em tracking (`.welcome h1`, `.page-heading h1`).
        InkText(
            title, HeyTheme.colors.fgDefault,
            variant = if (hero) HeyTextVariant.Hero else HeyTextVariant.Title,
            maxLines = if (hero) 3 else 2,
            weight = androidx.compose.ui.text.font.FontWeight(450),
            trackingEm = -0.055f,
            // Mockup `.car[data-layout=parked] .welcome h1`: 72px at line-height 1.35 (the token's 84 is too tight).
            lineHeight = if (hero) 97.sp else null,
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
        modifier = modifier.fillMaxWidth().heightIn(min = HeaderTouch).then(if (onBack != null) Modifier.padding(bottom = 32.dp) else Modifier),
    ) {
        // Mockup `.app-header .identity`: 42px/600 title, 24 gap, 12 margin + hairline + 36 padding, 33px mode tag.
        HStack(HeyGap.G24) {
            // Studio 4dc8ad3 `.app-header .back-button`: a bare 28 icon at x56 with the title at x106 (page margin 80 -> icon centre
            // 70, title 26 right of the margin). The 84 touch circle is kept but laid out 2 wide (+24 gap), so the title lands on 106.
            if (onBack != null) Box(
                Modifier.layout { m, c ->
                    val p = m.measure(c)
                    layout(2.dp.roundToPx(), p.height) { p.place(-52.dp.roundToPx(), 0) }
                },
            ) { HeyBackButton(backLabel, onBack, ghost = true) }
            InkText(
                title, HeyTheme.colors.fgDefault, variant = HeyTextVariant.Title, maxLines = 1,
                weight = androidx.compose.ui.text.font.FontWeight.SemiBold, metrics = HeyTextMetrics(42.sp, 63.sp),
            )
            if (stateLabel != null) {
                Box(Modifier.padding(start = 12.dp).size(HeyTokens.Border.hairline, 50.dp).background(HeyTheme.colors.borderDefault))
                InkText(
                    stateLabel, HeyTheme.colors.fgMuted, Modifier.padding(start = 12.dp), maxLines = 1,
                weight = androidx.compose.ui.text.font.FontWeight.Normal, metrics = HeyTextMetrics(33.sp, 50.sp),
                )
            }
        }
        if (trailing != null) HStack(HeyGap.G16, content = trailing)
    }
}

private val RowShape = RoundedCornerShape(HeyTokens.Radius.md)

/** Mockup settings rows: the first two are tinted cards, the last is a plain row with a hairline under it. */
enum class ToggleSurface { Info, Secondary, Plain }

/**
 * Switch row with an optional description (mockup `.toggle-row`: 28sp title, 22sp description, 64x36 track, 24dp padding,
 * 20dp card radius). While [saving] it shows a spinner and takes no input.
 */
@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
    saving: Boolean = false,
    surface: ToggleSurface = ToggleSurface.Info,
) {
    val colors = HeyTheme.colors
    val active = enabled && !saving
    val shape = if (surface == ToggleSurface.Plain) RectangleShape else RoundedCornerShape(HeyTokens.Space.s24 - 4.dp)
    val fill = when (surface) {
        ToggleSurface.Info -> colors.infoContainer
        ToggleSurface.Secondary -> colors.secondaryContainer
        ToggleSurface.Plain -> Color.Transparent
    }
    HStack(
        gap = HeyGap.G24,
        modifier = modifier
            .clip(shape)
            .background(fill, shape)
            .toggleable(
                value = checked,
                enabled = active,
                role = Role.Switch,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onValueChange = onCheckedChange,
            )
            .let {
                if (surface == ToggleSurface.Plain) {
                    it.drawBehind {
                        val h = HeyTokens.Border.hairline.toPx()
                        drawRect(colors.borderDefault, Offset(0f, size.height - h), Size(size.width, h))
                    }
                } else it
            }
            .heightIn(min = HeyTokens.Size.touchCarPrimary)
            .padding(
                start = if (surface == ToggleSurface.Plain) 0.dp else HeyTokens.Space.s24,
                end = if (surface == ToggleSurface.Plain) 0.dp else HeyTokens.Space.s24,
                // Mockup C7: the plain row's text sits 72 dp under the chips; card rows keep 24.
                top = if (surface == ToggleSurface.Plain) HeyTokens.Space.s32 + 4.dp else HeyTokens.Space.s24,
                bottom = HeyTokens.Space.s24,
            ),
    ) {
        VStack(HeyGap.G16, modifier = Modifier.grow()) {
            InkText(
                label, if (enabled) colors.fgDefault else colors.fgDisabled,
                weight = androidx.compose.ui.text.font.FontWeight.Medium, metrics = HeyTextMetrics(28.sp, 42.sp), maxLines = 2,
            )
            if (description != null) InkText(description, colors.fgMuted, metrics = HeyTextMetrics(22.sp, 33.sp), maxLines = 3)
        }
        if (saving) {
            HeySpinner(colors.fgMuted)
        }
        val track = when {
            !enabled -> colors.bgSunken
            checked -> colors.accent
            else -> colors.borderStrong
        }
        Box(
            Modifier
                .size(width = 64.dp, height = 36.dp)
                .clip(RoundedCornerShape(HeyTokens.Radius.full))
                .background(track)
                .padding(5.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(HeyTokens.Radius.full))
                    .background(colors.bgPage),
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
