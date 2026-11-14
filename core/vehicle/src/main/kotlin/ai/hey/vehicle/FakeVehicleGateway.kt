package ai.hey.vehicle

import ai.hey.model.HeyConfig
import ai.hey.model.VehicleSignals
import ai.hey.model.Place
import ai.hey.model.SpeechPriority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Stand-in for previews and tests only (production never falls back to it). Driving state and arrival are driven by the
 * caller; speech goes through the real [SpeechQueue] so queue policy is exercised in tests too.
 */
class FakeVehicleGateway(
    scope: CoroutineScope,
    initialDriving: DrivingStatus = DrivingStatus.Parked,
    now: () -> Long = { System.currentTimeMillis() },
    /** How long a fake utterance "takes". */
    private val speechMillis: Long = 0,
    private val tripInfo: TripInfo? = TripInfo(etaSec = 1_380, distanceM = 18_400),
) : VehicleGateway {
    private val _driving = MutableStateFlow(initialDriving)
    private val _gear = MutableStateFlow(VehicleGear.Park)
    private val _doors = MutableStateFlow<Set<VehicleDoor>>(emptySet())
    private val _signals = MutableStateFlow(VehicleSignals(gear = "P", speedKmh = 0, doorOpen = false))
    private val _events = MutableSharedFlow<VehicleEvent>(extraBufferCapacity = 64)
    private val _mouth = MutableStateFlow(0)

    private val _spoken = ArrayList<String>()
    @Volatile private var settings = SpeechSettings()
    @Volatile private var speechRetryCount = 0
    @Volatile private var navigationRetryCount = 0

    /** Texts that were read, in order. */
    val spoken: List<String> get() = synchronized(_spoken) { _spoken.toList() }

    /** Set to false to make the speaker fail: texts are then dropped instead of heard. */
    @Volatile var speakerWorks: Boolean = true

    /** How often the D1 retry was used, so a screen test can prove the button is wired. */
    val speechRetries: Int get() = speechRetryCount
    val navigationRetries: Int get() = navigationRetryCount

    override val drivingState: StateFlow<DrivingStatus> = _driving.asStateFlow()
    override val gear: StateFlow<VehicleGear> = _gear.asStateFlow()
    override val doors: StateFlow<Set<VehicleDoor>> = _doors.asStateFlow()
    override val signals: StateFlow<VehicleSignals> = _signals.asStateFlow()

    /** Test/preview control of the context the server gets. */
    fun setSignals(transform: (VehicleSignals) -> VehicleSignals) = _signals.update(transform)
    override val events: SharedFlow<VehicleEvent> = _events.asSharedFlow()
    override val mouth: StateFlow<Int> = _mouth.asStateFlow()

    private val queue = SpeechQueue(
        scope = scope,
        engine = object : SpeechEngine {
            override suspend fun speak(text: String): SpeechResult {
                if (!speakerWorks) return SpeechResult.Dropped(SpeechDropReason.EngineUnavailable)
                synchronized(_spoken) { _spoken += text }
                if (speechMillis > 0) delay(speechMillis)
                return SpeechResult.Delivered
            }
        },
        settings = { settings },
        now = now,
        emit = { _events.tryEmit(it) },
        dedupWindowMs = HeyConfig.SPEECH_DEDUP_WINDOW_SEC * 1000L,
        queueMax = HeyConfig.SPEECH_QUEUE_MAX,
    )

    fun setDriving(driving: Boolean) {
        _driving.value = if (driving) DrivingStatus.Driving else DrivingStatus.Parked
    }

    fun setDrivingStatus(status: DrivingStatus) {
        _driving.value = status
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

    /** Simulates the gateway reporting that a text could not be read (no engine, engine error). */
    fun emitSpeechDropped(speechId: String, reason: SpeechDropReason) {
        _events.tryEmit(VehicleEvent.SpeechDropped(speechId, reason))
    }

    /** Simulates the navigation service failing outside a route request (init failure, lost connection). */
    fun emitNavigationUnavailable(code: String) {
        _events.tryEmit(VehicleEvent.NavigationUnavailable(code))
    }

    override fun speak(textKo: String, priority: SpeechPriority, speechId: String?) = queue.enqueue(textKo, priority, speechId)

    override fun replay(textKo: String, speechId: String?) = queue.enqueue(textKo, SpeechPriority.High, speechId, force = true)

    override fun configureSpeech(settings: SpeechSettings) {
        this.settings = settings
    }

    override fun warmUpSpeech() = Unit

    /** How often [stopSpeech] was called. */
    @Volatile var stops = 0
        private set

    override fun stopSpeech() {
        stops++
        queue.stopAll()
    }

    override suspend fun requestTrip(place: Place): TripInfo? = tripInfo

    override fun retrySpeech() {
        speechRetryCount++
        speakerWorks = true
    }

    override fun retryNavigation() {
        navigationRetryCount++
    }

    override fun release() = Unit
}
