package ai.hey.session

import ai.hey.model.CarCardView
import ai.hey.model.CarSettings
import ai.hey.model.CarView
import ai.hey.model.DrivingState
import ai.hey.model.ErrorCode
import ai.hey.model.Place
import ai.hey.model.QuickReplyId
import ai.hey.model.TripState
import ai.hey.vehicle.DrivingStatus
import ai.hey.vehicle.FakeVehicleGateway
import ai.hey.vehicle.SpeechDropReason
import ai.hey.vehicle.VehicleEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/** The repository's own rules, without a relay: driving locks, draft survival, command clearing, vehicle issues. */
@OptIn(ExperimentalCoroutinesApi::class)
class TripRepositoryTest {
    private val place = Place("p1", "Hotel", "호텔", "서울 중구", "서울", 37.5, 127.0)

    private fun repo(scope: kotlinx.coroutines.CoroutineScope, vehicle: FakeVehicleGateway) = TripRepository(
        scope,
        vehicle,
        InMemoryCarStorage(CarSettings(relayUrl = "http://127.0.0.1:1")),
        http = OkHttpClient.Builder().connectTimeout(50, TimeUnit.MILLISECONDS).build(),
        progressPollMs = 3_600_000,
    )

    private fun view(
        state: TripState = TripState.CONNECTED,
        pendingJoin: ai.hey.model.PendingJoin? = null,
        card: CarCardView? = null,
        tripId: String? = "t1",
    ) = CarView(
        state = state,
        carCode = "C",
        plateSuffix = "1",
        driving = DrivingState.PARKED,
        tripId = tripId,
        pendingJoin = pendingJoin,
        card = card,
    )

    private val cardView = view(card = CarCardView(place, emptyList(), emptyList()))

    @Test fun theDropoffDraftAndPanelSurviveADrivingToggle() = runTest {
        val vehicle = FakeVehicleGateway(backgroundScope)
        val repo = repo(backgroundScope, vehicle)
        repo.start()
        repo.onSnapshotForTest(cardView)
        repo.openDropoff()
        repo.selectCandidate("fake-2")
        repo.selectReason(1)
        val before = repo.state.value
        assertEquals(Panel.Dropoff, before.panel)

        vehicle.setDriving(true)
        advanceTimeBy(10)
        assertEquals(DrivingStatus.Driving, repo.state.value.driving)
        assertEquals("fake-2", repo.state.value.draft.candidateId)

        vehicle.setDriving(false)
        advanceTimeBy(10)
        assertEquals(Panel.Dropoff, repo.state.value.panel)
        assertEquals("fake-2", repo.state.value.draft.candidateId)
        assertEquals(1, repo.state.value.draft.reasonIndex)
    }

    /**
     * The screen cannot reach these methods while driving (they are not on the type it holds), but a caller that
     * bypasses the screen — a test, a future feature — must still be refused by the repository itself.
     */
    @Test fun handsOnCommandsAreRefusedWhileDrivingThroughTheRepositorySeam() = runTest {
        val vehicle = FakeVehicleGateway(backgroundScope, initialDriving = DrivingStatus.Driving)
        val repo = repo(backgroundScope, vehicle)
        repo.onSnapshotForTest(cardView.copy(state = TripState.IN_TRIP))

        repo.approveJoin("jr_1")
        repo.declineJoin("jr_1")
        repo.recheckCard()
        repo.startRoute()
        repo.proposeDropoff(ai.hey.model.DropoffCandidate("c1", ai.hey.model.DropoffKind.Default, null, null, null, "기본", "기본", ai.hey.model.LatLng(37.5, 127.0), 0.0), null)
        repo.decidePin(1, true)
        repo.sendReply("req_1", QuickReplyId.Yes)
        repo.arrive()
        repo.endTrip()
        repo.openDropoff()
        repo.openSettings()
        repo.openEndDialog()
        repo.reregister()

        val s = repo.state.value
        assertTrue("no command may be in flight while driving", s.commands.isEmpty())
        assertEquals(Panel.None, s.panel)
        assertFalse(s.endDialogOpen)
        assertTrue(vehicle.routes.isEmpty())
    }

    @Test fun anUnknownCarIsTreatedAsDriving() = runTest {
        val vehicle = FakeVehicleGateway(backgroundScope, initialDriving = DrivingStatus.Unknown)
        val repo = repo(backgroundScope, vehicle)
        repo.onSnapshotForTest(cardView)

        repo.approveJoin("jr_1")

        assertTrue(repo.state.value.commands.isEmpty())
    }

    /** Rule 1: the snapshot shows the effect, so the button is released. */
    @Test fun aSnapshotShowingTheEffectClearsTheCommand() = runTest {
        val vehicle = FakeVehicleGateway(backgroundScope)
        val repo = repo(backgroundScope, vehicle)
        repo.approveJoin("jr_1")
        val key = CommandKey.Join("jr_1")
        assertTrue(repo.state.value.isLoading(key))

        repo.onSnapshotForTest(view(state = TripState.CONNECTED, tripId = "t1"))

        assertFalse(repo.state.value.commands.containsKey(key))
    }

    /** Rule 2: the relay names the envelope, the key fails, and the banner clears on its own. */
    @Test fun anErrorNamingTheEnvelopeFailsThenClearsTheCommand() = runTest {
        val vehicle = FakeVehicleGateway(backgroundScope)
        val repo = repo(backgroundScope, vehicle)
        repo.approveJoin("jr_1")
        val key = CommandKey.Join("jr_1")
        val envelope = repo.envelopeForTest(key)!!

        repo.onErrorForTest(ErrorCode.BAD_REQUEST, envelope)

        assertEquals(CommandState.Failed("BAD_REQUEST"), repo.state.value.commandState(key))
        assertEquals(ErrorCode.BAD_REQUEST, repo.state.value.error)
        advanceTimeBy(6_001)
        assertFalse(repo.state.value.commands.containsKey(key))
        assertNull(repo.state.value.error)
    }

    /** Rule 3: a lost answer cannot leave a button spinning for good. */
    @Test fun aLostAnswerTimesOutAndClearsTheCommand() = runTest {
        val vehicle = FakeVehicleGateway(backgroundScope)
        val repo = repo(backgroundScope, vehicle)
        repo.approveJoin("jr_1")
        val key = CommandKey.Join("jr_1")
        assertTrue(repo.state.value.isLoading(key))

        advanceTimeBy(20_001)

        assertFalse(repo.state.value.commands.containsKey(key))
    }

    @Test fun aSpeechFailureAppearsOnD1AndRetryClearsIt() = runTest {
        val vehicle = FakeVehicleGateway(backgroundScope)
        val repo = repo(backgroundScope, vehicle)
        val issue = VehicleIssue.Speech(SpeechDropReason.EngineUnavailable)

        repo.onVehicleEventForTest(VehicleEvent.SpeechDropped("req_1", SpeechDropReason.EngineUnavailable))
        assertTrue(repo.state.value.issues.contains(issue))

        repo.retryVehicleIssue(issue)
        assertFalse(repo.state.value.issues.contains(issue))
        assertEquals(1, vehicle.speechRetries)
    }

    @Test fun aNaviFailureAppearsOnD1AndRetryClearsIt() = runTest {
        val vehicle = FakeVehicleGateway(backgroundScope)
        val repo = repo(backgroundScope, vehicle)
        val issue = VehicleIssue.Navi("NAVI_UNAVAILABLE")

        repo.onVehicleEventForTest(VehicleEvent.NavigationUnavailable("NAVI_UNAVAILABLE"))
        assertTrue(repo.state.value.issues.contains(issue))

        repo.retryVehicleIssue(issue)
        assertFalse(repo.state.value.issues.contains(issue))
        assertEquals(1, vehicle.navigationRetries)
    }

    @Test fun aSpokenTextClearsTheSpeechIssue() = runTest {
        val vehicle = FakeVehicleGateway(backgroundScope)
        val repo = repo(backgroundScope, vehicle)
        val issue = VehicleIssue.Speech(SpeechDropReason.EngineUnavailable)
        repo.onVehicleEventForTest(VehicleEvent.SpeechDropped("req_1", SpeechDropReason.EngineUnavailable))
        assertTrue(repo.state.value.issues.contains(issue))

        repo.onVehicleEventForTest(VehicleEvent.SpeechDone("s1"))

        assertFalse(repo.state.value.issues.contains(issue))
    }

    @Test fun aQueueOverflowDoesNotRaiseTheSpeechBanner() = runTest {
        val vehicle = FakeVehicleGateway(backgroundScope)
        val repo = repo(backgroundScope, vehicle)

        repo.onVehicleEventForTest(VehicleEvent.SpeechDropped("req_1", SpeechDropReason.QueueOverflow))
        repo.onVehicleEventForTest(VehicleEvent.SpeechDropped("req_2", SpeechDropReason.Blank))

        assertTrue(repo.state.value.issues.isEmpty())
    }

    @Test fun aStartedRouteClearsTheNaviIssue() = runTest {
        val vehicle = FakeVehicleGateway(backgroundScope)
        val repo = repo(backgroundScope, vehicle)
        repo.onSnapshotForTest(cardView)
        val issue = VehicleIssue.Navi("NAVI_UNAVAILABLE")
        repo.onVehicleEventForTest(VehicleEvent.NavigationUnavailable("NAVI_UNAVAILABLE"))
        assertTrue(repo.state.value.issues.contains(issue))

        repo.startRoute()
        advanceTimeBy(10)

        assertFalse(repo.state.value.issues.contains(issue))
        assertEquals(1, vehicle.routes.size)
    }
}
