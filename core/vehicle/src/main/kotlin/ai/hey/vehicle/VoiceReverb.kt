package ai.hey.vehicle

import ai.hey.model.HeyLog

private const val TAG = "VoiceReverb"

/** A playback's way to take an auxiliary effect: the Android [AudioTrack] sends its sound into effect [attach]. */
fun interface AuxSend {
    /** True when the playback now sends into the effect with this id. */
    fun attach(effectId: Int): Boolean
}

/** A reverb that is running on one playback. */
fun interface ReverbHandle {
    fun release()
}

/** Creates the reverb for one playback; returns null (or throws) when the device cannot. */
fun interface ReverbFactory {
    fun create(send: AuxSend): ReverbHandle?
}

/**
 * Decides which playbacks get the whale's reverb: the whale's, never heyri's. The effect is a courtesy: when the device cannot
 * create it the playback goes on dry, a message is logged once, and playback is never blocked.
 */
class VoiceReverb(private val factory: ReverbFactory) {
    @Volatile private var warned = false

    /** The running effect for a [voice] playback, or null when the voice stays dry or the device has no effect. */
    fun attach(voice: ReplyVoice, send: AuxSend): ReverbHandle? {
        if (voice != ReplyVoice.Whale) return null
        val handle = try {
            factory.create(send)
        } catch (t: Throwable) {
            warnOnce("reverb failed (${t.javaClass.simpleName}), the whale plays dry")
            return null
        }
        if (handle == null) warnOnce("no reverb on this device, the whale plays dry")
        return handle
    }

    private fun warnOnce(message: String) {
        if (warned) return
        warned = true
        HeyLog.w(TAG, message)
    }
}
