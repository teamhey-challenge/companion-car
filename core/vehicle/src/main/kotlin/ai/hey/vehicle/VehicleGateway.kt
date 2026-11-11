package ai.hey.vehicle

import ai.hey.model.DrivingState
import ai.hey.model.HeyConfig
import ai.hey.model.Place
import ai.hey.model.RouteInfo
import ai.hey.model.SpeechPriority
import ai.hey.model.SpeechRate
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

data class SpeechSettings(
    val gapSec: Int = HeyConfig.SPEECH_GAP_SEC,
    val rate: SpeechRate = SpeechRate.Normal,
)

enum class VehicleGear { Unknown, Park, Reverse, Neutral, Drive }

enum class VehicleDoor { DriverRow1, PassengerRow1, Row2Left, Row2Right }

/** Remaining distance/time of the running navigation, as reported by the navigation service. */
data class TripInfo(val etaSec: Int, val distanceM: Int)

sealed interface RouteResult {
    data class Started(val route: RouteInfo) : RouteResult
    data class Failed(val code: String) : RouteResult
}

sealed interface VehicleEvent {
    /** The text with this id was read aloud (or was an exact repeat inside the dedup window). */
    data class SpeechDone(val speechId: String) : VehicleEvent

    /** The text with this id was pushed out of a full queue, or the speech engine could not read it. */
    data class SpeechDropped(val speechId: String) : VehicleEvent

    /** The navigation service reported arrival at the destination. */
    data object Arrived : VehicleEvent
}

/**
 * Everything the app needs from the car. Screens and the session only know this interface; the Pleos SDK and
 * android.car stay behind [PleosVehicleGateway].
 */
interface VehicleGateway {
    val drivingState: StateFlow<DrivingState>
    val gear: StateFlow<VehicleGear>

    /** Doors that are currently open. */
    val doors: StateFlow<Set<VehicleDoor>>
    val events: SharedFlow<VehicleEvent>

    /**
     * Queues [textKo] for the driver. [speechId] comes back in [VehicleEvent.SpeechDone] / [VehicleEvent.SpeechDropped].
     * High priority goes first and skips the gap; an identical text inside the dedup window is not read twice.
     */
    fun speak(textKo: String, priority: SpeechPriority, speechId: String? = null)

    /** Driver pressed "다시 듣기": reads again even if the same text was just read. */
    fun replay(textKo: String)

    fun configureSpeech(settings: SpeechSettings)

    suspend fun startRoute(place: Place): RouteResult

    /** Null when the navigation service does not answer (no route, service missing). */
    suspend fun requestTrip(place: Place): TripInfo?

    fun release()
}
