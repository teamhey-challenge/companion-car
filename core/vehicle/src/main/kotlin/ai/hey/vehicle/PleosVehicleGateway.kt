package ai.hey.vehicle

import ai.hey.model.DrivingState
import ai.hey.model.HeyConfig
import ai.hey.model.HeyLog
import ai.hey.model.Place
import ai.hey.model.RouteInfo
import ai.hey.model.SpeechPriority
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.hey.model.SpeechRate
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
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

private const val TAG = "PleosVehicle"
private const val ROUTE_TIMEOUT_MS = 10_000L
private const val TRIP_TIMEOUT_MS = 5_000L
private const val SPEECH_TIMEOUT_MS = 30_000L

/**
 * Pleos SDK + android.car behind [VehicleGateway]. Every SDK touch is wrapped: if a service is missing (an ordinary
 * Android device, an emulator without the Pleos system image) the part degrades to the behaviour a
 * [FakeVehicleGateway] has and one warning is logged. Nothing here may crash the app.
 *
 * Measured behaviour this relies on (docs/policy/maltong-dev-spec.md 2.4): TTS needs Locale.KOREA, the TTS event
 * listener must be added after initialize succeeds, onRouteEnded arrives right after onRouteStarted and is ignored.
 */
class PleosVehicleGateway(
    private val context: Context,
    private val scope: CoroutineScope,
    now: () -> Long = { System.currentTimeMillis() },
) : VehicleGateway {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    private val _driving = MutableStateFlow(DrivingState.PARKED)
    private val _gear = MutableStateFlow(VehicleGear.Unknown)
    private val _doors = MutableStateFlow<Set<VehicleDoor>>(emptySet())
    private val _events = MutableSharedFlow<VehicleEvent>(extraBufferCapacity = 64)
    private var settings = SpeechSettings()

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

    // ---- navigation
    @Volatile private var navi: NaviHelper? = null
    @Volatile private var naviReady = false
    private var pendingRoute: CompletableDeferred<RouteResult>? = null
    private var pendingTrip: CompletableDeferred<TripInfo?>? = null

    private val naviListener = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            // `duration` is seconds, `distance` metres, `tollFare` won (probe: tollFare=700).
            pendingRoute?.complete(
                RouteResult.Started(RouteInfo(routeStartInfo.duration, routeStartInfo.distance, routeStartInfo.tollFare)),
            )
        }

        // onRouteEnded fires right after onRouteStarted: deliberately not overridden.

        override fun onError(errorCode: NaviErrorCode) {
            pendingRoute?.complete(RouteResult.Failed(errorCode.name))
            pendingTrip?.complete(null)
        }

        override fun onTripInfo(tripInfo: PleosTripInfo) {
            pendingTrip?.complete(TripInfo(etaSec = tripInfo.eta, distanceM = tripInfo.distance))
        }

        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
            _events.tryEmit(VehicleEvent.Arrived)
        }
    }

    // ---- android.car driving state
    private var car: Any? = null
    private var vehicle: Vehicle? = null

    init {
        guard("driving state") { CarUx.start(app, main) { _driving.value = if (it) DrivingState.DRIVING else DrivingState.PARKED } }
        guard("navigation") { connectNavi() }
        guard("vehicle signals") { connectVehicle() }
    }

    private inline fun guard(what: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            HeyLog.w(TAG, "$what unavailable, using fake behaviour: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private fun connectNavi() {
        val helper = NaviHelper(app)
        helper.addListener(naviListener)
        helper.initialize(
            { naviReady = true },
            { e -> HeyLog.w(TAG, "navigation service not connected, routes fall back to fake: ${e.message}") },
        )
        navi = helper
    }

    private fun connectVehicle() {
        val v = Vehicle(app)
        v.initialize()
        vehicle = v
        // Optional signals: a failure here is ignored, nothing in the product depends on them.
        scope.launchSafely("gear") {
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
            scope.launchSafely("door $door") {
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

    private fun CoroutineScope.launchSafely(what: String, block: () -> Unit) {
        guard(what, block)
    }

    override fun speak(textKo: String, priority: SpeechPriority, speechId: String?) = queue.enqueue(textKo, priority, speechId)

    override fun replay(textKo: String) = queue.enqueue(textKo, SpeechPriority.High, null, force = true)

    override fun configureSpeech(settings: SpeechSettings) {
        this.settings = settings
    }

    override suspend fun startRoute(place: Place): RouteResult {
        val helper = navi
        if (helper == null || !naviReady) {
            HeyLog.w(TAG, "startRoute without navigation service: answering with a fake route")
            return RouteResult.Started(RouteInfo(durationSec = 0, distanceM = 0, tollFare = 0))
        }
        val result = CompletableDeferred<RouteResult>()
        pendingRoute = result
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
                    poiSubId = "0",
                    routeOption = RouteOption.RECOMMENDED,
                    poiProvider = "hey",
                    isMobileRequest = false,
                ),
            )
        } catch (t: Throwable) {
            pendingRoute = null
            HeyLog.w(TAG, "requestRoute threw ${t.javaClass.simpleName}: ${t.message}")
            return RouteResult.Failed("NAVI_ERROR")
        }
        val outcome = withTimeoutOrNull(ROUTE_TIMEOUT_MS) { result.await() } ?: RouteResult.Failed("TIMEOUT")
        pendingRoute = null
        return outcome
    }

    override suspend fun requestTrip(place: Place): TripInfo? {
        val helper = navi
        if (helper == null || !naviReady) return null
        val result = CompletableDeferred<TripInfo?>()
        pendingTrip = result
        try {
            helper.requestTrip(TripRequest(place.lat, place.lng, place.placeId, place.nameKo, "0"))
        } catch (t: Throwable) {
            pendingTrip = null
            HeyLog.w(TAG, "requestTrip threw ${t.javaClass.simpleName}: ${t.message}")
            return null
        }
        val outcome = withTimeoutOrNull(TRIP_TIMEOUT_MS) { result.await() }
        pendingTrip = null
        return outcome
    }

    override fun release() {
        guard("release") { navi?.removeListener(naviListener); navi?.release() }
        guard("release vehicle") { vehicle?.release() }
        guard("release tts") { ttsEngine.release() }
        guard("release car") { CarUx.stop() }
    }
}

/**
 * On-device Korean TTS. Init is lazy and retried on the next utterance; when the engine cannot start the text is
 * logged and counted as read, so a request never stays "received" because the speaker is missing.
 */
internal class PleosSpeechEngine(private val context: Context) : SpeechEngine {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var failedInit = false
    private var current: CompletableDeferred<Boolean>? = null

    private val events = object : EventListener {
        override fun onStart() = Unit
        override fun onReady() = Unit
        override fun onDone() { current?.complete(true) }
        override fun onError(e: ErrorType) {
            HeyLog.w(TAG, "tts error $e")
            current?.complete(false)
        }
        override fun onStop() { current?.complete(false) }
        override fun onUpdatedRms(rms: Double) = Unit
    }

    private suspend fun ensureReady(): Boolean {
        if (ready) return true
        if (failedInit) return false
        val init = CompletableDeferred<Boolean>()
        try {
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
            HeyLog.w(TAG, "tts unavailable, speech is logged only: ${t.javaClass.simpleName}: ${t.message}")
            failedInit = true
            return false
        }
        ready = withTimeoutOrNull(SPEECH_TIMEOUT_MS) { init.await() } ?: false
        if (!ready) failedInit = true
        return ready
    }

    override suspend fun speak(text: String, rate: SpeechRate): Boolean {
        if (!ensureReady()) {
            HeyLog.w(TAG, "speech without engine, treated as read")
            return true
        }
        val done = CompletableDeferred<Boolean>()
        current = done
        try {
            tts?.speak(text, true)
        } catch (t: Throwable) {
            HeyLog.w(TAG, "tts speak threw ${t.javaClass.simpleName}: ${t.message}")
            current = null
            return false
        }
        val ok = withTimeoutOrNull(SPEECH_TIMEOUT_MS) { done.await() } ?: false
        current = null
        return ok
    }

    fun release() {
        tts?.removeEventListener(events)
        tts?.release()
        tts = null
        ready = false
    }
}
