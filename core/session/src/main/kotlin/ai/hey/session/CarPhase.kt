package ai.hey.session

import ai.hey.model.CarView
import ai.hey.model.PendingJoin
import ai.hey.model.TripState
import ai.hey.model.actionableCount
import ai.hey.vehicle.DrivingStatus
import ai.hey.vehicle.RouteStartState

/** Local UI flags that influence screen choice but are not server facts. */
data class LocalUi(
    val panel: Panel = Panel.None,
    val requestsDismissedAt: Int = 0,
    val route: RouteStartState = RouteStartState.Idle,
)

/**
 * What the car is doing, derived in one pure function from the snapshot, the car's motion and local UI flags.
 * [Driving] covers both [DrivingStatus.Driving] and [DrivingStatus.Unknown]: an unknown car is treated as moving.
 *
 * Every parked phase carries [Parked.enabled]: the hands-on commands the relay accepts in exactly that situation
 * (docs/transitions.json is the table; `CarPhaseTransitionsTest` checks this file against it). A button is enabled
 * from this set and from nothing else, so the car cannot offer what the relay would reject.
 */
sealed interface CarPhase {
    /** Phases in which the driver's hands are free. */
    sealed interface Parked : CarPhase {
        val enabled: Set<CarCommand>
    }

    /** The boarding card screen (D3), one type per trip state because each state accepts different commands. */
    sealed interface Card : Parked {
        val view: CarView

        /** The car's own route-start attempt; D3's status pill and route banners come from here, nowhere else. */
        val route: RouteStartState get() = RouteStartState.Idle
    }

    data object Driving : CarPhase

    data object Waiting : Parked {
        override val enabled: Set<CarCommand> = emptySet()
    }

    data class JoinRequest(val join: PendingJoin) : Parked {
        override val enabled: Set<CarCommand> = setOf(CarCommand.ApproveJoin, CarCommand.DeclineJoin)
    }

    /** CONNECTED: the passenger is writing the card, or the driver asked for a re-check and the old card is shown. */
    data class AwaitingCard(override val view: CarView) : Card {
        override val enabled: Set<CarCommand> = setOf(CarCommand.Reply, CarCommand.End)
    }

    /** CARD_SENT: the card is out; the route is not started yet. */
    data class CardSent(override val view: CarView, override val route: RouteStartState) : Card {
        override val enabled: Set<CarCommand> = setOf(
            CarCommand.Recheck, CarCommand.StartRoute, CarCommand.Propose, CarCommand.DecidePin, CarCommand.Reply, CarCommand.End,
        )
    }

    /** IN_TRIP: the route runs. A re-check is not possible any more (the relay refuses `card.recheck`). */
    data class InTrip(override val view: CarView, override val route: RouteStartState) : Card {
        override val enabled: Set<CarCommand> = setOf(
            CarCommand.StartRoute, CarCommand.Propose, CarCommand.DecidePin, CarCommand.Reply, CarCommand.Arrive, CarCommand.End,
        )
    }

    /** D4. Only built in CARD_SENT and IN_TRIP, where `dropoff.propose` and `dropoff.decide` are legal. */
    data class DropoffProposal(val view: CarView) : Parked {
        override val enabled: Set<CarCommand> = setOf(CarCommand.Propose, CarCommand.DecidePin)
    }

    /** D6: open questions or pins wait. Built in every state in which the relay accepts `request.send`. */
    data class Requests(val view: CarView) : Parked {
        override val enabled: Set<CarCommand> = when (view.state) {
            TripState.IN_TRIP -> setOf(CarCommand.Reply, CarCommand.DecidePin, CarCommand.Arrive, CarCommand.End)
            TripState.CARD_SENT -> setOf(CarCommand.Reply, CarCommand.DecidePin, CarCommand.End)
            TripState.CONNECTED, TripState.ARRIVED -> setOf(CarCommand.Reply, CarCommand.End)
            // phaseOf never builds Requests here.
            TripState.IDLE, TripState.REQUESTED, TripState.ENDED -> emptySet()
        }
    }

    data class Arrived(val view: CarView) : Parked {
        override val enabled: Set<CarCommand> = setOf(CarCommand.Reply, CarCommand.End)
    }
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
    val dropoffOpen = local.panel == Panel.Dropoff && view.card != null
    val questionsWait = view.actionableCount > local.requestsDismissedAt
    return when (view.state) {
        TripState.IDLE, TripState.ENDED -> CarPhase.Waiting
        TripState.REQUESTED -> view.pendingJoin?.let { CarPhase.JoinRequest(it) } ?: CarPhase.Waiting
        TripState.CONNECTED -> if (questionsWait) CarPhase.Requests(view) else CarPhase.AwaitingCard(view)
        TripState.CARD_SENT -> when {
            dropoffOpen -> CarPhase.DropoffProposal(view)
            questionsWait -> CarPhase.Requests(view)
            else -> CarPhase.CardSent(view, local.route)
        }
        TripState.IN_TRIP -> when {
            dropoffOpen -> CarPhase.DropoffProposal(view)
            questionsWait -> CarPhase.Requests(view)
            else -> CarPhase.InTrip(view, local.route)
        }
        TripState.ARRIVED -> if (questionsWait) CarPhase.Requests(view) else CarPhase.Arrived(view)
    }
}
