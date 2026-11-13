package ai.hey.session

import ai.hey.model.AudioSpec
import ai.hey.model.CarSettings
import ai.hey.model.ClientFrames
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
            vehicle.mouth.collect { level ->
                pleosMouth = level
                publishMouth()
            }
        }
        scope.launch {
            vehicle.events.collect { e ->
                val mine = speakingId ?: return@collect
                val id = when (e) {
                    is VehicleEvent.SpeechDone -> e.speechId
                    is VehicleEvent.SpeechDropped -> e.speechId
                    else -> return@collect
                }
                if (id == mine) finishSpeaking()
            }
        }
    }

    fun stop() {
        voiceJob?.cancel()
        link.close()
        silence()
        started = false
    }

    private fun publishMouth() {
        val level = maxOf(pcmMouth, pleosMouth)
        _state.update { if (it.mouth == level) it else it.copy(mouth = level) }
    }

    private inner class LinkListener : TalkTransport.Listener {
        override fun onConnection(status: ConnectionStatus) {
            _state.update { it.copy(connection = status) }
            if (status != ConnectionStatus.Online) onServerGone()
        }

        override fun onFrame(frame: ServerFrame) {
            when (frame) {
                is ServerFrame.ReplyStart -> if (frame.turnId == turnId) {
                    replyAudio = frame.audio
                    HeyLog.i(TAG, "reply.start audio=${frame.audio} ${clock() - turnEndSentAt} ms after turn.end")
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
                    if (frame.turnId == null || frame.turnId == turnId) {
                        silence()
                        endTurn(TurnError.Server)
                    }
                }
                is ServerFrame.Proactive -> HeyLog.i(TAG, "proactive ${frame.reason} (not handled yet)")
            }
        }

        override fun onAudio(pcm: ByteArray) {
            if (turnId != null && replyAudio is AudioSpec.Pcm) audio?.write(pcm)
        }
    }

    private fun onReplyEnd(frame: ServerFrame.ReplyEnd) {
        HeyLog.i(TAG, "reply.end chars=${frame.text.length} firstTokenMs=${frame.firstTokenMs} totalMs=${frame.totalMs} text=${frame.text}")
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
        if (speechFocus?.request { vehicle.stopSpeech() } == false) {
            HeyLog.w(TAG, "audio focus refused, not reading")
            speakingId = null
            return endSpeaking()
        }
        vehicle.speak(text, SpeechPriority.High, speechId)
    }

    private fun finishSpeaking() {
        speakingId = null
        speechFocus?.abandon()
        endSpeaking()
    }

    private fun endSpeaking() {
        _state.update { if (it.turn == TurnState.Replying) it.copy(turn = TurnState.Idle) else it }
    }

    private fun onPlaybackEnded(reason: PlaybackEnd) {
        HeyLog.i(TAG, "playback ended: $reason")
        if (reason == PlaybackEnd.FocusLost) {
            // D-14: someone else needs the speaker. Stop everything of ours and do not resume.
            vehicle.stopSpeech()
            turnId = null
        }
        if (reason != PlaybackEnd.Stopped) _state.update { if (it.turn == TurnState.Replying) it.copy(turn = TurnState.Idle) else it }
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
        audio?.stop()
        vehicle.stopSpeech()
        speakingId = null
        speechFocus?.abandon()
        pcmMouth = 0
        pleosMouth = 0
        publishMouth()
    }

    private fun endTurn(error: TurnError?) {
        turnId = null
        _state.update { it.copy(turn = TurnState.Idle, error = error) }
    }

    // ---------------------------------------------------------------- talking

    override fun startTalk() {
        val s = _state.value
        when (s.turn) {
            TurnState.Idle -> Unit
            // D-24: the character is talking and the driver presses: stop the voice at once and listen.
            TurnState.Replying -> {
                silence()
                endTurn(null)
            }
            else -> return
        }
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
        replyAudio = AudioSpec.None
        val ok = link.send(ClientFrames.turnStart(id)) &&
            link.send(ClientFrames.text(id, text)) &&
            link.send(ClientFrames.turnEnd(id))
        if (!ok) {
            HeyLog.w(TAG, "turn not sent: not connected")
            endTurn(TurnError.NotConnected)
            return
        }
        turnEndSentAt = clock()
        turnId = id
        HeyLog.i(TAG, "turn sent chars=${text.length} text=$text")
        _state.update { it.copy(turn = TurnState.Thinking, heard = text) }
    }

    override fun cancelTalk() {
        voiceJob?.cancel()
        turnId?.let { link.send(ClientFrames.turnCancel(it)) }
        silence()
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
