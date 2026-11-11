package ai.hey.vehicle

import ai.hey.model.DrivingState
import ai.hey.model.HeyConfig
import ai.hey.model.Place
import ai.hey.model.RouteInfo
import ai.hey.model.SpeechPriority
import ai.hey.model.SpeechRate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Stand-in for previews, tests and devices without the Pleos services. Driving state and arrival are driven by
 * the caller; speech goes through the real [SpeechQueue] so queue policy is exercised in tests too.
 */
class FakeVehicleGateway(
    scope: CoroutineScope,
    initialDriving: DrivingState = DrivingState.PARKED,
    now: () -> Long = { System.currentTimeMillis() },
    /** How long a fake utterance "takes". */
    private val speechMillis: Long = 0,
    private val route: RouteInfo = RouteInfo(durationSec = 2_520, distanceM = 18_400, tollFare = 0),
    private val tripInfo: TripInfo? = TripInfo(etaSec = 1_380, distanceM = 18_400),
) : VehicleGateway {
    private val _driving = MutableStateFlow(initialDriving)
    private val _gear = MutableStateFlow(VehicleGear.Park)
    private val _doors = MutableStateFlow<Set<VehicleDoor>>(emptySet())
    private val _events = MutableSharedFlow<VehicleEvent>(extraBufferCapacity = 64)

    private val _spoken = ArrayList<String>()
    private val _routes = ArrayList<Place>()
    private var settings = SpeechSettings()

    /** Texts that were read, in order. */
    val spoken: List<String> get() = synchronized(_spoken) { _spoken.toList() }

    /** Destinations passed to [startRoute]. */
    val routes: List<Place> get() = synchronized(_routes) { _routes.toList() }

    /** Set to make the next [startRoute] fail with this code. */
    @Volatile var routeFailure: String? = null

    override val drivingState: StateFlow<DrivingState> = _driving.asStateFlow()
    override val gear: StateFlow<VehicleGear> = _gear.asStateFlow()
    override val doors: StateFlow<Set<VehicleDoor>> = _doors.asStateFlow()
    override val events: SharedFlow<VehicleEvent> = _events.asSharedFlow()

    private val queue = SpeechQueue(
        scope = scope,
        engine = object : SpeechEngine {
            override suspend fun speak(text: String, rate: SpeechRate): Boolean {
                synchronized(_spoken) { _spoken += text }
                if (speechMillis > 0) delay(speechMillis)
                return true
            }
        },
        settings = { settings },
        now = now,
        emit = { _events.tryEmit(it) },
        dedupWindowMs = HeyConfig.SPEECH_DEDUP_WINDOW_SEC * 1000L,
        queueMax = HeyConfig.SPEECH_QUEUE_MAX,
    )

    fun setDriving(driving: Boolean) {
        _driving.value = if (driving) DrivingState.DRIVING else DrivingState.PARKED
    }

    fun setGear(value: VehicleGear) {
        _gear.value = value
    }

    fun setDoors(open: Set<VehicleDoor>) {
        _doors.value = open
    }

    fun arrive() {
        _events.tryEmit(VehicleEvent.Arrived)
    }

    override fun speak(textKo: String, priority: SpeechPriority, speechId: String?) = queue.enqueue(textKo, priority, speechId)

    override fun replay(textKo: String) = queue.enqueue(textKo, SpeechPriority.High, null, force = true)

    override fun configureSpeech(settings: SpeechSettings) {
        this.settings = settings
    }

    override suspend fun startRoute(place: Place): RouteResult {
        synchronized(_routes) { _routes += place }
        val failure = routeFailure
        return if (failure != null) RouteResult.Failed(failure) else RouteResult.Started(route)
    }

    override suspend fun requestTrip(place: Place): TripInfo? = tripInfo

    override fun release() = Unit
}
