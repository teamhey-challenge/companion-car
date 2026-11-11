package ai.hey.session

import ai.hey.model.DrivingState
import ai.hey.model.TripState
import ai.hey.vehicle.FakeVehicleGateway
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The repository's own rules, without a relay: driving locks, draft survival, arrival latch, speaker failure. */
@OptIn(ExperimentalCoroutinesApi::class)
class TripRepositoryTest {
    private fun repo(scope: kotlinx.coroutines.CoroutineScope, vehicle: FakeVehicleGateway) =
        TripRepository(scope, vehicle, InMemoryCarStorage(), http = OkHttpClient(), progressPollMs = 3_600_000)

    @Test fun theDropoffDraftAndPanelSurviveADrivingToggle() = runTest {
        val vehicle = FakeVehicleGateway(backgroundScope)
        val repo = repo(backgroundScope, vehicle)
        repo.start()
        repo.openDropoff()
        repo.selectCandidate("fake-2")
        repo.selectReason(1)
        val before = repo.state.value
        assertEquals(Panel.Dropoff, before.panel)

        vehicle.setDriving(true)
        advanceTimeBy(10)
        assertEquals(DrivingState.DRIVING, repo.state.value.driving)
        assertEquals("fake-2", repo.state.value.draft.candidateId)

        vehicle.setDriving(false)
        advanceTimeBy(10)
        assertEquals(Panel.Dropoff, repo.state.value.panel)
        assertEquals("fake-2", repo.state.value.draft.candidateId)
        assertEquals(1, repo.state.value.draft.reasonIndex)
    }

    @Test fun handsOnCommandsAreRefusedWhileDriving() = runTest {
        val vehicle = FakeVehicleGateway(backgroundScope, initialDriving = DrivingState.DRIVING)
        val repo = repo(backgroundScope, vehicle)
        repo.start()
        advanceTimeBy(10)
        repo.openSettings()
        repo.openDropoff()
        repo.openEndDialog()
        repo.startRoute()
        val s = repo.state.value
        assertEquals(Panel.None, s.panel)
        assertFalse(s.endDialogOpen)
        assertFalse(s.routeStarting)
        assertTrue(vehicle.routes.isEmpty())
    }
}
