package ai.hey.vehicle

import ai.hey.model.DrivingState
import ai.hey.model.HeyConfig
import ai.hey.model.HeyLog
import ai.hey.model.Place
import ai.hey.model.RouteInfo
import ai.hey.model.SpeechPriority
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.TripInfo as PleosTripInfo
import ai.pleos.playground.navi.data.TripRequest
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear as PleosGear
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "PleosVehicle"
private const val ROUTE_TIMEOUT_MS = 10_000L
private const val TRIP_TIMEOUT_MS = 5_000L
private const val SPEECH_TIMEOUT_MS = 30_000L
private const val TTS_RETRY_BACKOFF_MS = 5_000L

/**
 * Pleos SDK + android.car behind [VehicleGateway]. Every SDK touch is wrapped so a missing service never crashes the
 * app. A missing service is never reported as success: no navigation means [RouteResult.Failed], no speech engine
 * means the text is *not* heard ([VehicleEvent.SpeechDropped]), and no car service means the car is treated as driving.
 *
 * Measured behaviour this relies on (docs/policy/maltong-dev-spec.md 2.4): TTS needs Locale.KOREA, the TTS event
 * listener must be added after initialize succeeds, onRouteEnded arrives right after onRouteStarted and is ignored.
 * The Pleos TTS API has no speaking-rate setter, so there is no rate setting in the app.
 */
class PleosVehicleGateway(
    private val context: Context,
    private val scope: CoroutineScope,
    now: () -> Long = { System.currentTimeMillis() },
) : VehicleGateway {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    // Fails closed: DRIVING until android.car says PARKED.
    private val _driving = MutableStateFlow(DrivingState.DRIVING)
    private val _gear = MutableStateFlow(VehicleGear.Unknown)
    private val _doors = MutableStateFlow<Set<VehicleDoor>>(emptySet())
    private val _events = MutableSharedFlow<VehicleEvent>(extraBufferCapacity = 64)

    @Volatile private var settings = SpeechSettings()

    override val drivingState: StateFlow<DrivingState> = _driving.asStateFlow()
    override val gear: StateFlow<VehicleGear> = _gear.asStateFlow()
    override val doors: StateFlow<Set<VehicleDoor>> = _doors.asStateFlow()
    override val events: SharedFlow<VehicleEvent> = _events.asSharedFlow()

    private val ttsEngine = PleosSpeechEngine(app)
    private val queue = SpeechQueue(
        scope = scope,
        engine = ttsEngine,
        settings = { settings },
        now = now,
        emit = { _events.tryEmit(it) },
        dedupWindowMs = HeyConfig.SPEECH_DEDUP_WINDOW_SEC * 1000L,
        queueMax = HeyConfig.SPEECH_QUEUE_MAX,
    )

    // ---- navigation: at most one outstanding request of each kind, completed only by its own callback
    @Volatile private var navi: NaviHelper? = null
    @Volatile private var naviReady = false
    private val routeMutex = Mutex()
    private val tripMutex = Mutex()
    private val pendingRoute = AtomicReference<CompletableDeferred<RouteResult>?>(null)
    private val pendingTrip = AtomicReference<CompletableDeferred<TripInfo?>?>(null)

    private val naviListener = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            // `duration` is seconds, `distance` metres, `tollFare` won (probe: tollFare=700).
            pendingRoute.getAndSet(null)?.complete(
                RouteResult.Started(RouteInfo(routeStartInfo.duration, routeStartInfo.distance, routeStartInfo.tollFare)),
            )
        }

        // onRouteEnded fires right after onRouteStarted: deliberately not overridden.

        override fun onError(errorCode: NaviErrorCode) {
            if (errorCode == NaviErrorCode.CAUSE_SEARCH_TRIP) {
                pendingTrip.getAndSet(null)?.complete(null)
            } else {
                pendingRoute.getAndSet(null)?.complete(RouteResult.Failed(errorCode.name))
            }
        }

        override fun onTripInfo(tripInfo: PleosTripInfo) {
            pendingTrip.getAndSet(null)?.complete(TripInfo(etaSec = tripInfo.eta, distanceM = tripInfo.distance))
        }

        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
            _events.tryEmit(VehicleEvent.Arrived)
        }
    }

    private var vehicle: Vehicle? = null

    init {
        guard("driving state") {
            CarUx.start(
                app,
                main,
                onDriving = { _driving.value = if (it) DrivingState.DRIVING else DrivingState.PARKED },
                onLost = { _driving.value = DrivingState.DRIVING },
            )
        }
        guard("navigation") { connectNavi() }
        guard("vehicle signals") { connectVehicle() }
    }

    private inline fun guard(what: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            HeyLog.w(TAG, "$what unavailable: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private fun connectNavi() {
        val helper = NaviHelper(app)
        helper.addListener(naviListener)
        helper.initialize(
            { naviReady = true },
            { e -> HeyLog.w(TAG, "navigation service not connected: ${e.message}") },
        )
        navi = helper
    }

    private fun connectVehicle() {
        val v = Vehicle(app)
        v.initialize()
        vehicle = v
        // Optional signals: a failure here is ignored, nothing in the product depends on them.
        guard("gear") {
            @Suppress("DEPRECATION")
            v.getGear().registerGearSelection(object : GearSelectionListener {
                override fun onSelected(gear: PleosGear?) {
                    _gear.value = when (gear) {
                        PleosGear.GEAR_PARK -> VehicleGear.Park
                        PleosGear.GEAR_DRIVE -> VehicleGear.Drive
                        PleosGear.GEAR_REVERSE -> VehicleGear.Reverse
                        PleosGear.GEAR_NEUTRAL -> VehicleGear.Neutral
                        else -> VehicleGear.Unknown
                    }
                }

                override fun onFailed(e: Exception) = HeyLog.d(TAG, "gear listener failed: ${e.message}")
            })
        }
        val areas = listOf(
            VehicleDoor.DriverRow1 to DoorValue.ROW_1_LEFT,
            VehicleDoor.PassengerRow1 to DoorValue.ROW_1_RIGHT,
            VehicleDoor.Row2Left to DoorValue.ROW_2_LEFT,
            VehicleDoor.Row2Right to DoorValue.ROW_2_RIGHT,
        )
        for ((door, value) in areas) {
            guard("door $door") {
                v.getDoor().registerDoorPosition(
                    DoorArea(value),
                    object : DoorPositionListener {
                        override fun onPositionUpdated(position: Int?) {
                            _doors.value = if ((position ?: 0) > 0) _doors.value + door else _doors.value - door
                        }

                        override fun onFailed(e: Exception) = HeyLog.d(TAG, "door listener failed: ${e.message}")
                    },
                )
            }
        }
    }

    override fun speak(textKo: String, priority: SpeechPriority, speechId: String?) = queue.enqueue(textKo, priority, speechId)

    override fun replay(textKo: String, speechId: String?) = queue.enqueue(textKo, SpeechPriority.High, speechId, force = true)

    override fun configureSpeech(settings: SpeechSettings) {
        this.settings = settings
    }

    override suspend fun startRoute(place: Place): RouteResult = routeMutex.withLock {
        val helper = navi
        if (helper == null || !naviReady) {
            HeyLog.w(TAG, "startRoute: navigation service not connected")
            return@withLock RouteResult.Failed("NAVI_UNAVAILABLE")
        }
        val result = CompletableDeferred<RouteResult>()
        pendingRoute.set(result)
        try {
            helper.requestRoute(
                ai.pleos.playground.navi.data.RouteInfo(
                    latitude = place.lat,
                    longitude = place.lng,
                    displayLatitude = null,
                    displayLongitude = null,
                    poiName = place.nameKo,
                    poiId = place.placeId,
                    address = place.addressKo,
                    poiSubId = "",
                    routeOption = RouteOption.RECOMMENDED,
                    poiProvider = "hey",
                    isMobileRequest = false,
                ),
            )
        } catch (t: Throwable) {
            pendingRoute.compareAndSet(result, null)
            HeyLog.w(TAG, "requestRoute threw ${t.javaClass.simpleName}: ${t.message}")
            return@withLock RouteResult.Failed("NAVI_ERROR")
        }
        val outcome = withTimeoutOrNull(ROUTE_TIMEOUT_MS) { result.await() } ?: RouteResult.Failed("TIMEOUT")
        pendingRoute.compareAndSet(result, null)
        outcome
    }

    override suspend fun requestTrip(place: Place): TripInfo? = tripMutex.withLock {
        val helper = navi
        if (helper == null || !naviReady) return@withLock null
        val result = CompletableDeferred<TripInfo?>()
        pendingTrip.set(result)
        try {
            helper.requestTrip(TripRequest(place.lat, place.lng, place.placeId, place.nameKo, ""))
        } catch (t: Throwable) {
            pendingTrip.compareAndSet(result, null)
            HeyLog.w(TAG, "requestTrip threw ${t.javaClass.simpleName}: ${t.message}")
            return@withLock null
        }
        val outcome = withTimeoutOrNull(TRIP_TIMEOUT_MS) { result.await() }
        pendingTrip.compareAndSet(result, null)
        outcome
    }

    override fun release() {
        guard("release navigation") { navi?.removeListener(naviListener); navi?.release() }
        guard("release vehicle") { vehicle?.release() }
        guard("release tts") { ttsEngine.release() }
        guard("release car") { CarUx.stop() }
    }
}

/**
 * On-device Korean TTS. Initialisation is lazy and retried (with a pause after a failure) on the next utterance, so a
 * speech service that starts late after boot does not silence the car for the life of the process. A text is reported
 * as read only when the engine said so; with no engine it is reported as not read.
 */
internal class PleosSpeechEngine(private val context: Context) : SpeechEngine {
    @Volatile private var tts: TextToSpeech? = null
    @Volatile private var ready = false
    @Volatile private var retryAfter = 0L
    private val current = AtomicReference<CompletableDeferred<Boolean>?>(null)

    private val events = object : EventListener {
        override fun onStart() = Unit
        override fun onReady() = Unit
        override fun onDone() { current.get()?.complete(true) }
        override fun onError(e: ErrorType) {
            HeyLog.w(TAG, "tts error $e")
            current.get()?.complete(false)
        }
        override fun onStop() { current.get()?.complete(false) }
        override fun onUpdatedRms(rms: Double) = Unit
    }

    private suspend fun ensureReady(): Boolean {
        if (ready) return true
        val now = System.currentTimeMillis()
        if (now < retryAfter) return false
        val init = CompletableDeferred<Boolean>()
        try {
            tts?.release()
            val engine = TextToSpeech(context, Mode.ON_DEVICE)
            tts = engine
            engine.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    // The listener must be added after initialize succeeded (measured).
                    try {
                        engine.addEventListener(events)
                        engine.setLocale(Locale.KOREA)
                        init.complete(true)
                    } catch (t: Throwable) {
                        HeyLog.w(TAG, "tts setup threw ${t.javaClass.simpleName}: ${t.message}")
                        init.complete(false)
                    }
                }

                override fun onFailure(e: ErrorType) {
                    HeyLog.w(TAG, "tts init failed $e")
                    init.complete(false)
                }
            })
        } catch (t: Throwable) {
            HeyLog.w(TAG, "tts unavailable: ${t.javaClass.simpleName}: ${t.message}")
            retryAfter = System.currentTimeMillis() + TTS_RETRY_BACKOFF_MS
            return false
        }
        ready = withTimeoutOrNull(SPEECH_TIMEOUT_MS) { init.await() } ?: false
        if (!ready) retryAfter = System.currentTimeMillis() + TTS_RETRY_BACKOFF_MS
        return ready
    }

    override suspend fun speak(text: String): Boolean {
        if (!ensureReady()) {
            HeyLog.w(TAG, "speech engine not available, text not read")
            return false
        }
        val done = CompletableDeferred<Boolean>()
        current.set(done)
        try {
            tts?.speak(text, true)
        } catch (t: Throwable) {
            HeyLog.w(TAG, "tts speak threw ${t.javaClass.simpleName}: ${t.message}")
            current.compareAndSet(done, null)
            return false
        }
        val ok = withTimeoutOrNull(SPEECH_TIMEOUT_MS) { done.await() } ?: false
        current.compareAndSet(done, null)
        return ok
    }

    fun release() {
        tts?.removeEventListener(events)
        tts?.release()
        tts = null
        ready = false
    }
}
