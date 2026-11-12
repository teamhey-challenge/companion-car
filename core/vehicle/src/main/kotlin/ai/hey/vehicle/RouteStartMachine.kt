package ai.hey.vehicle

import ai.hey.model.Place
import ai.hey.model.RouteInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The route-start attempt as a state machine, free of the Pleos SDK so it is unit-testable.
 *
 * ```
 * Idle --start--> Requested --navigation ready--> AwaitingNaviConfirm --onRouteStarted--> Started
 *                    |                                   |
 *                    +-- not ready in 15 s --> Failed(NAVI_NOT_READY)
 *                    +-- send threw ---------> Failed(NAVI_ERROR)
 *                                                        +-- onError(code) ----------> Failed(code)
 *                                                        +-- 3 min, no confirm ------> Failed(NAVI_CONFIRM_TIMEOUT)
 * any active state --cancel--> Idle
 * ```
 *
 * The navigation shows a route preview that a person confirms with "안내 시작", so there is deliberately no short
 * timeout while [RouteStartState.AwaitingNaviConfirm]. An answer that arrives when no attempt is waiting for it (after
 * a cancel, after a failure, or from guidance somebody started in the navigation by hand) is ignored.
 *
 * Callbacks come from binder threads; every transition happens under [lock].
 */
class RouteStartMachine(
    private val scope: CoroutineScope,
    /** True once the navigation service answers requests (it has to have been opened once after boot). */
    private val isReady: () -> Boolean,
    /** Asks the navigation to answer, so [isReady] can turn true; called about every [PROBE_EVERY_MS] while waiting. */
    private val probe: () -> Unit,
    /** Hands the destination to the navigation. May throw. */
    private val send: (Place) -> Unit,
    private val readyWaitMs: Long = READY_WAIT_MS,
    private val confirmTimeoutMs: Long = CONFIRM_TIMEOUT_MS,
) {
    private val lock = Any()
    private val _state = MutableStateFlow<RouteStartState>(RouteStartState.Idle)
    val state: StateFlow<RouteStartState> = _state.asStateFlow()

    private var attempt = 0
    private var job: Job? = null

    /** Begins an attempt; ignored while one is active. */
    fun start(place: Place) {
        synchronized(lock) {
            if (isActive(_state.value)) return
            val mine = ++attempt
            _state.value = RouteStartState.Requested
            job = scope.launch { run(place, mine) }
        }
    }

    /** Abandons the active attempt (or clears a finished one) so a late navigation answer finds nobody waiting. */
    fun cancel() {
        synchronized(lock) {
            attempt++
            job?.cancel()
            job = null
            _state.value = RouteStartState.Idle
        }
    }

    /** The navigation reported the route started. Only completes an attempt that is waiting for the confirmation. */
    fun onRouteStarted(route: RouteInfo) {
        synchronized(lock) {
            if (_state.value != RouteStartState.AwaitingNaviConfirm) return
            job?.cancel()
            job = null
            _state.value = RouteStartState.Started(attempt, route)
        }
    }

    /** The navigation reported a route error. Returns true when an attempt consumed it. */
    fun onError(code: String): Boolean = synchronized(lock) {
        if (!isActive(_state.value)) return false
        job?.cancel()
        job = null
        _state.value = RouteStartState.Failed(attempt, code)
        true
    }

    private suspend fun run(place: Place, mine: Int) {
        var waited = 0L
        var untilProbe = 0L
        while (!isReady()) {
            if (waited >= readyWaitMs) return fail(mine, RouteFailure.NAVI_NOT_READY)
            if (untilProbe <= 0L) {
                probe()
                untilProbe = PROBE_EVERY_MS
            }
            delay(POLL_MS)
            waited += POLL_MS
            untilProbe -= POLL_MS
        }
        // Awaiting is entered before the request leaves, so an answer cannot overtake the state change.
        val entered = synchronized(lock) {
            if (attempt != mine || _state.value != RouteStartState.Requested) false else {
                _state.value = RouteStartState.AwaitingNaviConfirm
                true
            }
        }
        if (!entered) return
        try {
            send(place)
        } catch (e: Exception) {
            return fail(mine, RouteFailure.NAVI_ERROR)
        }
        delay(confirmTimeoutMs)
        fail(mine, RouteFailure.NAVI_CONFIRM_TIMEOUT)
    }

    private fun fail(mine: Int, code: String) {
        synchronized(lock) {
            if (attempt == mine && isActive(_state.value)) _state.value = RouteStartState.Failed(mine, code)
        }
    }

    private fun isActive(s: RouteStartState) = s == RouteStartState.Requested || s == RouteStartState.AwaitingNaviConfirm

    companion object {
        /** How long to keep waiting for the navigation service to answer before "not ready". */
        const val READY_WAIT_MS = 15_000L

        /** How long the person has to press 안내 시작 in the navigation's preview. */
        const val CONFIRM_TIMEOUT_MS = 180_000L
        private const val POLL_MS = 500L
        private const val PROBE_EVERY_MS = 3_000L
    }
}
