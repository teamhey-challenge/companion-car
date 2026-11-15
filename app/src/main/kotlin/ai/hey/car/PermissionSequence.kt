package ai.hey.car

import ai.hey.vehicle.DrivingStatus
import ai.hey.vehicle.VehicleGear
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What one runtime-permission request ended in. [Cancelled] is not an answer: nobody refused, the request has to be made again. */
internal enum class PermissionResult { Granted, Denied, Cancelled }

/** Which permissions got a real answer (grant or refusal) already; survives the process. */
internal interface PermissionProgress {
    fun asked(permission: String): Boolean
    fun markAsked(permission: String)
}

/**
 * The runtime permissions after the consent is on the server, one at a time (microphone, notifications on Android 13+,
 * vehicle speed). Free of Android so the order, the resume and the cancel rules are testable.
 *
 * A step is done once it is granted or got a real answer (a refusal is kept: the question's own retry, CarOnbFix, handles the
 * microphone). A [PermissionResult.Cancelled] request (the car drove off, the system dropped it) is not a refusal: the step is
 * asked again at the next park. Calling [run] again, e.g. after a process death, resumes at the first undone step.
 */
internal class PermissionSequence(
    private val steps: List<String>,
    private val microphone: String,
    private val isGranted: (String) -> Boolean,
    private val progress: PermissionProgress,
    /** Returns when the car is parked. */
    private val awaitParked: suspend () -> Unit,
    /** Shows the system dialog for one permission and returns how it ended. */
    private val ask: suspend (permission: String) -> PermissionResult,
) {
    private val one = Mutex()

    /** True when the microphone is allowed once every step is done. */
    suspend fun run(): Boolean = one.withLock {
        for (permission in steps) {
            while (!isGranted(permission) && !progress.asked(permission)) {
                awaitParked()
                if (ask(permission) != PermissionResult.Cancelled) progress.markAsked(permission)
            }
        }
        isGranted(microphone)
    }
}

/**
 * The one place that opens system permission dialogs: never two at a time (a second launch while one is open cancels the first
 * with an empty result). A caller that stops waiting (the car drove off) leaves the dialog in flight; the next [ask] waits for
 * that same dialog instead of launching another.
 */
internal class PermissionDialogs(private val launch: (String) -> Unit) {
    private var inFlight: CompletableDeferred<PermissionResult>? = null
    private var current: String? = null

    suspend fun ask(permission: String): PermissionResult {
        while (true) {
            val open = inFlight
            if (open == null) {
                val mine = CompletableDeferred<PermissionResult>()
                inFlight = mine
                current = permission
                launch(permission)
                return mine.await()
            }
            // The same dialog is still open (its asker gave up): share it. Another one: let it end first, then ask.
            val sameDialog = current == permission
            val result = open.await()
            if (sameDialog) return result
        }
    }


    /** The activity's result callback: [answers] holds the asked permission, or nothing when the request was cancelled. */
    fun onResult(answers: Map<String, Boolean>) {
        val open = inFlight ?: return
        val permission = current ?: return
        inFlight = null
        current = null
        open.complete(
            when (answers[permission]) {
                true -> PermissionResult.Granted
                false -> PermissionResult.Denied
                null -> PermissionResult.Cancelled
            },
        )
    }
}

/**
 * Runs [block] unless the car really drives for [holdMillis] before it ends: then null. A blip shorter than that (service
 * reconnect, signal jitter) never cancels a dialog the driver is reading.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal suspend fun <T> cancelWhenDriving(moving: Flow<Boolean>, holdMillis: Long, block: suspend () -> T): T? = coroutineScope {
    val work = async { block() }
    val left = async { moving.distinctUntilChanged().mapLatest { now -> now && run { delay(holdMillis); true } }.first { it } }
    val out = select<T?> {
        work.onAwait { it }
        left.onAwait { null }
    }
    work.cancel()
    left.cancel()
    out
}

/** A drive shorter than this never cancels a permission dialog. */
internal const val DRIVING_CANCEL_HOLD_MS = 1_000L

/**
 * Real motion, not a guess: gear D or R, or the car service says Driving. [DrivingStatus.Unknown] (service lost, not yet
 * answered) blocks hands-on actions elsewhere but is a blip here: it must neither cancel nor cover a permission dialog.
 */
internal fun isTrulyMoving(gear: VehicleGear, driving: DrivingStatus): Boolean =
    gear == VehicleGear.Drive || gear == VehicleGear.Reverse || driving == DrivingStatus.Driving
