package ai.hey.session

import ai.hey.model.CarCardView
import ai.hey.model.CarSettings
import ai.hey.model.CarView
import ai.hey.model.ClientType
import ai.hey.model.ConditionId
import ai.hey.model.DriverDecision
import ai.hey.model.DrivingStateData
import ai.hey.model.DropoffCandidate
import ai.hey.model.DropoffDecideData
import ai.hey.model.DropoffProposeData
import ai.hey.model.DropoffReasonId
import ai.hey.model.EmptyData
import ai.hey.model.ErrorCode
import ai.hey.model.HeyConfig
import ai.hey.model.HeyIds
import ai.hey.model.HeyJson
import ai.hey.model.HeyLog
import ai.hey.model.LocalSpeech
import ai.hey.model.SpeechPriority

import ai.hey.model.JoinDecision
import ai.hey.model.ProgressInfo
import ai.hey.model.QuickReplyId
import ai.hey.model.isOpenQuestion
import ai.hey.model.ReplySendData
import ai.hey.model.RequestSpokenData
import ai.hey.model.RequestState
import ai.hey.model.RouteFailedData
import ai.hey.model.RouteInfo
import ai.hey.model.ServerMessage
import ai.hey.model.TripState
import ai.hey.model.actionableCount
import ai.hey.model.nextClockOffset
import ai.hey.model.routeDestination
import ai.hey.vehicle.DrivingStatus
import ai.hey.vehicle.RouteFailure
import ai.hey.vehicle.RouteStartState
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
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "TripRepository"

/** Whether a dropped text means the speech engine itself is unusable (the only case D1 offers a retry for). */
private fun ai.hey.vehicle.SpeechDropReason.isEngineFailure(): Boolean = when (this) {
    ai.hey.vehicle.SpeechDropReason.EngineUnavailable, ai.hey.vehicle.SpeechDropReason.EngineError -> true
    ai.hey.vehicle.SpeechDropReason.Blank, ai.hey.vehicle.SpeechDropReason.QueueOverflow -> false
}

/**
 * Owns the relay connection and the vehicle and exposes ONE state. Every screen reads [CarUiState.phase]; safe
 * actions are [CarCommands] (this class), hands-on actions exist only on [CarUiState.parked] (a [ParkedCommands] this
 * class creates, null unless the car is parked) and each of them also checks the live phase's
 * [CarPhase.Parked.enabled], so what the relay would refuse is not sent from any caller.
 *
 * [transport] builds the relay connection; the default is the real [RelayLink], a test passes a recording fake and
 * drives the repository through the same listener the socket uses.
 */
class TripRepository(
    private val scope: CoroutineScope,
    private val vehicle: VehicleGateway,
    private val storage: CarStorage,
    private val http: OkHttpClient = newHttpClient(),
    /** Sent as `Authorization: Provision <secret>` when registering; empty sends no header. */
    private val provisionSecret: String = "",
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val progressPollMs: Long = HeyConfig.TRIP_PROGRESS_POLL_SEC * 1000L,
    private val backoffSec: List<Long> = HeyConfig.RECONNECT_BACKOFF_SEC,
    private val ackTimeoutMs: Long = HeyConfig.WS_ACK_TIMEOUT_MS,
    transport: (RelayTransport.Listener) -> RelayTransport = { listener -> RelayLink(http, scope, listener, ackTimeoutMs, backoffSec, clock) },
) : CarCommands {
    private val hands = Hands()
    private val _state = MutableStateFlow(CarUiState(driving = vehicle.drivingState.value, hands = hands))
    val state: StateFlow<CarUiState> = _state.asStateFlow()

    private val link: RelayTransport = transport(LinkListener())

    @Volatile private var registration: Registration? = null
    private var connectJob: Job? = null

    /** Texts waiting to be read, by speech id, so "방금 읽은 요청" can show the text once it was read. */
    private val spokenTexts = ConcurrentHashMap<String, String>()

    /** Envelope id of a command -> the typed key it set, so a relay error for it can fail that key. */
    private val envelopeKeys = ConcurrentHashMap<String, CommandKey>()

    /** Trip whose arrival was already sent: the navigation event and the manual button must not both fire. */
    @Volatile private var arrivalSentFor: String? = null
    private var errorJob: Job? = null

    /** The route-start attempt whose outcome the relay was already told, so one outcome is sent once. */
    private var routeReported = 0
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
                _state.update { it.copy(driving = d, endDialogOpen = if (d == DrivingStatus.Parked) it.endDialogOpen else false) }
                sendDriving(d)
            }
        }
        scope.launch { vehicle.events.collect { onVehicleEvent(it) } }
        scope.launch { vehicle.routeState.collect { onRouteState(it) } }
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
                val created = registerCar(http, relayUrl, plate, provisionSecret = provisionSecret)
                Registration(created.carCode, created.driverToken, created.qrUrl, relayUrl, plate).also { storage.saveRegistration(it) }
            } catch (e: ProtocolMismatchException) {
                // Another protocol major: registering again gets the same answer, so no backoff loop.
                _state.update { it.copy(connection = ConnectionStatus.ProtocolMismatch, view = null, commands = emptyMap()) }
                return
            } catch (e: Exception) {
                HeyLog.w(TAG, "registration failed: ${e.javaClass.simpleName}")
                _state.update { it.copy(connection = ConnectionStatus.RegistrationFailed) }
                delay(backoffSec[minOf(failures, backoffSec.lastIndex)] * 1000)
                failures++
                null
            }
        }
        registration = reg
        _state.update { it.copy(connection = ConnectionStatus.Connecting, qrUrl = reg.qrUrl, view = null, commands = emptyMap()) }
        link.connect(relayUrl, reg.driverToken)
    }

    private inner class LinkListener : RelayTransport.Listener {
        override fun onConnection(connected: Boolean) {
            if (connected) {
                _state.update { it.copy(connection = ConnectionStatus.Online) }
                sendDriving(_state.value.driving)
            } else {
                _state.update { it.copy(connection = ConnectionStatus.Reconnecting) }
            }
        }

        override fun onTokenRejected() {
            _state.update { it.copy(connection = ConnectionStatus.Rejected, view = null, commands = emptyMap()) }
        }

        override fun onProtocolMismatch() {
            _state.update { it.copy(connection = ConnectionStatus.ProtocolMismatch, view = null, commands = emptyMap()) }
        }

        override fun onMessage(message: ServerMessage) {
            when (message) {
                is ServerMessage.Snapshot -> onSnapshot(message.view, message.ts)
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

    /**
     * Rule 2: an error that names the envelope of a button's command fails that command, and the banner clears on
     * its own. An error for a fire-and-forget message (a progress update, a read receipt) has no button to explain it
     * to, so it is not shown: a `request.spoken` that arrives after the trip ended must not paint D1.
     */
    private fun onError(code: ErrorCode, ref: String?) {
        val key = ref?.let { envelopeKeys.remove(it) } ?: run {
            HeyLog.w(TAG, "relay refused an unattended message: ${code.name}")
            return
        }
        _state.update { old -> old.copy(error = code, commands = old.commands + (key to CommandState.Failed(code.name))) }
        errorJob?.cancel()
        errorJob = scope.launch {
            delay(ERROR_SHOWN_MS)
            _state.update { old ->
                old.copy(
                    error = if (old.error == code) null else old.error,
                    // (the route start has its own state and banner; see CarUiState.route)
                    commands = old.commands.filterNot { (_, v) -> v is CommandState.Failed },
                )
            }
        }
    }

    private fun sendDriving(driving: DrivingStatus) {
        val moving = driving != DrivingStatus.Parked
        link.send(ClientType.DRIVING_STATE, HeyJson.encodeToJsonElement(DrivingStateData.serializer(), DrivingStateData(moving)), "driving")
    }

    // ---------------------------------------------------------------- snapshots

    /** Rule 1: true when the snapshot shows the effect of the command that set [key]. */
    private fun settled(key: CommandKey, view: CarView): Boolean = when (key) {
        is CommandKey.Join -> view.pendingJoin?.joinRequestId != key.joinRequestId
        CommandKey.Recheck -> view.state != TripState.CARD_SENT
        is CommandKey.Propose -> (view.dropoff?.version ?: 0) > key.previousVersion
        is CommandKey.Pin -> view.pendingPins.none { it.version == key.version }
        // The relay keeps only the newest replies, so the count can stop growing: the newest reply changing is the sign.
        is CommandKey.Reply ->
            view.replies.lastOrNull() != key.newestReplyBefore ||
                view.requests.firstOrNull { it.requestId == key.requestId }?.state == RequestState.Answered
        CommandKey.End -> view.state == TripState.IDLE || view.state == TripState.ENDED
        CommandKey.Arrive -> view.state != TripState.IN_TRIP
        // A route start is not a relay command; it lives in CarUiState.route.
    }

    private fun onSnapshot(view: CarView, serverTs: Long?) {
        val tripChanged = view.tripId != _state.value.view?.tripId
        // Command state belongs to one trip: a failed route start or a pending tap of the last trip must not show on the next.
        if (tripChanged) {
            arrivalSentFor = null
            envelopeKeys.clear()
            // The attempt of the last trip must not leak into the next: drop it (a late answer is then ignored).
            vehicle.cancelRoute()
        }
        // Arrival is the one thing the relay does not say: speak it here, once, when this trip's state first becomes ARRIVED.
        val prior = _state.value.view
        if (!tripChanged && prior != null && prior.state != TripState.ARRIVED && view.state == TripState.ARRIVED) {
            vehicle.speak(LocalSpeech.ARRIVED, SpeechPriority.High, null)
        }
        _state.update { old ->
            val previous = old.view
            val changed = if (!tripChanged) changedConditions(previous?.card, view.card, old.changedConditions) else emptySet()
            val tripEnded = view.state == TripState.IDLE
            val dropoffPossible = view.state == TripState.CARD_SENT || view.state == TripState.IN_TRIP
            old.copy(
                view = view,
                commands = if (tripChanged) emptyMap() else old.commands.filterNot { (key, _) -> settled(key, view) },
                changedConditions = changed,
                // a new/other proposal makes the typed selection obsolete (activeDraft); D4 closes where the relay stops accepting proposals
                panel = if (old.panel == Panel.Dropoff && !dropoffPossible) Panel.None else old.panel,
                requestsDismissedAt = minOf(old.requestsDismissedAt, view.actionableCount),
                lastSpokenKo = if (tripEnded) null else old.lastSpokenKo,
                endDialogOpen = if (tripEnded) false else old.endDialogOpen,
                candidates = if (view.card?.destination?.placeId != old.candidates.forPlaceId) Candidates() else old.candidates,
                progressAt = if (view.progress != previous?.progress) clock() else old.progressAt,
                clockOffsetMs = serverTs?.let { nextClockOffset(old.clockOffsetMs, clock(), it) } ?: old.clockOffsetMs,
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
                // The engine works again: the D1 speech banner is no longer true.
                _state.update { it.copy(issues = it.issues.filterNot { issue -> issue is VehicleIssue.Speech }.toSet()) }
                markSpoken(event.speechId)
            }
            // Only a broken engine is a device problem the driver can retry; a queue overflow or a blank text is not.
            is VehicleEvent.SpeechDropped -> if (event.reason.isEngineFailure()) {
                _state.update { it.copy(issues = it.issues + VehicleIssue.Speech(event.reason)) }
            }
            is VehicleEvent.NavigationUnavailable -> _state.update { it.copy(issues = it.issues + VehicleIssue.Navi(event.code)) }
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

    /** The live phase offers this command (parked, and the relay accepts it in the trip's state). */
    private fun allowed(command: CarCommand): Boolean = (_state.value.phase as? CarPhase.Parked)?.enabled?.contains(command) == true

    private fun parkedNow(): Boolean = _state.value.phase is CarPhase.Parked

    /**
     * Sends a command. Rule 3: the safety timeout clears the key only if this exact envelope is still the one in
     * flight, so a late timer never releases a newer tap.
     */
    private fun sendCommand(type: String, data: JsonElement, key: CommandKey? = null) {
        val id = UUID.randomUUID().toString()
        if (key != null) {
            // Registered before the send: the relay's refusal can come back before send() returns.
            envelopeKeys[id] = key
            _state.update { it.copy(commands = it.commands + (key to CommandState.InFlight(id)), error = null) }
            scope.launch {
                delay(BUSY_GUARD_MS)
                envelopeKeys.remove(id)
                _state.update { old ->
                    val current = old.commands[key]
                    if (current is CommandState.InFlight && current.envelopeId == id) old.copy(commands = old.commands - key) else old
                }
            }
        }
        link.send(type, data, id = id)
    }

    private val empty: JsonElement get() = HeyJson.encodeToJsonElement(EmptyData.serializer(), EmptyData())

    /** The one [ParkedCommands]; it only forwards to the gated functions below. */
    private inner class Hands : ParkedCommands() {
        override fun approveJoin(joinRequestId: String) = this@TripRepository.decideJoin(CarCommand.ApproveJoin, ClientType.JOIN_APPROVE, joinRequestId)
        override fun declineJoin(joinRequestId: String) = this@TripRepository.decideJoin(CarCommand.DeclineJoin, ClientType.JOIN_DECLINE, joinRequestId)
        override fun recheckCard() = this@TripRepository.recheck()
        override fun startRoute() = this@TripRepository.beginRoute()
        override fun cancelRoute() = this@TripRepository.cancelRouteStart()
        override fun openNavi() = this@TripRepository.openNavi()
        override fun proposeDropoff(candidate: DropoffCandidate, reasonId: DropoffReasonId?) = this@TripRepository.propose(candidate, reasonId)
        override fun decidePin(version: Int, accept: Boolean) = this@TripRepository.decide(version, accept)
        override fun sendReply(requestId: String, replyId: QuickReplyId) = this@TripRepository.reply(requestId, replyId)
        override fun arrive() = this@TripRepository.arriveByButton()
        override fun endTrip() = this@TripRepository.end()
        override fun openDropoff() = this@TripRepository.showDropoff()
        override fun openSettings() = this@TripRepository.showSettings()
        override fun openEndDialog() = this@TripRepository.showEndDialog()
        override fun reregister() = this@TripRepository.forgetAndRegisterAgain()
    }

    private fun decideJoin(command: CarCommand, type: String, joinRequestId: String) {
        if (!allowed(command)) return
        if (_state.value.joinInFlight) return
        sendCommand(type, HeyJson.encodeToJsonElement(JoinDecision.serializer(), JoinDecision(joinRequestId)), CommandKey.Join(joinRequestId))
    }

    private fun recheck() {
        if (!allowed(CarCommand.Recheck)) return
        if (_state.value.isLoading(CommandKey.Recheck)) return
        sendCommand(ClientType.CARD_RECHECK, empty, CommandKey.Recheck)
    }

    private fun beginRoute() {
        if (!allowed(CarCommand.StartRoute)) return
        val destination = _state.value.view?.routeDestination() ?: return
        if (_state.value.route.let { it == RouteStartState.Requested || it == RouteStartState.AwaitingNaviConfirm }) return
        _state.update { it.copy(issues = it.issues.filterNot { issue -> issue is VehicleIssue.Navi }.toSet()) }
        vehicle.startRoute(destination)
    }

    private fun cancelRouteStart() {
        if (!allowed(CarCommand.StartRoute)) return
        vehicle.cancelRoute()
    }

    private fun openNavi() {
        if (!allowed(CarCommand.StartRoute)) return
        if (!vehicle.openNavi()) _state.update { it.copy(issues = it.issues + VehicleIssue.Navi(RouteFailure.NAVI_NOT_READY)) }
    }

    /**
     * Follows the vehicle's route-start state. The relay hears about it exactly once per attempt and only from a real
     * outcome: route.started when the navigation reported the route started, route.failed when the attempt failed.
     * Waiting for the person to confirm in the navigation sends nothing.
     */
    private fun onRouteState(route: RouteStartState) {
        _state.update { it.copy(route = route) }
        when (route) {
            is RouteStartState.Started -> if (routeReported != route.attempt) {
                routeReported = route.attempt
                if (relayAccepts(ClientType.ROUTE_STARTED)) {
                    link.send(ClientType.ROUTE_STARTED, HeyJson.encodeToJsonElement(RouteInfo.serializer(), route.route))
                }
                _state.update { it.copy(issues = it.issues.filterNot { issue -> issue is VehicleIssue.Navi }.toSet()) }
            }
            is RouteStartState.Failed -> if (routeReported != route.attempt) {
                routeReported = route.attempt
                if (relayAccepts(ClientType.ROUTE_FAILED)) {
                    link.send(ClientType.ROUTE_FAILED, HeyJson.encodeToJsonElement(RouteFailedData.serializer(), RouteFailedData(route.code)))
                }
            }
            RouteStartState.Idle, RouteStartState.Requested, RouteStartState.AwaitingNaviConfirm -> Unit
        }
    }

    /** The relay takes route.started / route.failed only in CARD_SENT and IN_TRIP (docs/transitions.json). */
    private fun relayAccepts(type: String): Boolean {
        val state = _state.value.view?.state
        return (state == TripState.CARD_SENT || state == TripState.IN_TRIP) && CarCommand.StartRoute.wire.contains(type)
    }

    private fun propose(candidate: DropoffCandidate, reasonId: DropoffReasonId?) {
        if (!allowed(CarCommand.Propose)) return
        if (_state.value.proposeInFlight) return
        val version = _state.value.view?.dropoff?.version ?: 0
        sendCommand(
            ClientType.DROPOFF_PROPOSE,
            HeyJson.encodeToJsonElement(DropoffProposeData.serializer(), DropoffProposeData.of(candidate, reasonId)),
            CommandKey.Propose(version),
        )
    }

    private fun decide(version: Int, accept: Boolean) {
        if (!allowed(CarCommand.DecidePin)) return
        val key = CommandKey.Pin(version)
        if (_state.value.isLoading(key)) return
        val decision = if (accept) DriverDecision.Accept else DriverDecision.Decline
        sendCommand(
            ClientType.DROPOFF_DECIDE,
            HeyJson.encodeToJsonElement(DropoffDecideData.serializer(), DropoffDecideData(version, decision)),
            key,
        )
    }

    private fun markSpoken(requestId: String) {
        link.send(ClientType.REQUEST_SPOKEN, HeyJson.encodeToJsonElement(RequestSpokenData.serializer(), RequestSpokenData(requestId)))
    }

    private fun reply(requestId: String, replyId: QuickReplyId) {
        if (!allowed(CarCommand.Reply)) return
        val s = _state.value
        if (s.replyInFlight != null) return
        // Only an open question can be answered: a stale tap for a request that is answered or gone is not sent.
        if (s.view?.requests?.firstOrNull { it.requestId == requestId }?.isOpenQuestion != true) return
        sendCommand(
            ClientType.REPLY_SEND,
            HeyJson.encodeToJsonElement(ReplySendData.serializer(), ReplySendData(requestId, replyId)),
            CommandKey.Reply(requestId, replyId, s.view?.replies?.lastOrNull()),
        )
    }

    private fun arriveByButton() {
        if (!allowed(CarCommand.Arrive)) return
        sendArrive(busy = true)
    }

    private fun sendArrive(busy: Boolean) {
        val trip = _state.value.view?.tripId ?: return
        if (arrivalSentFor == trip) return
        arrivalSentFor = trip
        sendCommand(ClientType.TRIP_ARRIVE, empty, if (busy) CommandKey.Arrive else null)
    }

    private fun end() {
        if (!allowed(CarCommand.End)) return
        if (_state.value.isLoading(CommandKey.End)) return
        _state.update { it.copy(endDialogOpen = false) }
        sendCommand(ClientType.TRIP_END, empty, CommandKey.End)
    }

    override fun replay(textKo: String) = vehicle.replay(textKo)

    override fun replayRequest(requestId: String) {
        val request = _state.value.view?.requests?.firstOrNull { it.requestId == requestId } ?: return
        // A request that was never heard (queue overflow, speaker down) is marked spoken once this reading finishes.
        vehicle.replay(request.text, if (request.state == RequestState.Received) requestId else null)
    }

    override fun retryVehicleIssue(issue: VehicleIssue) {
        _state.update { it.copy(issues = it.issues - issue) }
        when (issue) {
            is VehicleIssue.Speech -> vehicle.retrySpeech()
            is VehicleIssue.Navi -> vehicle.retryNavigation()
        }
    }

    private fun forgetAndRegisterAgain() {
        if (!parkedNow()) return
        val url = _state.value.settings.relayUrl
        scope.launch {
            storage.forgetRegistration(url)
            restartConnection(url)
        }
    }

    // ---------------------------------------------------------------- local UI state

    private fun showDropoff() {
        if (!allowed(CarCommand.Propose)) return
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

    private fun showSettings() {
        if (!parkedNow()) return
        _state.update { it.copy(panel = Panel.Settings) }
    }

    override fun closeSettings() = _state.update { it.copy(panel = Panel.None, settingsSave = SaveResult.Saved) }

    private fun showEndDialog() {
        if (!allowed(CarCommand.End)) return
        _state.update { it.copy(endDialogOpen = true) }
    }

    override fun closeEndDialog() = _state.update { it.copy(endDialogOpen = false) }

    override fun updateSettings(transform: (CarSettings) -> CarSettings) {
        scope.launch {
            val result = storage.updateSettings(transform)
            _state.update { it.copy(settingsSave = result) }
        }
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
                else it.copy(candidates = Candidates(place.placeId, result.orEmpty(), loading = false))
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
