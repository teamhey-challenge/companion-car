package ai.hey.vehicle

import ai.hey.model.HeyConfig
import ai.hey.model.Place
import ai.hey.model.RouteInfo
import ai.hey.model.SpeechPriority
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

data class SpeechSettings(val gapSec: Int = HeyConfig.SPEECH_GAP_SEC)

enum class VehicleGear { Unknown, Park, Reverse, Neutral, Drive }

enum class VehicleDoor { DriverRow1, PassengerRow1, Row2Left, Row2Right }

/** Remaining distance/time of the running navigation, as reported by the navigation service. */
data class TripInfo(val etaSec: Int, val distanceM: Int)

/**
 * The car's own motion as the gateway knows it. [Unknown] is not "probably parked": the car service has not answered
 * yet, or has gone away. Every consumer treats it exactly like [Driving], so no hands-on action is offered on a guess.
 */
sealed interface DrivingStatus {
    data object Unknown : DrivingStatus
    data object Parked : DrivingStatus
    data object Driving : DrivingStatus
}

/** Why a text was not read to the end. */
enum class SpeechDropReason {
    /** The text was blank; there was nothing to read. */
    Blank,

    /** More waiting texts than the queue allows; this one was pushed out. */
    QueueOverflow,

    /** No on-device speech engine (not installed, not initialised, still backing off). */
    EngineUnavailable,

    /** The engine started but reported an error or stopped before the end. */
    EngineError,
}

/** What the speech engine did with one text. */
sealed interface SpeechResult {
    data object Delivered : SpeechResult
    data class Dropped(val reason: SpeechDropReason) : SpeechResult
}

/**
 * One "start the route in the car's navigation" attempt as explicit states. The navigation app shows a route preview
 * that a person must confirm with "안내 시작", so a request being accepted is not the route having started.
 *
 * `Idle -> Requested -> AwaitingNaviConfirm -> Started | Failed`; cancelling returns an active attempt to `Idle`.
 * [Started] and [Failed] carry the attempt number so two attempts with equal content stay distinguishable.
 */
sealed interface RouteStartState {
    data object Idle : RouteStartState

    /** Asked for; waiting for the navigation service to be ready, then handing it the destination. */
    data object Requested : RouteStartState

    /** The navigation accepted the destination and shows its preview; waiting for the person to press 안내 시작 there. */
    data object AwaitingNaviConfirm : RouteStartState

    data class Started(val attempt: Int, val route: RouteInfo) : RouteStartState
    data class Failed(val attempt: Int, val code: String) : RouteStartState
}

/** [RouteStartState.Failed] codes the screens explain specifically; any other code is a generic failure. */
object RouteFailure {
    /** The navigation app did not answer (not opened since boot). Opening it and trying again fixes it. */
    const val NAVI_NOT_READY = "NAVI_NOT_READY"
    const val NAVI_ERROR = "NAVI_ERROR"

    /** The preview was not confirmed with 안내 시작 within [RouteStartMachine.CONFIRM_TIMEOUT_MS]. */
    const val NAVI_CONFIRM_TIMEOUT = "NAVI_CONFIRM_TIMEOUT"
}

sealed interface VehicleEvent {
    /** The text with this id was heard (read to the end by the speaker). Never emitted for text that was not spoken. */
    data class SpeechDone(val speechId: String) : VehicleEvent

    /** The text with this id was not heard; [reason] says why. */
    data class SpeechDropped(val speechId: String, val reason: SpeechDropReason) : VehicleEvent

    /** The navigation service reported arrival at the destination. */
    data object Arrived : VehicleEvent

    /** The navigation service is missing, failed to initialise, or reported an error with no request outstanding. */
    data class NavigationUnavailable(val code: String) : VehicleEvent
}

/**
 * Everything the app needs from the car. Screens and the session only know this interface; the Pleos SDK and
 * android.car stay behind [PleosVehicleGateway].
 */
interface VehicleGateway {
    /**
     * Fails closed: [DrivingStatus.Unknown] until the car service has said otherwise (and again when it goes away),
     * so hands-on actions are never offered on a guess.
     */
    val drivingState: StateFlow<DrivingStatus>
    val gear: StateFlow<VehicleGear>

    /** Doors that are currently open. */
    val doors: StateFlow<Set<VehicleDoor>>
    val events: SharedFlow<VehicleEvent>

    /**
     * Queues [textKo] for the driver. [speechId] comes back in [VehicleEvent.SpeechDone] / [VehicleEvent.SpeechDropped].
     * High priority goes first and skips the gap; request speech (has an id, normal priority) is de-duplicated.
     */
    fun speak(textKo: String, priority: SpeechPriority, speechId: String? = null)

    /** Driver pressed "다시 듣기": reads again even if the same text was just read. [speechId] gets the done event. */
    fun replay(textKo: String, speechId: String? = null)

    fun configureSpeech(settings: SpeechSettings)

    /**
     * Begins one route attempt (ignored while one is active); progress is [routeState]. Success is only
     * [RouteStartState.Started], which the navigation reports when the person confirms its preview.
     */
    fun startRoute(place: Place)

    val routeState: StateFlow<RouteStartState>

    /** Abandons the active attempt: [routeState] returns to [RouteStartState.Idle] and a late navigation answer is ignored. */
    fun cancelRoute()

    /** Brings the navigation app to the front (it has to be opened once after boot). False when it cannot be launched. */
    fun openNavi(): Boolean

    /** Null when the navigation service does not answer (no route, service missing). */
    suspend fun requestTrip(place: Place): TripInfo?

    /** D1 retry: forget the speech engine's backoff and try to initialise it again. */
    fun retrySpeech()

    /** D1 retry: drop the navigation connection and connect again. */
    fun retryNavigation()

    fun release()
}
