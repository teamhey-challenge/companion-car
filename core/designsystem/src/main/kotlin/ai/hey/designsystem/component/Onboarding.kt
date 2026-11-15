package ai.hey.designsystem.component

import ai.hey.designsystem.R
import ai.hey.designsystem.layout.HStack
import ai.hey.designsystem.layout.HeyStackScope
import ai.hey.designsystem.layout.HeyAlign
import ai.hey.designsystem.layout.HeyArrange
import ai.hey.designsystem.layout.VStack
import ai.hey.designsystem.theme.HeyGap
import ai.hey.designsystem.theme.HeyTextVariant
import ai.hey.designsystem.token.HeyDarkColors
import ai.hey.designsystem.token.HeyLightColors
import ai.hey.designsystem.token.HeyTokens
import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.Dp
import ai.hey.designsystem.icon.HeyIcons
import kotlinx.coroutines.launch

/*
 * D-48 onboarding scene parts (PLEOS-190), ported from the studio's stage.tsx / Onboarding.tsx / onboarding.css.
 * One living scene (a video over its still), a few overlays and side panels on top. The scene is always the night sea, so
 * every overlay is drawn in the dark palette whatever the car theme is. Parents decide size and place (nothing here has
 * outer margin); the screens in :feature:onboarding only fill the slots.
 */

/**
 * True where animations must show their settled picture (snapshots and previews): fades and drops start finished.
 * The one flag every onboarding screen reads, so a screen snapshot shows polaroids dropped and heyri faded in.
 */
val LocalHeyStill = androidx.compose.runtime.staticCompositionLocalOf { false }
private val SceneInk = HeyDarkColors.bgPage
// The studio's scene palette (onboarding.css): text on the night sea, whatever the car theme is.
private val SceneText = Color(0xFFF6F8FF)
private val SceneMuted = Color(0xFFD4E3EF)
private val ButtonInk = Color(0xFF153B50)
private val ButtonOffFill = Color(0xFF30495C)
private val ButtonOffInk = Color(0xFFACBFCD)
private val GlassFill = Color(0xFF193144).copy(alpha = 0.68f)
private val GlassRim = Color(0xFFD6EBF7).copy(alpha = 0.3f)
private val RowRim = Color(0xFFD6EBF7).copy(alpha = 0.24f)
private val Halo = Color(0xFFFFDE96)

// ---------------------------------------------------------------- scene

/**
 * The bundled shots, each with the still that stays when the video cannot play or motion is reduced (D-48: "영상을 못
 * 불러옴 → 같은 장면의 정지 그림"). Each cut starts on the previous cut's last frame; a one-shot video holds its last frame.
 */
enum class OnbShot(@DrawableRes val still: Int, @RawRes val video: Int?, val loop: Boolean) {
    /** O1 title over the coast road (picture). */
    Title(R.drawable.onb_title_coast, null, false),

    /** O1 the camera goes into the sea and the whale arrives (about 5 s). */
    Intro(R.drawable.onb_title_coast, R.raw.onb_intro, false),

    /** The whale waiting, for the consent, the three questions and "found you". */
    Whale(R.drawable.onb_whale_still, R.raw.onb_whale_loop, true),

    /** O4 the lights gather into a ball, the whale leaves, the ball opens into the golden ring. */
    Match(R.drawable.onb_whale_still, R.raw.onb_match, false),

    /** O4 the golden ring waiting for the long press (picture). */
    Ring(R.drawable.onb_eclipse, null, false),

    /** O4 flash, then through the ring into the sea. */
    Dive(R.drawable.onb_eclipse, R.raw.onb_dive, false),

    /** O5 to O8: heyri's sea. */
    Sea(R.drawable.onb_bg_underwater, R.raw.onb_sea_loop, true),
}

/**
 * Silent video that covers its box (centre crop). TextureView + MediaPlayer, because VideoView can only letterbox.
 * [onShown] is the first rendered frame, [onEnded] the end of a one-shot, [onFailed] a file that cannot be played.
 */
private class CoverVideoView(context: Context, @RawRes private val res: Int, private val loop: Boolean) : FrameLayout(context) {
    private val texture = TextureView(context)
    private var player: MediaPlayer? = null
    private var videoW = 0
    private var videoH = 0
    var onShown: () -> Unit = {}
    var onEnded: () -> Unit = {}
    var onFailed: () -> Unit = {}

    init {
        addView(texture, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        texture.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) = start(Surface(surface))
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                release()
                return true
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
        }
    }

    private fun start(surface: Surface) {
        // One decoder at a time: a cut that starts while the old one fades out takes the codec over (the old view keeps its last frame).
        active?.takeIf { it !== this }?.release()
        release()
        active = this
        runCatching {
            val mp = MediaPlayer()
            player = mp
            this.surface = surface
            context.resources.openRawResourceFd(res).use { mp.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            mp.setSurface(surface)
            mp.isLooping = loop
            mp.setVolume(0f, 0f)
            mp.setOnVideoSizeChangedListener { _, w, h ->
                if (player !== mp) return@setOnVideoSizeChangedListener
                videoW = w
                videoH = h
                requestLayout()
            }
            mp.setOnInfoListener { _, what, _ ->
                // The still stays until the first frame is really on screen.
                if (player === mp && what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) onShown()
                false
            }
            mp.setOnCompletionListener { if (player === mp && !loop) onEnded() }
            mp.setOnErrorListener { _, _, _ ->
                if (player === mp) onFailed()
                true
            }
            mp.setOnPreparedListener { if (player === mp) it.start() }
            mp.prepareAsync()
        }.onFailure { onFailed() }
    }

    fun release() {
        player?.let { runCatching { it.release() } }
        player = null
        surface?.let { runCatching { it.release() } }
        surface = null
        if (active === this) active = null
    }

    private var surface: Surface? = null

    private companion object {
        /** The view whose MediaPlayer is alive; at most one exists. */
        var active: CoverVideoView? = null
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(getDefaultSize(0, widthMeasureSpec), getDefaultSize(0, heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l
        val h = b - t
        if (videoW <= 0 || videoH <= 0 || w <= 0 || h <= 0) {
            texture.layout(0, 0, w, h)
            return
        }
        val scale = maxOf(w.toFloat() / videoW, h.toFloat() / videoH)
        val cw = (videoW * scale).toInt()
        val ch = (videoH * scale).toInt()
        val x = (w - cw) / 2
        val y = (h - ch) / 2
        texture.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(cw, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(ch, android.view.View.MeasureSpec.EXACTLY),
        )
        texture.layout(x, y, x + cw, y + ch)
    }
}

/** One shot: the still, and over it the video once it has a frame. A failed video leaves the still. */
@Composable
private fun SceneLayer(shot: OnbShot, fadeIn: Boolean, onEnded: () -> Unit) {
    val preview = LocalInspectionMode.current
    val appear = remember { Animatable(if (fadeIn && !preview) 0f else 1f) }
    LaunchedEffect(Unit) { if (appear.value < 1f) appear.animateTo(1f, tween(400)) }
    Box(Modifier.fillMaxSize().alpha(appear.value)) {
        Image(painterResource(shot.still), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        val video = shot.video
        val lowRam = LocalContext.current.let { (it.getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager)?.isLowRamDevice == true }
        if (video != null && !preview && !lowRam && !rememberReducedMotion()) {
            var shown by remember { mutableStateOf(false) }
            var failed by remember { mutableStateOf(false) }
            val videoAlpha by animateFloatAsState(if (shown) 1f else 0f, tween(400), label = "videoFade")
            if (!failed) {
                AndroidView(
                    factory = { ctx ->
                        CoverVideoView(ctx, video, shot.loop).apply {
                            onShown = { shown = true }
                            this.onEnded = onEnded
                            onFailed = { failed = true; onEnded() }
                        }
                    },
                    modifier = Modifier.fillMaxSize().alpha(videoAlpha),
                    onRelease = { it.release() },
                )
            }
        } else if (video != null && !shot.loop) {
            // No video (reduced motion, preview): the still stands for the whole shot, so the flow must not wait for the end.
            LaunchedEffect(shot) { if (!preview) onEnded() }
        }
    }
}

/** Where an overlay sits in the scene (the parent decides; parts carry no position). */
enum class OnbAlign(internal val alignment: Alignment) {
    TopStart(Alignment.TopStart), TopCenter(Alignment.TopCenter), TopEnd(Alignment.TopEnd),
    CenterStart(Alignment.CenterStart), Center(Alignment.Center), CenterEnd(Alignment.CenterEnd),
    BottomCenter(Alignment.BottomCenter), BottomStart(Alignment.BottomStart), BottomEnd(Alignment.BottomEnd),
}

/** What the scene gives its overlays: only a place to sit. */
interface OnbSceneScope {
    fun Modifier.place(at: OnbAlign): Modifier
}

private class BoxSceneScope(private val scope: BoxScope) : OnbSceneScope {
    override fun Modifier.place(at: OnbAlign): Modifier = with(scope) { this@place.align(at.alignment) }
}

/**
 * The one living scene of the onboarding: [shot] on the whole box, with [content] over it. When the shot changes, the new
 * one fades in over the old one (no black gap). [onShotEnded] fires when a one-shot video ends, fails, or is replaced by
 * its still (reduced motion), so the flow never waits on a stalled video.
 */
@Composable
fun OnbScene(
    shot: OnbShot,
    modifier: Modifier = Modifier,
    onShotEnded: () -> Unit = {},
    content: @Composable OnbSceneScope.() -> Unit,
) {
    var previous by remember { mutableStateOf<OnbShot?>(null) }
    var current by remember { mutableStateOf(shot) }
    if (current != shot) {
        previous = current
        current = shot
    }
    LaunchedEffect(current) {
        delay(500)
        previous = null
    }
    Box(modifier.background(SceneInk)) {
        previous?.let { SceneLayer(it, fadeIn = false, onEnded = {}) }
        androidx.compose.runtime.key(current) { SceneLayer(current, fadeIn = previous != null, onEnded = onShotEnded) }
        BoxSceneScope(this).content()
    }
}

// ---------------------------------------------------------------- lights

private class Light(val phase: Float, val ring: Float, val speed: Float, val hue: Float, val size: Float)

private val LightSet: List<Light> = List(48) { i ->
    Light(
        phase = i / 48f * (2f * PI.toFloat()),
        ring = 0.8f + ((i * 37) % 30) / 100f,
        speed = 0.0018f + ((i * 13) % 10) / 6000f,
        hue = floatArrayOf(330f, 45f, 190f, 275f, 15f, 160f)[i % 6],
        size = 14f + (i * 7) % 16,
    )
}

/** The most lights there are: [OnbLights] draws the first `count` of them. */
const val ONB_LIGHTS_MAX = 48

/**
 * The bokeh lights circling the whale (O3): each one is a possible heyri, fewer as the whale learns more. Frozen under
 * reduced motion and in previews.
 */
@Composable
fun OnbLights(count: Int, modifier: Modifier = Modifier) {
    val lit by animateFloatAsState(count.coerceIn(0, ONB_LIGHTS_MAX).toFloat(), tween(800), label = "lit")
    val moving = !LocalInspectionMode.current && !rememberReducedMotion() && count > 0
    val clock: Float = if (moving) {
        val t by rememberInfiniteTransition(label = "lights").animateFloat(
            0f, 1_000_000f, infiniteRepeatable(tween(1_000_000, easing = LinearEasing), RepeatMode.Restart), label = "lightsClock",
        )
        t
    } else {
        0f
    }
    Canvas(modifier.fillMaxSize()) {
        val cx = size.width * 0.53f
        val cy = size.height * 0.44f
        val rx = size.width * 0.35f
        val ry = size.height * 0.17f
        LightSet.forEachIndexed { i, p ->
            val alpha = (lit - i).coerceIn(0f, 1f) * 0.75f
            if (alpha < 0.01f) return@forEachIndexed
            val a = p.phase + p.speed * clock / 16f
            val x = cx + cos(a) * rx * p.ring
            val y = cy + sin(a) * ry * p.ring
            val k = 0.7f + 0.55f * (sin(a) + 1f) / 2f
            val rad = p.size.dp.toPx() * k * 2.2f
            drawCircle(
                Brush.radialGradient(
                    0f to Color.hsv(p.hue, 0.12f, 1f).copy(alpha = alpha),
                    0.35f to Color.hsv(p.hue, 0.4f, 1f).copy(alpha = alpha * 0.5f),
                    1f to Color.hsv(p.hue, 0.55f, 1f).copy(alpha = 0f),
                    center = Offset(x, y), radius = rad,
                ),
                rad, Offset(x, y),
            )
        }
    }
}

/** "헤이리 찾는 중" with a thin light-orbit progress bar (O3). [progress] is 0..1 of the three questions. */
@Composable
fun OnbProgress(label: String, progress: Float, modifier: Modifier = Modifier) {
    // The studio's eyebrow sits 44 under the top edge: the parent gives 32, the label's own box 12 more.
    VStack(HeyGap.G8, modifier = modifier.padding(top = 12.dp), align = HeyAlign.Center) {
        InkText(label, SceneText, variant = HeyTextVariant.Label, weight = FontWeight.Bold, trackingEm = 0.28f, metrics = HeyTextMetrics(17.sp, 24.sp), maxLines = 1)
        val shape = RoundedCornerShape(HeyTokens.Radius.full)
        Box(Modifier.width(180.dp).heightIn(min = 3.dp).clip(shape).background(Color.White.copy(alpha = 0.22f))) {
            val fill by animateFloatAsState(progress.coerceIn(0f, 1f), tween(800), label = "progress")
            Box(
                Modifier.fillMaxWidth(fill).heightIn(min = 3.dp)
                    .background(Brush.horizontalGradient(listOf(Color(0xFFFFB3C7), Color(0xFFFFE3A0), Color(0xFF9FE6FF)))),
            )
        }
    }
}

// ---------------------------------------------------------------- caption layer

/**
 * The subtitle layer at the bottom of the scene: a dark fade, what the driver said (quoted), the listening pill while the
 * microphone is open, and the line being spoken (39 sp). [narrow] keeps the text in the left half while a side panel is
 * open on the right. Shown while parked only (no captions on the road); the owner composes nothing while driving.
 */
@Composable
fun OnbCaption(
    text: String,
    modifier: Modifier = Modifier,
    heard: String? = null,
    listening: Boolean = false,
    listeningLabel: String = "",
    skipLabel: String = "",
    onSkip: (() -> Unit)? = null,
    narrow: Boolean = false,
    compact: Boolean = false,
) {
    // The studio centres the subtitle in whatever width it has (the left half while a panel is open).
    val align = HeyAlign.Center
    val textAlign = HeyTextAlign.Center
    Box(
        modifier.fillMaxWidth().background(
            Brush.verticalGradient(listOf(Color.Transparent, SceneInk.copy(alpha = 0.36f), SceneInk.copy(alpha = 0.74f), SceneInk.copy(alpha = 0.92f))),
        ).padding(start = if (compact) 32.dp else 56.dp, end = if (compact) 32.dp else 56.dp, top = 80.dp, bottom = if (compact) 32.dp else 46.dp),
        contentAlignment = if (narrow) Alignment.BottomStart else Alignment.BottomCenter,
    ) {
        VStack(HeyGap.G16, modifier = if (narrow) Modifier.fillMaxWidth(0.5f) else Modifier.fillMaxWidth(), align = align) {
            if (!heard.isNullOrBlank()) {
                InkText("“$heard”", HeyDarkColors.fgMuted, maxLines = 2, align = textAlign, variant = HeyTextVariant.Heading, weight = FontWeight.Normal)
            }
            if (listening) OnbListenPill(listeningLabel, skipLabel, onSkip)
            if (text.isNotBlank()) {
                InkText(
                    text, SceneText, Modifier.fillMaxWidth(), maxLines = 4, align = textAlign,
                    weight = FontWeight.Normal, metrics = if (compact) HeyTextMetrics(28.sp, 40.sp) else HeyTextMetrics(39.sp, 56.sp),
                )
            }
        }
    }
}

/** "듣고 있어요" with level bars and an optional "넘어갈래" button. */
@Composable
fun OnbListenPill(label: String, skipLabel: String, onSkip: (() -> Unit)?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(HeyTokens.Radius.full)
    val still = LocalInspectionMode.current || rememberReducedMotion()
    val t: Float = if (still) {
        0.6f
    } else {
        val v by rememberInfiniteTransition(label = "bars").animateFloat(0f, 1f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "barsT")
        v
    }
    HStack(
        HeyGap.G24,
        modifier = modifier.clip(shape).background(HeyDarkColors.surfaceContainer.copy(alpha = 0.92f), shape)
            .border(HeyTokens.Border.hairline, HeyDarkColors.borderStrong, shape)
            .padding(start = 28.dp, top = 12.dp, bottom = 12.dp, end = if (onSkip != null) 12.dp else 28.dp),
    ) {
        HStack(HeyGap.G4, modifier = Modifier.heightIn(min = 26.dp)) {
            repeat(5) { i ->
                val k = 0.3f + 0.7f * (if (i % 2 == 0) t else 1f - t)
                Box(Modifier.size(4.dp, 26.dp * k).clip(RoundedCornerShape(2.dp)).background(HeyDarkColors.accent))
            }
        }
        InkText(label, SceneText, maxLines = 1, metrics = HeyTextMetrics(28.sp, 33.04.sp), weight = FontWeight.Normal)
        if (onSkip != null) GlassButton(skipLabel, onSkip)
    }
}

// ---------------------------------------------------------------- titles and banner

/** O1: the "Hey" title (150 sp) with its line under it. */
@Composable
fun OnbTitle(title: String, tagline: String, modifier: Modifier = Modifier, compact: Boolean = false) {
    VStack(HeyGap.G24, modifier = modifier.fillMaxWidth(), align = HeyAlign.Center) {
        InkText(
            title, HeyLightColors.accentContainer, maxLines = 1, align = HeyTextAlign.Center,
            weight = FontWeight.ExtraBold, metrics = if (compact) HeyTextMetrics(96.sp, 96.sp) else HeyTextMetrics(150.sp, 150.sp), trackingEm = -0.04f,
        )
        InkText(
            tagline, SceneText, Modifier.padding(top = 3.dp), maxLines = 2, align = HeyTextAlign.Center,
            metrics = if (compact) HeyTextMetrics(24.sp, 24.sp) else HeyTextMetrics(30.sp, 24.sp), weight = FontWeight.Normal,
        )
    }
}

/** O1 "다음": the white round button with a chevron (104 dp, 72 dp over the bottom edge); [label] is only its description. */
@Composable
fun OnbNextButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.padding(bottom = 72.dp)) {
        Box(
            Modifier.size(104.dp).clip(RoundedCornerShape(HeyTokens.Radius.full)).background(Color.White)
                .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) { InkIcon(HeyIcons.ChevronRight, null, HeyLightColors.fgOnAccent, Modifier.size(44.dp)) }
    }
}

/** O5: "너의 단짝 / 헤이리" (the studio's INTRODUCING Roman): a small eyebrow over a large name. */
@Composable
fun OnbMeetTitle(name: String, modifier: Modifier = Modifier, compact: Boolean = false) {
    InkText(
        name, SceneText, modifier, maxLines = 1, align = HeyTextAlign.Center,
        metrics = if (compact) HeyTextMetrics(56.sp, 72.sp) else HeyTextMetrics(84.sp, 109.sp), weight = FontWeight.Medium, trackingEm = -0.02f,
    )
}

/** "누르는 건 정차하면 이어서 할게": the one line shown while a press step waits for the car to stop. */
@Composable
fun OnbParkedBanner(text: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(HeyTokens.Radius.full)
    InkText(
        text, SceneText,
        modifier.clip(shape).background(SceneInk.copy(alpha = 0.7f), shape).padding(horizontal = 24.dp, vertical = 12.dp),
        maxLines = 2, metrics = HeyTextMetrics(22.sp, 25.96.sp), align = HeyTextAlign.Center, weight = FontWeight.Normal,
    )
}

// ---------------------------------------------------------------- hold ring

/**
 * O4 "길게 눌러 헤이리 만나기": the press is on the golden ring itself (a plain tap does nothing) and its halo swells with
 * the hold. The parent sizes the ring (square). [onComplete] fires once, when the hold is long enough; the accessibility
 * action completes it directly. [previewProgress] freezes the halo for snapshots.
 */
@Composable
fun OnbHoldRing(
    label: String,
    holdMillis: Long,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    previewProgress: Float? = null,
) {
    val scope = rememberCoroutineScope()
    val held = remember { Animatable(0f) }
    val breathe = if (LocalInspectionMode.current || rememberReducedMotion()) {
        1f
    } else {
        val v by rememberInfiniteTransition(label = "halo").animateFloat(0.75f, 1f, infiniteRepeatable(tween(1300), RepeatMode.Reverse), label = "haloA")
        v
    }
    val p = previewProgress ?: held.value
    Box(
        modifier
            .drawBehind {
                val c = center
                val r = size.minDimension / 2f * (1f + p * 0.3f)
                val outer = r + (40.dp.toPx() + p * 180.dp.toPx())
                val a = (0.28f + p * 0.6f) * breathe
                drawCircle(
                    Brush.radialGradient(0.0f to Color.Transparent, (r * 0.92f) / outer to Halo.copy(alpha = a), 1f to Color.Transparent, center = c, radius = outer),
                    outer, c,
                )
                val inner = (0.12f + p * 0.55f) * breathe
                drawCircle(
                    Brush.radialGradient(0.55f to Color.Transparent, 1f to Halo.copy(alpha = inner), center = c, radius = r),
                    r, c,
                )
            }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = label
                if (enabled) onClick(label) { onComplete(); true }
            }
            .pointerInput(enabled, holdMillis) {
                if (!enabled) return@pointerInput
                detectTapGestures(onPress = {
                    val job = scope.launch {
                        held.animateTo(1f, tween(holdMillis.toInt(), easing = LinearEasing))
                        onComplete()
                    }
                    tryAwaitRelease()
                    if (held.value < 1f) {
                        job.cancel()
                        scope.launch { held.snapTo(0f) }
                    }
                })
            },
    )
}

// ---------------------------------------------------------------- polaroids

/** One memory polaroid: [slot] 0..2 is the fixed picture (name, trips, topics), [caption] the answer in the driver's words. */
class OnbPolaroid(val slot: Int, val caption: String)

private val PolaroidPictures = intArrayOf(R.drawable.onb_polaroid_1, R.drawable.onb_polaroid_2, R.drawable.onb_polaroid_3)

/**
 * O6 "너를 이만큼 알아": the answers dropped as polaroids over the dimmed scene, each with its answer as the caption. A skipped
 * question has no polaroid (the caller leaves it out). [stored] flies them away. Fills its parent.
 */
@Composable
fun OnbPolaroids(
    eyebrow: String,
    items: List<OnbPolaroid>,
    modifier: Modifier = Modifier,
    stored: Boolean = false,
    /** Snapshots: draw the cards already dropped. */
    settled: Boolean = false,
) {
    val still = settled || LocalHeyStill.current || LocalInspectionMode.current
    val drop = remember { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(Unit) { if (drop.value < 1f) drop.animateTo(1f, tween(1200)) }
    val away by animateFloatAsState(if (stored) 1f else 0f, tween(900), label = "stored")
    BoxWithConstraints(modifier.fillMaxSize().background(SceneInk.copy(alpha = 0.5f * (1f - away)))) {
        val card = (maxWidth / 3.3f).coerceAtMost(300.dp)
        val step = card * 1.1f
        InkText(
            eyebrow, SceneText, Modifier.align(Alignment.TopCenter).padding(top = 60.dp).alpha(1f - away),
            maxLines = 1, metrics = HeyTextMetrics(17.sp, 24.sp), weight = FontWeight.Bold, trackingEm = 0.02f,
        )
        items.forEachIndexed { i, item ->
            val order = i - (items.size - 1) / 2f
            val enter = ((drop.value * 3f - i * 0.6f) / 1.2f).coerceIn(0f, 1f)
            Box(
                Modifier.align(Alignment.Center)
                    .offset(x = step * order + 600.dp * (away * (1f - 0.1f * i)), y = 220.dp * (-(1f - enter)) - 80.dp * away)
                    .graphicsLayer {
                        rotationZ = order * 5f - 2f + (1f - enter) * -12f
                        alpha = enter * (1f - away)
                        val s = 1f - away * 0.75f
                        scaleX = s
                        scaleY = s
                    }
                    .shadow(24.dp, RoundedCornerShape(6.dp))
                    .width(card)
                    .background(Color(0xFFFBFAF6), RoundedCornerShape(6.dp))
                    .padding(start = 14.dp, end = 14.dp, top = 14.dp),
            ) {
                VStack(HeyGap.G8, align = HeyAlign.Center) {
                    Image(
                        painterResource(PolaroidPictures[item.slot.coerceIn(0, 2)]), null,
                        Modifier.fillMaxWidth().size(card - 28.dp).clip(RoundedCornerShape(2.dp)), contentScale = ContentScale.Crop,
                    )
                    InkText(
                        item.caption, Color(0xFF2A2A2A), Modifier.padding(bottom = 16.dp), maxLines = 2,
                        align = HeyTextAlign.Center, metrics = if (card < 220.dp) HeyTextMetrics(20.sp, 24.sp) else HeyTextMetrics(26.sp, 24.sp), weight = FontWeight.Normal,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- side panel

/** Which edge a [OnbSidePanel] is anchored to. */
enum class OnbSide { Start, End }

/**
 * A panel that slides over half of the scene (consent on the left, dress-up and the phone QR on the right): the scene
 * stays visible, with a dark fade behind the text that melts into it. [compact] (1/2 and 1/3 windows) makes it full width.
 * Fills its parent; the content is a column centred in the panel's height (the studio's `justify-content: center`) that
 * scrolls when it is taller than the panel.
 */
@Composable
fun OnbSidePanel(
    side: OnbSide,
    compact: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable HeyStackScope.() -> Unit,
) {
    val start = side == OnbSide.Start
    val dim = listOf(SceneInk.copy(alpha = 0.96f), SceneInk.copy(alpha = 0.92f), SceneInk.copy(alpha = 0.68f), Color.Transparent)
    BoxWithConstraints(modifier.fillMaxSize()) {
        val panel = if (compact) maxWidth else maxWidth / 2
        val pad = if (compact) 32.dp else 56.dp
        Box(
            Modifier.align(if (start) Alignment.CenterStart else Alignment.CenterEnd)
                .width(panel).fillMaxHeight()
                .drawBehind {
                    val reach = if (compact) 0f else 360.dp.toPx()
                    val w = size.width + reach
                    val x0 = if (start) 0f else -reach
                    val fade = Brush.horizontalGradient(
                        colors = if (start) dim else dim.asReversed(),
                        startX = x0, endX = x0 + w,
                    ).takeIf { !compact }
                    if (fade != null) drawRect(fade, Offset(x0, 0f), androidx.compose.ui.geometry.Size(w, size.height))
                    else drawRect(SceneInk.copy(alpha = 0.88f))
                },
        ) {
            // The scene is always the night sea: whatever sits on the panel is drawn in the dark palette.
            ai.hey.designsystem.theme.HeyTheme(dark = true) {
                Box(
                    Modifier.fillMaxSize().padding(horizontal = pad, vertical = if (compact) 32.dp else 64.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Top) {
                        OnbColumnScope(this).content()
                    }
                }
            }
        }
    }
}

private class OnbColumnScope(private val scope: ColumnScope) : HeyStackScope {
    override fun Modifier.grow(weight: Float): Modifier = with(scope) { this@grow.weight(weight) }
}

/** The space the studio puts between two blocks of a panel (its margins are not on the 4-step scale). */
enum class OnbSpace(internal val value: Dp) { S8(8.dp), S16(16.dp), S18(18.dp), S20(20.dp), S24(24.dp), S28(28.dp), S32(32.dp), S40(40.dp) }

@Composable
fun OnbGap(space: OnbSpace) {
    Spacer(Modifier.height(space.value))
}

/** One line of a panel's promise list: a dot, then 26 sp muted text. */
@Composable
fun OnbBullet(text: String, modifier: Modifier = Modifier) {
    HStack(HeyGap.G4, modifier = modifier.fillMaxWidth(), align = HeyAlign.Start) {
        Box(Modifier.width(24.dp).height(41.6.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(6.dp).clip(RoundedCornerShape(HeyTokens.Radius.full)).background(SceneMuted))
        }
        InkText(text, SceneMuted, Modifier.grow(), metrics = HeyTextMetrics(26.sp, 41.6.sp), weight = FontWeight.Normal)
    }
}

/** A consent row on the scene: 44 dp box, 28 sp label, a hairline above ([last] also below). [error] sits under the row. */
@Composable
fun OnbCheckRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    last: Boolean = false,
    error: String? = null,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(modifier.fillMaxWidth()) {
        HStack(
            HeyGap.G24,
            modifier = Modifier.fillMaxWidth().heightIn(min = 112.dp)
                .drawBehind {
                    val w = 1.dp.toPx()
                    drawLine(RowRim, Offset(0f, 0f), Offset(size.width, 0f), w)
                    if (last) drawLine(RowRim, Offset(0f, size.height), Offset(size.width, size.height), w)
                }
                .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange)
                .padding(horizontal = 8.dp, vertical = 20.dp),
        ) {
            Box(
                Modifier.size(44.dp).clip(shape)
                    .background(if (checked) HeyLightColors.accent else Color(0xFF0D2335).copy(alpha = 0.65f), shape)
                    .border(HeyTokens.Border.strong, if (checked) HeyLightColors.accent else Color(0xFFA5BDCE), shape),
                contentAlignment = Alignment.Center,
            ) { if (checked) InkIcon(HeyIcons.Check, null, ButtonInk) }
            InkText(label, if (enabled) SceneText else SceneMuted, Modifier.grow(), maxLines = 3, metrics = HeyTextMetrics(28.sp, 42.sp), weight = FontWeight.Normal)
        }
        if (error != null) InkText(error, ai.hey.designsystem.theme.HeyTheme.colors.danger, Modifier.padding(top = 8.dp), maxLines = 2, metrics = HeyTextMetrics(22.sp, 32.sp), weight = FontWeight.Normal)
    }
}

/** The underlined link under the consent rows (24 sp, 56 dp tall). */
@Composable
fun OnbLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.CenterStart) {
        InkText(
            text, SceneMuted,
            Modifier.drawBehind { drawLine(SceneMuted, Offset(0f, size.height + 2.dp.toPx()), Offset(size.width, size.height + 2.dp.toPx()), 1.5.dp.toPx()) },
            maxLines = 1, metrics = HeyTextMetrics(24.sp, 24.sp), weight = FontWeight.Normal,
        )
    }
}

/** The panel's one main action: a full-width 104 dp accent pill, 32 sp. [dimmed] draws the blocked look and still takes the press. */
@Composable
fun OnbPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, dimmed: Boolean = false) {
    val shape = RoundedCornerShape(HeyTokens.Radius.full)
    val off = dimmed || !enabled
    Box(
        modifier.fillMaxWidth().heightIn(min = 104.dp).clip(shape).background(if (off) ButtonOffFill else HeyLightColors.accent, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        InkText(text, if (off) ButtonOffInk else ButtonInk, maxLines = 1, align = HeyTextAlign.Center, metrics = HeyTextMetrics(32.sp, 24.sp), weight = FontWeight.Medium)
    }
}

/** The quiet twin of [OnbPrimaryButton] (glass fill and rim, 28 sp). */
@Composable
fun OnbGhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val shape = RoundedCornerShape(HeyTokens.Radius.full)
    Box(
        modifier.heightIn(min = 104.dp).clip(shape).background(GlassFill, shape).border(HeyTokens.Border.strong, GlassRim, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        InkText(text, SceneText, maxLines = 1, align = HeyTextAlign.Center, metrics = HeyTextMetrics(28.sp, 24.sp), weight = FontWeight.Normal)
    }
}


// ---------------------------------------------------------------- dress-up sheet

/** One dress-up choice (D-41): a colour dot and its name. */
class OnbDressChoice(val label: String, val swatch: Color, val selected: Boolean, val onClick: () -> Unit)

/** The colour dots of the five choices as they look on the character (aqua, peach, beanie, bucket, scarf). */
object OnbSwatch {
    val Aqua = Color(0xFFBFEFF0)
    val Peach = Color(0xFFFFC9A8)
    val Beanie = Color(0xFF8FA8E8)
    val Bucket = Color(0xFFE3B45C)
    val Scarf = Color(0xFFD9534F)
}

/** The dress-up choices as a two-column grid of 112 dp tiles; a chosen tile reads by its fill and rim, not by colour alone. */
@Composable
fun OnbDressGrid(choices: List<OnbDressChoice>, modifier: Modifier = Modifier) {
    VStack(HeyGap.G16, modifier = modifier) {
        choices.chunked(2).forEach { row ->
            HStack(HeyGap.G16, modifier = Modifier.fillMaxWidth()) {
                row.forEach { c -> OnbDressTile(c, Modifier.grow()) }
                if (row.size == 1) Box(Modifier.grow())
            }
        }
    }
}

@Composable
private fun OnbDressTile(c: OnbDressChoice, modifier: Modifier) {
    val shape = RoundedCornerShape(24.dp)
    val ink = if (c.selected) HeyLightColors.fgOnAccent else SceneText
    HStack(
        HeyGap.G16,
        modifier = modifier.heightIn(min = 112.dp).clip(shape)
            .background(if (c.selected) HeyLightColors.accentContainer else GlassFill, shape)
            .border(HeyTokens.Border.strong, if (c.selected) HeyLightColors.accent else GlassRim, shape)
            .toggleable(value = c.selected, role = Role.Checkbox, onValueChange = { c.onClick() })
            .semantics { selected = c.selected }
            .padding(20.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(HeyTokens.Radius.full)).background(c.swatch).border(HeyTokens.Border.strong, Color.Black.copy(alpha = 0.12f), RoundedCornerShape(HeyTokens.Radius.full)))
        InkText(c.label, ink, Modifier.grow(), maxLines = 2, metrics = HeyTextMetrics(28.sp, 24.sp), weight = FontWeight.Normal)
    }
}

/** Panel title in the panel's ink (60 sp at full size, 44 in compact windows). */
@Composable
fun OnbPanelTitle(text: String, modifier: Modifier = Modifier, compact: Boolean = false) {
    InkText(
        text, SceneText, modifier, maxLines = 3, weight = FontWeight(450), trackingEm = -0.055f,
        metrics = if (compact) HeyTextMetrics(44.sp, 58.sp) else HeyTextMetrics(60.sp, 78.sp),
    )
}

/** Panel body line in the panel's muted ink. */
@Composable
fun OnbPanelText(text: String, modifier: Modifier = Modifier, large: Boolean = false) {
    InkText(text, SceneMuted, modifier, metrics = if (large) HeyTextMetrics(28.sp, 42.sp) else HeyTextMetrics(26.sp, 41.6.sp), weight = FontWeight.Normal)
}
