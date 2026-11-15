package ai.hey.vehicle

import ai.hey.model.AudioSpec
import ai.hey.model.HeyLog
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "ReplyAudio"
private const val POLL_MS = 10L
private const val DRAIN_TIMEOUT_MS = 5_000L

/** The speaker side of a reply: blocking PCM output with a play position. [AudioTrackSink] is the real one. */
interface PcmSink {
    /** Opens the output for 16-bit mono at [sampleRate] and starts it. Throws when the device cannot. */
    fun start(sampleRate: Int)

    /** Blocks until the bytes are accepted (this is what keeps the writer at real-time speed). Returns bytes written or a negative error. */
    fun write(buf: ByteArray, offset: Int, length: Int): Int

    /** Frames the speaker has played so far. */
    fun playedFrames(): Long

    /** Silences the output right now and unblocks a [write] in progress. Safe to call more than once. */
    fun stopNow()

    fun release()
}

/** Audio focus for the assistant voice. [onLoss] fires on ANY loss, ducking included: the app does not rely on ducking (D-14). */
interface FocusGate {
    /** True when the focus is held. */
    fun request(onLoss: () -> Unit): Boolean
    fun abandon()
}

/** Whether another app is playing audio right now (music, guidance, a call): the character never starts a line over it (PLEOS-171). */
fun interface OtherAudioProbe {
    fun otherAppPlaying(): Boolean
}

/**
 * Plays the server's PCM reply (s16le mono) and reports the mouth level. Pure JVM so the stop-on-focus-loss and
 * RMS rules are unit tested; Android supplies [PcmSink] and [FocusGate].
 *
 * - The output is fed in 30 ms windows; each window's RMS is turned into a mouth level (D-13) and the level shown is the
 *   one of the window under the play position, not the one being written.
 * - Focus lost at any time: the output is cut at once ([PcmSink.stopNow]), the listener hears [PlaybackEnd.FocusLost],
 *   and nothing is resumed (D-14).
 */
class StreamingReplyAudio(
    private val newSink: (ReplyVoice) -> PcmSink,
    private val focus: FocusGate,
    private val thresholds: MouthThresholds = MouthThresholds.PCM,
) : ReplyAudio {
    @Volatile override var listener: PlaybackListener? = null

    private val lock = Any()
    private var session: Session? = null

    private inner class Session(val sampleRate: Int, voice: ReplyVoice) {
        val sink: PcmSink = newSink(voice)
        val chunks = LinkedBlockingQueue<ByteArray>()
        val windowBytes = PcmRms.windowBytes(sampleRate)
        val windowFrames = windowBytes / 2
        val levels = ArrayList<Int>()
        val ended = AtomicBoolean(false)

        @Volatile var finishing = false
        @Volatile var written = 0L // frames handed to the sink
        var writer: Thread? = null
        var monitor: Thread? = null

        fun end(reason: PlaybackEnd) {
            if (!ended.compareAndSet(false, true)) return
            sink.stopNow()
            focus.abandon()
            writer?.interrupt()
            monitor?.interrupt()
            val l = listener
            l?.onMouth(0)
            l?.onEnded(reason)
            Thread { runCatching { sink.release() } }.start()
        }
    }

    override fun begin(spec: AudioSpec, voice: ReplyVoice) {
        stop()
        if (spec !is AudioSpec.Pcm || spec.channels != 1) return
        val s = Session(spec.sampleRate, voice)
        synchronized(lock) { session = s }
        if (!focus.request { s.end(PlaybackEnd.FocusLost) }) {
            HeyLog.w(TAG, "audio focus refused, not playing")
            s.end(PlaybackEnd.FocusLost)
            return
        }
        try {
            s.sink.start(spec.sampleRate)
        } catch (t: Throwable) {
            HeyLog.w(TAG, "output not available: ${t.javaClass.simpleName}")
            s.end(PlaybackEnd.Failed)
            return
        }
        s.writer = Thread({ runWriter(s) }, "reply-writer").also { it.isDaemon = true; it.start() }
        s.monitor = Thread({ runMonitor(s) }, "reply-mouth").also { it.isDaemon = true; it.start() }
    }

    override fun write(pcm: ByteArray) {
        synchronized(lock) { session }?.takeIf { !it.ended.get() }?.chunks?.put(pcm)
    }

    override fun finish() {
        synchronized(lock) { session }?.finishing = true
    }

    override fun stop() {
        val s = synchronized(lock) { session.also { session = null } } ?: return
        s.end(PlaybackEnd.Stopped)
    }

    private fun runWriter(s: Session) {
        val pending = java.io.ByteArrayOutputStream()
        fun emit(window: ByteArray, length: Int) {
            val level = thresholds.level(PcmRms.rms(window, 0, length))
            synchronized(s.levels) { s.levels += level }
            var off = 0
            while (off < length && !s.ended.get()) {
                val n = s.sink.write(window, off, length - off)
                if (n < 0) {
                    HeyLog.w(TAG, "output write failed")
                    s.end(PlaybackEnd.Failed)
                    return
                }
                off += n
            }
            s.written += length / 2
        }
        try {
            while (!s.ended.get()) {
                val chunk = s.chunks.poll(POLL_MS, TimeUnit.MILLISECONDS)
                if (chunk != null) {
                    pending.write(chunk)
                    var all = pending.toByteArray()
                    var start = 0
                    while (all.size - start >= s.windowBytes && !s.ended.get()) {
                        emit(all.copyOfRange(start, start + s.windowBytes), s.windowBytes)
                        start += s.windowBytes
                    }
                    pending.reset()
                    pending.write(all, start, all.size - start)
                } else if (s.finishing && s.chunks.isEmpty()) {
                    val rest = pending.toByteArray()
                    val even = rest.size - rest.size % 2
                    if (even > 0) emit(rest, even)
                    // Everything is handed over: wait until it has been heard, then done.
                    val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(DRAIN_TIMEOUT_MS)
                    while (!s.ended.get() && s.sink.playedFrames() < s.written && System.nanoTime() < deadline) Thread.sleep(POLL_MS)
                    s.end(PlaybackEnd.Completed)
                    return
                }
            }
        } catch (e: InterruptedException) {
            // ended from outside
        } catch (t: Throwable) {
            HeyLog.w(TAG, "writer failed: ${t.javaClass.simpleName}")
            s.end(PlaybackEnd.Failed)
        }
    }

    private fun runMonitor(s: Session) {
        var last = 0
        var sounded = false
        try {
            while (!s.ended.get()) {
                val head = s.sink.playedFrames()
                if (head > 0 && !sounded) {
                    sounded = true
                    listener?.onFirstSound()
                }
                val level = if (head <= 0) 0 else synchronized(s.levels) { s.levels.getOrElse((head / s.windowFrames).toInt()) { 0 } }
                if (level != last) {
                    last = level
                    listener?.onMouth(level)
                }
                Thread.sleep(POLL_MS)
            }
        } catch (e: InterruptedException) {
            // ended
        }
    }
}
