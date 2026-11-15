package ai.hey.vehicle

import ai.hey.model.HeyLog
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "PleosVoice"
private const val INIT_TIMEOUT_MS = 10_000L
private const val LISTEN_TIMEOUT_MS = 15_000L

/**
 * Pleos SpeechToText, ON_DEVICE, `Locale.KOREA` (a bare `ko` fails), one utterance per [listen]. ON_DEVICE needs no app
 * registration. The service is initialised on first use and kept; a failed initialisation is retried on the next press.
 * The final result of a recognition is the first update the SDK flags as final.
 */
class PleosVoiceInput(private val context: Context) : VoiceInput {
    private val app = context.applicationContext
    private val lock = Mutex()
    @Volatile private var stt: SpeechToText? = null
    private val current = AtomicReference<CompletableDeferred<VoiceResult>?>(null)
    @Volatile private var lastPartial = ""
    @Volatile private var speechStart: () -> Unit = {}

    private val results = object : ResultListener {
        override fun onUpdated(text: String, isFinal: Boolean) {
            if (text.isNotBlank()) speechStart().also { speechStart = {} }
            if (!isFinal) {
                lastPartial = text
                return
            }
            val heard = text.trim().ifEmpty { lastPartial.trim() }
            current.get()?.complete(if (heard.isEmpty()) VoiceResult.NoSpeech else VoiceResult.Text(heard))
        }

        override fun onUpdatedEpdData(epdOn: Long, epdOff: Long) = Unit
        override fun onStartedRecognition() = Unit

        override fun onEndedRecognition() {
            // The engine closed the microphone without a final result: whatever was heard so far is the answer.
            val partial = lastPartial.trim()
            current.get()?.complete(if (partial.isEmpty()) VoiceResult.NoSpeech else VoiceResult.Text(partial))
        }

        override fun onError(e: ErrorType) {
            HeyLog.w(TAG, "stt error $e")
            current.get()?.complete(
                when (e) {
                    ErrorType.PERMISSION_DENIED -> VoiceResult.PermissionDenied
                    ErrorType.TIMEOUT -> VoiceResult.NoSpeech
                    else -> VoiceResult.Unavailable(e.name)
                },
            )
        }

        override fun onReady() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
    }

    override fun finishListening() {
        val partial = lastPartial.trim()
        current.get()?.complete(if (partial.isEmpty()) VoiceResult.NoSpeech else VoiceResult.Text(partial))
    }

    override fun hasPermission(): Boolean =
        app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private suspend fun ensureReady(): ErrorType? {
        if (stt != null) return null
        val init = initEngine(
            timeoutMs = INIT_TIMEOUT_MS,
            create = { SpeechToText(app, Mode.ON_DEVICE) },
            start = { engine, report ->
                engine.initialize(object : OnInitializeListener {
                    override fun onSuccess() {
                        try {
                            engine.addListener(results)
                            engine.setLocale(Locale.KOREA)
                            report(EngineInit.Ready(engine))
                        } catch (t: Throwable) {
                            HeyLog.w(TAG, "stt setup threw ${t.javaClass.simpleName}: ${t.message}")
                            report(EngineInit.Failed(ErrorType.UNKNOWN))
                        }
                    }

                    override fun onFailure(e: ErrorType) {
                        HeyLog.w(TAG, "stt init failed $e")
                        report(EngineInit.Failed(e))
                    }
                })
            },
            release = { it.release() },
        )
        return when (init) {
            is EngineInit.Ready -> { stt = init.engine; null }
            is EngineInit.Failed -> init.error
        }
    }

    override suspend fun listen(): VoiceResult = listenWith {}

    /** The first partial result of the engine is the start of speech (PLEOS-194): the caller's wait-for-speech timer ends there. */
    override suspend fun listenAfterReply(onSpeechStart: () -> Unit): VoiceResult = listenWith(onSpeechStart)

    private suspend fun listenWith(onSpeechStart: () -> Unit): VoiceResult = lock.withLock {
        if (!hasPermission()) return VoiceResult.PermissionDenied
        ensureReady()?.let { return if (it == ErrorType.PERMISSION_DENIED) VoiceResult.PermissionDenied else VoiceResult.Unavailable(it.name) }
        val engine = stt ?: return VoiceResult.Unavailable("NO_ENGINE")
        val done = CompletableDeferred<VoiceResult>()
        current.set(done)
        lastPartial = ""
        speechStart = onSpeechStart
        try {
            engine.request()
            withTimeoutOrNull(LISTEN_TIMEOUT_MS) { done.await() } ?: VoiceResult.NoSpeech
        } catch (t: kotlinx.coroutines.CancellationException) {
            throw t
        } catch (t: Throwable) {
            HeyLog.w(TAG, "stt request threw ${t.javaClass.simpleName}: ${t.message}")
            VoiceResult.Unavailable("REQUEST_FAILED")
        } finally {
            speechStart = {}
            current.compareAndSet(done, null)
            try { engine.stop() } catch (_: Throwable) { }
        }
    }

    override fun release() {
        val engine = stt ?: return
        stt = null
        try {
            engine.removeListener(results)
            engine.release()
        } catch (_: Throwable) { }
    }
}
