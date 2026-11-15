package ai.hey.session

import ai.hey.model.AudioSpec
import ai.hey.model.VehicleSignals
import ai.hey.model.OnboardingStep
import ai.hey.model.CarSettings
import ai.hey.model.ClientFrames
import ai.hey.model.Wire
import ai.hey.model.HeyLog
import ai.hey.model.ServerFrame
import ai.hey.model.SpeechPriority
import ai.hey.vehicle.DrivingStatus
import ai.hey.vehicle.FocusGate
import ai.hey.vehicle.PlaybackEnd
import ai.hey.vehicle.PlaybackListener
import ai.hey.vehicle.ReplyAudio
import ai.hey.model.OnboardingAnswers
import ai.hey.vehicle.SpeechSettings
import ai.hey.vehicle.VehicleEvent
import ai.hey.vehicle.VehicleGateway
import ai.hey.vehicle.VoiceInput
import ai.hey.vehicle.OtherAudioProbe
import ai.hey.vehicle.VoiceResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.selects.select
import ai.hey.vehicle.ReplyVoice
import java.util.UUID

private const val TAG = "Talk"
private const val CONSENT_REQUIRED = "consent_required"

/**
 * Owns the one /v1/talk connection and the vehicle and exposes ONE state. A turn is: Pleos STT hears one sentence ->
 * `turn.start`, `text`, `turn.end` -> the reply streams back as `reply.*` frames and, when the server has a voice, as
 * PCM that [audio] plays (mouth level from its loudness, D-13). Without server audio, or when the server is gone, the
 * Pleos TTS reads the text and its `onUpdatedRms` drives the mouth.
 *
 * Driving does not interrupt a turn: listening and the turn go on when the car leaves P (AC-4.4-18, AC-4.5-07). Only
 * the hands-on commands ([ParkedCommands]) disappear while the car moves.
 *
 * The voice stops at once when another app takes the audio focus and is not resumed (D-14), and a button press while the
 * character talks stops it and starts listening (D-24).
 *
 * [transport] builds the connection; a test passes a recording fake and drives the repository through the listener.
 */
class TalkRepository(
    private val scope: CoroutineScope,
    private val vehicle: VehicleGateway,
    private val storage: CarStorage,
    private val voiceInput: VoiceInput,
    private val transport: (TalkTransport.Listener) -> TalkTransport,
    private val newTurnId: () -> String = { UUID.randomUUID().toString() },
    /** Plays the server's PCM reply; null = no server voice in this build (the Pleos TTS reads everything). */
    private val audio: ReplyAudio? = null,
    /** Focus held while the Pleos TTS reads; a loss stops it. Null in tests. */
    private val speechFocus: FocusGate? = null,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    /** REST side (consents, pairing, memory, server settings); its state is mirrored into [CarUiState.companion]. Null in tests that do not need it. */
    val companion: CompanionRepository? = null,
    /** How long the microphone stays open by itself after a line that ends with a question mark (F4-F6). */
    private val autoListenMillis: Long = AUTO_LISTEN_MILLIS,
    private val ctxDebounceMillis: Long = CTX_DEBOUNCE_MILLIS,
    /** Asked when the character wants to start a line by itself; true = another app is playing, so the line is dropped (PLEOS-171). Null in tests that do not need it. */
    private val otherAudio: OtherAudioProbe? = null,
) : CarCommands {
    private val hands = Hands()
    private val _state = MutableStateFlow(CarUiState(hands = hands))
    val state: StateFlow<CarUiState> = _state.asStateFlow()

    private val link: TalkTransport = transport(LinkListener())

    private var voiceJob: Job? = null
    private var started = false

    /** The turn in flight on the socket; frames of another turn are dropped. */
    @Volatile private var turnId: String? = null
    private val replyText = StringBuilder()

    @Volatile private var replyAudio: AudioSpec = AudioSpec.None

    /** Whose voice the reply in flight is: only an onboarding frame with `voice: "whale"` makes it the whale's (reverb). */
    @Volatile private var replyVoice: ReplyVoice = ReplyVoice.Heyri
    @Volatile private var turnEndSentAt = 0L
    @Volatile private var pcmMouth = 0
    @Volatile private var pleosMouth = 0

    /** Id of the Pleos reading of the reply that is on now (its [VehicleEvent.SpeechDone] ends the turn on screen). */
    @Volatile private var speakingId: String? = null
    private var watchdog: Job? = null
    private var noteJob: Job? = null

    @OptIn(FlowPreview::class)
    fun start() {
        if (started) return
        started = true
        vehicle.warmUpSpeech()
        audio?.listener = object : PlaybackListener {
            override fun onFirstSound() {
                HeyLog.i(TAG, "first sound ${clock() - turnEndSentAt} ms after turn.end")
            }

            override fun onMouth(level: Int) {
                HeyLog.i(TAG, "mouth=$level (server audio)")
                pcmMouth = level
                publishMouth()
            }

            override fun onEnded(reason: PlaybackEnd) = onPlaybackEnded(reason)
        }
        scope.launch {
            storage.settings.collect { s ->
                _state.update { it.copy(settings = s) }
                vehicle.configureSpeech(SpeechSettings(gapSec = s.speechGapSec))
            }
        }
        scope.launch {
            storage.settings.map { it.serverUrl }.distinctUntilChanged().collect { url -> link.connect(url) }
        }
        // Moving does NOT touch the turn: it only decides whether hands-on commands exist.
        scope.launch {
            vehicle.drivingState.collect { d ->
                _state.update {
                    it.copy(
                        driving = d,
                        panel = if (d == DrivingStatus.Parked) it.panel else Panel.None,
                        friendNote = if (d == DrivingStatus.Parked) it.friendNote else null,
                    )
                }
            }
        }
        scope.launch {
            storage.consents.collect { c -> _state.update { it.copy(needsOnboarding = !c.recordDone) } }
        }
        scope.launch { storage.onboardingStep.collect { st -> _state.update { it.copy(onboardingStep = st) }; if (st == OnboardingStep.Done) drainFriends() } }
        scope.launch {
            // A spoken step runs while the car is moving too (a press step waits for park); the steps before the consent sheet
            // need no consent. collectLatest restarts the step when the step, the link, the consent or the gear (for press steps)
            // changes, so a restart of the app resumes at the stored step.
            _state.map { s ->
                val step = s.onboardingStep
                val may = step.spoken && step.canRun(s.isParked) && (step.consentFree || !s.needsOnboarding)
                OnboardingRun(step, run = may && s.online, offline = may && !s.online)
            }
                .distinctUntilChanged()
                .collectLatest { k -> if (k.run) runSpokenStep(k.step) else if (k.offline) passStepOffline(k.step) }
        }
        companion?.let { c ->
            scope.launch { c.state.collect { s -> _state.update { it.copy(companion = s) } } }
        }
        // Vehicle context goes to the server on change, debounced; a new connection sends the current values at once.
        scope.launch {
            // Coarse speed and range (10 steps) keep the traffic to real changes.
            vehicle.signals.map { it.copy(speedKmh = it.speedKmh?.let { v -> v / 10 * 10 }, rangeKm = it.rangeKm?.let { v -> v / 10 * 10 }) }
                .debounce(ctxDebounceMillis).distinctUntilChanged().collect { sendCtx(it) }
        }
        scope.launch {
            vehicle.mouth.collect { level ->
                pleosMouth = level
                publishMouth()
            }
        }
        scope.launch {
            vehicle.events.collect { e ->
                val mine = speakingId ?: return@collect
                when (e) {
                    is VehicleEvent.SpeechDone -> if (e.speechId == mine) finishSpeaking(heard = true)
                    is VehicleEvent.SpeechDropped -> if (e.speechId == mine) finishSpeaking(heard = false)
                    else -> Unit
                }
            }
        }
    }

    fun stop() {
        voiceJob?.cancel()
        link.close()
        silence()
        started = false
    }

    /** C8 "다시 연결": try the server now instead of waiting out the backoff. */
    fun reconnectNow() = link.reconnectNow()

    private fun publishMouth() {
        val level = maxOf(pcmMouth, pleosMouth)
        _state.update { if (it.mouth == level) it else it.copy(mouth = level) }
    }

    private inner class LinkListener : TalkTransport.Listener {
        override fun onConnection(status: ConnectionStatus) {
            _state.update { it.copy(connection = status) }
            if (status == ConnectionStatus.Online) {
                sendCtx(vehicle.signals.value)
                companion?.onOnline()
            } else {
                onServerGone()
            }
        }

        override fun onFrame(frame: ServerFrame) {
            when (frame) {
                is ServerFrame.ReplyStart -> if (frame.turnId == turnId) {
                    replyAudio = frame.audio
                    HeyLog.i(TAG, "reply.start audio=${frame.audio} ${clock() - turnEndSentAt} ms after turn.end")
                    audio?.begin(frame.audio, replyVoice)
                    armWatchdog(frame.turnId, REPLY_END_TIMEOUT_MILLIS)
                    _state.update { it.copy(turn = TurnState.Replying) }
                }
                is ServerFrame.ReplyDelta -> if (frame.turnId == turnId) {
                    armWatchdog(frame.turnId, REPLY_END_TIMEOUT_MILLIS)
                    replyText.append(frame.text)
                    // A friend line is shown only as [CarUiState.friendNote] (parked), never as the reply subtitle.
                    _state.update { if (it.source.quiet()) it else it.copy(reply = replyText.toString()) }
                }
                is ServerFrame.ReplyEnd -> if (frame.turnId == turnId) onReplyEnd(frame)
                is ServerFrame.Error -> {
                    HeyLog.w(TAG, "server error: ${frame.code}")
                    if (frame.turnId == null || frame.turnId == turnId) {
                        silence()
                        // AC-4.9-05: the server holds no record/external_ai consent for this car; C1 asks again instead of a server fault.
                        if (frame.code == CONSENT_REQUIRED) {
                            endTurn(null)
                            _state.update { it.copy(onboardingRequests = it.onboardingRequests + 1) }
                        } else {
                            endTurn(TurnError.Server)
                        }
                    }
                }
                is ServerFrame.Proactive -> onProactive(frame)
                is ServerFrame.FriendMessage -> onFriend(frame.id, frame.fromName, frame.text, showText = true)
                is ServerFrame.FriendArrived -> onFriend(frame.id, frame.fromName, "", showText = false)
                is ServerFrame.TripDone -> Unit // the notification centre ([FrameNotifier]) reads it; the repository has nothing to do
                is ServerFrame.Settings -> companion?.onServerSettings(frame.settings)
                is ServerFrame.Pairing -> companion?.onServerPairing(frame.phase)
                is ServerFrame.Onboarding -> onOnboardingFrame(frame)
                is ServerFrame.OnboardingAck -> onOnboardingAck(frame)
            }
        }

        override fun onAudio(pcm: ByteArray) {
            if (turnId != null && replyAudio is AudioSpec.Pcm) audio?.write(pcm)
        }
    }

    private fun onReplyEnd(frame: ServerFrame.ReplyEnd) {
        HeyLog.content(TAG, "reply.end chars=${frame.text.length} firstTokenMs=${frame.firstTokenMs} totalMs=${frame.totalMs}", frame.text)
        watchdog?.cancel()
        replyText.setLength(0)
        replyText.append(frame.text)
        val id = turnId
        turnId = null
        _state.update {
            when {
                // M4: the greeting's text comes only here (reply.end), not in the `onboarding` frame.
                it.source == TurnSource.Onboarding -> it.copy(onboarding = it.onboarding.copy(line = frame.text))
                it.source.quiet() -> it
                else -> it.copy(reply = frame.text)
            }
        }
        val spec = replyAudio
        if (spec is AudioSpec.Pcm && audio != null) {
            audio.finish() // the turn ends on screen when the playback does
        } else {
            readWithPleos(frame.text, id)
        }
    }

    /** No server voice (audio none), or the server went away: the car's own voice reads [text], mouth from `onUpdatedRms`. */
    private fun readWithPleos(text: String, id: String?) {
        if (text.isBlank()) return endSpeaking()
        val speechId = "reply-${id ?: "x"}"
        speakingId = speechId
        _state.update { it.copy(turn = TurnState.Replying) }
        val focusOk = speechFocus?.request {
            // D-14: someone else took the speaker. Stop and do not resume; this is not an audio fault.
            if (speakingId == speechId) {
                speakingId = null
                vehicle.stopSpeech()
                speechFocus.abandon()
                endSpeaking()
            }
        }
        if (focusOk == false) {
            HeyLog.w(TAG, "audio focus refused, not reading")
            speakingId = null
            return endSpeaking()
        }
        vehicle.speak(text, SpeechPriority.High, speechId)
    }

    private fun finishSpeaking(heard: Boolean) {
        speakingId = null
        speechFocus?.abandon()
        if (heard) afterReply() else failAudio()
    }

    private fun endSpeaking() {
        _state.update { if (it.turn == TurnState.Replying) it.copy(turn = TurnState.Idle) else it }
        if (_state.value.source == TurnSource.Onboarding) lineDone?.complete(Unit)
        drainFriends()
    }

    private fun failAudio() {
        endSpeaking()
        _state.update { it.copy(audioFailed = true) }
    }

    private fun onPlaybackEnded(reason: PlaybackEnd) {
        HeyLog.i(TAG, "playback ended: $reason")
        when (reason) {
            PlaybackEnd.Completed -> afterReply()
            PlaybackEnd.FocusLost -> {
                // D-14: someone else needs the speaker. Stop everything of ours and do not resume, do not listen.
                vehicle.stopSpeech()
                turnId = null
                endSpeaking()
            }
            PlaybackEnd.Failed -> failAudio()
            PlaybackEnd.Stopped -> Unit
        }
    }

    /**
     * The reply was read to the end. D-48 4: the microphone stays open for [autoListenMillis] after every answer to a
     * driver turn (follow-ups need no second tap), and right away after a line the character started by itself
     * ([TurnSource.Proactive]); the end of the speech sends the turn by itself. Never after a friend line, never while
     * navigation guides, and never twice in a row (the answer to an automatic listening ends the chain).
     */
    private fun afterReply() {
        endSpeaking()
        val s = _state.value
        if (s.turn != TurnState.Idle || s.source.quiet()) return
        if (s.source == TurnSource.AutoListen) {
            // PLEOS-79: at most one automatic listening per driver-started turn, or a repeating voice loops forever.
            HeyLog.i(TAG, "talk ignored: auto-listen skipped, the turn was itself an auto-listen")
            return
        }
        val why = when {
            s.needsOnboarding -> "consent not recorded"
            !s.online -> "offline"
            // A route can be set while parked: no guidance voice runs while the car stands, so listening is allowed.
            vehicle.signals.value.naviGuiding && s.driving != DrivingStatus.Parked -> "navigation guiding"
            !voiceInput.hasPermission() -> "no microphone permission"
            else -> null
        }
        if (why != null) {
            HeyLog.i(TAG, "talk ignored: auto-listen skipped, $why")
            return
        }
        listenOnce(TurnSource.AutoListen)
    }

    /** `proactive`: the character starts talking by itself; its `reply.*` frames follow with [ServerFrame.Proactive.id] as turn id. */
    private fun onProactive(frame: ServerFrame.Proactive) {
        val s = _state.value
        val why = when {
            s.needsOnboarding -> "consent not recorded"
            s.onboardingStep != OnboardingStep.Done -> "onboarding is running"
            s.turn != TurnState.Idle -> "busy (${s.turn})"
            // D-14: no new line while navigation guides.
            vehicle.signals.value.naviGuiding -> "navigation guiding"
            otherAudio?.otherAppPlaying() == true -> "another app is playing audio"
            else -> null
        }
        if (why != null) {
            HeyLog.i(TAG, "talk ignored: proactive ${frame.reason} blocked, $why")
            link.send(ClientFrames.turnCancel(frame.id))
            return
        }
        HeyLog.i(TAG, "proactive ${frame.reason}")
        replyText.setLength(0)
        replyAudio = AudioSpec.None
        replyVoice = ReplyVoice.Heyri
        turnId = frame.id
        turnEndSentAt = clock()
        _state.update { it.copy(turn = TurnState.Replying, source = TurnSource.Proactive, heard = null, reply = "", error = null, audioFailed = false) }
        armWatchdog(frame.id, REPLY_START_TIMEOUT_MILLIS)
    }

    /**
     * `friend_message` / `friend_arrived`: the spoken line follows as `reply.*` + PCM with turn id [id], like a proactive
     * line, but it is played in any driving state, navigation guiding or not. Another app's audio focus (navigation
     * voice, phone call) still wins through the normal playback path. The text goes on screen only while parked, and
     * only for a message ([showText]); an arrival is spoken only. The microphone never opens after it.
     */
    private fun onFriend(id: String, fromName: String, text: String, showText: Boolean) {
        val s = _state.value
        if (s.needsOnboarding) {
            // Without the record consent the server has no friend to deliver; nothing to keep.
            HeyLog.i(TAG, "talk ignored: friend line blocked, consent not recorded")
            link.send(ClientFrames.turnCancel(id))
            return
        }
        // H1: the server marks a friend line delivered when it sends it, so it is never cancelled here. A line that cannot play now
        // waits: an automatic listening gives way to it, a running reply or the first-run flow lets it come after them.
        if (s.turn != TurnState.Idle || s.onboardingStep != OnboardingStep.Done) {
            HeyLog.i(TAG, "friend line kept: busy (${s.turn})")
            pendingFriends += PendingFriend(id, fromName, text, showText)
            if (s.turn == TurnState.Listening && s.source == TurnSource.AutoListen) {
                // The open microphone gives way: this friend line is read now, with the server's voice that is just arriving.
                pendingFriends.removeLastOrNull()
                voiceJob?.cancel()
                endTurn(null)
                startFriend(PendingFriend(id, fromName, text, showText), serverVoice = true)
            }
            return
        }
        startFriend(PendingFriend(id, fromName, text, showText), serverVoice = true)
    }

    private class PendingFriend(val id: String, val fromName: String, val text: String, val showText: Boolean)

    private val pendingFriends = ArrayDeque<PendingFriend>()

    /** The next kept friend line, once nothing else is running: the car's own voice reads it (the server's audio for it went by). */
    private fun drainFriends() {
        val s = _state.value
        if (s.turn != TurnState.Idle || s.onboardingStep != OnboardingStep.Done) return
        val next = pendingFriends.removeFirstOrNull() ?: return
        startFriend(next, serverVoice = false)
    }

    private fun startFriend(f: PendingFriend, serverVoice: Boolean) {
        val s = _state.value
        HeyLog.i(TAG, "friend line ${if (f.showText) "message" else "arrival"} serverVoice=$serverVoice")
        replyText.setLength(0)
        replyAudio = AudioSpec.None
        replyVoice = ReplyVoice.Heyri
        turnId = if (serverVoice) f.id else null
        turnEndSentAt = clock()
        val note = if (f.showText && s.isParked) FriendNote(f.fromName, f.text) else null
        _state.update {
            it.copy(turn = TurnState.Replying, source = TurnSource.Friend, heard = null, reply = "", friendNote = note, error = null, audioFailed = false)
        }
        if (note != null) {
            noteJob?.cancel()
            noteJob = scope.launch {
                delay(FRIEND_NOTE_MILLIS)
                _state.update { if (it.friendNote == note) it.copy(friendNote = null) else it }
            }
        }
        if (serverVoice) armWatchdog(f.id, REPLY_START_TIMEOUT_MILLIS)
        else readWithPleos(if (f.showText) "${f.fromName}가 그러는데, ${f.text}" else "${f.fromName}가 도착했대", f.id)
    }

    private fun sendCtx(signals: VehicleSignals) {
        link.send(ClientFrames.ctx(signals))
    }

    /** The connection went. A reply that was playing is read by the Pleos TTS instead (D-12), a turn that was waiting fails. */
    private fun onServerGone() {
        val id = turnId ?: return
        turnId = null
        val spec = replyAudio
        val heard = replyText.toString()
        if (_state.value.turn == TurnState.Replying && heard.isNotBlank()) {
            audio?.stop()
            readWithPleos(heard, id)
        } else {
            silence()
            endTurn(TurnError.ConnectionLost)
        }
    }

    /** Every voice of ours off, now. */
    private fun silence() {
        watchdog?.cancel()
        audio?.stop()
        vehicle.stopSpeech()
        speakingId = null
        speechFocus?.abandon()
        pcmMouth = 0
        pleosMouth = 0
        publishMouth()
    }

    private fun endTurn(error: TurnError?) {
        watchdog?.cancel()
        turnId = null
        _state.update { it.copy(turn = TurnState.Idle, error = error) }
    }

    /** PLEOS-134: the turn [id] must show a sign of life within [millis], else it ends with the server fault and the talk button is free again. */
    private fun armWatchdog(id: String, millis: Long) {
        watchdog?.cancel()
        watchdog = scope.launch {
            delay(millis)
            if (turnId != id) return@launch
            HeyLog.w(TAG, "turn timed out after $millis ms without a reply frame")
            link.send(ClientFrames.turnCancel(id))
            silence()
            endTurn(TurnError.Server)
        }
    }

    // ---------------------------------------------------------------- spoken onboarding (D-48, D-52)

    /** `onboarding` frame: the whale's (or heyri's) line of the step follows as `reply.*` + PCM with turn id [ServerFrame.Onboarding.id]. */
    private fun onOnboardingFrame(frame: ServerFrame.Onboarding) {
        val s = _state.value
        // Thinking = the whale's answer to what the driver just said (`step: "ack"` / `"skip"`).
        val open = s.turn == TurnState.Idle || (s.turn == TurnState.Thinking && frame.step in ANSWER_LINE_STEPS)
        if (s.source != TurnSource.Onboarding || !open) {
            HeyLog.i(TAG, "onboarding line dropped: busy (${s.turn})")
            link.send(ClientFrames.turnCancel(frame.id))
            return
        }
        HeyLog.i(TAG, "onboarding line step=${frame.step} whale=${frame.whale}")
        replyText.setLength(0)
        replyAudio = AudioSpec.None
        replyVoice = if (frame.whale) ReplyVoice.Whale else ReplyVoice.Heyri
        turnId = frame.id
        turnEndSentAt = clock()
        _state.update {
            it.copy(turn = TurnState.Replying, heard = if (frame.step in ANSWER_LINE_STEPS) it.heard else null, reply = "", error = null, audioFailed = false, onboarding = it.onboarding.copy(line = frame.text, whale = frame.whale))
        }
        armWatchdog(frame.id, REPLY_START_TIMEOUT_MILLIS)
    }

    private fun onOnboardingAck(frame: ServerFrame.OnboardingAck) {
        HeyLog.i(TAG, "onboarding ack step=${frame.step} ok=${frame.ok} skipped=${frame.skipped}")
        ack?.complete(frame)
    }

    @Volatile private var lineDone: CompletableDeferred<Unit>? = null
    @Volatile private var ack: CompletableDeferred<ServerFrame.OnboardingAck>? = null
    private val skipSignal = Channel<Unit>(Channel.CONFLATED)

    /**
     * No server link: the step shows its own text (the screen has a copy of the fixed lines) for a few seconds and goes on, so the
     * first run never hangs on the network. A question is passed without an answer; a press step simply waits for its screen.
     */
    private suspend fun passStepOffline(step: OnboardingStep) {
        delay(ONBOARDING_OFFLINE_GRACE_MILLIS)
        HeyLog.w(TAG, "onboarding step=${step.name}: no server, text only")
        _state.update { it.copy(source = TurnSource.Onboarding, onboarding = it.onboarding.copy(line = "", whale = step.whale)) }
        if (step.awaitsScreen) return
        delay(ONBOARDING_OFFLINE_HOLD_MILLIS)
        storage.setOnboardingStep(step.next())
    }

    /**
     * Asks the server to speak [wire] and waits until the line has been heard. False when it could not be spoken (no line, no
     * link): the caller tries again or goes on with the subtitle text only.
     */
    private suspend fun speakLine(wire: String, whale: Boolean): Boolean {
        _state.first { it.turn == TurnState.Idle }
        val done = CompletableDeferred<Unit>().also { lineDone = it }
        _state.update { it.copy(source = TurnSource.Onboarding, heard = null, reply = "", error = null, onboarding = it.onboarding.copy(line = "", whale = whale)) }
        if (!link.send(ClientFrames.onboarding(wire))) return false
        HeyLog.i(TAG, "onboarding step=$wire asked")
        if (withTimeoutOrNull(ONBOARDING_LINE_TIMEOUT_MILLIS) { done.await() } == null) {
            HeyLog.w(TAG, "onboarding step=$wire: no line")
            silence()
            endTurn(null)
            return false
        }
        return true
    }

    /**
     * Runs one spoken onboarding step at its stored place (every driving state, except the steps that need the screen: those wait
     * for park): the server speaks it, a question then opens the microphone by itself (no tap) and the answer goes back. The step
     * is written to the device only when it is over, so a restart or a drive repeats at most the step that was running.
     */
    private suspend fun runSpokenStep(step: OnboardingStep) {
        val wire = step.wire ?: return
        // M5: a line that does not arrive is tried once more, then the step goes on with the screen's own fixed text; a question
        // whose line never came is passed without an answer. There is no endless re-request.
        var spoken = false
        var tries = 0
        while (!spoken && tries < ONBOARDING_LINE_TRIES) {
            if (tries++ > 0) delay(ONBOARDING_RETRY_MILLIS)
            spoken = speakLine(wire, step.whale)
        }
        if (!spoken) {
            HeyLog.w(TAG, "onboarding step=$wire: text only")
            _state.update { it.copy(source = TurnSource.Onboarding, onboarding = it.onboarding.copy(line = "", whale = step.whale)) }
            if (!step.awaitsScreen) {
                delay(ONBOARDING_OFFLINE_HOLD_MILLIS)
                storage.setOnboardingStep(step.next())
            }
            return
        }
        when {
            step.isQuestion -> askQuestion(step)
            // Dress-up and the phone link: heyri has spoken, the screen's press finishes the step.
            step.awaitsScreen -> Unit
            else -> {
                if (step.holdMillis > 0) delay(step.holdMillis)
                storage.setOnboardingStep(step.next())
            }
        }
    }

    /** The listening of a question, ended by the answer, by the silence, or by "넘어갈래" (null). */
    private suspend fun listenOrSkip(): VoiceResult? = coroutineScope {
        skipSignal.tryReceive()
        val heard = async<VoiceResult?> { voiceInput.listen() }
        val skipped = async<VoiceResult?> { skipSignal.receive(); null }
        val result = select<VoiceResult?> {
            heard.onAwait { it }
            skipped.onAwait { it }
        }
        coroutineContext.job.cancelChildren()
        result
    }

    private suspend fun askQuestion(step: OnboardingStep) {
        var silences = 0
        while (true) {
            _state.update { it.copy(turn = TurnState.Listening, heard = null, reply = "", error = null) }
            HeyLog.i(TAG, "listen start source=Onboarding")
            when (val result = listenOrSkip()) {
                null -> return passQuestion(step)
                is VoiceResult.Text -> return answerQuestion(step, result.text)
                VoiceResult.NoSpeech, is VoiceResult.Unavailable -> {
                    endTurn(null)
                    if (++silences >= ONBOARDING_MAX_SILENCES) return passQuestion(step)
                }
                VoiceResult.PermissionDenied -> {
                    // L6: only the questions are given up; the press steps after them (long press, cards, dress-up, phone) still run.
                    // C8 (microphone) shows the fault on the home screen afterwards.
                    endTurn(TurnError.PermissionDenied)
                    storage.setOnboardingStep(OnboardingStep.Found)
                    return
                }
            }
        }
    }

    /** An unanswered question: the whale says "괜찮아, 다음에 알려 줘"; that answer stays out of the memory cards and the greeting. */
    private suspend fun passQuestion(step: OnboardingStep) {
        endTurn(null)
        speakLine("skip", whale = true)
        storage.setOnboardingStep(step.next())
    }

    private suspend fun answerQuestion(step: OnboardingStep, text: String) {
        val wire = step.wire ?: return
        val verdict = CompletableDeferred<ServerFrame.OnboardingAck>().also { ack = it }
        val done = CompletableDeferred<Unit>().also { lineDone = it }
        // Thinking first: the whale's answer line can arrive the moment the answer is sent.
        _state.update { it.copy(turn = TurnState.Thinking, heard = text) }
        link.send(ClientFrames.onboardingAnswer(wire, text))
        HeyLog.content(TAG, "onboarding answer chars=${text.length}", text)
        val reply = withTimeoutOrNull(ONBOARDING_ACK_TIMEOUT_MILLIS) { verdict.await() }
        // The server speaks a short answer (or the skip line) right after its verdict: let it finish before the next step.
        if (reply != null) withTimeoutOrNull(ONBOARDING_ANSWER_LINE_MILLIS) { done.await() }
        val accepted = reply != null && reply.ok && !reply.skipped
        endTurn(null)
        if (accepted) {
            val clean = text.trim().take(ONBOARDING_ANSWER_MAX_CHARS)
            _state.update {
                val a = it.onboarding.answers
                it.copy(onboarding = it.onboarding.copy(answers = when (step) {
                    OnboardingStep.Q1 -> a.copy(name = reply?.name?.takeIf { n -> n.isNotBlank() })
                    OnboardingStep.Q2 -> a.copy(trips = clean)
                    else -> a.copy(topics = clean)
                }))
            }
        }
        storage.setOnboardingStep(step.next())
    }

    /** A press step (title, consent, long press, dress-up, phone link) was done on its screen: go on to the next one. */
    fun finishOnboardingStep(step: OnboardingStep) {
        scope.launch { if (storage.onboardingStep.value == step) storage.setOnboardingStep(step.next()) }
    }

    /** "넘어갈래" on a whale question: stop listening, the whale says "괜찮아, 다음에 알려 줘" and the next step starts. */
    fun skipOnboardingQuestion() {
        skipSignal.trySend(Unit)
    }

    // ---------------------------------------------------------------- talking

    override fun startTalk() {
        val s = _state.value
        // The onboarding turn owns the microphone: a press must not cut the whale's line or open a second turn.
        if (s.source == TurnSource.Onboarding && s.turn != TurnState.Idle && s.turn != TurnState.NeedsPermission) return
        if (s.onboardingStep != OnboardingStep.Done && !s.needsOnboarding) {
            HeyLog.i(TAG, "talk ignored: onboarding step ${s.onboardingStep} is running")
            return
        }
        if (s.needsOnboarding) {
            HeyLog.i(TAG, "talk ignored: consent not recorded, showing onboarding")
            _state.update { it.copy(onboardingRequests = it.onboardingRequests + 1) }
            return
        }
        when (s.turn) {
            TurnState.Idle -> Unit
            // D-24: the character is talking and the driver presses: stop the voice at once and listen.
            TurnState.Replying -> {
                silence()
                endTurn(null)
            }
            // The automatic 5 s listening is on: the press makes it a normal turn without the timeout.
            TurnState.Listening -> if (s.source == TurnSource.AutoListen) Unit else {
                HeyLog.i(TAG, "talk ignored: already listening")
                return
            }
            // The prompt is still open (or never got answered) but the permission arrived by another road (settings, pm grant).
            TurnState.NeedsPermission -> if (!voiceInput.hasPermission()) {
                HeyLog.i(TAG, "talk ignored: waiting for the microphone permission answer")
                return
            }
            else -> {
                HeyLog.i(TAG, "talk ignored: busy (${s.turn})")
                return
            }
        }
        if (!s.online) {
            HeyLog.i(TAG, "talk ignored: offline (${s.connection})")
            _state.update { it.copy(error = TurnError.NotConnected) }
            return
        }
        // Read on every press, never cached: the driver may have granted it in the settings or by adb since the last answer.
        if (!voiceInput.hasPermission()) {
            HeyLog.i(TAG, "talk: no microphone permission, asking")
            _state.update { it.copy(turn = TurnState.NeedsPermission, error = null) }
            return
        }
        if (_state.value.error == TurnError.PermissionDenied) HeyLog.i(TAG, "talk: microphone permission granted again, fault cleared")
        listenOnce(TurnSource.Ptt)
    }

    private fun listenOnce(source: TurnSource) {
        voiceJob?.cancel()
        HeyLog.i(TAG, "listen start source=$source")
        replyText.setLength(0)
        _state.update {
            if (source == TurnSource.AutoListen) it.copy(turn = TurnState.Listening, source = source, error = null)
            else it.copy(turn = TurnState.Listening, source = source, heard = null, reply = "", error = null, audioFailed = false)
        }
        voiceJob = scope.launch {
            val result = if (source == TurnSource.AutoListen) {
                withTimeoutOrNull(autoListenMillis) { voiceInput.listenAfterReply() } ?: VoiceResult.NoSpeech
            } else {
                voiceInput.listen()
            }
            when (result) {
                is VoiceResult.Text -> sendTurn(result.text, source)
                // Nothing said in the automatic listening: end quietly, no prompt, no error (F4-F6).
                VoiceResult.NoSpeech -> endTurn(if (source == TurnSource.AutoListen) null else TurnError.NoSpeech)
                VoiceResult.PermissionDenied -> endTurn(TurnError.PermissionDenied)
                is VoiceResult.Unavailable -> endTurn(if (source == TurnSource.AutoListen) null else TurnError.SpeechUnavailable)
            }
        }
    }

    private fun sendTurn(text: String, source: TurnSource) {
        val id = newTurnId()
        replyAudio = AudioSpec.None
        replyVoice = ReplyVoice.Heyri
        val trigger = if (source == TurnSource.AutoListen) Wire.TRIGGER_AFTER_PROACTIVE else Wire.TRIGGER_PTT
        val ok = link.send(ClientFrames.turnStart(id, trigger)) &&
            link.send(ClientFrames.text(id, text)) &&
            link.send(ClientFrames.turnEnd(id))
        if (!ok) {
            HeyLog.w(TAG, "turn not sent: not connected")
            endTurn(TurnError.NotConnected)
            return
        }
        turnEndSentAt = clock()
        turnId = id
        HeyLog.content(TAG, "turn sent chars=${text.length}", text)
        _state.update { it.copy(turn = TurnState.Thinking, heard = text, reply = "") }
        armWatchdog(id, REPLY_START_TIMEOUT_MILLIS)
    }

    override fun endListening() {
        if (_state.value.turn == TurnState.Listening) voiceInput.finishListening()
    }

    /**
     * "대화 마치기" (PLEOS-194): the one button of every active phase. Stops the microphone, drops a turn that has not been
     * answered, silences the voice and clears the conversation on screen, so the home is idle again.
     */
    override fun cancelTalk() {
        voiceJob?.cancel()
        turnId?.let { link.send(ClientFrames.turnCancel(it)) }
        silence()
        endTurn(null)
        _state.update { it.copy(heard = null, reply = "") }
    }

    override fun voicePermissionResult(granted: Boolean) {
        if (_state.value.turn != TurnState.NeedsPermission) return
        if (granted) listenOnce(TurnSource.Ptt) else _state.update { it.copy(turn = TurnState.Idle, error = TurnError.PermissionDenied) }
    }

    /** The screen came back (settings, permission dialog): a microphone permission that is granted now ends the Microphone fault. */
    override fun refreshVoicePermission() {
        if (!voiceInput.hasPermission()) return
        val s = _state.value
        if (s.turn == TurnState.NeedsPermission) listenOnce(TurnSource.Ptt)
        else if (s.error == TurnError.PermissionDenied) {
            HeyLog.i(TAG, "microphone permission granted, fault cleared")
            _state.update { it.copy(error = null) }
        }
    }

    /** C1: the microphone was refused after the consent. C8 (microphone) shows with its "권한 열기" button (planner F1). */
    fun microphoneDeniedAtOnboarding() {
        _state.update { it.copy(error = TurnError.PermissionDenied) }
    }

    // ---------------------------------------------------------------- local UI state

    private inner class Hands : ParkedCommands() {
        override fun openSettings() {
            if (_state.value.driving != DrivingStatus.Parked) return
            _state.update { it.copy(panel = Panel.Settings) }
        }
    }

    override fun closeSettings() = _state.update { it.copy(panel = Panel.None, settingsSave = SaveResult.Saved) }

    override fun updateSettings(transform: (CarSettings) -> CarSettings) {
        scope.launch {
            val result = storage.updateSettings(transform)
            _state.update { it.copy(settingsSave = result) }
        }
    }
}

const val AUTO_LISTEN_MILLIS = 5_000L

/** A source whose reply text never shows as a subtitle and that never opens the automatic listening: a friend line, an onboarding line. */
private fun TurnSource.quiet(): Boolean = this == TurnSource.Friend || this == TurnSource.Onboarding

private const val ONBOARDING_RETRY_MILLIS = 2_000L
private const val ONBOARDING_LINE_TIMEOUT_MILLIS = 45_000L
private const val ONBOARDING_ACK_TIMEOUT_MILLIS = 15_000L

/** The whale's short answer (or "괜찮아, 다음에 알려 줘") after an answer: the server makes it in about 2.5 s. */
private const val ONBOARDING_ANSWER_LINE_MILLIS = 12_000L

/** A line that could not be spoken is tried this many times, then the step goes on with the subtitle text only. */
private const val ONBOARDING_LINE_TRIES = 2

/** Two silent listenings in a row move a question on with the whale's "괜찮아, 다음에 알려 줘". */
private const val ONBOARDING_MAX_SILENCES = 2

/** Without a server link a spoken step waits this long (the link may come back), then shows its text and goes on. */
private const val ONBOARDING_OFFLINE_GRACE_MILLIS = 4_000L
private const val ONBOARDING_OFFLINE_HOLD_MILLIS = 3_000L
private const val ONBOARDING_ANSWER_MAX_CHARS = 40
const val CTX_DEBOUNCE_MILLIS = 1_000L

/** How long a friend's message text stays on C3 after it arrived. */
const val FRIEND_NOTE_MILLIS = 8_000L

/** A sent turn with no `reply.start` for this long ends with the server fault (PLEOS-134). */
const val REPLY_START_TIMEOUT_MILLIS = 12_000L

/** A reply that goes quiet (no frame, no `reply.end`) for this long ends the turn (PLEOS-134). */
const val REPLY_END_TIMEOUT_MILLIS = 30_000L
