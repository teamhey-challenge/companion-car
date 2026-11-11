package ai.hey.session

import ai.hey.model.CarCardView
import ai.hey.model.CarSettings
import ai.hey.model.CarView
import ai.hey.model.ClientType
import ai.hey.model.ConditionId
import ai.hey.model.DrivingState
import ai.hey.model.DrivingStateData
import ai.hey.model.DropoffBy
import ai.hey.model.DropoffCandidate
import ai.hey.model.DropoffDecideData
import ai.hey.model.DropoffProposeData
import ai.hey.model.DropoffReasonId
import ai.hey.model.DriverDecision
import ai.hey.model.EmptyData
import ai.hey.model.HeyConfig
import ai.hey.model.HeyIds
import ai.hey.model.HeyJson
import ai.hey.model.HeyLog
import ai.hey.model.JoinDecision
import ai.hey.model.LatLng
import ai.hey.model.ProgressInfo
import ai.hey.model.QuickReplyId
import ai.hey.model.ReplySendData
import ai.hey.model.RequestSpokenData
import ai.hey.model.RouteFailedData
import ai.hey.model.ServerMessage
import ai.hey.model.TripState
import ai.hey.model.actionableCount
import ai.hey.model.routeDestination
import ai.hey.vehicle.RouteResult
import ai.hey.vehicle.SpeechSettings
import ai.hey.vehicle.VehicleEvent
import ai.hey.vehicle.VehicleGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "TripRepository"

/**
 * Owns the relay connection and the vehicle and exposes ONE state. Every screen reads [state]; every user action
 * is a function of [CarCommands]. While the car is moving, commands that need the driver's hands are ignored here as
 * well as being absent from the screen, so a late tap cannot slip through.
 */
class TripRepository(
    private val scope: CoroutineScope,
    private val vehicle: VehicleGateway,
    private val storage: CarStorage,
    private val http: OkHttpClient = newHttpClient(),
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val progressPollMs: Long = HeyConfig.TRIP_PROGRESS_POLL_SEC * 1000L,
    private val backoffSec: List<Long> = HeyConfig.RECONNECT_BACKOFF_SEC,
    private val ackTimeoutMs: Long = HeyConfig.WS_ACK_TIMEOUT_MS,
) : CarCommands {
    private val _state = MutableStateFlow(CarUiState(driving = vehicle.drivingState.value))
    val state: StateFlow<CarUiState> = _state.asStateFlow()

    private val link = RelayLink(http, scope, LinkListener(), ackTimeoutMs, backoffSec, clock)

    @Volatile private var registration: Registration? = null

    /** Texts waiting to be read, by speech id, so "방금 읽은 요청" can show the text once it was read. */
    private val spokenTexts = ConcurrentHashMap<String, String>()

    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            // Settings drive speech and the relay address.
            storage.settings.collect { s ->
                _state.update { it.copy(settings = s) }
                vehicle.configureSpeech(SpeechSettings(gapSec = s.speechGapSec, rate = s.speechRate))
            }
        }
        scope.launch {
            storage.settings.map { it.relayUrl }.distinctUntilChanged().collectLatest { connectTo(it) }
        }
        scope.launch {
            vehicle.drivingState.collect { d ->
                _state.update { it.copy(driving = d, endDialogOpen = if (d == DrivingState.DRIVING) false else it.endDialogOpen) }
                sendDriving(d)
            }
        }
        scope.launch { vehicle.events.collect { onVehicleEvent(it) } }
        scope.launch { progressLoop() }
    }

    fun stop() {
        link.close()
        started = false
    }

    // ---------------------------------------------------------------- connection

    private suspend fun connectTo(relayUrl: String) {
        _state.update { it.copy(connection = ConnectionStatus.Registering) }
        var reg = storage.loadRegistration()?.takeIf { it.relayUrl == relayUrl }
        var failures = 0
        while (reg == null) {
            val plate = storage.settings.value.plateSuffix
            reg = try {
                val created = registerCar(http, relayUrl, plate)
                Registration(created.carCode, created.driverToken, created.qrUrl, relayUrl, plate).also { storage.saveRegistration(it) }
            } catch (e: Exception) {
                HeyLog.w(TAG, "registration failed: ${e.javaClass.simpleName}")
                _state.update { it.copy(connection = ConnectionStatus.RegistrationFailed) }
                delay(backoffSec[minOf(failures, backoffSec.lastIndex)] * 1000)
                failures++
                null
            }
        }
        registration = reg
        _state.update { it.copy(connection = ConnectionStatus.Connecting, qrUrl = reg.qrUrl, view = null) }
        link.connect(relayUrl, reg.driverToken)
    }

    private inner class LinkListener : RelayLink.Listener {
        override fun onConnection(connected: Boolean) {
            if (connected) {
                _state.update { it.copy(connection = ConnectionStatus.Online) }
                sendDriving(_state.value.driving)
            } else {
                _state.update { it.copy(connection = ConnectionStatus.Reconnecting, busy = emptySet()) }
            }
        }

        override fun onMessage(message: ServerMessage) {
            when (message) {
                is ServerMessage.Snapshot -> onSnapshot(message.view)
                is ServerMessage.Say -> {
                    val id = message.data.speechId.takeIf(HeyIds::isRequestSpeech)
                    if (id != null) spokenTexts[id] = message.data.textKo
                    vehicle.speak(message.data.textKo, message.data.priority, id)
                }
                is ServerMessage.Error -> _state.update { it.copy(error = message.data.code, busy = emptySet()) }
                is ServerMessage.Notice, is ServerMessage.Ack -> Unit
            }
        }
    }

    private fun sendDriving(driving: DrivingState) {
        link.send(ClientType.DRIVING_STATE, HeyJson.encodeToJsonElement(DrivingStateData.serializer(), DrivingStateData(driving == DrivingState.DRIVING)), "driving")
    }

    // ---------------------------------------------------------------- snapshots

    private fun onSnapshot(view: CarView) {
        _state.update { old ->
            val previous = old.view
            val changed = if (previous?.tripId == view.tripId) changedConditions(previous?.card, view.card, old.changedConditions) else emptySet()
            val tripEnded = view.state == TripState.IDLE
            old.copy(
                view = view,
                busy = emptySet(),
                changedConditions = changed,
                // a new/other proposal makes the typed selection obsolete (activeDraft), the panel stays as the driver left it
                panel = if (tripEnded && old.panel == Panel.Dropoff) Panel.None else old.panel,
                requestsDismissedAt = minOf(old.requestsDismissedAt, view.actionableCount),
                routeStarting = false,
                routeFailed = if (view.route != null) false else old.routeFailed && view.card != null,
                lastSpokenKo = if (tripEnded) null else old.lastSpokenKo,
                endDialogOpen = if (tripEnded) false else old.endDialogOpen,
                candidates = if (view.card?.destination?.placeId != old.candidates.forPlaceId) Candidates() else old.candidates,
                progressAt = if (view.progress != previous?.progress) clock() else old.progressAt,
                error = old.error,
            )
        }
    }

    private fun changedConditions(old: CarCardView?, new: CarCardView?, keep: Set<ConditionId>): Set<ConditionId> {
        if (new == null) return emptySet()
        if (old == null || old == new) return keep
        val out = LinkedHashSet<ConditionId>()
        new.conditions.filterTo(out) { it !in old.conditions }
        if (old.entranceNote != new.entranceNote && ConditionId.MeetEntrance in new.conditions) out += ConditionId.MeetEntrance
        if (old.temperature != new.temperature && ConditionId.Temperature in new.conditions) out += ConditionId.Temperature
        return out
    }

    // ---------------------------------------------------------------- vehicle events

    private fun onVehicleEvent(event: VehicleEvent) {
        when (event) {
            is VehicleEvent.SpeechDone -> {
                val text = spokenTexts.remove(event.speechId)
                if (text != null) _state.update { it.copy(lastSpokenKo = text) }
                markSpoken(event.speechId)
            }
            is VehicleEvent.SpeechDropped -> spokenTexts.remove(event.speechId)
            VehicleEvent.Arrived -> if (_state.value.view?.state == TripState.IN_TRIP) arrive(force = true)
        }
    }

    private suspend fun progressLoop() {
        _state.map { s -> s.view?.takeIf { it.state == TripState.IN_TRIP && it.route != null }?.routeDestination() }
            .distinctUntilChanged()
            .collectLatest { destination ->
                if (destination == null) return@collectLatest
                while (true) {
                    delay(progressPollMs)
                    val info = vehicle.requestTrip(destination)
                    val current = _state.value.view?.progress
                    val next = when {
                        info != null -> ProgressInfo(info.etaSec, info.distanceM, stale = false)
                        current != null && !current.stale -> current.copy(stale = true)
                        else -> null
                    }
                    if (next != null) link.send(ClientType.TRIP_PROGRESS, HeyJson.encodeToJsonElement(ProgressInfo.serializer(), next), "progress")
                }
            }
    }

    // ---------------------------------------------------------------- commands

    private fun isParked(): Boolean = _state.value.driving == DrivingState.PARKED

    private fun sendCommand(type: String, data: JsonElement, busy: String? = null) {
        if (busy != null) _state.update { it.copy(busy = it.busy + busy) }
        link.send(type, data)
    }

    private val empty: JsonElement get() = HeyJson.encodeToJsonElement(EmptyData.serializer(), EmptyData())

    override fun approveJoin(joinRequestId: String) {
        if (!isParked()) return
        if (Busy.JOIN in _state.value.busy) return
        sendCommand(ClientType.JOIN_APPROVE, HeyJson.encodeToJsonElement(JoinDecision.serializer(), JoinDecision(joinRequestId)), Busy.JOIN)
    }

    override fun declineJoin(joinRequestId: String) {
        if (!isParked()) return
        if (Busy.JOIN in _state.value.busy) return
        sendCommand(ClientType.JOIN_DECLINE, HeyJson.encodeToJsonElement(JoinDecision.serializer(), JoinDecision(joinRequestId)), Busy.JOIN)
    }

    override fun recheckCard() {
        if (!isParked()) return
        if (Busy.RECHECK in _state.value.busy) return
        sendCommand(ClientType.CARD_RECHECK, empty, Busy.RECHECK)
    }

    override fun startRoute() {
        if (!isParked()) return
        val s = _state.value
        val destination = s.view?.routeDestination() ?: return
        if (s.routeStarting) return
        _state.update { it.copy(routeStarting = true, routeFailed = false) }
        scope.launch {
            val result = vehicle.startRoute(destination)
            when (result) {
                is RouteResult.Started -> link.send(ClientType.ROUTE_STARTED, HeyJson.encodeToJsonElement(ai.hey.model.RouteInfo.serializer(), result.route))
                is RouteResult.Failed -> {
                    link.send(ClientType.ROUTE_FAILED, HeyJson.encodeToJsonElement(RouteFailedData.serializer(), RouteFailedData(result.code)))
                    _state.update { it.copy(routeStarting = false, routeFailed = true) }
                }
            }
            // On success the next snapshot (state IN_TRIP, route set) clears routeStarting; guard against a lost one.
            delay(ROUTE_FLAG_GUARD_MS)
            _state.update { if (it.routeStarting) it.copy(routeStarting = false) else it }
        }
    }

    override fun proposeDropoff(point: LatLng, label: String, reasonId: DropoffReasonId?) {
        if (!isParked()) return
        if (Busy.PROPOSE in _state.value.busy) return
        sendCommand(
            ClientType.DROPOFF_PROPOSE,
            HeyJson.encodeToJsonElement(DropoffProposeData.serializer(), DropoffProposeData(point, label, reasonId)),
            Busy.PROPOSE,
        )
    }

    override fun decidePin(version: Int, accept: Boolean) {
        if (!isParked()) return
        if (Busy.pin(version) in _state.value.busy) return
        val decision = if (accept) DriverDecision.Accept else DriverDecision.Decline
        sendCommand(
            ClientType.DROPOFF_DECIDE,
            HeyJson.encodeToJsonElement(DropoffDecideData.serializer(), DropoffDecideData(version, decision)),
            Busy.pin(version),
        )
    }

    override fun markSpoken(requestId: String) {
        link.send(ClientType.REQUEST_SPOKEN, HeyJson.encodeToJsonElement(RequestSpokenData.serializer(), RequestSpokenData(requestId)))
    }

    override fun sendReply(requestId: String?, replyId: QuickReplyId) {
        if (!isParked()) return
        val key = Busy.reply(requestId, replyId.name)
        if (_state.value.busy.any { it.startsWith("reply:") }) return
        sendCommand(
            ClientType.REPLY_SEND,
            HeyJson.encodeToJsonElement(ReplySendData.serializer(), ReplySendData(requestId, replyId)),
            key,
        )
    }

    override fun arrive() = arrive(force = false)

    private fun arrive(force: Boolean) {
        if (!force && _state.value.driving != DrivingState.PARKED) return
        if (Busy.ARRIVE in _state.value.busy) return
        sendCommand(ClientType.TRIP_ARRIVE, empty, if (force) null else Busy.ARRIVE)
    }

    override fun endTrip() {
        if (!isParked()) return
        if (Busy.END in _state.value.busy) return
        _state.update { it.copy(endDialogOpen = false) }
        sendCommand(ClientType.TRIP_END, empty, Busy.END)
    }

    override fun replay(textKo: String) = vehicle.replay(textKo)

    // ---------------------------------------------------------------- local UI state

    override fun openDropoff() {
        if (!isParked()) return
        _state.update { it.copy(panel = Panel.Dropoff) }
        loadCandidates()
    }

    override fun closeDropoff() = _state.update { it.copy(panel = Panel.None) }

    override fun selectCandidate(candidateId: String) = _state.update {
        val d = it.activeDraft
        it.copy(draft = d.copy(candidateId = candidateId))
    }

    override fun selectReason(index: Int) = _state.update {
        val d = it.activeDraft
        it.copy(draft = d.copy(reasonIndex = index))
    }

    override fun dismissRequests() = _state.update { it.copy(requestsDismissedAt = it.view?.actionableCount ?: 0) }

    override fun openSettings() {
        if (!isParked()) return
        _state.update { it.copy(panel = Panel.Settings) }
    }

    override fun closeSettings() = _state.update { it.copy(panel = Panel.None) }

    override fun openEndDialog() {
        if (!isParked()) return
        _state.update { it.copy(endDialogOpen = true) }
    }

    override fun closeEndDialog() = _state.update { it.copy(endDialogOpen = false) }

    override fun dismissError() = _state.update { it.copy(error = null) }

    override fun updateSettings(transform: (CarSettings) -> CarSettings) {
        scope.launch { storage.updateSettings(transform) }
    }

    // ---------------------------------------------------------------- entrances

    private fun loadCandidates() {
        val place = _state.value.view?.card?.destination ?: return
        val reg = registration ?: return
        val current = _state.value.candidates
        if (current.forPlaceId == place.placeId && (current.loading || current.items.isNotEmpty())) return
        _state.update { it.copy(candidates = Candidates(forPlaceId = place.placeId, loading = true)) }
        scope.launch {
            val result = try {
                withContext(Dispatchers.IO) { fetchEntrances(reg, place.placeId) }
            } catch (e: Exception) {
                HeyLog.w(TAG, "entrances failed: ${e.javaClass.simpleName}")
                null
            }
            _state.update {
                if (it.candidates.forPlaceId != place.placeId) it
                else it.copy(candidates = Candidates(place.placeId, result.orEmpty(), loading = false, failed = result == null))
            }
        }
    }

    private fun fetchEntrances(reg: Registration, placeId: String): List<DropoffCandidate> {
        val url = reg.relayUrl.toHttpUrl().newBuilder().addPathSegments("v1/places").addPathSegment(placeId).addPathSegment("entrances").build()
        val request = Request.Builder().url(url).header("Authorization", "Driver ${reg.driverToken}").build()
        http.newCall(request).execute().use { res ->
            if (!res.isSuccessful) throw java.io.IOException("HTTP ${res.code}")
            return HeyJson.decodeFromString(ListSerializer(DropoffCandidate.serializer()), res.body?.string().orEmpty())
        }
    }

    private companion object {
        const val ROUTE_FLAG_GUARD_MS = 15_000L
    }
}
