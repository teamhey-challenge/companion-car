package ai.hey.vehicle

import ai.hey.model.AudioSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.sin

/** s16le mono tone of [ms] milliseconds at [amplitude] (fraction of full scale). */
private fun tone(ms: Int, amplitude: Double, rate: Int = 24_000): ByteArray {
    val n = rate * ms / 1000
    val out = ByteArray(n * 2)
    for (i in 0 until n) {
        val v = (sin(2 * PI * 220 * i / rate) * amplitude * 32767).toInt()
        out[2 * i] = (v and 0xFF).toByte()
        out[2 * i + 1] = ((v shr 8) and 0xFF).toByte()
    }
    return out
}

class MouthLevelTest {
    @Test fun `rms of a full scale square wave is one and silence is zero`() {
        val square = ByteArray(960 * 2) { i -> if (i % 2 == 0) 0xFF.toByte() else 0x7F }
        assertEquals(1.0, PcmRms.rms(square, 0, square.size), 0.001)
        assertEquals(0.0, PcmRms.rms(ByteArray(1440), 0, 1440), 0.0)
    }

    @Test fun `rms of a sine is amplitude over root two`() {
        val t = tone(30, 0.5)
        assertEquals(0.5 / Math.sqrt(2.0), PcmRms.rms(t, 0, t.size), 0.01)
    }

    @Test fun `a 30 ms window at 24 kHz is 720 samples`() {
        assertEquals(1440, PcmRms.windowBytes(24_000))
    }

    @Test fun `rms maps to mouth 0 to 3 at the configured edges`() {
        val th = MouthThresholds(0.02, 0.06, 0.12)
        assertEquals(listOf(0, 0, 1, 1, 2, 2, 3, 3), listOf(0.0, 0.0199, 0.02, 0.0599, 0.06, 0.1199, 0.12, 0.9).map(th::level))
        // end to end: a 0.5 amplitude tone is loud (3), a 0.05 one is soft (1), digital silence is shut (0)
        val levels = listOf(0.5, 0.05, 0.0).map { a -> tone(30, a).let { th.level(PcmRms.rms(it, 0, it.size)) } }
        assertEquals(listOf(3, 1, 0), levels)
    }

    @Test(expected = IllegalArgumentException::class) fun `thresholds must not decrease`() {
        MouthThresholds(0.1, 0.05, 0.2)
    }
}

/**
 * A sink whose write blocks like a real one when asked. The play position is the number of frames written, unless a test
 * takes it over with [manualHead]: then it moves only when the test says so, which makes the mouth level independent of
 * thread timing.
 */
private class FakeSink : PcmSink {
    val frames = AtomicLong(0)
    val stopped = AtomicBoolean(false)
    val stoppedAtNanos = AtomicLong(0)
    var started = CountDownLatch(1)
    @Volatile var manualHead: Long? = null
    override fun start(sampleRate: Int) { started.countDown() }
    override fun write(buf: ByteArray, offset: Int, length: Int): Int {
        if (stopped.get()) return -1
        Thread.sleep(1) // a little back-pressure
        frames.addAndGet((length / 2).toLong())
        return length
    }
    override fun playedFrames(): Long = manualHead ?: frames.get()
    override fun stopNow() { stoppedAtNanos.compareAndSet(0, System.nanoTime()); stopped.set(true) }
    override fun release() = Unit
}

/** Waits (up to 5 s) for [condition]; true as soon as it holds. Waiting for a fact, not sampling a moving value. */
private fun awaitTrue(condition: () -> Boolean): Boolean {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
    while (System.nanoTime() < deadline) {
        if (condition()) return true
        Thread.sleep(1)
    }
    return condition()
}

private class FakeFocus(val grant: Boolean = true) : FocusGate {
    @Volatile var onLoss: (() -> Unit)? = null
    val abandoned = AtomicBoolean(false)
    override fun request(onLoss: () -> Unit): Boolean { this.onLoss = onLoss; return grant }
    override fun abandon() { abandoned.set(true) }
}

private class Rec : PlaybackListener {
    val mouth = CopyOnWriteArrayList<Int>()
    val ended = CopyOnWriteArrayList<PlaybackEnd>()
    val endedLatch = CountDownLatch(1)
    val firstSound = CountDownLatch(1)
    override fun onFirstSound() = firstSound.countDown()
    override fun onMouth(level: Int) { mouth += level }
    override fun onEnded(reason: PlaybackEnd) { ended += reason; endedLatch.countDown() }
}

class StreamingReplyAudioTest {
    private val pcm = AudioSpec.Pcm(24_000, 1)

    private fun rig(focus: FocusGate = FakeFocus()): Triple<StreamingReplyAudio, FakeSink, Rec> {
        val sink = FakeSink()
        val audio = StreamingReplyAudio({ sink }, focus)
        val rec = Rec().also { audio.listener = it }
        return Triple(audio, sink, rec)
    }

    @Test fun `plays everything then completes and the mouth follows the loudness`() {
        val (audio, sink, rec) = rig()
        sink.manualHead = 0 // the speaker "plays" only when the test moves the head
        audio.begin(pcm)
        audio.write(tone(300, 0.5) + tone(300, 0.05) + tone(300, 0.0))
        audio.finish()
        val window = 720L // 30 ms at 24 kHz
        assertTrue("all frames handed to the sink", awaitTrue { sink.frames.get() == 24_000L * 900 / 1000 })
        // loud window under the head -> 3
        sink.manualHead = 1
        assertTrue("saw loud: ${rec.mouth}", awaitTrue { 3 in rec.mouth })
        // the head moves into the soft part (windows 10..19) -> 1
        sink.manualHead = window * 10 + 1
        assertTrue("saw soft: ${rec.mouth}", awaitTrue { 1 in rec.mouth })
        // then into the silent part (windows 20..29) -> 0
        sink.manualHead = window * 20 + 1
        assertTrue("shut again: ${rec.mouth}", awaitTrue { rec.mouth.lastOrNull() == 0 })
        // everything heard -> completes
        sink.manualHead = 24_000L * 900 / 1000
        assertTrue(rec.endedLatch.await(5, TimeUnit.SECONDS))
        assertEquals(listOf(PlaybackEnd.Completed), rec.ended.toList())
        val m = rec.mouth.toList()
        assertEquals("ends shut: $m", 0, m.last())
        assertTrue("loud (3) came before soft (1): $m", m.indexOf(3) in 0 until m.indexOf(1))
    }

    @Test fun `focus loss stops the output within half a second and never resumes`() {
        val focus = FakeFocus()
        val (audio, sink, rec) = rig(focus)
        audio.begin(pcm)
        audio.write(tone(5000, 0.3)) // far more than will be played
        assertTrue(sink.started.await(1, TimeUnit.SECONDS))
        Thread.sleep(100)
        val lostAt = System.nanoTime()
        focus.onLoss!!.invoke()
        assertTrue(rec.endedLatch.await(500, TimeUnit.MILLISECONDS))
        assertEquals(listOf(PlaybackEnd.FocusLost), rec.ended.toList())
        val stopMs = (sink.stoppedAtNanos.get() - lostAt) / 1_000_000
        assertTrue("sink silenced $stopMs ms after the loss", stopMs in 0..500)
        val framesAtStop = sink.frames.get()
        audio.write(tone(300, 0.3)) // late chunk and a "focus regained" must not restart anything
        audio.finish()
        Thread.sleep(200)
        assertEquals(framesAtStop, sink.frames.get())
        assertEquals(1, rec.ended.size)
        assertTrue(focus.abandoned.get())
    }

    @Test fun `no focus means nothing is played`() {
        val (audio, sink, rec) = rig(FakeFocus(grant = false))
        audio.begin(pcm)
        audio.write(tone(300, 0.3))
        assertTrue(rec.endedLatch.await(500, TimeUnit.MILLISECONDS))
        assertEquals(listOf(PlaybackEnd.FocusLost), rec.ended.toList())
        assertEquals(0L, sink.frames.get())
    }

    @Test fun `stop cuts at once and is safe twice`() {
        val (audio, sink, rec) = rig()
        audio.begin(pcm)
        audio.write(tone(5000, 0.3))
        assertTrue(sink.started.await(1, TimeUnit.SECONDS))
        audio.stop()
        audio.stop()
        assertTrue(rec.endedLatch.await(500, TimeUnit.MILLISECONDS))
        assertEquals(listOf(PlaybackEnd.Stopped), rec.ended.toList())
        assertTrue(sink.stopped.get())
    }

    @Test fun `audio none opens no output`() {
        val sink = FakeSink()
        val audio = StreamingReplyAudio({ sink }, FakeFocus())
        audio.begin(AudioSpec.None)
        audio.write(tone(100, 0.3))
        assertFalse(sink.started.await(100, TimeUnit.MILLISECONDS))
    }
}
