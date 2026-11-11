package ai.hey.vehicle

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.os.Handler

/**
 * android.car lives in its own object so that a device without the car library fails only when this object is
 * first touched (NoClassDefFoundError, caught by the caller) instead of when the gateway is loaded.
 */
internal object CarUx {
    private var car: Car? = null

    /** Calls [onDriving] with the current value and on every change; "driving" = distraction optimisation required. */
    fun start(context: Context, handler: Handler, onDriving: (Boolean) -> Unit) {
        Car.createCar(
            context,
            handler,
            10_000L,
            Car.CarServiceLifecycleListener { connected, ready ->
                if (!ready) return@CarServiceLifecycleListener
                car = connected
                val manager = connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
                onDriving(manager.currentCarUxRestrictions.isRequiresDistractionOptimization)
                manager.registerListener { restrictions -> onDriving(restrictions.isRequiresDistractionOptimization) }
            },
        )
    }

    fun stop() {
        car?.disconnect()
        car = null
    }
}
