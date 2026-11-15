package ai.hey.vehicle

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.audiofx.EnvironmentalReverb

/**
 * [PcmSink] on an [AudioTrack]: USAGE_ASSISTANT, s16le mono, streaming. The whale's voice goes through a light
 * [EnvironmentalReverb] (planner request 506, `whale_voice.json`); heyri's voice is never given one.
 */
class AudioTrackSink(
    private val voice: ReplyVoice = ReplyVoice.Heyri,
    private val reverb: VoiceReverb = VoiceReverb(AndroidReverbFactory),
) : PcmSink {
    @Volatile private var track: AudioTrack? = null
    @Volatile private var effect: ReverbHandle? = null

    override fun start(sampleRate: Int) {
        val min = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(min > 0) { "no output buffer for $sampleRate Hz" }
        val t = AudioTrack.Builder()
            .setAudioAttributes(ASSISTANT)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            // A little over the minimum (about 250 ms at 24 kHz) so one late network chunk does not underrun.
            .setBufferSizeInBytes(maxOf(min, sampleRate / 4 * 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        check(t.state == AudioTrack.STATE_INITIALIZED) { "AudioTrack not initialised" }
        track = t
        effect = reverb.attach(voice) { id -> t.attachAuxEffect(id) == AudioTrack.SUCCESS && t.setAuxEffectSendLevel(1f) == AudioTrack.SUCCESS }
        t.play()
    }

    override fun write(buf: ByteArray, offset: Int, length: Int): Int {
        val t = track ?: return -1
        return t.write(buf, offset, length, AudioTrack.WRITE_BLOCKING)
    }

    override fun playedFrames(): Long {
        val t = track ?: return 0
        return try { t.playbackHeadPosition.toLong() and 0xFFFFFFFFL } catch (e: IllegalStateException) { 0 }
    }

    override fun stopNow() {
        val t = track ?: return
        try {
            t.pause()
            t.flush()
        } catch (e: IllegalStateException) {
            // already released
        }
    }

    override fun release() {
        val t = track
        track = null
        val e = effect
        effect = null
        try { t?.attachAuxEffect(0) } catch (ex: IllegalStateException) { }
        try { e?.release() } catch (ex: RuntimeException) { }
        try { t?.release() } catch (ex: IllegalStateException) { }
    }

    companion object {
        val ASSISTANT: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    }
}

/**
 * The whale's reverb as an auxiliary [EnvironmentalReverb] (session 0) with the values of `whale_voice.json`
 * ("android_EnvironmentalReverb_approx"): a short, dark, light room.
 */
object AndroidReverbFactory : ReverbFactory {
    override fun create(send: AuxSend): ReverbHandle? {
        val fx = EnvironmentalReverb(0, 0)
        try {
            fx.decayTime = 900
            fx.reflectionsDelay = 55
            fx.reverbDelay = 30
            fx.roomLevel = -1000
            fx.roomHFLevel = -1500
            fx.reverbLevel = -1400
            fx.diffusion = 1000
            fx.density = 1000
            fx.enabled = true
            if (!send.attach(fx.id)) {
                fx.release()
                return null
            }
        } catch (t: Throwable) {
            fx.release()
            throw t
        }
        return ReverbHandle { fx.release() }
    }
}

/**
 * USAGE_ASSISTANT focus with GAIN_TRANSIENT_MAY_DUCK. Any loss, ducking included, calls [onLoss]: the app stops and does
 * not rely on the system ducking it.
 */
class AudioFocusGateAndroid(context: Context) : FocusGate {
    private val audio = context.applicationContext.getSystemService(AudioManager::class.java)
    private var request: AudioFocusRequest? = null

    override fun request(onLoss: () -> Unit): Boolean {
        abandon()
        val r = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(AudioTrackSink.ASSISTANT)
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS ||
                    change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
                    change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
                ) {
                    onLoss()
                }
            }
            .build()
        request = r
        return audio.requestAudioFocus(r) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    override fun abandon() {
        val r = request ?: return
        request = null
        audio.abandonAudioFocusRequest(r)
    }
}

/**
 * [OtherAudioProbe] on the active playback configurations. Decided by usage, never by [AudioManager.isMusicActive]:
 * that flag is true for any STREAM_MUSIC playback, and the legacy mapping puts our own USAGE_ASSISTANT voice (Breeze PCM,
 * Pleos TTS) on STREAM_MUSIC, so it would block a line right after our own reply.
 */
class AndroidOtherAudioProbe(context: Context) : OtherAudioProbe {
    private val audio = context.applicationContext.getSystemService(AudioManager::class.java)

    override fun otherAppPlaying(): Boolean =
        HeldAudio.blocks(audio.activePlaybackConfigurations.map { it.audioAttributes.usage })
}

/** Which active playback usages stop the character from starting a line. USAGE_ASSISTANT, our own voice, never does. */
object HeldAudio {
    private val HELD_USAGES = setOf(
        AudioAttributes.USAGE_UNKNOWN,
        AudioAttributes.USAGE_MEDIA,
        AudioAttributes.USAGE_GAME,
        AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE,
        AudioAttributes.USAGE_VOICE_COMMUNICATION,
        AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING,
        AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY,
    )

    fun blocks(activeUsages: Collection<Int>): Boolean = activeUsages.any { it in HELD_USAGES }
}
