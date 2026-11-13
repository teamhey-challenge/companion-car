package ai.hey.session

import ai.hey.model.AudioSpec
import ai.hey.model.CarSettings
import ai.hey.model.ClientFrames
import ai.hey.model.HeyLog
import ai.hey.model.ServerFrame
import ai.hey.model.SpeechPriority
import ai.hey.vehicle.DrivingStatus
import ai.hey.vehicle.SpeechSettings
import ai.hey.vehicle.VehicleGateway
import ai.hey.vehicle.VoiceInput
import ai.hey.vehicle.VoiceResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

private const val TAG = "Talk"

/**
 * Owns the one /v1/talk connection and the vehicle and exposes ONE state. A turn is: Pleos STT hears one sentence ->
 * `turn.start`, `text`, `turn.end` -> the reply streams back as `reply.*` frames.
 *
 * Driving does not interrupt a turn: listening and the turn go on when the car leaves P (AC-4.4-18, AC-4.5-07). Only
 * the hands-on commands ([ParkedCommands]) disappear while the car moves.
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
    /** Receives the server's PCM audio of the current reply (stage 2); null = audio is ignored. */
    private val audio: ReplyAudio? = null,
) : CarCommands {
    private val hands = Hands()
    private val _state = MutableStateFlow(CarUiState(hands = hands))
    val state: StateFlow<CarUiState> = _state.asStateFlow()

    private val link: TalkTransport = transport(LinkListener())

    private var connectJob: Job? = null
    private var voiceJob: Job? = null
    private var started = false

    /** The turn in flight on the socket; frames of another turn are dropped. Guarded by the main flow of callbacks. */
    @Volatile private var turnId: String? = null
    private val replyText = StringBuilder()

    @Volatile private var replyAudio: AudioSpec = AudioSpec.None

    fun start() {
        if (started) return
        started = true
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
    }

    fun stop() {
        voiceJob?.cancel()
        link.close()
        audio?.stop()
        started = false
    }

    private inner class LinkListener : TalkTransport.Listener {
        override fun onConnection(status: ConnectionStatus) {
            _state.update { it.copy(connection = status) }
            if (status != ConnectionStatus.Online && turnId != null) endTurn(TurnError.ConnectionLost)
        }

        override fun onFrame(frame: ServerFrame) {
            when (frame) {
                is ServerFrame.ReplyStart -> if (frame.turnId == turnId) {
                    replyAudio = frame.audio
                    audio?.begin(frame.audio)
                    _state.update { it.copy(turn = TurnState.Replying) }
                }
                is ServerFrame.ReplyDelta -> if (frame.turnId == turnId) {
                    replyText.append(frame.text)
                    _state.update { it.copy(reply = replyText.toString()) }
                }
                is ServerFrame.ReplyEnd -> if (frame.turnId == turnId) onReplyEnd(frame)
                is ServerFrame.Error -> {
                    HeyLog.w(TAG, "server error: ${frame.code}")
                    if (frame.turnId == null || frame.turnId == turnId) endTurn(TurnError.Server)
                }
                is ServerFrame.Proactive -> HeyLog.i(TAG, "proactive ${frame.reason} (not handled yet)")
            }
        }

        override fun onAudio(pcm: ByteArray) {
            if (turnId != null) audio?.write(pcm)
        }
    }

    private fun onReplyEnd(frame: ServerFrame.ReplyEnd) {
        HeyLog.i(TAG, "reply.end chars=${frame.text.length} firstTokenMs=${frame.firstTokenMs} totalMs=${frame.totalMs} text=${frame.text}")
        replyText.setLength(0)
        replyText.append(frame.text)
        turnId = null
        _state.update { it.copy(turn = TurnState.Idle, reply = frame.text) }
        when (replyAudio) {
            // No server voice for this turn: the car's own TTS reads the text.
            AudioSpec.None -> vehicle.speak(frame.text, SpeechPriority.High, null)
            is AudioSpec.Pcm -> audio?.finish()
        }
    }

    private fun endTurn(error: TurnError?) {
        turnId = null
        audio?.stop()
        _state.update { it.copy(turn = TurnState.Idle, error = error) }
    }

    // ---------------------------------------------------------------- talking

    override fun startTalk() {
        val s = _state.value
        if (s.turn != TurnState.Idle) return
        if (!voiceInput.hasPermission()) {
            _state.update { it.copy(turn = TurnState.NeedsPermission, error = null) }
            return
        }
        listenOnce()
    }

    private fun listenOnce() {
        voiceJob?.cancel()
        replyText.setLength(0)
        _state.update { it.copy(turn = TurnState.Listening, heard = null, reply = "", error = null) }
        voiceJob = scope.launch {
            when (val result = voiceInput.listen()) {
                is VoiceResult.Text -> sendTurn(result.text)
                VoiceResult.NoSpeech -> endTurn(TurnError.NoSpeech)
                VoiceResult.PermissionDenied -> endTurn(TurnError.PermissionDenied)
                is VoiceResult.Unavailable -> endTurn(TurnError.SpeechUnavailable)
            }
        }
    }

    private fun sendTurn(text: String) {
        val id = newTurnId()
        val ok = link.send(ClientFrames.turnStart(id)) &&
            link.send(ClientFrames.text(id, text)) &&
            link.send(ClientFrames.turnEnd(id))
        if (!ok) {
            HeyLog.w(TAG, "turn not sent: not connected")
            endTurn(TurnError.NotConnected)
            return
        }
        turnId = id
        HeyLog.i(TAG, "turn sent chars=${text.length} text=$text")
        _state.update { it.copy(turn = TurnState.Thinking, heard = text) }
    }

    override fun cancelTalk() {
        voiceJob?.cancel()
        turnId?.let { link.send(ClientFrames.turnCancel(it)) }
        endTurn(null)
    }

    override fun voicePermissionResult(granted: Boolean) {
        if (_state.value.turn != TurnState.NeedsPermission) return
        if (granted) listenOnce() else _state.update { it.copy(turn = TurnState.Idle, error = TurnError.PermissionDenied) }
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

/** Sink for the server's PCM reply (stage 2 plays it). */
interface ReplyAudio {
    fun begin(spec: AudioSpec)
    fun write(pcm: ByteArray)

    /** The server finished the reply; play what is queued, then done. */
    fun finish()

    /** Cut now (cancel, error, lost connection). */
    fun stop()
}
