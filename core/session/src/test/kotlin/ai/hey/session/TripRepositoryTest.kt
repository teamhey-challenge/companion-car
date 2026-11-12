package ai.hey.session

import ai.hey.model.CarCardView
import ai.hey.model.CarSettings
import ai.hey.model.CarView
import ai.hey.model.ClientType
import ai.hey.model.DrivingState
import ai.hey.model.ErrorCode
import ai.hey.model.ErrorData
import ai.hey.model.Place
import ai.hey.model.QuickReplyId
import ai.hey.model.ReplyItem
import ai.hey.model.RequestItem
import ai.hey.model.RequestKind
import ai.hey.model.RequestState
import ai.hey.model.ServerMessage
import ai.hey.model.TripState
import ai.hey.vehicle.DrivingStatus
import ai.hey.vehicle.FakeVehicleGateway
import ai.hey.vehicle.SpeechDropReason
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Plays the relay: records what the repository sends and lets a test deliver messages through the real listener. */
private class RecordingTransport(val listener: RelayTransport.Listener) : RelayTransport {
    class Sent(val type: String, val id: String)

    val sent = ArrayList<Sent>()
    override fun connect(baseUrl: String, token: String) = Unit
    override fun close() = Unit
    override fun send(type: String, data: JsonElement, supersedeKey: String?, id: String): String {
        sent += Sent(type, id)
        return id
    }

    fun types() = sent.map { it.type }
    fun deliver(message: ServerMessage) = listener.onMessage(message)
}

/** The repository's own rules, without a relay: driving locks, phase gates, command clearing, vehicle issues, clocks. */
@OptIn(ExperimentalCoroutinesApi::class)
class TripRepositoryTest {
    private val place = Place("p1", "Hotel", "호텔", "서울 중구", "서울", 37.5, 127.0)
    private val card = CarCardView(place, emptyList(), emptyList())

    private class Rig(val repo: TripRepository, val transport: RecordingTransport)

    private fun rig(scope: kotlinx.coroutines.CoroutineScope, vehicle: FakeVehicleGateway, clock: () -> Long = { 0L }): Rig {
        lateinit var transport: RecordingTransport
        val repo = TripRepository(
            scope,
            vehicle,
            InMemoryCarStorage(CarSettings(relayUrl = "http://127.0.0.1:1")),
            http = OkHttpClient.Builder().connectTimeout(50, TimeUnit.MILLISECONDS).build(),
            clock = clock,
            progressPollMs = 3_600_000,
            transport = { RecordingTransport(it).also { t -> transport = t } },
        )
        return Rig(repo, transport)
    }

    private fun view(
        state: TripState = TripState.CONNECTED,
        pendingJoin: ai.hey.model.PendingJoin? = null,
        card: CarCardView? = null,
        tripId: String? = "t1",
        requests: List<RequestItem> = emptyList(),
        replies: List<ReplyItem> = emptyList(),
    ) = CarView(
        state = state,
        carCode = "C",
        plateSuffix = "1",
        driving = DrivingState.PARKED,
        tripId = tripId,
        pendingJoin = pendingJoin,
        card = card,
        requests = requests,
        replies = replies,
    )

    private fun snapshot(rig: Rig, view: CarView, ts: Long? = null) = rig.transport.deliver(ServerMessage.Snapshot("s-${rig.transport.sent.size}-${view.state}", view, ts))

    private val cardView get() = view(card = card)
    private val question = RequestItem("req_1", RequestKind.Preset, null, "창문을 열어도 될까요?", true, RequestState.Received, ts = 0)

    @Test fun theDropoffDraftAndPanelSurviveADrivingToggle() = runTest {
        val vehicle = FakeVehicleGateway(backgroundScope)
        val rig = rig(backgroundScope, vehicle)
        val repo = rig.repo
        repo.start()
        snapshot(rig, cardView.copy(state = TripState.CARD_SENT))
        repo.state.value.parked!!.openDropoff()
        repo.selectCandidate("fake-2")
        repo.selectReason(1)
        assertEquals(Panel.Dropoff, repo.state.value.panel)

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

    /** M6: while driving (or unknown) there is no ParkedCommands to call at all. */
    @Test fun noHandsOnCommandsExistWhileDrivingOrUnknown() = runTest {
        for (status in listOf(DrivingStatus.Driving, DrivingStatus.Unknown)) {
            val rig = rig(backgroundScope, FakeVehicleGateway(backgroundScope, initialDriving = status))
            snapshot(rig, cardView.copy(state = TripState.IN_TRIP))
            assertNull("$status must not hand out hands-on commands", rig.repo.state.value.parked)
        }
    }

    /** M6: a reference kept from a parked moment still cannot act once the car moves (the live phase is asked again). */
    @Test fun aKeptParkedReferenceIsRefusedOnceTheCarMoves() = runTest {
        val vehicle = FakeVehicleGateway(backgroundScope)
        val rig = rig(backgroundScope, vehicle)
        rig.repo.start()
        snapshot(rig, cardView.copy(state = TripState.IN_TRIP))
        val kept = rig.repo.state.value.parked!!
        vehicle.setDriving(true)
        advanceTimeBy(10)
        val before = rig.transport.sent.size

        kept.arrive()
        kept.endTrip()
        kept.openDropoff()
        kept.openSettings()
        kept.openEndDialog()
        kept.startRoute()

        assertEquals(before, rig.transport.sent.size)
        assertTrue(rig.repo.state.value.commands.isEmpty())
        assertEquals(Panel.None, rig.repo.state.value.panel)
        assertFalse(rig.repo.state.value.endDialogOpen)
        assertTrue(vehicle.routes.isEmpty())
    }

    /** B3: in CONNECTED the relay refuses route.started; the car must not start navigation or send it. */
    @Test fun startRouteInConnectedStartsNoNavigationAndSendsNothing() = runTest {
        val vehicle = FakeVehicleGateway(backgroundScope)
        val rig = rig(backgroundScope, vehicle)
        snapshot(rig, cardView.copy(state = TripState.CONNECTED))

        rig.repo.state.value.parked!!.startRoute()
        advanceTimeBy(10)

        assertTrue(vehicle.routes.isEmpty())
        assertFalse(ClientType.ROUTE_STARTED in rig.transport.types())
        assertFalse(rig.repo.state.value.routeStarting)
    }

    @Test fun recheckInTripAndArriveInCardSentAreNotSent() = runTest {
        val rig = rig(backgroundScope, FakeVehicleGateway(backgroundScope))
        snapshot(rig, cardView.copy(state = TripState.IN_TRIP))
        rig.repo.state.value.parked!!.recheckCard()
        snapshot(rig, cardView.copy(state = TripState.CARD_SENT))
        rig.repo.state.value.parked!!.arrive()

        assertFalse(ClientType.CARD_RECHECK in rig.transport.types())
        assertFalse(ClientType.TRIP_ARRIVE in rig.transport.types())
    }

    /** M3: end is legal from every non-terminal parked state; it was only reachable from ARRIVED. */
    @Test fun theTripCanBeEndedFromConnectedCardSentAndInTrip() = runTest {
        for (state in listOf(TripState.CONNECTED, TripState.CARD_SENT, TripState.IN_TRIP)) {
            val rig = rig(backgroundScope, FakeVehicleGateway(backgroundScope))
            snapshot(rig, cardView.copy(state = state))
            rig.repo.state.value.parked!!.openEndDialog()
            assertTrue("$state: the confirm dialog opens", rig.repo.state.value.endDialogOpen)

            rig.repo.state.value.parked!!.endTrip()

            assertEquals("$state: trip.end is sent", listOf(ClientType.TRIP_END), rig.transport.types().filter { it == ClientType.TRIP_END })
            assertFalse(rig.repo.state.value.endDialogOpen)
        }
    }

    /** M2: a question asked while CONNECTED or CARD_SENT can be answered. */
    @Test fun aReplyIsSentInConnectedAndCardSent() = runTest {
        for (state in listOf(TripState.CONNECTED, TripState.CARD_SENT)) {
            val rig = rig(backgroundScope, FakeVehicleGateway(backgroundScope))
            snapshot(rig, cardView.copy(state = state, requests = listOf(question)))
            assertTrue(rig.repo.state.value.phase is CarPhase.Requests)

            rig.repo.state.value.parked!!.sendReply("req_1", QuickReplyId.Yes)

            assertTrue("$state: reply.send goes out", ClientType.REPLY_SEND in rig.transport.types())
        }
    }

    /** M8: a failed route start belongs to its trip; the next trip must not show it. */
    @Test fun aFailedRouteStartDoesNotCarryOverToTheNextTrip() = runTest {
        val vehicle = FakeVehicleGateway(backgroundScope).also { it.routeFailure = "NAVI_UNAVAILABLE" }
        val rig = rig(backgroundScope, vehicle)
        snapshot(rig, cardView.copy(state = TripState.CARD_SENT, tripId = "t1"))
        rig.repo.state.value.parked!!.startRoute()
        advanceTimeBy(10)
        assertTrue(rig.repo.state.value.routeFailed)

        snapshot(rig, cardView.copy(state = TripState.CARD_SENT, tripId = "t2"))

        assertFalse("the next trip starts clean", rig.repo.state.value.routeFailed)
        assertTrue(rig.repo.state.value.commands.isEmpty())
    }

    /** Rule 1: the snapshot shows the effect, so the button is released. */
    @Test fun aSnapshotShowingTheEffectClearsTheCommand() = runTest {
        val rig = rig(backgroundScope, FakeVehicleGateway(backgroundScope))
        snapshot(rig, view(state = TripState.REQUESTED, pendingJoin = ai.hey.model.PendingJoin("jr_1", ai.hey.model.Lang.En, 1, 0)))
        rig.repo.state.value.parked!!.approveJoin("jr_1")
        val key = CommandKey.Join("jr_1")
        assertTrue(rig.repo.state.value.isLoading(key))

        snapshot(rig, view(state = TripState.CONNECTED, tripId = "t1"))

        assertFalse(rig.repo.state.value.commands.containsKey(key))
    }

    /** Rule 2: the relay names the envelope, the key fails, and the banner clears on its own. */
    @Test fun anErrorNamingTheEnvelopeFailsThenClearsTheCommand() = runTest {
        val rig = rig(backgroundScope, FakeVehicleGateway(backgroundScope))
        snapshot(rig, view(state = TripState.REQUESTED, pendingJoin = ai.hey.model.PendingJoin("jr_1", ai.hey.model.Lang.En, 1, 0)))
        rig.repo.state.value.parked!!.approveJoin("jr_1")
        val key = CommandKey.Join("jr_1")
        val envelope = rig.transport.sent.last { it.type == ClientType.JOIN_APPROVE }.id

        rig.transport.deliver(ServerMessage.Error("e1", ErrorData(ErrorCode.BAD_REQUEST, ref = envelope)))

        assertEquals(CommandState.Failed("BAD_REQUEST"), rig.repo.state.value.commandState(key))
        assertEquals(ErrorCode.BAD_REQUEST, rig.repo.state.value.error)
        advanceTimeBy(6_001)
        assertFalse(rig.repo.state.value.commands.containsKey(key))
        assertNull(rig.repo.state.value.error)
    }

    /** m4: a refusal of a message no button sent (a read receipt after the trip ended) paints no banner. */
    @Test fun anErrorForAnUnattendedMessageShowsNoBanner() = runTest {
        val rig = rig(backgroundScope, FakeVehicleGateway(backgroundScope))

        rig.transport.deliver(ServerMessage.Error("e2", ErrorData(ErrorCode.TRIP_ENDED, ref = "some-request-spoken-envelope")))
        rig.transport.deliver(ServerMessage.Error("e3", ErrorData(ErrorCode.TRIP_ENDED, ref = null)))

        assertNull(rig.repo.state.value.error)
    }

    /** m4: the relay keeps only the newest replies, so after the cap the count stalls; the newest reply changing still releases the button. */
    @Test fun aReplyIsSettledEvenWhenTheRelayListIsAtItsCap() = runTest {
        val rig = rig(backgroundScope, FakeVehicleGateway(backgroundScope))
        val full = (1..100).map { ReplyItem(QuickReplyId.Yes, null, "네", ts = it.toLong()) }
        snapshot(rig, cardView.copy(state = TripState.CARD_SENT, requests = listOf(question), replies = full))
        rig.repo.state.value.parked!!.sendReply("req_1", QuickReplyId.Moment)
        assertNotNull(rig.repo.state.value.replyInFlight)

        // 100 replies in, 100 replies out: the oldest dropped, the new one last.
        snapshot(rig, cardView.copy(state = TripState.CARD_SENT, requests = listOf(question), replies = full.drop(1) + ReplyItem(QuickReplyId.Moment, null, "잠시만요", ts = 101)))

        assertNull(rig.repo.state.value.replyInFlight)
    }

    /** m3: the join countdown runs on the relay's clock, corrected by the envelope timestamp. */
    @Test fun theClockOffsetFollowsTheSnapshotTimestamp() = runTest {
        var deviceNow = 1_000_000L
        val rig = rig(backgroundScope, FakeVehicleGateway(backgroundScope), clock = { deviceNow })

        snapshot(rig, view(), ts = deviceNow - 60_000)
        assertEquals(60_000L, rig.repo.state.value.clockOffsetMs)

        // jitter below a second keeps the offset, a real step replaces it
        snapshot(rig, view(), ts = deviceNow - 60_400)
        assertEquals(60_000L, rig.repo.state.value.clockOffsetMs)
        deviceNow += 10_000
        snapshot(rig, view(), ts = deviceNow - 5_000)
        assertEquals(5_000L, rig.repo.state.value.clockOffsetMs)
    }

    /** Rule 3: a lost answer cannot leave a button spinning for good. */
    @Test fun aLostAnswerTimesOutAndClearsTheCommand() = runTest {
        val rig = rig(backgroundScope, FakeVehicleGateway(backgroundScope))
        snapshot(rig, view(state = TripState.REQUESTED, pendingJoin = ai.hey.model.PendingJoin("jr_1", ai.hey.model.Lang.En, 1, 0)))
        rig.repo.state.value.parked!!.approveJoin("jr_1")
        val key = CommandKey.Join("jr_1")
        assertTrue(rig.repo.state.value.isLoading(key))

        advanceTimeBy(20_001)

        assertFalse(rig.repo.state.value.commands.containsKey(key))
    }

    @Test fun aSpeechFailureAppearsOnD1AndRetryClearsIt() = runTest {
        val vehicle = FakeVehicleGateway(backgroundScope)
        val rig = rig(backgroundScope, vehicle)
        rig.repo.start()
        advanceTimeBy(10) // let the event collector subscribe before the fake emits
        val issue = VehicleIssue.Speech(SpeechDropReason.EngineUnavailable)

        vehicle.emitSpeechDropped("req_1", SpeechDropReason.EngineUnavailable)
        advanceTimeBy(10)
        assertTrue(rig.repo.state.value.issues.contains(issue))

        rig.repo.retryVehicleIssue(issue)
        assertFalse(rig.repo.state.value.issues.contains(issue))
        assertEquals(1, vehicle.speechRetries)
    }

    @Test fun aNaviFailureAppearsOnD1AndRetryClearsIt() = runTest {
        val vehicle = FakeVehicleGateway(backgroundScope)
        val rig = rig(backgroundScope, vehicle)
        rig.repo.start()
        advanceTimeBy(10)
        val issue = VehicleIssue.Navi("NAVI_UNAVAILABLE")

        vehicle.emitNavigationUnavailable("NAVI_UNAVAILABLE")
        advanceTimeBy(10)
        assertTrue(rig.repo.state.value.issues.contains(issue))

        rig.repo.retryVehicleIssue(issue)
        assertFalse(rig.repo.state.value.issues.contains(issue))
        assertEquals(1, vehicle.navigationRetries)
    }

    @Test fun aQueueOverflowDoesNotRaiseTheSpeechBanner() = runTest {
        val vehicle = FakeVehicleGateway(backgroundScope)
        val rig = rig(backgroundScope, vehicle)
        rig.repo.start()
        advanceTimeBy(10)

        vehicle.emitSpeechDropped("req_1", SpeechDropReason.QueueOverflow)
        vehicle.emitSpeechDropped("req_2", SpeechDropReason.Blank)
        advanceTimeBy(10)

        assertTrue(rig.repo.state.value.issues.isEmpty())
    }

    @Test fun aStartedRouteClearsTheNaviIssue() = runTest {
        val vehicle = FakeVehicleGateway(backgroundScope)
        val rig = rig(backgroundScope, vehicle)
        rig.repo.start()
        advanceTimeBy(10)
        snapshot(rig, cardView.copy(state = TripState.CARD_SENT))
        val issue = VehicleIssue.Navi("NAVI_UNAVAILABLE")
        vehicle.emitNavigationUnavailable("NAVI_UNAVAILABLE")
        advanceTimeBy(10)
        assertTrue(rig.repo.state.value.issues.contains(issue))

        rig.repo.state.value.parked!!.startRoute()
        advanceTimeBy(10)

        assertFalse(rig.repo.state.value.issues.contains(issue))
        assertEquals(1, vehicle.routes.size)
        assertTrue(ClientType.ROUTE_STARTED in rig.transport.types())
    }
}
