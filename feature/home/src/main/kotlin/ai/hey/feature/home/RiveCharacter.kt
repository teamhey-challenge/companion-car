package ai.hey.feature.home

import ai.hey.model.HeyLog
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import android.content.Context
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Rive
import app.rive.runtime.kotlin.core.ViewModelInstance
import app.rive.runtime.kotlin.renderers.Renderer
import app.rive.runtime.kotlin.renderers.RiveArtboardRenderer

/** `Companion` view-model `state` values (handoff/2026-11-14-companion/rive-rig.md section 2). */
object RiveState {
    const val Idle = 0
    const val Listening = 1
    const val Thinking = 2
    const val Speaking = 3
    const val Happy = 4
    const val Sorry = 5
}

/** The Rive `state` for what the screen shows: errors look sorry (design C8), otherwise the talk phase. */
fun riveStateFor(ui: HomeUi): Int = when {
    ui.fault != null && ui.phase == HomePhase.Idle -> RiveState.Sorry
    else -> when (ui.phase) {
        HomePhase.Idle -> RiveState.Idle
        HomePhase.Listening -> RiveState.Listening
        HomePhase.Thinking -> RiveState.Thinking
        HomePhase.Speaking -> RiveState.Speaking
    }
}

/** Frames per second Rive draws at most. */
internal const val RIVE_FPS_CAP = 30

/**
 * Decides by frame timestamp whether a vsync may be drawn: at least 1/[RIVE_FPS_CAP] s (33.3 ms) after the last drawn frame.
 * Unlike "every second vsync" this keeps 30 fps on 60, 90 and 120 Hz displays and after a skipped vsync. A tolerance of
 * one millisecond lets a 60 Hz vsync pair (33.33 ms apart, jittered) through.
 */
internal class FrameThrottle(private val minIntervalNanos: Long = 1_000_000_000L / RIVE_FPS_CAP - TOLERANCE_NANOS) {
    private var lastDrawn = Long.MIN_VALUE

    fun shouldDraw(frameTimeNanos: Long): Boolean {
        if (lastDrawn != Long.MIN_VALUE && frameTimeNanos - lastDrawn < minIntervalNanos) return false
        lastDrawn = frameTimeNanos
        return true
    }

    private companion object {
        const val TOLERANCE_NANOS = 1_000_000L
    }
}

/**
 * Rive draws at most 30 frames per second: a vsync closer than 33.3 ms to the last drawn frame is skipped. The base
 * view re-arms the Choreographer itself (scheduleFrame), so a skipped frame costs one callback and no drawing.
 */
private class CompanionRiveView(context: Context) : RiveAnimationView(context, null) {
    override fun createRenderer(): Renderer =
        object : RiveArtboardRenderer(false, Rive.defaultRendererType, controller) {
            private val throttle = FrameThrottle()
            private var drawn = 0
            private var windowStart = 0L
            override fun doFrame(frameTimeNanos: Long) {
                if (!throttle.shouldDraw(frameTimeNanos)) { scheduleFrame(); return }
                super.doFrame(frameTimeNanos)
                // Debug aid for the CPU measurement: drawn frames per 5 s window, expected ~RIVE_FPS_CAP per second.
                if (windowStart == 0L) windowStart = frameTimeNanos
                drawn++
                if (frameTimeNanos - windowStart >= 5_000_000_000L) {
                    HeyLog.d("Rive", "drawn fps=${drawn * 1_000_000_000L / (frameTimeNanos - windowStart)} (cap $RIVE_FPS_CAP)")
                    drawn = 0
                    windowStart = frameTimeNanos
                }
            }
        }
}

private class Binding(val view: CompanionRiveView, val instance: ViewModelInstance)

/** C5 renders the rig at this fraction of the slot size (about 1 pixel per dp on the 2560 px Pleos display) and the GPU scales it up. */
internal const val DRIVING_RENDER_SCALE = 0.625f

/** Measures the child at [scale] of the available size and draws it scaled back up, so a texture view renders fewer pixels. */
private fun Modifier.renderedAt(scale: Float): Modifier = if (scale >= 1f) this else layout { measurable, constraints ->
    val w = constraints.maxWidth
    val h = constraints.maxHeight
    val placeable = measurable.measure(Constraints.fixed((w * scale).roundToInt(), (h * scale).roundToInt()))
    layout(w, h) {
        placeable.placeWithLayer(0, 0) {
            transformOrigin = TransformOrigin(0f, 0f)
            scaleX = 1f / scale
            scaleY = 1f / scale
        }
    }
}

/**
 * The live character: artboard Companion / state machine Companion of `hey_companion.riv`, driven only through the
 * `state`, `mouth`, `driving` and `bodyColor` properties of the Companion view model. [onFailure] is called once when
 * the file cannot be loaded or bound; the caller then shows the still image (rive-rig.md; C3/C4/C5 never go blank).
 *
 * Composed on C3/C4 and, since (cancels), on C5 too: the rig's `driving` property stays false so the idle
 * animation keeps running while the car moves. [whileDriving] labels the debug log line of the fps cap
 * ([RIVE_FPS_CAP] = 30) and lowers the render resolution (pixel ratio at most 1 on C5, 2 parked): the Rive
 * Android view has no pixel-ratio setting, so the texture is laid out at [DRIVING_RENDER_SCALE] of the slot and scaled
 * back up by the GPU. Reduced motion, a hidden app, the motion budget flag and failure use the still image instead.
 */
@Composable
fun RiveCharacter(
    riveState: Int,
    mouth: Int,
    bodyColor: Int,
    hat: Int,
    neck: Int,
    modifier: Modifier = Modifier,
    onFailure: () -> Unit,
    whileDriving: Boolean = false,
) {
    var binding by remember { mutableStateOf<Binding?>(null) }
    val renderScale = if (whileDriving) DRIVING_RENDER_SCALE else 1f
    AndroidView(
        modifier = modifier.renderedAt(renderScale),
        factory = { ctx ->
            runCatching {
                Rive.init(ctx, Rive.defaultRendererType)
                HeyLog.d("Rive", "character live screen=${if (whileDriving) "C5-driving" else "parked"} fpsCap=$RIVE_FPS_CAP (every 2nd vsync drawn)")
                val view = CompanionRiveView(ctx)
                view.setRiveResource(R.raw.hey_companion, stateMachineName = "Companion", autoplay = true)
                val file = checkNotNull(view.file) { "hey_companion.riv did not load" }
                val instance = file.getViewModelByName("Companion").createDefaultInstance()
                view.controller.stateMachines.forEach { it.viewModelInstance = instance }
                instance.getBooleanProperty("driving").value = false
                binding = Binding(view, instance)
                view
            }.getOrElse {
                onFailure()
                android.view.View(ctx)
            }
        },
        onRelease = { (it as? RiveAnimationView)?.stop() },
    )
    val b = binding
    if (b != null) {
        LaunchedEffect(b, riveState) { b.instance.getNumberProperty("state").value = riveState.toFloat() }
        LaunchedEffect(b, mouth) { b.instance.getNumberProperty("mouth").value = mouth.coerceIn(0, 3).toFloat() }
        LaunchedEffect(b, bodyColor) { b.instance.getNumberProperty("bodyColor").value = bodyColor.toFloat() }
        LaunchedEffect(b, hat) { b.instance.getNumberProperty("hat").value = hat.coerceIn(0, 2).toFloat() }
        LaunchedEffect(b, neck) { b.instance.getNumberProperty("neck").value = neck.coerceIn(0, 1).toFloat() }
    }
}
