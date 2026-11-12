package ai.hey.session

import ai.hey.model.CarView
import ai.hey.model.PendingJoin
import ai.hey.model.TripState
import ai.hey.model.actionableCount
import ai.hey.vehicle.DrivingStatus

/** Local UI flags that influence screen choice but are not server facts. */
data class LocalUi(
    val panel: Panel = Panel.None,
    val requestsDismissedAt: Int = 0,
)

/**
 * What the car is doing, derived in one pure function from the snapshot, the car's motion and local UI flags.
 * [Driving] covers both [DrivingStatus.Driving] and [DrivingStatus.Unknown]: an unknown car is treated as moving.
 */
sealed interface CarPhase {
    /** Phases in which the driver's hands are free. Hands-on commands exist only here. */
    sealed interface Parked : CarPhase {
        /**
         * The only way to obtain [ParkedCommands]. A driving phase does not have this method, so screen code
         * outside a parked branch cannot compile a hands-on call.
         */
        fun requireParked(commands: ParkedCommands): ParkedCommands = commands
    }

    data object Driving : CarPhase

    data object Waiting : Parked

    data class JoinRequest(val join: PendingJoin) : Parked

    data class BoardingCard(val view: CarView, val inTrip: Boolean) : Parked

    data class DropoffProposal(val view: CarView) : Parked

    data class Requests(val view: CarView) : Parked

    data class Arrived(val view: CarView) : Parked
}

/**
 * The one state machine. Every screen choice and every hands-on gate comes from here; no caller re-checks
 * `driving == PARKED` or `state == IN_TRIP` on its own. Exhaustive over [TripState] and [DrivingStatus], with no
 * `else` fallthrough: a new trip state fails compilation until it is placed here.
 */
fun phaseOf(view: CarView?, driving: DrivingStatus, local: LocalUi): CarPhase {
    when (driving) {
        DrivingStatus.Unknown, DrivingStatus.Driving -> return CarPhase.Driving
        DrivingStatus.Parked -> Unit
    }
    if (view == null) return CarPhase.Waiting
    return when (view.state) {
        TripState.IDLE, TripState.ENDED -> CarPhase.Waiting
        TripState.REQUESTED -> view.pendingJoin?.let { CarPhase.JoinRequest(it) } ?: CarPhase.Waiting
        TripState.CONNECTED, TripState.CARD_SENT ->
            if (local.panel == Panel.Dropoff && view.card != null) CarPhase.DropoffProposal(view)
            else CarPhase.BoardingCard(view, inTrip = false)
        TripState.IN_TRIP -> when {
            local.panel == Panel.Dropoff && view.card != null -> CarPhase.DropoffProposal(view)
            view.actionableCount > local.requestsDismissedAt -> CarPhase.Requests(view)
            else -> CarPhase.BoardingCard(view, inTrip = true)
        }
        TripState.ARRIVED -> CarPhase.Arrived(view)
    }
}
