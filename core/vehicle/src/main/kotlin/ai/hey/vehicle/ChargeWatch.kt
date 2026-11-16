package ai.hey.vehicle

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine

/**
 * Whether the EV charge switch may be read. Only a car in P can start charging. A car without a gear signal is judged
 * by its own distraction restriction state, and a car that is driving — or whose state is not known — is never read.
 */
internal fun chargePollAllowed(gear: VehicleGear, driving: DrivingStatus): Boolean = when (gear) {
    VehicleGear.Park -> true
    VehicleGear.Unknown -> driving == DrivingStatus.Parked
    else -> false
}

/**
 * Reads the EV charge switch while the car is parked.
 *
 * The Pleos SDK has no listener for the charge switch (`EvBattery` only listens for the charge port, the battery level
 * and efficiency), so the switch is read by polling. [run] polls only in P: a car that is driving cannot start
 * charging, and a background read every [intervalMs] during a drive is exactly the needless polling the Pleos review
 * rules cap at 5% CPU for a non-navigation app.
 *
 * Leaving P reports "not charging" once, so a charge start in the next parked session is seen again as its own
 * false -> true edge (the server raises its charging event on that edge). A car whose charge switch never answered
 * (no EV battery) reports nothing, as before.
 */
internal class ChargeWatch(
    private val intervalMs: Long,
    private val gear: StateFlow<VehicleGear>,
    private val driving: StateFlow<DrivingStatus>,
    private val read: (onResult: (Boolean) -> Unit) -> Unit,
    private val onChange: (Boolean) -> Unit,
) {
    @Volatile private var charging: Boolean? = null

    suspend fun run() = coroutineScope {
        combine(gear, driving) { g, d -> chargePollAllowed(g, d) }.collectLatest { parked ->
            if (parked) {
                while (true) {
                    // An SDK answer that lands after the car started moving is dropped: only a parked car reports.
                    read { on -> if (chargePollAllowed(gear.value, driving.value)) report(on) }
                    delay(intervalMs)
                }
            } else if (charging == true) {
                // The cable is out before a car moves off, so the next parked session reports its own charge start.
                report(false)
            }
        }
    }

    @Synchronized
    private fun report(on: Boolean) {
        if (charging == on) return
        charging = on
        onChange(on)
    }
}
