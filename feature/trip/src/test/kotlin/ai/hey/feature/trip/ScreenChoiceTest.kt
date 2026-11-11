package ai.hey.feature.trip

import ai.hey.model.DrivingState
import ai.hey.session.Panel
import org.junit.Assert.assertEquals
import org.junit.Test

/** The TripState x DrivingState table of the screen spec, as code. */
class ScreenChoiceTest {
    private fun id(state: ai.hey.session.CarUiState) = tripScreenFor(state)

    @Test fun idleIsWaiting() = assertEquals(TripScreenId.D1Waiting, id(TripSamples.waiting))

    @Test fun joinRequestWhileParkedOpensD2() = assertEquals(TripScreenId.D2Join, id(TripSamples.join))

    @Test fun joinRequestWhileDrivingIsD5() = assertEquals(TripScreenId.D5Driving, id(TripSamples.joinDriving))

    @Test fun cardStatesAreD3() {
        assertEquals(TripScreenId.D3Card, id(TripSamples.cardWaiting))
        assertEquals(TripScreenId.D3Card, id(TripSamples.cardOk))
        assertEquals(TripScreenId.D3Card, id(TripSamples.cardRouteStarted))
    }

    @Test fun dropoffPanelIsD4() = assertEquals(TripScreenId.D4Dropoff, id(TripSamples.dropoffSelected))

    @Test fun anyStateWhileDrivingIsOnlyD5() {
        listOf(
            TripSamples.waiting, TripSamples.join, TripSamples.cardOk, TripSamples.dropoffSelected,
            TripSamples.requestsPlain, TripSamples.arrivedAgreed,
        ).forEach { s ->
            assertEquals(TripScreenId.D5Driving, id(s.copy(driving = DrivingState.DRIVING)))
        }
    }

    @Test fun drivingKeepsTheDropoffDraftAndReturnsToD4() {
        val parked = TripSamples.dropoffSelected
        val driving = parked.copy(driving = DrivingState.DRIVING)
        val back = driving.copy(driving = DrivingState.PARKED)
        assertEquals(TripScreenId.D4Dropoff, id(back))
        assertEquals(parked.draft, back.draft)
        assertEquals(Panel.Dropoff, back.panel)
    }

    @Test fun openQuestionsOpenD6AndDismissHidesUntilMoreArrive() {
        assertEquals(TripScreenId.D6Requests, id(TripSamples.requestsPlain))
        val dismissed = TripSamples.requestsPlain.copy(requestsDismissedAt = 2)
        assertEquals(TripScreenId.D3Card, id(dismissed))
        assertEquals(TripScreenId.D6Requests, id(TripSamples.requestsMany.copy(requestsDismissedAt = 2)))
    }

    @Test fun noticesOnlyStayOnD3() = assertEquals(TripScreenId.D3Card, id(TripSamples.requestsNoticeOnly))

    @Test fun arrivedIsD7() = assertEquals(TripScreenId.D7Arrived, id(TripSamples.arrivedAgreed))
}
