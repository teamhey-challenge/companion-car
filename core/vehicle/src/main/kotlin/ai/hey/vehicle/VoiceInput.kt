package ai.hey.vehicle

import kotlinx.coroutines.delay

/** What one push-to-talk recognition produced. */
sealed interface VoiceResult {
    /** [text] is the recognized Korean sentence, trimmed and never blank. */
    data class Text(val text: String) : VoiceResult

    /** The microphone was open but no speech was recognized. */
    data object NoSpeech : VoiceResult

    /** The microphone permission is missing (or the engine refused for lack of it). */
    data object PermissionDenied : VoiceResult

    /** The recognizer itself could not run (service missing, engine error); [reason] is a log-safe code. */
    data class Unavailable(val reason: String) : VoiceResult
}

/**
 * Korean speech recognition for the driver's voice reply, one utterance per [listen]. Cancelling the coroutine that
 * runs [listen] stops the microphone. Only the parked screens start it (docs/architecture.md section 6).
 */
interface VoiceInput {
    /** The runtime microphone permission (RECORD_AUDIO) is granted; [listen] without it answers [VoiceResult.PermissionDenied]. */
    fun hasPermission(): Boolean

    suspend fun listen(): VoiceResult

    /**
     * The driver ended the sentence by hand (button released / pressed again): the running [listen] returns what was heard
     * so far (NoSpeech when nothing). No-op when nothing is listening.
     */
    fun finishListening() = Unit

    /**
     * The automatic listening that follows an answer (F4-F6, D-48 4): the same recognition as [listen], plus [onSpeechStart] the
     * moment the first words are heard. The caller times only the wait for that call; once speech has started the recognizer
     * is left to hear the sentence to its end (PLEOS-194 follow-up listening).
     */
    suspend fun listenAfterReply(onSpeechStart: () -> Unit = {}): VoiceResult = listen()

    fun release()
}

/**
 * A recognizer that returns a given sentence instead of listening, for tests and for the debug builds' injected override
 * (wrapped by [InjectableVoiceInput]; the debug builds listen to the real microphone unless a sentence is injected).
 * The text comes from [text] at the moment of each call, so it can be swapped while the app runs.
 */
class ScriptedVoiceInput(
    @Volatile var permission: Boolean = true,
    /** How long the "listening" state lasts before the text is "heard". */
    private val listenMillis: Long = 0,
    /** The sentence to return; null or blank is [VoiceResult.NoSpeech]. */
    @Volatile var text: String? = null,
    /** The demo voice repeats one fixed sentence; the automatic listening after a question must hear nothing, or it answers itself in a loop. */
    private val silentAfterReply: Boolean = false,
) : VoiceInput {
    @Volatile var listens = 0
        private set

    override fun hasPermission(): Boolean = permission

    override suspend fun listen(): VoiceResult {
        listens++
        if (!permission) return VoiceResult.PermissionDenied
        if (listenMillis > 0) delay(listenMillis)
        val heard = text?.trim().orEmpty()
        return if (heard.isEmpty()) VoiceResult.NoSpeech else VoiceResult.Text(heard)
    }

    override suspend fun listenAfterReply(onSpeechStart: () -> Unit): VoiceResult {
        if (!silentAfterReply) return listen().also { if (it is VoiceResult.Text) onSpeechStart() }
        listens++
        if (!permission) return VoiceResult.PermissionDenied
        if (listenMillis > 0) delay(listenMillis)
        return VoiceResult.NoSpeech
    }

    override fun release() = Unit
}

/** Used where no recognizer exists (previews): every attempt reports the engine as unavailable. */
object NoVoiceInput : VoiceInput {
    override fun hasPermission(): Boolean = true
    override suspend fun listen(): VoiceResult = VoiceResult.Unavailable("NO_RECOGNIZER")
    override fun release() = Unit
}
