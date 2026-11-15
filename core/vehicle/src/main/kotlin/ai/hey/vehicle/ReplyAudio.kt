package ai.hey.vehicle

import ai.hey.model.AudioSpec

/** Why a reply's playback ended. */
enum class PlaybackEnd {
    /** Everything the server sent was played. */
    Completed,

    /** [ReplyAudio.stop] (new button press, cancel, lost connection). */
    Stopped,

    /** Another app took the audio focus (navigation guidance, call, ...): stopped at once and never resumed. */
    FocusLost,

    /** No audio focus could be had or the output failed; nothing (more) was played. */
    Failed,
}

interface PlaybackListener {
    /** The first sample became audible (output head moved). */
    fun onFirstSound()

    /** Mouth level 0..3 for what is audible right now; called only when the level changes. */
    fun onMouth(level: Int)

    /** Called once per [ReplyAudio.begin]. */
    fun onEnded(reason: PlaybackEnd)
}

/** Whose voice a reply is. The onboarding whale gets a light reverb (planner request 506); heyri stays dry. */
enum class ReplyVoice { Heyri, Whale }

/** Sink for the server's PCM s16le mono reply audio (developer.md 6.2 / 6.5b). */
interface ReplyAudio {
    var listener: PlaybackListener?

    /** A reply with [spec] audio starts in [voice]. Anything still playing from before is cut. */
    fun begin(spec: AudioSpec, voice: ReplyVoice = ReplyVoice.Heyri)
    fun write(pcm: ByteArray)

    /** The server sent the last chunk: play what is queued, then [PlaybackListener.onEnded] with [PlaybackEnd.Completed]. */
    fun finish()

    /** Cut now. Safe to call at any time and more than once. */
    fun stop()
}
