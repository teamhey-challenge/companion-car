package ai.hey.session

import ai.hey.model.CarCardView
import ai.hey.model.CarView
import ai.hey.model.DrivingState
import ai.hey.model.Lang
import ai.hey.model.PendingJoin
import ai.hey.model.Place
import ai.hey.model.RequestItem
import ai.hey.model.RequestKind
import ai.hey.model.RequestState
import ai.hey.model.TripState
import ai.hey.vehicle.DrivingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The one state machine: every TripState x DrivingStatus pair, and the hands-on gate. */
class CarPhaseTest {
    private val place = Place("p1", "Hotel", "호텔", "서울 중구", "서울", 37.5, 127.0)
    private val card = CarCardView(place, emptyList(), emptyList())
    private val join = PendingJoin("jr_1", Lang.En, 2, 0)
    private val question = RequestItem("req_1", RequestKind.Preset, null, "창문을 열어도 될까요?", true, RequestState.Received, ts = 0)
    private val base = CarView(state = TripState.IDLE, carCode = "C", plateSuffix = "1", driving = DrivingState.PARKED)

    private fun view(state: TripState, card: Boolean = false, join: Boolean = false, question: Boolean = false) =
        base.copy(
            state = state,
            card = if (card) this.card else null,
            pendingJoin = if (join) this.join else null,
            requests = if (question) listOf(this.question) else emptyList(),
        )

    @Test fun everyTripStateAndDrivingStatusHasAPhase() {
        for (state in TripState.entries) {
            for (driving in listOf(DrivingStatus.Unknown, DrivingStatus.Parked, DrivingStatus.Driving)) {
                val phase = phaseOf(view(state), driving, LocalUi())
                if (driving == DrivingStatus.Parked) {
                    assertFalse("$state while parked must not be the driving phase", phase is CarPhase.Driving)
                } else {
                    assertEquals("$state while $driving must be the driving phase", CarPhase.Driving, phase)
                }
            }
        }
    }

    @Test fun unknownDrivingBehavesExactlyLikeDriving() {
        val view = view(TripState.CARD_SENT, card = true)
        assertEquals(CarPhase.Driving, phaseOf(view, DrivingStatus.Unknown, LocalUi()))
        assertEquals(phaseOf(view, DrivingStatus.Driving, LocalUi()), phaseOf(view, DrivingStatus.Unknown, LocalUi()))
    }

    @Test fun aDrivingPhaseCannotProduceParkedCommands() {
        // Compile-time: CarPhase.Driving does not implement CarPhase.Parked, so requireParked() is not in its API.
        assertFalse(CarPhase.Parked::class.java.isAssignableFrom(CarPhase.Driving::class.java))
        assertTrue(CarPhase.Parked::class.java.isAssignableFrom(CarPhase.Waiting::class.java))
        assertSame(NoCommands, CarPhase.Waiting.requireParked(NoCommands))
        // The only door to hands-on commands is the method on the parked interface.
        val door = CarPhase.Parked::class.java.methods.single { it.name == "requireParked" }
        assertEquals(ParkedCommands::class.java, door.returnType)
        assertTrue("Driving must not declare requireParked", CarPhase.Driving::class.java.methods.none { it.name == "requireParked" })
    }

    @Test fun requestedWithoutAJoinIsWaiting() {
        assertEquals(CarPhase.Waiting, phaseOf(view(TripState.REQUESTED), DrivingStatus.Parked, LocalUi()))
    }

    @Test fun requestedWithAJoinCarriesTheJoin() {
        val phase = phaseOf(view(TripState.REQUESTED, join = true), DrivingStatus.Parked, LocalUi())
        assertEquals(CarPhase.JoinRequest(join), phase)
    }

    @Test fun dropoffPanelNeedsACard() {
        val withCard = phaseOf(view(TripState.CONNECTED, card = true), DrivingStatus.Parked, LocalUi(panel = Panel.Dropoff))
        assertTrue(withCard is CarPhase.DropoffProposal)
        val withoutCard = phaseOf(view(TripState.CONNECTED), DrivingStatus.Parked, LocalUi(panel = Panel.Dropoff))
        assertTrue(withoutCard is CarPhase.BoardingCard)
    }

    @Test fun inTripShowsRequestsUntilDismissed() {
        val open = phaseOf(view(TripState.IN_TRIP, card = true, question = true), DrivingStatus.Parked, LocalUi())
        assertTrue(open is CarPhase.Requests)
        val dismissed = phaseOf(
            view(TripState.IN_TRIP, card = true, question = true),
            DrivingStatus.Parked,
            LocalUi(requestsDismissedAt = 1),
        )
        assertEquals(CarPhase.BoardingCard(view(TripState.IN_TRIP, card = true, question = true), inTrip = true), dismissed)
    }

    @Test fun arrivedIsArrived() {
        val phase = phaseOf(view(TripState.ARRIVED), DrivingStatus.Parked, LocalUi())
        assertTrue(phase is CarPhase.Arrived)
    }

    @Test fun endedReturnsToWaiting() {
        assertEquals(CarPhase.Waiting, phaseOf(view(TripState.ENDED), DrivingStatus.Parked, LocalUi()))
    }
}
