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

    fun release()
}

/**
 * A recognizer that returns a given sentence instead of listening: the emulator's microphone is silent, so the demo
 * and the tests inject the text. The text comes from [text] at the moment of each call, so it can be swapped while
 * the app runs (the debug build wires it to a bundled fixture and an adb broadcast, see app/src/e2e).
 */
class ScriptedVoiceInput(
    @Volatile var permission: Boolean = true,
    /** How long the "listening" state lasts before the text is "heard". */
    private val listenMillis: Long = 0,
    /** The sentence to return; null or blank is [VoiceResult.NoSpeech]. */
    @Volatile var text: String? = null,
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

    override fun release() = Unit
}

/** Used where no recognizer exists (previews): every attempt reports the engine as unavailable. */
object NoVoiceInput : VoiceInput {
    override fun hasPermission(): Boolean = true
    override suspend fun listen(): VoiceResult = VoiceResult.Unavailable("NO_RECOGNIZER")
    override fun release() = Unit
}
