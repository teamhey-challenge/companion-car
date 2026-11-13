package ai.hey.vehicle

import ai.hey.model.HeyConfig
import ai.hey.model.Place
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

    /** Mouth level 0..3 while the local (Pleos) voice reads, from its `onUpdatedRms` (D-13); 0 when it is quiet. */
    val mouth: StateFlow<Int>

    /**
     * Queues [textKo] for the driver. [speechId] comes back in [VehicleEvent.SpeechDone] / [VehicleEvent.SpeechDropped].
     * High priority goes first and skips the gap; request speech (has an id, normal priority) is de-duplicated.
     */
    fun speak(textKo: String, priority: SpeechPriority, speechId: String? = null)

    /** Driver pressed "다시 듣기": reads again even if the same text was just read. [speechId] gets the done event. */
    fun replay(textKo: String, speechId: String? = null)

    fun configureSpeech(settings: SpeechSettings)

    /** The driver pressed the button or the audio focus went: cut what is being read and drop what waits. Nothing is read again (D-14, D-24). */
    fun stopSpeech()

    /** Opens the on-device voice now so the first spoken reply does not wait for its start-up (Go/No-go 8.2). */
    fun warmUpSpeech()

    /** Null when the navigation service does not answer (no route, service missing). */
    suspend fun requestTrip(place: Place): TripInfo?

    /** D1 retry: forget the speech engine's backoff and try to initialise it again. */
    fun retrySpeech()

    /** D1 retry: drop the navigation connection and connect again. */
    fun retryNavigation()

    fun release()
}
