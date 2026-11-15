package ai.hey.car

import ai.hey.model.HeyLog
import ai.hey.session.CarStorage
import ai.hey.session.CompanionRepository
import ai.hey.session.HttpCompanionApi
import ai.hey.session.HttpTalkApi
import ai.hey.session.PrefsCarStorage
import ai.hey.session.AndroidConnectivity
import ai.hey.session.ReconnectPacer
import ai.hey.session.TalkLink
import ai.hey.session.TalkRepository
import ai.hey.session.newHttpClient
import ai.hey.vehicle.AndroidOtherAudioProbe
import ai.hey.vehicle.AudioFocusGateAndroid
import ai.hey.vehicle.AudioTrackSink
import ai.hey.vehicle.PleosVehicleGateway
import ai.hey.vehicle.StreamingReplyAudio
import ai.hey.vehicle.PleosVoiceInput
import ai.hey.vehicle.VehicleGateway
import ai.hey.vehicle.VoiceInput
import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import ai.hey.model.SpeechPriority
import ai.hey.vehicle.VehicleEvent
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val FAULT_SPEECH_MAX_MILLIS = 15_000L

/** Manual DI: one graph per process, built lazily so the boot receiver and the activity share it. */
class AppGraph(private val app: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val storage: CarStorage by lazy { PrefsCarStorage(app, BuildConfig.SERVER_URL) }
    val vehicle: VehicleGateway by lazy { PleosVehicleGateway(app, scope) }

    /** Pleos SpeechToText (ON_DEVICE, Locale.KOREA); the debug/e2e builds wrap it so adb can inject a sentence (default: real mic). */
    val voice: VoiceInput by lazy { demoVoiceOrNull(app) ?: PleosVoiceInput(app) }
    val companion: CompanionRepository by lazy { CompanionRepository(scope, HttpCompanionApi(storage, newHttpClient(), AndroidConnectivity(app)), storage) }

    /** friend messages and trip-end diaries also go to the notification centre (two kinds only). */
    private val notifier by lazy { FrameNotifier(AndroidNoticeSink(app)) }
    val repository: TalkRepository by lazy {
        val http = newHttpClient()
        TalkRepository(
            scope = scope,
            vehicle = vehicle,
            storage = storage,
            voiceInput = voice,
            transport = { listener ->
                TalkLink(
                    api = HttpTalkApi(http),
                    http = http,
                    storage = storage,
                    scope = scope,
                    listener = listener.alsoTo(notifier),
                    appVersion = BuildConfig.VERSION_NAME,
                    pacer = ReconnectPacer(clock = { System.nanoTime() / 1_000_000 }, connectivity = AndroidConnectivity(app)),
                )
            },
            audio = StreamingReplyAudio({ voice -> AudioTrackSink(voice) }, AudioFocusGateAndroid(app)),
            companion = companion,
            speechFocus = AudioFocusGateAndroid(app),
            otherAudio = AndroidOtherAudioProbe(app),
        ).also { it.start() }
    }

    private val faultFocus by lazy { AudioFocusGateAndroid(app) }

    /**
     * the driving fault line never talks over another app. It is spoken only when navigation is not
     * guiding and the transient audio focus is granted; a refusal or a later loss drops it without a retry.
     */
    fun speakFault(text: String, id: String) {
        if (vehicle.signals.value.naviGuiding) {
            HeyLog.i("Fault", "fault line skipped: navigation guiding")
            return
        }
        if (!faultFocus.request { vehicle.stopSpeech() }) {
            HeyLog.i("Fault", "fault line skipped: audio focus refused")
            faultFocus.abandon()
            return
        }
        scope.launch {
            val end = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { vehicle.events.first { (it is VehicleEvent.SpeechDone && it.speechId == id) || (it is VehicleEvent.SpeechDropped && it.speechId == id) } }
            vehicle.speak(text, SpeechPriority.High, id)
            withTimeoutOrNull(FAULT_SPEECH_MAX_MILLIS) { end.await() }
            end.cancel()
            faultFocus.abandon()
        }
    }
}

class HeyApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        HeyLog.contentEnabled = BuildConfig.DEBUG
        HeyLog.sink = { level, tag, message ->
            when (level) {
                'W' -> Log.w(tag, message)
                'I' -> Log.i(tag, message)
                else -> Log.d(tag, message)
            }
        }
        graph = AppGraph(this)
    }
}
