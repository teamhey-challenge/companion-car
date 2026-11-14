package ai.hey.vehicle

/**
 * Debug/e2e wrapper: the real recognizer ([real], Pleos SpeechToText) hears the driver unless a sentence was injected.
 *
 *  - [inject] with `sticky = false`: the NEXT [listen] hears [text] ("" = nothing heard), then it is back to the real recognizer;
 *  - [inject] with `sticky = true`: every [listen] hears [text] until [useReal] (E2E suites that repeat a turn);
 *  - [useReal]: drop any override.
 *
 * Nothing is injected by default, so a fresh install always listens to the microphone.
 */
class InjectableVoiceInput(
    private val real: VoiceInput,
    private val scripted: ScriptedVoiceInput = ScriptedVoiceInput(listenMillis = 800, silentAfterReply = true),
) : VoiceInput {
    private data class Override(val text: String, val sticky: Boolean)

    private val lock = Any()
    private var override: Override? = null

    fun inject(text: String, sticky: Boolean = false) = synchronized(lock) { override = Override(text, sticky) }

    fun useReal() = synchronized(lock) { override = null }

    /** True while an injected sentence is waiting (one-shot not yet consumed, or sticky). */
    val injected: Boolean get() = synchronized(lock) { override != null }

    override fun hasPermission(): Boolean = injected || real.hasPermission()

    override suspend fun listen(): VoiceResult {
        val o = synchronized(lock) { override?.also { if (!it.sticky) override = null } }
            ?: return real.listen()
        scripted.text = o.text
        return scripted.listen()
    }

    /** While a sentence is injected the automatic listening after a reply hears nothing (no self-answer loop); otherwise the real microphone. */
    override suspend fun listenAfterReply(): VoiceResult = if (injected) scripted.listenAfterReply() else real.listenAfterReply()

    override fun finishListening() = real.finishListening()

    override fun release() = real.release()
}
