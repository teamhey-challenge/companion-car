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
import ai.hey.model.ReplyKind
import ai.hey.vehicle.ScriptedVoiceInput
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
import ai.hey.model.ConditionId
import ai.hey.model.MusicPref
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
    class Sent(val type: String, val id: String, val data: JsonElement? = null)

    val sent = ArrayList<Sent>()
    override fun connect(baseUrl: String, token: String) = Unit
    override fun close() = Unit
    override fun send(type: String, data: JsonElement, supersedeKey: String?, id: String): String {
        sent += Sent(type, id, data)
        return id
    }

    fun types() = sent.map { it.type }
    fun deliver(message: ServerMessage) = listener.onMessage(message)
    fun lastData(type: String) = sent.last { it.type == type }.data!!.jsonObject
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

        assertEquals(before, rig.transport.sent.size)
        assertTrue(rig.repo.state.value.commands.isEmpty())
        assertEquals(Panel.None, rig.repo.state.value.panel)
        assertFalse(rig.repo.state.value.endDialogOpen)
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

    /** Rule 1: the snapshot shows the effect, so the button is released. */
    @Test fun aSnapshotShowingTheEffectClearsTheCommand() = runTest {
        val rig = rig(backgroundScope, FakeVehicleGateway(backgroundScope))
        snapshot(rig, view(state = TripState.REQUESTED, pendingJoin = ai.hey.model.PendingJoin("jr_1", ai.hey.model.Lang.En, 1, 0, "47")))
        rig.repo.state.value.parked!!.approveJoin("jr_1")
        val key = CommandKey.Join("jr_1")
        assertTrue(rig.repo.state.value.isLoading(key))

        snapshot(rig, view(state = TripState.CONNECTED, tripId = "t1"))

        assertFalse(rig.repo.state.value.commands.containsKey(key))
    }

    /** Rule 2: the relay names the envelope, the key fails, and the banner clears on its own. */
    @Test fun anErrorNamingTheEnvelopeFailsThenClearsTheCommand() = runTest {
        val rig = rig(backgroundScope, FakeVehicleGateway(backgroundScope))
        snapshot(rig, view(state = TripState.REQUESTED, pendingJoin = ai.hey.model.PendingJoin("jr_1", ai.hey.model.Lang.En, 1, 0, "47")))
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

    /** Handshake: a 426 on the socket is its own state (not Rejected, not Reconnecting) and clears the trip view. */
    @Test fun aProtocolMismatchFromTheLinkIsItsOwnConnectionState() = runTest {
        val rig = rig(backgroundScope, FakeVehicleGateway(backgroundScope))
        snapshot(rig, cardView.copy(state = TripState.CARD_SENT))

        rig.transport.listener.onProtocolMismatch()

        assertEquals(ConnectionStatus.ProtocolMismatch, rig.repo.state.value.connection)
        assertNull(rig.repo.state.value.view)
        assertFalse(rig.repo.state.value.online)
    }

    /** m4: the relay keeps only the newest replies, so after the cap the count stalls; the newest reply changing still releases the button. */
    @Test fun aReplyIsSettledEvenWhenTheRelayListIsAtItsCap() = runTest {
        val rig = rig(backgroundScope, FakeVehicleGateway(backgroundScope))
        val full = (1..100).map { ReplyItem(ReplyKind.Button, QuickReplyId.Yes, null, "네", true, ts = it.toLong()) }
        snapshot(rig, cardView.copy(state = TripState.CARD_SENT, requests = listOf(question), replies = full))
        rig.repo.state.value.parked!!.sendReply("req_1", QuickReplyId.Moment)
        assertNotNull(rig.repo.state.value.replyInFlight)

        // 100 replies in, 100 replies out: the oldest dropped, the new one last.
        snapshot(rig, cardView.copy(state = TripState.CARD_SENT, requests = listOf(question), replies = full.drop(1) + ReplyItem(ReplyKind.Button, QuickReplyId.Moment, null, "잠시만요", true, ts = 101)))

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
        snapshot(rig, view(state = TripState.REQUESTED, pendingJoin = ai.hey.model.PendingJoin("jr_1", ai.hey.model.Lang.En, 1, 0, "47")))
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

    /** D8: a storage that cannot keep the value is reported (the value stays in use), and a later good write clears it. */
    @Test fun aFailingSettingsWriteIsReportedAndClearedByTheNextGoodOne() = runTest {
        class FlakyStorage(val inner: InMemoryCarStorage = InMemoryCarStorage(CarSettings(relayUrl = "http://127.0.0.1:1"))) : CarStorage by inner {
            var failing = true
            override suspend fun updateSettings(transform: (CarSettings) -> CarSettings): SaveResult {
                inner.updateSettings(transform)
                return if (failing) SaveResult.Failed(SaveFailure.WriteRejected) else SaveResult.Saved
            }
        }
        val storage = FlakyStorage()
        val repo = TripRepository(
            backgroundScope, FakeVehicleGateway(backgroundScope), storage,
            http = OkHttpClient.Builder().connectTimeout(50, TimeUnit.MILLISECONDS).build(),
            progressPollMs = 3_600_000, transport = { RecordingTransport(it) },
        )
        repo.start()
        advanceTimeBy(10)

        repo.updateSettings { it.copy(speechGapSec = 12) }
        advanceTimeBy(10)
        assertEquals(SaveResult.Failed(SaveFailure.WriteRejected), repo.state.value.settingsSave)
        assertEquals("the value stays in use", 12, repo.state.value.settings.speechGapSec)

        storage.failing = false
        repo.updateSettings { it.copy(speechGapSec = 14) }
        advanceTimeBy(10)
        assertEquals(SaveResult.Saved, repo.state.value.settingsSave)

        storage.failing = true
        repo.updateSettings { it.copy(dark = false) }
        advanceTimeBy(10)
        repo.closeSettings()
        assertEquals("a new visit to D8 starts clean", SaveResult.Saved, repo.state.value.settingsSave)
    }

    /** D6: a reply belongs to an open question; a stale or invented request id sends nothing. */
    @Test fun aReplyForNoOpenQuestionIsNotSent() = runTest {
        val rig = rig(backgroundScope, FakeVehicleGateway(backgroundScope))
        snapshot(rig, cardView.copy(state = TripState.CARD_SENT, requests = listOf(question.copy(state = RequestState.Answered))))
        rig.repo.state.value.parked!!.sendReply("req_1", QuickReplyId.Yes)
        rig.repo.state.value.parked!!.sendReply("req_gone", QuickReplyId.Yes)
        assertFalse(ClientType.REPLY_SEND in rig.transport.types())
    }

    /** Group B: the driver speaks first; it needs no passenger question and carries no request id. */
    @Test fun aDriverFirstReplyNeedsNoQuestionAndCarriesNoRequest() = runTest {
        val rig = rig(backgroundScope, FakeVehicleGateway(backgroundScope))
        snapshot(rig, cardView.copy(state = TripState.CARD_SENT))
        rig.repo.state.value.parked!!.sendReply(null, QuickReplyId.StopHere)
        assertTrue(ClientType.REPLY_SEND in rig.transport.types())
        assertNotNull(rig.repo.state.value.replyInFlight)
    }

    /** Group A without a question is not sendable (and a group-B reply does not become one by naming a request). */
    @Test fun anAnswerReplyWithoutAnOpenQuestionIsNotSent() = runTest {
        val rig = rig(backgroundScope, FakeVehicleGateway(backgroundScope))
        snapshot(rig, cardView.copy(state = TripState.CARD_SENT))
        rig.repo.state.value.parked!!.sendReply(null, QuickReplyId.Yes)
        assertFalse(ClientType.REPLY_SEND in rig.transport.types())
    }

    // ---------------------------------------------------------------- voice reply

    private fun voiceRig(scope: kotlinx.coroutines.CoroutineScope, vehicle: FakeVehicleGateway, voice: ScriptedVoiceInput): Rig {
        lateinit var transport: RecordingTransport
        val repo = TripRepository(
            scope,
            vehicle,
            InMemoryCarStorage(CarSettings(relayUrl = "http://127.0.0.1:1")),
            http = OkHttpClient.Builder().connectTimeout(50, TimeUnit.MILLISECONDS).build(),
            progressPollMs = 3_600_000,
            voiceInput = voice,
            transport = { RecordingTransport(it).also { t -> transport = t } },
        )
        return Rig(repo, transport)
    }

    @Test fun aVoiceReplyIsHeardConfirmedAndOnlyThenSent() = runTest {
        val voice = ScriptedVoiceInput(text = "네, 입구 앞에서 기다릴게요.")
        val rig = voiceRig(backgroundScope, FakeVehicleGateway(backgroundScope), voice)
        snapshot(rig, cardView.copy(state = TripState.CARD_SENT))
        rig.repo.state.value.parked!!.openReply()
        rig.repo.state.value.parked!!.startVoiceReply()
        advanceTimeBy(50)
        assertEquals(VoiceState.Confirm("네, 입구 앞에서 기다릴게요."), rig.repo.state.value.voice)
        assertFalse("nothing is sent before the driver confirms", ClientType.REPLY_SEND in rig.transport.types())
        rig.repo.state.value.parked!!.sendVoiceReply()
        assertTrue(ClientType.REPLY_SEND in rig.transport.types())
        assertTrue(rig.repo.state.value.voiceInFlight)
        // the snapshot shows the new reply: the button releases and the screen says it was delivered
        snapshot(rig, cardView.copy(state = TripState.CARD_SENT, replies = listOf(ReplyItem(ReplyKind.Voice, null, null, "네, 입구 앞에서 기다릴게요.", true, 1))))
        assertFalse(rig.repo.state.value.voiceInFlight)
        assertEquals(VoiceState.Sent, rig.repo.state.value.voice)
    }

    @Test fun silenceIsReportedAndSendsNothing() = runTest {
        val rig = voiceRig(backgroundScope, FakeVehicleGateway(backgroundScope), ScriptedVoiceInput(text = null))
        snapshot(rig, cardView.copy(state = TripState.CARD_SENT))
        rig.repo.state.value.parked!!.startVoiceReply()
        advanceTimeBy(50)
        assertEquals(VoiceState.Failed(VoiceState.Reason.NoSpeech), rig.repo.state.value.voice)
        rig.repo.state.value.parked!!.sendVoiceReply()
        assertFalse(ClientType.REPLY_SEND in rig.transport.types())
    }

    @Test fun withoutTheMicrophonePermissionTheSessionAsksAndResumes() = runTest {
        val voice = ScriptedVoiceInput(permission = false, text = "잠시만요")
        val rig = voiceRig(backgroundScope, FakeVehicleGateway(backgroundScope), voice)
        snapshot(rig, cardView.copy(state = TripState.CARD_SENT))
        rig.repo.state.value.parked!!.startVoiceReply()
        assertEquals(VoiceState.NeedsPermission, rig.repo.state.value.voice)
        voice.permission = true
        rig.repo.voicePermissionResult(true)
        advanceTimeBy(50)
        assertEquals(VoiceState.Confirm("잠시만요"), rig.repo.state.value.voice)
        rig.repo.cancelVoice()
        voice.permission = false
        rig.repo.state.value.parked!!.startVoiceReply()
        rig.repo.voicePermissionResult(false)
        assertEquals(VoiceState.Failed(VoiceState.Reason.PermissionDenied), rig.repo.state.value.voice)
    }

    @Test fun theMicrophoneIsNeverOpenedWhileDrivingAndClosesWhenTheCarMoves() = runTest {
        val vehicle = FakeVehicleGateway(backgroundScope)
        val voice = ScriptedVoiceInput(listenMillis = 10_000, text = "네")
        val rig = voiceRig(backgroundScope, vehicle, voice)
        rig.repo.start()
        advanceTimeBy(10)
        snapshot(rig, cardView.copy(state = TripState.CARD_SENT))
        rig.repo.state.value.parked!!.startVoiceReply()
        advanceTimeBy(100)
        assertEquals(VoiceState.Listening, rig.repo.state.value.voice)
        vehicle.setDriving(true)
        advanceTimeBy(10)
        assertEquals("the sentence is thrown away once the car moves", VoiceState.Idle, rig.repo.state.value.voice)
        assertNull("no hands-on commands, so no push-to-talk, while driving", rig.repo.state.value.parked)
        advanceTimeBy(20_000)
        assertEquals(VoiceState.Idle, rig.repo.state.value.voice)
        assertEquals(1, voice.listens)
    }

    @Test fun aVoiceTextIsCutToTheRelayLimit() = runTest {
        val rig = voiceRig(backgroundScope, FakeVehicleGateway(backgroundScope), ScriptedVoiceInput(text = "가".repeat(150)))
        snapshot(rig, cardView.copy(state = TripState.CARD_SENT))
        rig.repo.state.value.parked!!.startVoiceReply()
        advanceTimeBy(50)
        assertEquals(100, (rig.repo.state.value.voice as VoiceState.Confirm).textKo.length)
    }

    @Test fun aVoiceTextIsCutByCodePointsNotUtf16Units() = runTest {
        val rig = voiceRig(backgroundScope, FakeVehicleGateway(backgroundScope), ScriptedVoiceInput(text = "가".repeat(99) + "😀😀"))
        snapshot(rig, cardView.copy(state = TripState.CARD_SENT))
        rig.repo.state.value.parked!!.startVoiceReply()
        advanceTimeBy(50)
        assertEquals("가".repeat(99) + "😀", (rig.repo.state.value.voice as VoiceState.Confirm).textKo)
    }

    /** M2: translation down — the dialog stays open and says why itself (the banner is behind the scrim); sending again clears it. */
    @Test fun aTranslateFailedVoiceReplyKeepsTheDialogWithTheReason() = runTest {
        val rig = voiceRig(backgroundScope, FakeVehicleGateway(backgroundScope), ScriptedVoiceInput(text = "네"))
        snapshot(rig, cardView.copy(state = TripState.CARD_SENT))
        rig.repo.state.value.parked!!.startVoiceReply()
        advanceTimeBy(50)
        rig.repo.state.value.parked!!.sendVoiceReply()
        val envelope = rig.transport.sent.last { it.type == ClientType.REPLY_SEND }.id
        rig.transport.deliver(ServerMessage.Error("e1", ErrorData(ErrorCode.TRANSLATE_FAILED, ref = envelope)))
        assertEquals(VoiceState.Confirm("네", ErrorCode.TRANSLATE_FAILED), rig.repo.state.value.voice)
        assertFalse(rig.repo.state.value.voiceInFlight)
        rig.repo.state.value.parked!!.sendVoiceReply()
        assertEquals(VoiceState.Confirm("네", null), rig.repo.state.value.voice)
        assertTrue(rig.repo.state.value.voiceInFlight)
    }

    /** M4: with no chosen question the voice reply answers the OLDEST open one (same rule as the relay). */
    @Test fun aVoiceReplyTargetsTheOldestOpenQuestion() = runTest {
        val rig = voiceRig(backgroundScope, FakeVehicleGateway(backgroundScope), ScriptedVoiceInput(text = "네"))
        val answered = question.copy(requestId = "req_0", state = RequestState.Answered)
        val newer = question.copy(requestId = "req_2")
        snapshot(rig, cardView.copy(state = TripState.CARD_SENT, requests = listOf(answered, question, newer)))
        rig.repo.state.value.parked!!.startVoiceReply()
        advanceTimeBy(50)
        rig.repo.state.value.parked!!.sendVoiceReply()
        assertEquals("req_1", rig.transport.lastData(ClientType.REPLY_SEND)["requestId"]?.jsonPrimitive?.content)
    }

    /** M5: a changed music value alone is highlighted on D3. */
    @Test fun aMusicOnlyChangeIsHighlighted() = runTest {
        val rig = rig(backgroundScope, FakeVehicleGateway(backgroundScope))
        val low = card.copy(conditions = listOf(ConditionId.Music), music = MusicPref.Low)
        snapshot(rig, cardView.copy(state = TripState.CARD_SENT, card = low))
        snapshot(rig, cardView.copy(state = TripState.CARD_SENT, card = low.copy(music = MusicPref.Off)))
        assertEquals(setOf(ConditionId.Music), rig.repo.state.value.changedConditions)
    }

    /** The driver opens the reply screen without any question: D6 shows and closes by itself. */
    @Test fun theDriverCanOpenTheReplyScreenWithoutAQuestion() = runTest {
        val rig = rig(backgroundScope, FakeVehicleGateway(backgroundScope))
        snapshot(rig, cardView.copy(state = TripState.CARD_SENT))
        rig.repo.state.value.parked!!.openReply()
        assertTrue(rig.repo.state.value.phase is CarPhase.Requests)
        rig.repo.dismissRequests()
        assertTrue(rig.repo.state.value.phase is CarPhase.CardSent)
    }

    // ---------------------------------------------------------------- speech the relay does not voice

    @Test fun aTripThatEndsForAnyReasonSpeaksTheEndOnce() = runTest {
        for (from in listOf(TripState.CONNECTED, TripState.CARD_SENT, TripState.IN_TRIP, TripState.ARRIVED)) {
            val vehicle = FakeVehicleGateway(backgroundScope)
            val rig = rig(backgroundScope, vehicle)
            snapshot(rig, view(state = from, card = card))
            snapshot(rig, view(state = TripState.ENDED, card = card))
            snapshot(rig, view(state = TripState.ENDED, card = card))
            advanceTimeBy(10)
            assertEquals("$from -> ENDED", listOf(ai.hey.model.LocalSpeech.ENDED), vehicle.spoken)
        }
    }

    @Test fun arrivalSpeaksTheArrivalAndNothingElseSpeaksOnPlainTransitions() = runTest {
        val vehicle = FakeVehicleGateway(backgroundScope)
        val rig = rig(backgroundScope, vehicle)
        snapshot(rig, view(state = TripState.CONNECTED, card = card))
        snapshot(rig, view(state = TripState.CARD_SENT, card = card))
        snapshot(rig, view(state = TripState.IN_TRIP, card = card))
        advanceTimeBy(10)
        assertTrue(vehicle.spoken.isEmpty())
        snapshot(rig, view(state = TripState.ARRIVED, card = card))
        advanceTimeBy(10)
        assertEquals(listOf(ai.hey.model.LocalSpeech.ARRIVED), vehicle.spoken)
    }

    @Test fun aJoinRequestThatLapsesIsSpokenButOneTheDriverDecidedIsNot() = runTest {
        val join = ai.hey.model.PendingJoin("jr_1", ai.hey.model.Lang.En, 1, 0, "47")
        val lapsed = FakeVehicleGateway(backgroundScope)
        val a = rig(backgroundScope, lapsed)
        snapshot(a, view(state = TripState.REQUESTED, pendingJoin = join))
        snapshot(a, view(state = TripState.IDLE))
        advanceTimeBy(10)
        assertEquals(listOf(ai.hey.model.LocalSpeech.JOIN_EXPIRED), lapsed.spoken)

        val declined = FakeVehicleGateway(backgroundScope)
        val b = rig(backgroundScope, declined)
        snapshot(b, view(state = TripState.REQUESTED, pendingJoin = join))
        b.repo.state.value.parked!!.declineJoin("jr_1")
        snapshot(b, view(state = TripState.IDLE))
        advanceTimeBy(10)
        assertTrue(declined.spoken.isEmpty())
    }

}
