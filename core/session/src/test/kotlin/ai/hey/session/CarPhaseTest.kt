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
import org.junit.Assert.assertTrue
import org.junit.Test

/** The one state machine: which phase each TripState x DrivingStatus pair is, and which commands a phase offers. */
class CarPhaseTest {
    private val place = Place("p1", "Hotel", "호텔", "서울 중구", "서울", 37.5, 127.0)
    private val card = CarCardView(place, emptyList(), emptyList())
    private val join = PendingJoin("jr_1", Lang.En, 2, 0, "47")
    private val question = RequestItem("req_1", RequestKind.Preset, null, "창문을 열어도 될까요?", true, RequestState.Received, ts = 0)
    private val base = CarView(state = TripState.IDLE, carCode = "C", plateSuffix = "1", driving = DrivingState.PARKED)

    private fun view(state: TripState, card: Boolean = false, join: Boolean = false, question: Boolean = false) =
        base.copy(
            state = state,
            card = if (card) this.card else null,
            pendingJoin = if (join) this.join else null,
            requests = if (question) listOf(this.question) else emptyList(),
        )

    private fun parked(view: CarView, local: LocalUi = LocalUi()) = phaseOf(view, DrivingStatus.Parked, local)

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

    @Test fun requestedWithoutAJoinIsWaiting() {
        assertEquals(CarPhase.Waiting, parked(view(TripState.REQUESTED)))
    }

    @Test fun requestedWithAJoinCarriesTheJoin() {
        assertEquals(CarPhase.JoinRequest(join), parked(view(TripState.REQUESTED, join = true)))
    }

    /** B3: the three card states are three phases, because the relay accepts different commands in each. */
    @Test fun connectedCardSentAndInTripAreDifferentPhases() {
        assertTrue(parked(view(TripState.CONNECTED, card = true)) is CarPhase.AwaitingCard)
        assertTrue(parked(view(TripState.CARD_SENT, card = true)) is CarPhase.CardSent)
        assertTrue(parked(view(TripState.IN_TRIP, card = true)) is CarPhase.InTrip)
    }

    /** B3: the relay refuses route.started in CONNECTED and card.recheck in IN_TRIP, so those phases do not offer them. */
    @Test fun aPhaseDoesNotOfferWhatTheRelayRefusesInThatState() {
        val connected = parked(view(TripState.CONNECTED, card = true)) as CarPhase.Parked
        assertFalse(CarCommand.Propose in connected.enabled)
        assertFalse(CarCommand.Arrive in connected.enabled)
        val inTrip = parked(view(TripState.IN_TRIP, card = true)) as CarPhase.Parked
        assertFalse(CarCommand.Recheck in inTrip.enabled)
        assertTrue(CarCommand.Arrive in inTrip.enabled)
        val sent = parked(view(TripState.CARD_SENT, card = true)) as CarPhase.Parked
        assertTrue(CarCommand.Recheck in sent.enabled)
        assertFalse(CarCommand.Arrive in sent.enabled || CarCommand.Arrive in connected.enabled)
    }

    @Test fun dropoffPanelNeedsACardAndAStateWhereTheRelayTakesProposals() {
        val dropoff = LocalUi(panel = Panel.Dropoff)
        assertTrue(parked(view(TripState.CARD_SENT, card = true), dropoff) is CarPhase.DropoffProposal)
        assertTrue(parked(view(TripState.IN_TRIP, card = true), dropoff) is CarPhase.DropoffProposal)
        assertTrue(parked(view(TripState.CARD_SENT), dropoff) is CarPhase.CardSent)
        // CONNECTED: dropoff.propose is not legal, so the panel cannot show there.
        assertTrue(parked(view(TripState.CONNECTED, card = true), dropoff) is CarPhase.AwaitingCard)
    }

    /** M2: a question is answerable in every parked state where the relay accepts reply.send, not only IN_TRIP. */
    @Test fun openQuestionsOpenTheRequestsScreenInEveryStateThatTakesReplies() {
        for (state in listOf(TripState.CONNECTED, TripState.CARD_SENT, TripState.IN_TRIP, TripState.ARRIVED)) {
            val phase = parked(view(state, card = true, question = true))
            assertTrue("$state with an open question must show requests", phase is CarPhase.Requests)
            assertTrue("$state must allow a reply", CarCommand.Reply in (phase as CarPhase.Parked).enabled)
        }
    }

    @Test fun dismissedQuestionsGoBackToTheCardOrSummary() {
        val dismissed = LocalUi(requestsDismissedAt = 1)
        assertTrue(parked(view(TripState.CONNECTED, card = true, question = true), dismissed) is CarPhase.AwaitingCard)
        assertTrue(parked(view(TripState.CARD_SENT, card = true, question = true), dismissed) is CarPhase.CardSent)
        assertTrue(parked(view(TripState.IN_TRIP, card = true, question = true), dismissed) is CarPhase.InTrip)
        assertTrue(parked(view(TripState.ARRIVED, question = true), dismissed) is CarPhase.Arrived)
    }

    /** M3: the driver can end the trip from every parked trip screen, as the relay and the state diagram allow. */
    @Test fun everyParkedTripPhaseCanEndTheTrip() {
        for (state in listOf(TripState.CONNECTED, TripState.CARD_SENT, TripState.IN_TRIP, TripState.ARRIVED)) {
            for (question in listOf(false, true)) {
                val phase = parked(view(state, card = true, question = question)) as CarPhase.Parked
                assertTrue("$state (question=$question) must offer End", CarCommand.End in phase.enabled)
            }
        }
    }

    @Test fun endedReturnsToWaiting() {
        assertEquals(CarPhase.Waiting, parked(view(TripState.ENDED)))
    }
}
