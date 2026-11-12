package ai.hey.feature.trip

import ai.hey.model.CarView
import ai.hey.model.DrivingState
import ai.hey.model.TripState
import ai.hey.session.LocalUi
import ai.hey.session.phaseOf
import ai.hey.vehicle.DrivingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The full state matrix, without rendering anything. Paparazzi already renders every screen; this test is the cheap
 * structural check that every TripState x DrivingStatus pair is placed, including the fail-closed Unknown value.
 */
class PhaseMatrixTest {
    private val base = CarView(state = TripState.IDLE, carCode = "C", plateSuffix = "1", driving = DrivingState.PARKED)

    @Test fun everyTripStateAndDrivingStatusChoosesAScreen() {
        for (state in TripState.entries) {
            for (driving in listOf(DrivingStatus.Unknown, DrivingStatus.Parked, DrivingStatus.Driving)) {
                val screen = tripScreenFor(phaseOf(base.copy(state = state), driving, LocalUi()))
                if (driving != DrivingStatus.Parked) {
                    assertEquals("$state while $driving", TripScreenId.D5Driving, screen)
                } else {
                    assertNotEquals("$state while parked", TripScreenId.D5Driving, screen)
                }
            }
        }
    }

    @Test fun everySampleChoosesTheSameScreenThroughTheStateAndThroughThePhase() {
        listOf(
            TripSamples.waiting, TripSamples.waitingOffline, TripSamples.registerFailed,
            TripSamples.join, TripSamples.joinDriving,
            TripSamples.cardWaiting, TripSamples.cardOk, TripSamples.cardRouteStarted, TripSamples.cardRouteFailed,
            TripSamples.dropoffDefault, TripSamples.dropoffSelected, TripSamples.dropoffNone,
            TripSamples.drivingNone, TripSamples.drivingRequest, TripSamples.drivingStale,
            TripSamples.requestsPlain, TripSamples.requestsMany, TripSamples.requestsNoticeOnly,
            TripSamples.arrivedAgreed, TripSamples.arrivedDialog,
        ).forEach { state ->
            assertEquals(tripScreenFor(state.phase), tripScreenFor(state))
        }
    }
}
