package ai.hey.feature.home

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

/**
 * Rive draws at most 30 frames per second (D-31): every second vsync is skipped. The base view re-arms the Choreographer
 * itself (scheduleFrame), so a skipped frame costs one callback and no drawing.
 */
private class CompanionRiveView(context: Context) : RiveAnimationView(context, null) {
    override fun createRenderer(): Renderer =
        object : RiveArtboardRenderer(false, Rive.defaultRendererType, controller) {
            private var n = 0
            override fun doFrame(frameTimeNanos: Long) {
                if (n++ % 2 == 1) { scheduleFrame(); return }
                super.doFrame(frameTimeNanos)
            }
        }
}

private class Binding(val view: CompanionRiveView, val instance: ViewModelInstance)

/**
 * The live character: artboard Companion / state machine Companion of `hey_companion.riv`, driven only through the
 * `state`, `mouth`, `driving` and `bodyColor` properties of the Companion view model. [onFailure] is called once when
 * the file cannot be loaded or bound; the caller then shows the still image (rive-rig.md; C3/C4 never go blank).
 *
 * Only composed on parked screens (C3/C4). C5, reduced motion and failure use the still image instead.
 */
@Composable
fun RiveCharacter(
    riveState: Int,
    mouth: Int,
    bodyColor: Int,
    modifier: Modifier = Modifier,
    onFailure: () -> Unit,
) {
    var binding by remember { mutableStateOf<Binding?>(null) }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            runCatching {
                Rive.init(ctx, Rive.defaultRendererType)
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
    }
}
