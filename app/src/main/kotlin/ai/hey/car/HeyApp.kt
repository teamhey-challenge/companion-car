package ai.hey.car

import ai.hey.model.HeyLog
import ai.hey.session.CarStorage
import ai.hey.session.PrefsCarStorage
import ai.hey.session.TripRepository
import ai.hey.vehicle.PleosVehicleGateway
import ai.hey.vehicle.PleosVoiceInput
import ai.hey.vehicle.VoiceInput
import ai.hey.vehicle.VehicleGateway
import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual DI: one graph per process, built lazily so the boot receiver and the activity share it. */
class AppGraph(private val app: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val storage: CarStorage by lazy { PrefsCarStorage(app, BuildConfig.RELAY_URL) }
    val vehicle: VehicleGateway by lazy { PleosVehicleGateway(app, scope) }
    /** Pleos SpeechToText (ON_DEVICE, Locale.KOREA); the debug/e2e builds swap in the injectable demo source. */
    val voice: VoiceInput by lazy { demoVoiceOrNull(app) ?: PleosVoiceInput(app) }
    val repository: TripRepository by lazy {
        TripRepository(scope, vehicle, storage, provisionSecret = BuildConfig.PROVISION_SECRET, voiceInput = voice).also { it.start() }
    }
}

class HeyApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
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
