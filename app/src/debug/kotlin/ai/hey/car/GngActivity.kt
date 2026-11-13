package ai.hey.car

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInit
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInit
import android.app.Activity
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

private const val TAG = "gng"

/**
 * Go/No-go spike (branch spike/audio-go-no-go, debug builds only, never merged). Driven by intent extras, e.g.
 *   am start -n ai.hey.car/.GngActivity --es cmd mic --ei sec 5 [--es src vr] [--es tts "text"]
 *   cmd=stt (one utterance, label via --es label) | cmd=tts --es text ... | cmd=ttsloop --ei min 30 --ei gap 60 --es text ...
 *   cmd=focus
 * Everything is logged under the tag `gng` (and mirrored on screen).
 */
class GngActivity : Activity() {
    private lateinit var out: TextView
    private val ui = Handler(Looper.getMainLooper())

    @Volatile private var stt: SpeechToText? = null
    @Volatile private var tts: TextToSpeech? = null
    @Volatile private var ttsEvt: ((String) -> Unit)? = null
    @Volatile private var sttEvt: ((String, String?) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        out = TextView(this).apply { textSize = 14f; setTextIsSelectable(true) }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun btn(label: String, extras: Intent.() -> Unit) =
            row.addView(Button(this).apply { text = label; setOnClickListener { run(Intent().apply(extras)) } })
        btn("mic 5s") { putExtra("cmd", "mic"); putExtra("sec", 5) }
        btn("stt") { putExtra("cmd", "stt") }
        btn("tts") { putExtra("cmd", "tts"); putExtra("text", "안녕하세요. 오늘도 안전 운전하세요.") }
        btn("focus") { putExtra("cmd", "focus") }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(row)
            addView(ScrollView(this@GngActivity).apply { addView(out) }, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        setContentView(col)
        log("activity created")
        run(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        run(intent)
    }

    private fun log(msg: String) {
        Log.i(TAG, msg)
        ui.post { out.append(msg + "\n") }
    }

    private fun now() = SystemClock.elapsedRealtime()

    private fun run(i: Intent) {
        val cmd = i.getStringExtra("cmd") ?: return
        log("cmd=$cmd extras=${i.extras?.keySet()?.joinToString()}")
        Thread({
            try {
                when (cmd) {
                    "mic" -> mic(i.getIntExtra("sec", 5), i.getStringExtra("src") ?: "mic", i.getStringExtra("tts"))
                    "stt" -> sttOnce(i.getStringExtra("label") ?: "1")
                    "tts" -> { ttsTurn(i.getStringExtra("text") ?: "안녕하세요", "single") }
                    "ttsloop" -> ttsLoop(i.getIntExtra("min", 30), i.getIntExtra("gap", 60), i.getStringExtra("text") ?: "오늘도 안전 운전하세요. 비가 오니 천천히 가요.")
                    "focus" -> focus()
                    else -> log("unknown cmd $cmd")
                }
            } catch (t: Throwable) {
                log("cmd=$cmd threw ${t.javaClass.simpleName}: ${t.message}")
            }
        }, "gng-$cmd").start()
    }

    // ---- test 1 / 5: AudioRecord 16 kHz mono PCM16, rms/peak per 500 ms window --------------------------------------

    private fun mic(sec: Int, src: String, speakText: String?) {
        val source = if (src == "vr") MediaRecorder.AudioSource.VOICE_RECOGNITION else MediaRecorder.AudioSource.MIC
        val rate = 16000
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(source, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, rate / 2 * 2))
        log("mic[$src] state=${rec.state} minBuf=$min")
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return }
        val t0 = now()
        rec.startRecording()
        log("mic[$src] recordingState=${rec.recordingState} t0=$t0")
        if (speakText != null) Thread({ Thread.sleep(2000); ttsTurn(speakText, "mic-tts") }, "gng-mic-tts").start()
        val buf = ShortArray(rate / 10)
        var reads = 0; var samples = 0L
        var winSq = 0.0; var winN = 0; var winPeak = 0; var winStart = t0
        var allSq = 0.0; var allPeak = 0
        while (now() - t0 < sec * 1000L) {
            val n = rec.read(buf, 0, buf.size)
            if (n <= 0) { log("mic[$src] read returned $n"); break }
            reads++; samples += n
            for (k in 0 until n) {
                val v = buf[k].toInt(); val a = if (v < 0) -v else v
                winSq += v.toDouble() * v; allSq += v.toDouble() * v
                if (a > winPeak) winPeak = a; if (a > allPeak) allPeak = a
            }
            winN += n
            if (now() - winStart >= 500) {
                log("mic[$src] t=+${now() - t0}ms rms=${"%.1f".format(Locale.US, sqrt(winSq / winN))} peak=$winPeak")
                winSq = 0.0; winN = 0; winPeak = 0; winStart = now()
            }
        }
        rec.stop(); rec.release()
        log("mic[$src] DONE reads=$reads samples=$samples overallRms=${"%.2f".format(Locale.US, if (samples > 0) sqrt(allSq / samples) else 0.0)} overallPeak=$allPeak")
    }

    // ---- test 2: Pleos STT ON_DEVICE Locale.KOREA, one utterance --------------------------------------------------------

    private val sttListener = object : ResultListener {
        override fun onUpdated(text: String, isFinal: Boolean) { sttEvt?.invoke(if (isFinal) "final" else "partial", text) }
        override fun onUpdatedEpdData(epdOn: Long, epdOff: Long) { sttEvt?.invoke("epd", "on=$epdOn off=$epdOff") }
        override fun onStartedRecognition() { sttEvt?.invoke("started", null) }
        override fun onEndedRecognition() { sttEvt?.invoke("ended", null) }
        override fun onError(e: ai.pleos.playground.stt.constant.ErrorType) { sttEvt?.invoke("error", e.name) }
        override fun onReady() { sttEvt?.invoke("ready", null) }
        override fun onUpdatedRms(rms: Double) { sttEvt?.invoke("rms", "%.1f".format(Locale.US, rms)) }
    }

    private fun ensureStt(): SpeechToText? {
        stt?.let { return it }
        val latch = CountDownLatch(1)
        var ok = false
        val t0 = now()
        val engine = SpeechToText(applicationContext, SttMode.ON_DEVICE)
        engine.initialize(object : SttInit {
            override fun onSuccess() {
                engine.addListener(sttListener); engine.setLocale(Locale.KOREA); ok = true; latch.countDown()
            }
            override fun onFailure(e: ai.pleos.playground.stt.constant.ErrorType) { log("stt init failed $e"); latch.countDown() }
        })
        latch.await(15, TimeUnit.SECONDS)
        log("stt init ok=$ok +${now() - t0}ms")
        if (ok) stt = engine
        return stt
    }

    private fun sttOnce(label: String) {
        val engine = ensureStt() ?: return
        val done = CountDownLatch(1)
        val t0 = now()
        var finalText: String? = null
        var lastPartial = ""
        var maxRms = 0.0
        var tReady = -1L; var tFirstPartial = -1L; var tFinal = -1L
        sttEvt = { kind, v ->
            val dt = now() - t0
            when (kind) {
                "rms" -> { val r = v!!.toDouble(); if (r > maxRms) maxRms = r }
                "ready" -> { tReady = dt; log("stt#$label onReady +${dt}ms") }
                "partial" -> { if (tFirstPartial < 0) tFirstPartial = dt; lastPartial = v!!; log("stt#$label partial +${dt}ms \"$v\"") }
                "final" -> { tFinal = dt; finalText = v; log("stt#$label FINAL +${dt}ms \"$v\""); done.countDown() }
                "ended" -> { log("stt#$label ended +${dt}ms"); if (finalText == null) done.countDown() }
                "error" -> { log("stt#$label ERROR +${dt}ms $v"); done.countDown() }
                else -> log("stt#$label $kind +${dt}ms ${v ?: ""}")
            }
        }
        log("stt#$label request() t0=$t0")
        engine.request()
        done.await(20, TimeUnit.SECONDS)
        try { engine.stop() } catch (_: Throwable) { }
        sttEvt = null
        log("stt#$label RESULT final=\"${finalText ?: ""}\" lastPartial=\"$lastPartial\" ready=${tReady}ms firstPartial=${tFirstPartial}ms final=${tFinal}ms maxRms=$maxRms")
    }

    // ---- test 3 / 6: Pleos TTS ON_DEVICE Locale.KOREA ---------------------------------------------------------------

    private val ttsListener = object : EventListener {
        override fun onReady() { ttsEvt?.invoke("onReady") }
        override fun onStart() { ttsEvt?.invoke("onStart") }
        override fun onDone() { ttsEvt?.invoke("onDone") }
        override fun onError(e: ai.pleos.playground.tts.constant.ErrorType) { ttsEvt?.invoke("onError ${e.name}") }
        override fun onStop() { ttsEvt?.invoke("onStop") }
        override fun onUpdatedRms(rms: Double) { ttsEvt?.invoke("rms") }
    }

    private fun ensureTts(): TextToSpeech? {
        tts?.let { return it }
        val latch = CountDownLatch(1)
        var ok = false
        val t0 = now()
        val engine = TextToSpeech(applicationContext, TtsMode.ON_DEVICE)
        engine.initialize(object : TtsInit {
            override fun onSuccess() {
                engine.addEventListener(ttsListener); engine.setLocale(Locale.KOREA); ok = true; latch.countDown()
            }
            override fun onFailure(e: ai.pleos.playground.tts.constant.ErrorType) { log("tts init failed $e"); latch.countDown() }
        })
        latch.await(15, TimeUnit.SECONDS)
        log("tts init ok=$ok +${now() - t0}ms")
        if (ok) tts = engine
        return tts
    }

    /** One spoken turn. Returns true when onDone arrived. */
    private fun ttsTurn(text: String, label: String): Boolean {
        val engine = ensureTts() ?: return false
        val done = CountDownLatch(1)
        var ok = false
        val t0 = now()
        var firstRms = -1L
        ttsEvt = { ev ->
            val dt = now() - t0
            if (ev == "rms") { if (firstRms < 0) { firstRms = dt; log("tts[$label] first onUpdatedRms +${dt}ms") } }
            else {
                log("tts[$label] $ev +${dt}ms (abs=${now()})")
                if (ev == "onDone") { ok = true; done.countDown() }
                if (ev.startsWith("onError") || ev == "onStop") done.countDown()
            }
        }
        log("tts[$label] speak() abs=$t0 text=\"$text\"")
        engine.speak(text, true)
        done.await(30, TimeUnit.SECONDS)
        ttsEvt = null
        log("tts[$label] RESULT ok=$ok total=${now() - t0}ms")
        return ok
    }

    private fun ttsLoop(minutes: Int, gapSec: Int, text: String) {
        val end = now() + minutes * 60_000L
        var n = 0; var fail = 0
        while (now() < end) {
            n++
            val ok = ttsTurn("$text ($n)", "loop$n")
            if (!ok) fail++
            log("ttsloop turn=$n ok=$ok fails=$fail elapsedMin=${"%.1f".format(Locale.US, (now() - (end - minutes * 60_000L)) / 60000.0)}")
            Thread.sleep(gapSec * 1000L)
        }
        log("ttsloop DONE turns=$n fails=$fail")
    }

    // ---- test 4: audio focus, ASSISTANT playback + NAVIGATION_GUIDANCE playback ---------------------------------------

    private fun tonePlayer(usage: Int, hz: Double, ms: Long, name: String): Thread {
        return Thread({
            val rate = 22050
            val attrs = AudioAttributes.Builder().setUsage(usage).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            val fmt = AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
            val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val track = AudioTrack.Builder().setAudioAttributes(attrs).setAudioFormat(fmt).setBufferSizeInBytes(maxOf(min, rate)).setTransferMode(AudioTrack.MODE_STREAM).build()
            val chunk = ShortArray(rate / 10) { k -> (sin(2 * PI * hz * k / rate) * 8000).toInt().toShort() }
            track.play()
            val t0 = now(); var bytes = 0L
            log("$name track playing state=${track.playState}")
            while (now() - t0 < ms) { bytes += track.write(chunk, 0, chunk.size) * 2L }
            track.stop(); track.release()
            log("$name track ended wroteBytes=$bytes")
        }, "gng-tone-$name")
    }

    private fun focus() {
        val am = getSystemService(AudioManager::class.java)
        fun req(usage: Int, name: String): AudioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(usage).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener { c -> log("focus[$name] change=${focusName(c)} abs=${now()}") }
            .build()
        val a = req(AudioAttributes.USAGE_ASSISTANT, "A-assistant")
        val b = req(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE, "B-nav")
        log("focus T0 abs=${now()}")
        log("focus[A] request result=${am.requestAudioFocus(a)}")
        val ta = tonePlayer(AudioAttributes.USAGE_ASSISTANT, 440.0, 14_000, "A").also { it.start() }
        Thread.sleep(4000)
        log("focus T+4 request B (nav) abs=${now()}")
        log("focus[B] request result=${am.requestAudioFocus(b)}")
        val tb = tonePlayer(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE, 880.0, 4_000, "B").also { it.start() }
        tb.join()
        log("focus T+8 abandon B abs=${now()}")
        log("focus[B] abandon result=${am.abandonAudioFocusRequest(b)}")
        ta.join()
        log("focus[A] abandon result=${am.abandonAudioFocusRequest(a)}")
        log("focus DONE")
    }

    private fun focusName(c: Int) = when (c) {
        AudioManager.AUDIOFOCUS_GAIN -> "GAIN"
        AudioManager.AUDIOFOCUS_LOSS -> "LOSS"
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> "LOSS_TRANSIENT"
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> "LOSS_TRANSIENT_CAN_DUCK"
        else -> c.toString()
    }
}
