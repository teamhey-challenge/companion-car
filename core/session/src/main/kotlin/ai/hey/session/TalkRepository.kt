package ai.hey.session

import ai.hey.model.AudioSpec
import ai.hey.model.VehicleSignals
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
import ai.hey.vehicle.SpeechSettings
import ai.hey.vehicle.VehicleEvent
import ai.hey.vehicle.VehicleGateway
import ai.hey.vehicle.VoiceInput
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
    @Volatile private var turnEndSentAt = 0L
    @Volatile private var pcmMouth = 0
    @Volatile private var pleosMouth = 0

    /** Id of the Pleos reading of the reply that is on now (its [VehicleEvent.SpeechDone] ends the turn on screen). */
    @Volatile private var speakingId: String? = null
    private var watchdog: Job? = null

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
                _state.update { it.copy(driving = d, panel = if (d == DrivingStatus.Parked) it.panel else Panel.None) }
            }
        }
        scope.launch {
            storage.consents.collect { c -> _state.update { it.copy(needsOnboarding = !c.recordDone) } }
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
                    audio?.begin(frame.audio)
                    armWatchdog(frame.turnId, REPLY_END_TIMEOUT_MILLIS)
                    _state.update { it.copy(turn = TurnState.Replying) }
                }
                is ServerFrame.ReplyDelta -> if (frame.turnId == turnId) {
                    armWatchdog(frame.turnId, REPLY_END_TIMEOUT_MILLIS)
                    replyText.append(frame.text)
                    _state.update { it.copy(reply = replyText.toString()) }
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
                is ServerFrame.Settings -> companion?.onServerSettings(frame.settings)
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
        _state.update { it.copy(reply = frame.text) }
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

    /** The reply was read to the end. A question opens the microphone for 5 s (F4-F6), unless navigation is guiding. */
    private fun afterReply() {
        endSpeaking()
        val s = _state.value
        val question = replyText.toString().trimEnd().let { it.endsWith("?") || it.endsWith("？") }
        if (!question || s.turn != TurnState.Idle) return
        if (s.source == TurnSource.AutoListen) {
            // PLEOS-79: at most one automatic listening per driver-started turn, or a repeating voice loops forever.
            HeyLog.i(TAG, "talk ignored: auto-listen skipped, the turn was itself an auto-listen")
            return
        }
        val why = when {
            s.needsOnboarding -> "consent not recorded"
            !s.online -> "offline"
            vehicle.signals.value.naviGuiding -> "navigation guiding"
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
            s.turn != TurnState.Idle -> "busy (${s.turn})"
            vehicle.signals.value.naviGuiding -> "navigation guiding"
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
        turnId = frame.id
        turnEndSentAt = clock()
        _state.update { it.copy(turn = TurnState.Replying, source = TurnSource.Proactive, heard = null, reply = "", error = null, audioFailed = false) }
        armWatchdog(frame.id, REPLY_START_TIMEOUT_MILLIS)
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

    // ---------------------------------------------------------------- talking

    override fun startTalk() {
        val s = _state.value
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

    override fun cancelTalk() {
        voiceJob?.cancel()
        turnId?.let { link.send(ClientFrames.turnCancel(it)) }
        silence()
        endTurn(null)
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
const val CTX_DEBOUNCE_MILLIS = 1_000L

/** A sent turn with no `reply.start` for this long ends with the server fault (PLEOS-134). */
const val REPLY_START_TIMEOUT_MILLIS = 12_000L

/** A reply that goes quiet (no frame, no `reply.end`) for this long ends the turn (PLEOS-134). */
const val REPLY_END_TIMEOUT_MILLIS = 30_000L
