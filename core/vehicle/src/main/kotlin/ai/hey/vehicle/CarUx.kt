package ai.hey.vehicle

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.os.Handler

/**
 * android.car lives in its own object so that a device without the car library fails only when this object is
 * first touched (NoClassDefFoundError, caught by the caller) instead of when the gateway is loaded.
 */
internal object CarUx {
    private var car: Car? = null

    /**
     * [onDriving] gets the current value and every change ("driving" = distraction optimisation required).
     * [onLost] is called when the car service is not (or no longer) available: the caller must treat that as driving.
     */
    fun start(context: Context, handler: Handler, onDriving: (Boolean) -> Unit, onLost: () -> Unit, onReady: () -> Unit = {}) {
        Car.createCar(
            context,
            handler,
            10_000L,
            Car.CarServiceLifecycleListener { connected, ready ->
                if (!ready) {
                    onLost()
                    return@CarServiceLifecycleListener
                }
                car = connected
                val manager = connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
                onDriving(manager.currentCarUxRestrictions.isRequiresDistractionOptimization)
                manager.registerListener { restrictions -> onDriving(restrictions.isRequiresDistractionOptimization) }
                onReady()
            },
        )
    }

    /** Reads INFO_FUEL_TYPE and subscribes to PERF_VEHICLE_SPEED (m/s). Call after the car service is ready; failures are the caller's to catch. */
    fun watchProperties(onFuelType: (IntArray) -> Unit, onSpeedMps: (Float) -> Unit) {
        val c = car ?: return
        val pm = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
        runCatching { pm.getIntArrayProperty(VehiclePropertyIds.INFO_FUEL_TYPE, 0) }.getOrNull()?.let(onFuelType)
        pm.registerCallback(
            object : CarPropertyManager.CarPropertyEventCallback {
                override fun onChangeEvent(value: CarPropertyValue<*>) {
                    (value.value as? Float)?.let(onSpeedMps)
                }

                override fun onErrorEvent(propId: Int, zone: Int) = Unit
            },
            VehiclePropertyIds.PERF_VEHICLE_SPEED,
            CarPropertyManager.SENSOR_RATE_ONCHANGE,
        )
    }

    fun stop() {
        car?.disconnect()
        car = null
    }
}
