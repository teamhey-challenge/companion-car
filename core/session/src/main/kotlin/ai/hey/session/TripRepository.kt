package ai.hey.session

import ai.hey.model.CarCardView
import ai.hey.model.CarSettings
import ai.hey.model.CarView
import ai.hey.model.ClientType
import ai.hey.model.ConditionId
import ai.hey.model.DriverDecision
import ai.hey.model.DrivingState
import ai.hey.model.DrivingStateData
import ai.hey.model.DropoffCandidate
import ai.hey.model.DropoffDecideData
import ai.hey.model.DropoffProposeData
import ai.hey.model.DropoffReasonId
import ai.hey.model.EmptyData
import ai.hey.model.HeyConfig
import ai.hey.model.HeyIds
import ai.hey.model.HeyJson
import ai.hey.model.HeyLog
import ai.hey.model.JoinDecision
import ai.hey.model.ProgressInfo
import ai.hey.model.QuickReplyId
import ai.hey.model.ReplySendData
import ai.hey.model.RequestSpokenData
import ai.hey.model.RequestState
import ai.hey.model.RouteFailedData
import ai.hey.model.RouteInfo
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
import kotlinx.coroutines.Job
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
 * is a function of [CarCommands]. While the car is moving (or its state is unknown) commands that need the driver's
 * hands are ignored here as well as being absent from the screen, so a late tap cannot slip through.
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
    private var connectJob: Job? = null

    /** Texts waiting to be read, by speech id, so "방금 읽은 요청" can show the text once it was read. */
    private val spokenTexts = ConcurrentHashMap<String, String>()

    /** Envelope id of a command -> the busy key it set, so a relay error for it can release that key. */
    private val busyByEnvelope = ConcurrentHashMap<String, String>()

    /** Trip whose arrival was already sent: the navigation event and the manual button must not both fire. */
    @Volatile private var arrivalSentFor: String? = null

    private var errorJob: Job? = null
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            // Settings drive speech and the relay address.
            storage.settings.collect { s ->
                _state.update { it.copy(settings = s) }
                vehicle.configureSpeech(SpeechSettings(gapSec = s.speechGapSec))
            }
        }
        scope.launch {
            storage.settings.map { it.relayUrl }.distinctUntilChanged().collect { url -> restartConnection(url) }
        }
        scope.launch {
            vehicle.drivingState.collect { d ->
                _state.update { it.copy(driving = d, endDialogOpen = if (d == DrivingState.PARKED) it.endDialogOpen else false) }
                sendDriving(d)
            }
        }
        scope.launch { vehicle.events.collect { onVehicleEvent(it) } }
        scope.launch { progressLoop() }
    }

    fun stop() {
        connectJob?.cancel()
        link.close()
        started = false
    }

    // ---------------------------------------------------------------- connection

    private fun restartConnection(relayUrl: String) {
        connectJob?.cancel()
        connectJob = scope.launch { connectTo(relayUrl) }
    }

    private suspend fun connectTo(relayUrl: String) {
        link.close()
        _state.update { it.copy(connection = ConnectionStatus.Registering) }
        var reg = storage.loadRegistration(relayUrl)
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
        _state.update { it.copy(connection = ConnectionStatus.Connecting, qrUrl = reg.qrUrl, view = null, busy = emptySet()) }
        link.connect(relayUrl, reg.driverToken)
    }

    private inner class LinkListener : RelayLink.Listener {
        override fun onConnection(connected: Boolean) {
            if (connected) {
                _state.update { it.copy(connection = ConnectionStatus.Online) }
                sendDriving(_state.value.driving)
            } else {
                _state.update { it.copy(connection = ConnectionStatus.Reconnecting) }
            }
        }

        override fun onTokenRejected() {
            _state.update { it.copy(connection = ConnectionStatus.Rejected, view = null, busy = emptySet()) }
        }

        override fun onMessage(message: ServerMessage) {
            when (message) {
                is ServerMessage.Snapshot -> onSnapshot(message.view)
                is ServerMessage.Say -> {
                    val id = message.data.speechId.takeIf(HeyIds::isRequestSpeech)
                    if (id != null) spokenTexts[id] = message.data.textKo
                    vehicle.speak(message.data.textKo, message.data.priority, id)
                }
                is ServerMessage.Error -> onError(message.data.code, message.data.ref)
                is ServerMessage.Notice, is ServerMessage.Ack -> Unit
            }
        }
    }

    private fun onError(code: ai.hey.model.ErrorCode, ref: String?) {
        val key = ref?.let { busyByEnvelope.remove(it) }
        _state.update { it.copy(error = code, busy = if (key != null) it.busy - key else it.busy) }
        errorJob?.cancel()
        errorJob = scope.launch {
            delay(ERROR_SHOWN_MS)
            _state.update { if (it.error == code) it.copy(error = null) else it }
        }
    }

    private fun sendDriving(driving: DrivingState) {
        link.send(ClientType.DRIVING_STATE, HeyJson.encodeToJsonElement(DrivingStateData.serializer(), DrivingStateData(driving == DrivingState.DRIVING)), "driving")
    }

    // ---------------------------------------------------------------- snapshots

    /** True when the snapshot shows the effect of the command that set [key]: only then is the button released. */
    private fun settled(key: String, view: CarView): Boolean {
        val parts = key.split(':')
        return when (parts[0]) {
            "join" -> view.pendingJoin?.joinRequestId != parts.getOrNull(1)
            "recheck" -> view.state != TripState.CARD_SENT
            "propose" -> (view.dropoff?.version ?: 0) > (parts.getOrNull(1)?.toIntOrNull() ?: 0)
            "pin" -> view.pendingPins.none { it.version == parts.getOrNull(1)?.toIntOrNull() }
            "reply" -> {
                val requestId = parts.getOrNull(1).orEmpty()
                val before = parts.getOrNull(3)?.toIntOrNull() ?: 0
                view.replies.size > before || view.requests.firstOrNull { it.requestId == requestId }?.state == RequestState.Answered
            }
            "end" -> view.state == TripState.IDLE || view.state == TripState.ENDED
            "arrive" -> view.state != TripState.IN_TRIP
            else -> true
        }
    }

    private fun onSnapshot(view: CarView) {
        _state.update { old ->
            val previous = old.view
            val changed = if (previous?.tripId == view.tripId) changedConditions(previous?.card, view.card, old.changedConditions) else emptySet()
            val tripEnded = view.state == TripState.IDLE
            if (view.tripId != previous?.tripId) arrivalSentFor = null
            old.copy(
                view = view,
                busy = old.busy.filterNot { settled(it, view) }.toSet(),
                changedConditions = changed,
                // a new/other proposal makes the typed selection obsolete (activeDraft), the panel stays as the driver left it
                panel = if (tripEnded && old.panel == Panel.Dropoff) Panel.None else old.panel,
                requestsDismissedAt = minOf(old.requestsDismissedAt, view.actionableCount),
                routeFailed = if (view.route != null) false else old.routeFailed && view.card != null,
                lastSpokenKo = if (tripEnded) null else old.lastSpokenKo,
                endDialogOpen = if (tripEnded) false else old.endDialogOpen,
                candidates = if (view.card?.destination?.placeId != old.candidates.forPlaceId) Candidates() else old.candidates,
                progressAt = if (view.progress != previous?.progress) clock() else old.progressAt,
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
            // The request stays "received" on the relay and visible on D6; "다시 듣기" there reads it and marks it spoken.
            is VehicleEvent.SpeechDropped -> Unit
            VehicleEvent.Arrived -> if (_state.value.view?.state == TripState.IN_TRIP) sendArrive(busy = false)
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

    /** Sends a command; [busyKey] keeps its button loading until a snapshot shows the effect or the relay refuses it. */
    private fun sendCommand(type: String, data: JsonElement, busyKey: String? = null) {
        if (busyKey != null) {
            _state.update { it.copy(busy = it.busy + busyKey, error = null) }
        }
        val id = link.send(type, data)
        if (busyKey != null) {
            busyByEnvelope[id] = busyKey
            scope.launch {
                // Safety net only: a lost answer must not leave a button spinning for good.
                delay(BUSY_GUARD_MS)
                busyByEnvelope.remove(id)
                _state.update { it.copy(busy = it.busy - busyKey) }
            }
        }
    }

    private val empty: JsonElement get() = HeyJson.encodeToJsonElement(EmptyData.serializer(), EmptyData())

    override fun approveJoin(joinRequestId: String) = decideJoin(ClientType.JOIN_APPROVE, joinRequestId)

    override fun declineJoin(joinRequestId: String) = decideJoin(ClientType.JOIN_DECLINE, joinRequestId)

    private fun decideJoin(type: String, joinRequestId: String) {
        if (!isParked()) return
        if (_state.value.isBusy(Busy.JOIN)) return
        sendCommand(type, HeyJson.encodeToJsonElement(JoinDecision.serializer(), JoinDecision(joinRequestId)), Busy.join(joinRequestId))
    }

    override fun recheckCard() {
        if (!isParked()) return
        if (_state.value.isBusy(Busy.RECHECK)) return
        sendCommand(ClientType.CARD_RECHECK, empty, Busy.RECHECK)
    }

    override fun startRoute() {
        if (!isParked()) return
        val s = _state.value
        val destination = s.view?.routeDestination() ?: return
        if (s.routeStarting) return
        _state.update { it.copy(routeStarting = true, routeFailed = false) }
        scope.launch {
            when (val result = vehicle.startRoute(destination)) {
                is RouteResult.Started -> {
                    link.send(ClientType.ROUTE_STARTED, HeyJson.encodeToJsonElement(RouteInfo.serializer(), result.route))
                    _state.update { it.copy(routeStarting = false) }
                }
                is RouteResult.Failed -> {
                    link.send(ClientType.ROUTE_FAILED, HeyJson.encodeToJsonElement(RouteFailedData.serializer(), RouteFailedData(result.code)))
                    _state.update { it.copy(routeStarting = false, routeFailed = true) }
                }
            }
        }
    }

    override fun proposeDropoff(candidate: DropoffCandidate, reasonId: DropoffReasonId?) {
        if (!isParked()) return
        if (_state.value.isBusy(Busy.PROPOSE)) return
        val version = _state.value.view?.dropoff?.version ?: 0
        sendCommand(
            ClientType.DROPOFF_PROPOSE,
            HeyJson.encodeToJsonElement(DropoffProposeData.serializer(), DropoffProposeData.of(candidate, reasonId)),
            Busy.propose(version),
        )
    }

    override fun decidePin(version: Int, accept: Boolean) {
        if (!isParked()) return
        if (_state.value.isBusy(Busy.pin(version))) return
        val decision = if (accept) DriverDecision.Accept else DriverDecision.Decline
        sendCommand(
            ClientType.DROPOFF_DECIDE,
            HeyJson.encodeToJsonElement(DropoffDecideData.serializer(), DropoffDecideData(version, decision)),
            Busy.pin(version),
        )
    }

    private fun markSpoken(requestId: String) {
        link.send(ClientType.REQUEST_SPOKEN, HeyJson.encodeToJsonElement(RequestSpokenData.serializer(), RequestSpokenData(requestId)))
    }

    override fun sendReply(requestId: String?, replyId: QuickReplyId) {
        if (!isParked()) return
        val s = _state.value
        if (s.isBusy(Busy.REPLY)) return
        sendCommand(
            ClientType.REPLY_SEND,
            HeyJson.encodeToJsonElement(ReplySendData.serializer(), ReplySendData(requestId, replyId)),
            Busy.reply(requestId, replyId.name, s.view?.replies?.size ?: 0),
        )
    }

    override fun arrive() {
        if (!isParked()) return
        if (_state.value.view?.state != TripState.IN_TRIP) return
        sendArrive(busy = true)
    }

    private fun sendArrive(busy: Boolean) {
        val trip = _state.value.view?.tripId ?: return
        if (arrivalSentFor == trip) return
        arrivalSentFor = trip
        sendCommand(ClientType.TRIP_ARRIVE, empty, if (busy) Busy.ARRIVE else null)
    }

    override fun endTrip() {
        if (!isParked()) return
        if (_state.value.isBusy(Busy.END)) return
        _state.update { it.copy(endDialogOpen = false) }
        sendCommand(ClientType.TRIP_END, empty, Busy.END)
    }

    override fun replay(textKo: String) = vehicle.replay(textKo)

    override fun replayRequest(requestId: String) {
        val request = _state.value.view?.requests?.firstOrNull { it.requestId == requestId } ?: return
        // A request that was never heard (queue overflow, speaker down) is marked spoken once this reading finishes.
        vehicle.replay(request.text, if (request.state == RequestState.Received) requestId else null)
    }

    override fun reregister() {
        val url = _state.value.settings.relayUrl
        scope.launch {
            storage.forgetRegistration(url)
            restartConnection(url)
        }
    }

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
        const val ERROR_SHOWN_MS = 6_000L
        const val BUSY_GUARD_MS = 20_000L
    }
}
