package ai.hey.car

import ai.hey.model.HeyLog
import ai.hey.session.CarStorage
import ai.hey.session.PrefsCarStorage
import ai.hey.session.TripRepository
import ai.hey.vehicle.PleosVehicleGateway
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
    val repository: TripRepository by lazy { TripRepository(scope, vehicle, storage).also { it.start() } }
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
