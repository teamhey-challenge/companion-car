package ai.hey.vehicle

import ai.hey.model.HeyConfig
import ai.hey.model.VehicleSignals
import ai.hey.model.HeyLog
import ai.hey.model.Place
import ai.hey.model.SpeechPriority
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NaviMode
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.TripInfo as PleosTripInfo
import ai.pleos.playground.navi.data.TripRequest
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.RangeRemainingListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear as PleosGear
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "PleosVehicle"
private const val TRIP_TIMEOUT_MS = 5_000L
private const val SPEECH_TIMEOUT_MS = 30_000L
private const val TTS_RETRY_BACKOFF_MS = 5_000L
private const val CHARGE_POLL_MS = 10_000L
private val AUDIO_ATTRIBUTES = AudioTrackSink.ASSISTANT

/**
 * Pleos SDK + android.car behind [VehicleGateway]. Every SDK touch is wrapped so a missing service never crashes the
 * app. A missing service is never reported as success: no navigation means [VehicleEvent.NavigationUnavailable], no speech engine
 * means the text is *not* heard ([VehicleEvent.SpeechDropped]), and no car service means the car is treated as driving.
 *
 * Measured behaviour this relies on (docs/policy/maltong-dev-spec.md 2.4): TTS needs Locale.KOREA, the TTS event
 * listener must be added after initialize succeeds.
 * The Pleos TTS API has no speaking-rate setter, so there is no rate setting in the app.
 */
class PleosVehicleGateway(
    private val context: Context,
    private val scope: CoroutineScope,
    now: () -> Long = { System.currentTimeMillis() },
) : VehicleGateway {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    // Fails closed: Unknown (treated as Driving) until android.car says Parked.
    private val _driving = MutableStateFlow<DrivingStatus>(DrivingStatus.Unknown)
    private val _gear = MutableStateFlow(VehicleGear.Unknown)
    private val _doors = MutableStateFlow<Set<VehicleDoor>>(emptySet())
    private val _signals = MutableStateFlow(VehicleSignals())
    private val _events = MutableSharedFlow<VehicleEvent>(extraBufferCapacity = 64)
    private val _mouth = MutableStateFlow(0)

    @Volatile private var settings = SpeechSettings()

    override val drivingState: StateFlow<DrivingStatus> = _driving.asStateFlow()
    override val gear: StateFlow<VehicleGear> = _gear.asStateFlow()
    override val doors: StateFlow<Set<VehicleDoor>> = _doors.asStateFlow()
    override val signals: StateFlow<VehicleSignals> = _signals.asStateFlow()

    private fun signal(change: (VehicleSignals) -> VehicleSignals) = _signals.update(change)
    override val events: SharedFlow<VehicleEvent> = _events.asSharedFlow()
    override val mouth: StateFlow<Int> = _mouth.asStateFlow()

    private val ttsEngine = PleosSpeechEngine(app) { _mouth.value = it }
    private val queue = SpeechQueue(
        scope = scope,
        engine = ttsEngine,
        settings = { settings },
        now = now,
        emit = { _events.tryEmit(it) },
        dedupWindowMs = HeyConfig.SPEECH_DEDUP_WINDOW_SEC * 1000L,
        queueMax = HeyConfig.SPEECH_QUEUE_MAX,
    )

    // ---- navigation: only trip estimates remain (one outstanding call); the HEY route-start flow was dropped
    @Volatile private var navi: NaviHelper? = null
    @Volatile private var naviBound = false
    private val tripMutex = Mutex()
    private val pendingTrip = AtomicReference<CompletableDeferred<TripInfo?>?>(null)

    private val guidance = NaviGuidance { g ->
        signal { it.copy(naviGuiding = g, destKm = if (g) it.destKm else null, destinationName = if (g || it.arrived) it.destinationName else null) }
    }

    private val naviListener = object : NaviHelperEventListener {

        override fun onError(errorCode: NaviErrorCode) {
            if (errorCode == NaviErrorCode.CAUSE_SEARCH_TRIP) {
                pendingTrip.getAndSet(null)?.complete(null)
            } else {
                _events.tryEmit(VehicleEvent.NavigationUnavailable(errorCode.name))
            }
        }

        override fun onTripInfo(tripInfo: PleosTripInfo) {
            pendingTrip.getAndSet(null)?.complete(TripInfo(etaSec = tripInfo.eta, distanceM = tripInfo.distance))
        }

        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
            // Set before the route stops so the destination name survives for this one report.
            signal { it.copy(arrived = true) }
            guidance.routeStopped("onDestinationArrived")
            _events.tryEmit(VehicleEvent.Arrived)
        }

        override fun onRouteStarted(info: ai.pleos.playground.navi.data.RouteStartInfo) {
            signal { it.copy(arrived = false) }
            guidance.routeStarted()
            // The callback alone proves nothing: ask whether a destination is really set.
            guard("destination query") { navi?.getDestinationInfo() }
        }

        override fun onDestinationInfo(destinationInfo: ai.pleos.playground.navi.data.DestinationInfo) {
            val present = destinationInfo.name.isNotBlank() || destinationInfo.address.isNotBlank() ||
                destinationInfo.latitude != 0.0 || destinationInfo.longitude != 0.0
            guidance.destination(present)
            // Meters left to the destination, as whole km for the range comparison (scenario 2); no destination = no key.
            val km = destinationInfo.remainDistance.toLong().takeIf { present && it > 0 }?.let { ((it + 500) / 1000).toInt() }
            signal { it.copy(destKm = km, destinationName = destinationInfo.name.takeIf { n -> present && n.isNotBlank() }) }
        }

        override fun onRouteStateInfo(routeStateInfo: ai.pleos.playground.navi.data.RouteStateInfo) {
            // RouteStateInfo.isRouting is not the signal for guidance; the navi mode is.
            val guiding = routeStateInfo.naviMode == NaviMode.ROUTE_GUIDING || routeStateInfo.naviMode == NaviMode.SIMULATION_GUIDING
            guidance.routing(guiding)
            if (guiding) guard("destination query") { navi?.getDestinationInfo() }
        }

        override fun onRouteEnded() = guidance.routeStopped("onRouteEnded")

        override fun onRouteCancelled() = guidance.routeStopped("onRouteCancelled")

        override fun onNavigationStatus(status: NavigationStatus) {
            guidance.status(status == NavigationStatus.ROUTE_DRIVE)
            if (status == NavigationStatus.ROUTE_DRIVE) guard("destination query") { navi?.getDestinationInfo() }
        }
    }

    private var vehicle: Vehicle? = null

    init {
        guard("driving state") {
            CarUx.start(
                app,
                main,
                onDriving = { _driving.value = if (it) DrivingStatus.Driving else DrivingStatus.Parked },
                onLost = { _driving.value = DrivingStatus.Unknown },
                onReady = { guard("car properties") { watchCarProperties() } },
            )
        }
        guard("navigation") { connectNavi() }
        guard("vehicle signals") { connectVehicle() }
    }

    private fun watchCarProperties() {
        CarUx.watchProperties(
            onFuelType = { v -> signal { it.copy(fuelType = fuelName(v)) } },
            onSpeedMps = { v -> signal { it.copy(speedKmh = Math.round(v * 3.6f).coerceAtLeast(0)) } },
        )
    }

    /** AAOS FuelType: 10 electric, 8 LPG, 3/4 diesel, 9 hydrogen, anything else gasoline. */
    private fun fuelName(values: IntArray): String? = values.firstOrNull()?.let {
        when (it) { 10 -> "electric"; 8 -> "lpg"; 3, 4 -> "diesel"; 9 -> "hydrogen"; else -> "gasoline" }
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
            { naviBound = true },
            { e ->
                HeyLog.w(TAG, "navigation service not connected: ${e.message}")
                _events.tryEmit(VehicleEvent.NavigationUnavailable("NAVI_INIT_FAILED"))
            },
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
                    signal { s -> s.copy(gear = when (_gear.value) { VehicleGear.Park -> "P"; VehicleGear.Reverse -> "R"; VehicleGear.Neutral -> "N"; VehicleGear.Drive -> "D"; VehicleGear.Unknown -> null }) }
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
                            signal {
                                it.copy(
                                    doorOpen = _doors.value.isNotEmpty(),
                                    driverDoorOpen = VehicleDoor.DriverRow1 in _doors.value,
                                )
                            }
                        }

                        override fun onFailed(e: Exception) = HeyLog.d(TAG, "door listener failed: ${e.message}")
                    },
                )
            }
        }
        guard("wiper") {
            v.getWiper().registerWiperState(
                WindowArea(WindowValue.FRONT_WINDSHIELD),
                object : WiperStateListener {
                    // The state API only knows OFF/ON/SERVICE: ON is sent as "low" (the speed is not readable).
                    override fun onWiperStateUpdated(state: WiperState?) = signal { it.copy(wiper = if (state == WiperState.ON) "low" else "off") }

                    override fun onFailed(e: Exception) = HeyLog.d(TAG, "wiper listener failed: ${e.message}")
                },
            )
        }
        guard("headlight") {
            v.getLight().registerLowBeamLightState(
                object : LightStateListener {
                    override fun onLightStateUpdated(state: LightState?) = signal { it.copy(headlight = state == LightState.ON) }

                    override fun onFailed(e: Exception) = HeyLog.d(TAG, "headlight listener failed: ${e.message}")
                },
            )
        }
        guard("range") {
            @Suppress("DEPRECATION")
            v.getOdometer().registerRangeRemainingListener(
                object : RangeRemainingListener {
                    override fun onRangeRemainingChanged(rangeRemaining: Float?) = signal { it.copy(rangeKm = rangeRemaining?.let { m -> (m / 1000f).toInt() }) }

                    override fun onFailed(e: Exception) = HeyLog.d(TAG, "range listener failed: ${e.message}")
                },
            )
        }
        guard("charge") {
            // No listener exists for the charge switch in the SDK: poll it. The emulator Virtual Console toggles it.
            val battery = v.getEvBattery()
            scope.launch {
                while (true) {
                    guard("charge read") {
                        battery.getEvChargeSwitchState(
                            { on -> signal { it.copy(charging = on) } },
                            { e -> HeyLog.d(TAG, "charge read failed: ${e.message}") },
                        )
                    }
                    kotlinx.coroutines.delay(CHARGE_POLL_MS)
                }
            }
        }
    }

    override fun speak(textKo: String, priority: SpeechPriority, speechId: String?) = queue.enqueue(textKo, priority, speechId)

    override fun replay(textKo: String, speechId: String?) = queue.enqueue(textKo, SpeechPriority.High, speechId, force = true)

    override fun configureSpeech(settings: SpeechSettings) {
        this.settings = settings
    }

    override fun stopSpeech() = queue.stopAll()

    override fun warmUpSpeech() {
        scope.launch { ttsEngine.warmUp() }
    }

    override suspend fun requestTrip(place: Place): TripInfo? = tripMutex.withLock {
        val helper = navi
        if (helper == null || !naviBound) return@withLock null
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

    override fun retrySpeech() {
        ttsEngine.reset()
    }

    override fun retryNavigation() {
        guard("retry navigation") {
            navi?.removeListener(naviListener)
            navi?.release()
            navi = null
            naviBound = false
            connectNavi()
        }
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
internal class PleosSpeechEngine(
    private val context: Context,
    private val thresholds: MouthThresholds = MouthThresholds.PLEOS,
    private val onMouth: (Int) -> Unit = {},
) : SpeechEngine {
    @Volatile private var tts: TextToSpeech? = null
    @Volatile private var ready = false
    @Volatile private var retryAfter = 0L
    private val current = AtomicReference<CompletableDeferred<Boolean>?>(null)

    private val events = object : EventListener {
        override fun onStart() = Unit
        override fun onReady() = Unit
        override fun onDone() { onMouth(0); current.get()?.complete(true) }
        override fun onError(e: ErrorType) {
            HeyLog.w(TAG, "tts error $e")
            onMouth(0)
            current.get()?.complete(false)
        }
        override fun onStop() { onMouth(0); current.get()?.complete(false) }

        // The mouth follows the engine's own loudness callback. The raw value is logged at a low rate so the thresholds can be tuned.
        override fun onUpdatedRms(rms: Double) {
            val level = thresholds.level(rms)
            if (level != lastLevel) {
                lastLevel = level
                HeyLog.d(TAG, "pleos rms=${"%.4f".format(rms)} mouth=$level")
                onMouth(level)
            }
        }
    }

    @Volatile private var lastLevel = 0

    /** Opens the engine now (app start) so the first reply does not wait for the initialisation (Go/No-go 8.2). */
    suspend fun warmUp() { ensureReady() }

    /** Cuts what is being read; the pending [speak] ends as not delivered. */
    override fun stop() {
        try { tts?.stop() } catch (t: Throwable) { HeyLog.w(TAG, "tts stop threw ${t.javaClass.simpleName}") }
        current.get()?.complete(false)
        onMouth(0)
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
                    // The listener must be added after initialize succeeded.
                    try {
                        engine.addEventListener(events)
                        engine.setLocale(Locale.KOREA)
                        try { engine.setAudioAttributes(AUDIO_ATTRIBUTES) } catch (t: Throwable) { HeyLog.w(TAG, "tts audio attributes not set: ${t.javaClass.simpleName}") }
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

    override suspend fun speak(text: String): SpeechResult {
        if (!ensureReady()) {
            HeyLog.w(TAG, "speech engine not available, text not read")
            return SpeechResult.Dropped(SpeechDropReason.EngineUnavailable)
        }
        val done = CompletableDeferred<Boolean>()
        current.set(done)
        try {
            tts?.speak(text, true)
        } catch (t: Throwable) {
            HeyLog.w(TAG, "tts speak threw ${t.javaClass.simpleName}: ${t.message}")
            current.compareAndSet(done, null)
            return SpeechResult.Dropped(SpeechDropReason.EngineError)
        }
        val ok = withTimeoutOrNull(SPEECH_TIMEOUT_MS) { done.await() } ?: false
        current.compareAndSet(done, null)
        return if (ok) SpeechResult.Delivered else SpeechResult.Dropped(SpeechDropReason.EngineError)
    }

    /** D1 retry: forget the backoff and drop the engine, so the next utterance initialises it again. */
    fun reset() {
        tts?.removeEventListener(events)
        tts?.release()
        tts = null
        ready = false
        retryAfter = 0
    }

    fun release() {
        tts?.removeEventListener(events)
        tts?.release()
        tts = null
        ready = false
    }
}
