package ai.hey.vehicle

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

/** [PcmSink] on an [AudioTrack]: USAGE_ASSISTANT, s16le mono, streaming. */
class AudioTrackSink : PcmSink {
    @Volatile private var track: AudioTrack? = null

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
        try { t?.release() } catch (e: IllegalStateException) { }
    }

    companion object {
        val ASSISTANT: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    }
}

/**
 * USAGE_ASSISTANT focus with GAIN_TRANSIENT_MAY_DUCK. Any loss, ducking included, calls [onLoss]: the app stops and does
 * not rely on the system ducking it (D-14).
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
 * [OtherAudioProbe] on [AudioManager]: music is active, or any active playback of a usage the driver must hear (media,
 * navigation guidance, call, accessibility). Asked only while the character is silent, so its own audio is not counted.
 */
class AndroidOtherAudioProbe(context: Context) : OtherAudioProbe {
    private val audio = context.applicationContext.getSystemService(AudioManager::class.java)

    override fun otherAppPlaying(): Boolean {
        if (audio.isMusicActive) return true
        return audio.activePlaybackConfigurations.any { it.audioAttributes.usage in HELD_USAGES }
    }

    private companion object {
        val HELD_USAGES = setOf(
            AudioAttributes.USAGE_MEDIA,
            AudioAttributes.USAGE_GAME,
            AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE,
            AudioAttributes.USAGE_VOICE_COMMUNICATION,
            AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING,
            AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY,
        )
    }
}
