package ai.hey.session

import ai.hey.model.AudioSpec
import ai.hey.model.ConsentType
import ai.hey.model.ServerFrame
import ai.hey.vehicle.FakeVehicleGateway
import ai.hey.vehicle.FocusGate
import ai.hey.vehicle.PlaybackEnd
import ai.hey.vehicle.PlaybackListener
import ai.hey.vehicle.ReplyAudio
import ai.hey.vehicle.ScriptedVoiceInput
import ai.hey.vehicle.VoiceInput
import ai.hey.vehicle.VoiceResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeTransport(val listener: TalkTransport.Listener) : TalkTransport {
    val sent = ArrayList<String>()
    var open = true
    override fun connect(serverUrl: String) = listener.onConnection(ConnectionStatus.Online)
    override fun send(text: String): Boolean = open.also { if (it) sent += text }
    override fun close() = Unit
}

private class FakeReplyAudio : ReplyAudio {
    override var listener: PlaybackListener? = null
    var begins = 0
    var writes = 0
    var finishes = 0
    var stops = 0
    override fun begin(spec: AudioSpec) { begins++ }
    override fun write(pcm: ByteArray) { writes++ }
    override fun finish() { finishes++ }
    override fun stop() { stops++ }
}

private class FakeFocusGate : FocusGate {
    var held = false
    var loss: (() -> Unit)? = null
    override fun request(onLoss: () -> Unit): Boolean { held = true; loss = onLoss; return true }
    override fun abandon() { held = false }
}

private fun types(sent: List<String>) = sent.map { Regex("\"type\":\"([^\"]+)\"").find(it)!!.groupValues[1] }.filter { it != "ctx" }
@OptIn(ExperimentalCoroutinesApi::class)
class TalkRepositoryTest {
    private class Rig(
        scope: CoroutineScope,
        voice: VoiceInput,
        audio: ReplyAudio? = null,
        focus: FocusGate? = null,
        speechMillis: Long = 0,
        consented: Boolean = true,
        autoListenMillis: Long = 5_000,
    ) {
        val vehicle = FakeVehicleGateway(scope, speechMillis = speechMillis)
        lateinit var transport: FakeTransport
        val repo = TalkRepository(
            scope = scope,
            vehicle = vehicle,
            storage = InMemoryCarStorage(initialConsents = if (consented) ConsentMirror(setOf(ConsentType.Record)) else ConsentMirror()),
            voiceInput = voice,
            transport = { FakeTransport(it).also { t -> transport = t } },
            newTurnId = { "t1" },
            audio = audio,
            speechFocus = focus,
            autoListenMillis = autoListenMillis,
            ctxDebounceMillis = 1_000,
        ).also { it.start() }
    }

    private fun rigScope(testScheduler: TestCoroutineScheduler) =
        CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

    @Test fun `a turn sends start text end and shows the streamed reply`() = runTest {
        val cs = rigScope(testScheduler)
        val rig = Rig(cs, ScriptedVoiceInput(text = "안녕"))
        rig.repo.startTalk()
        assertEquals(listOf("turn.start", "text", "turn.end"), types(rig.transport.sent))
        assertEquals(TurnState.Thinking, rig.repo.state.value.turn)
        rig.transport.listener.onFrame(ServerFrame.ReplyStart("t1", AudioSpec.None))
        rig.transport.listener.onFrame(ServerFrame.ReplyDelta("t1", "반가"))
        rig.transport.listener.onFrame(ServerFrame.ReplyDelta("t1", "워요"))
        assertEquals("반가워요", rig.repo.state.value.reply)
        rig.transport.listener.onFrame(ServerFrame.ReplyEnd("t1", "반가워요", 100, 900))
        testScheduler.advanceUntilIdle()
        // audio:none -> the car reads the text with its own TTS, and the turn is over when it was read
        assertEquals(listOf("반가워요"), rig.vehicle.spoken)
        assertEquals(TurnState.Idle, rig.repo.state.value.turn)
        cs.cancel()
    }

    @Test fun `leaving P does not cut listening or the turn`() = runTest {
        val cs = rigScope(testScheduler)
        val gate = CompletableDeferred<Unit>()
        val voice = object : VoiceInput {
            override fun hasPermission() = true
            override suspend fun listen(): VoiceResult {
                gate.await()
                return VoiceResult.Text("달리는 중이야")
            }
            override fun release() = Unit
        }
        val rig = Rig(cs, voice)
        rig.repo.startTalk()
        assertEquals(TurnState.Listening, rig.repo.state.value.turn)
        rig.vehicle.setDriving(true) // P -> D while the microphone is open
        assertEquals(TurnState.Listening, rig.repo.state.value.turn)
        assertNull(rig.repo.state.value.parked) // hands-on commands are gone, talking is not
        gate.complete(Unit)
        assertEquals(TurnState.Thinking, rig.repo.state.value.turn)
        assertTrue(rig.transport.sent.any { it.contains("달리는 중이야") })
        rig.transport.listener.onFrame(ServerFrame.ReplyEnd("t1", "조심히 가요", null, 800))
        assertEquals("조심히 가요", rig.repo.state.value.reply)
        cs.cancel()
    }

    @Test fun `not connected ends the turn with an error instead of hanging`() = runTest {
        val cs = rigScope(testScheduler)
        val rig = Rig(cs, ScriptedVoiceInput(text = "안녕"))
        rig.transport.open = false
        rig.repo.startTalk()
        assertEquals(TurnState.Idle, rig.repo.state.value.turn)
        assertEquals(TurnError.NotConnected, rig.repo.state.value.error)
        cs.cancel()
    }

    @Test fun `pressing talk without consent is never silent and asks for onboarding`() = runTest {
        val cs = rigScope(testScheduler)
        val rig = Rig(cs, ScriptedVoiceInput(text = "안녕"), consented = false)
        assertEquals(0, rig.repo.state.value.onboardingRequests)
        rig.repo.startTalk()
        assertEquals(1, rig.repo.state.value.onboardingRequests)
        rig.repo.startTalk()
        assertEquals(2, rig.repo.state.value.onboardingRequests)
        assertEquals(TurnState.Idle, rig.repo.state.value.turn)
        assertTrue(rig.transport.sent.none { it.contains("turn.start") })
        cs.cancel()
    }

    @Test fun `microphone granted after a refusal is picked up by the next press and by resume`() = runTest {
        val cs = rigScope(testScheduler)
        val voice = ScriptedVoiceInput(text = "안녕", permission = false)
        val rig = Rig(cs, voice)
        rig.repo.startTalk()
        assertEquals(TurnState.NeedsPermission, rig.repo.state.value.turn)
        // the prompt is denied
        rig.repo.voicePermissionResult(false)
        assertEquals(TurnError.PermissionDenied, rig.repo.state.value.error)
        assertEquals(CarFault.Microphone, rig.repo.state.value.fault)
        // granted outside the app (pm grant): resume clears the fault with no press
        voice.permission = true
        rig.repo.refreshVoicePermission()
        assertNull(rig.repo.state.value.error)
        assertNull(rig.repo.state.value.fault)
        // and a press runs a normal turn
        rig.repo.startTalk()
        assertEquals(TurnState.Thinking, rig.repo.state.value.turn)
        assertTrue(rig.transport.sent.any { it.contains("안녕") })
        cs.cancel()
    }

    @Test fun `a press while the prompt is stuck but the permission is granted listens`() = runTest {
        val cs = rigScope(testScheduler)
        val voice = ScriptedVoiceInput(text = "안녕", permission = false)
        val rig = Rig(cs, voice)
        rig.repo.startTalk()
        assertEquals(TurnState.NeedsPermission, rig.repo.state.value.turn)
        voice.permission = true // pm grant while the turn waits for the prompt answer
        rig.repo.startTalk()
        assertEquals(TurnState.Thinking, rig.repo.state.value.turn)
        cs.cancel()
    }

    @Test fun `resume with the prompt waiting and the permission granted starts the turn`() = runTest {
        val cs = rigScope(testScheduler)
        val voice = ScriptedVoiceInput(text = "안녕", permission = false)
        val rig = Rig(cs, voice)
        rig.repo.startTalk()
        voice.permission = true
        rig.repo.refreshVoicePermission()
        assertEquals(TurnState.Thinking, rig.repo.state.value.turn)
        cs.cancel()
    }

    @Test fun `pressing talk while offline sets the offline fault and listens to nothing`() = runTest {
        val cs = rigScope(testScheduler)
        val rig = Rig(cs, ScriptedVoiceInput(text = "안녕"))
        rig.transport.listener.onConnection(ConnectionStatus.RegistrationFailed)
        rig.repo.startTalk()
        assertEquals(TurnState.Idle, rig.repo.state.value.turn)
        assertEquals(TurnError.NotConnected, rig.repo.state.value.error)
        assertEquals(CarFault.Offline, rig.repo.state.value.fault)
        cs.cancel()
    }

    @Test fun `audio focus loss stops the voice and a late reply end does not resume it`() = runTest {
        val cs = rigScope(testScheduler)
        val audio = FakeReplyAudio()
        val rig = Rig(cs, ScriptedVoiceInput(text = "안녕"), audio = audio)
        rig.repo.startTalk()
        val l = rig.transport.listener
        l.onFrame(ServerFrame.ReplyStart("t1", AudioSpec.Pcm(24_000, 1)))
        assertEquals(1, audio.begins)
        l.onAudio(ByteArray(1440))
        assertEquals(1, audio.writes)
        audio.listener!!.onEnded(PlaybackEnd.FocusLost) // navigation guidance took the focus
        assertEquals(TurnState.Idle, rig.repo.state.value.turn)
        assertEquals(1, rig.vehicle.stops) // the Pleos voice is cut too (D-14)
        l.onFrame(ServerFrame.ReplyEnd("t1", "안녕하세요", 100, 900)) // arrives late: nothing is read, nothing resumes
        testScheduler.advanceUntilIdle()
        assertEquals(emptyList<String>(), rig.vehicle.spoken)
        assertEquals(0, audio.finishes)
        cs.cancel()
    }

    @Test fun `a button press while the character talks stops the voice and listens at once`() = runTest {
        val cs = rigScope(testScheduler)
        val audio = FakeReplyAudio()
        val rig = Rig(cs, ScriptedVoiceInput(text = "다음 질문"), audio = audio)
        rig.repo.startTalk()
        rig.transport.listener.onFrame(ServerFrame.ReplyStart("t1", AudioSpec.Pcm(24_000, 1)))
        assertEquals(TurnState.Replying, rig.repo.state.value.turn)
        rig.transport.sent.clear()
        rig.repo.startTalk() // D-24
        assertEquals(1, audio.stops)
        assertEquals(1, rig.vehicle.stops)
        assertEquals(TurnState.Thinking, rig.repo.state.value.turn) // the new sentence is already on its way
        assertTrue(rig.transport.sent.any { it.contains("다음 질문") })
        cs.cancel()
    }

    @Test fun `server audio plays to the end then the turn is over, and the Pleos voice stays silent`() = runTest {
        val cs = rigScope(testScheduler)
        val audio = FakeReplyAudio()
        val rig = Rig(cs, ScriptedVoiceInput(text = "안녕"), audio = audio)
        rig.repo.startTalk()
        val l = rig.transport.listener
        l.onFrame(ServerFrame.ReplyStart("t1", AudioSpec.Pcm(24_000, 1)))
        l.onFrame(ServerFrame.ReplyEnd("t1", "반가워요", 100, 900))
        assertEquals(1, audio.finishes)
        assertEquals(TurnState.Replying, rig.repo.state.value.turn) // still playing
        audio.listener!!.onMouth(2)
        assertEquals(2, rig.repo.state.value.mouth)
        audio.listener!!.onEnded(PlaybackEnd.Completed)
        assertEquals(TurnState.Idle, rig.repo.state.value.turn)
        assertEquals(emptyList<String>(), rig.vehicle.spoken)
        cs.cancel()
    }

    @Test fun `the server going away mid reply hands the rest to the Pleos voice`() = runTest {
        val cs = rigScope(testScheduler)
        val audio = FakeReplyAudio()
        val rig = Rig(cs, ScriptedVoiceInput(text = "안녕"), audio = audio)
        rig.repo.startTalk()
        val l = rig.transport.listener
        l.onFrame(ServerFrame.ReplyStart("t1", AudioSpec.Pcm(24_000, 1)))
        l.onFrame(ServerFrame.ReplyDelta("t1", "반가워요"))
        l.onConnection(ConnectionStatus.Reconnecting)
        testScheduler.advanceUntilIdle()
        assertEquals(1, audio.stops)
        assertEquals(listOf("반가워요"), rig.vehicle.spoken)
        cs.cancel()
    }

    @Test fun `a Pleos reading is cut by a focus loss and not read again`() = runTest {
        val cs = rigScope(testScheduler)
        val focus = FakeFocusGate()
        val rig = Rig(cs, ScriptedVoiceInput(text = "안녕"), focus = focus, speechMillis = 3_000)
        rig.repo.startTalk()
        rig.transport.listener.onFrame(ServerFrame.ReplyStart("t1", AudioSpec.None))
        rig.transport.listener.onFrame(ServerFrame.ReplyEnd("t1", "반가워요", 100, 900))
        assertTrue(focus.held)
        focus.loss!!.invoke()
        assertEquals(1, rig.vehicle.stops)
        testScheduler.advanceUntilIdle()
        assertEquals(1, rig.vehicle.spoken.size) // read once, never again
        cs.cancel()
    }

    @Test fun `reconnect backoff is 1 2 4 8 15 then stays at 15`() {
        assertEquals(listOf(1000L, 2000L, 4000L, 8000L, 15000L, 15000L, 15000L), (1..7).map { backoffMillis(it) })
    }

    // ---------------------------------------------------------------- F4-F6 auto-listen, proactive, ctx

    private fun TalkRepositoryTest.askThenHang(): Pair<VoiceInput, () -> Int> {
        var calls = 0
        val voice = object : VoiceInput {
            override fun hasPermission() = true
            override suspend fun listen(): VoiceResult {
                calls++
                if (calls == 1) return VoiceResult.Text("안녕")
                kotlinx.coroutines.awaitCancellation()
            }
            override fun release() = Unit
        }
        return voice to { calls }
    }

    private fun replyWith(rig: Rig, text: String) {
        rig.transport.listener.onFrame(ServerFrame.ReplyStart("t1", AudioSpec.None))
        rig.transport.listener.onFrame(ServerFrame.ReplyEnd("t1", text, 100, 900))
    }

    @Test fun `a reply ending with a question opens the microphone for 5 s and ends quietly when nothing is said`() = runTest {
        val cs = rigScope(testScheduler)
        val (voice, calls) = askThenHang()
        val rig = Rig(cs, voice)
        rig.repo.startTalk()
        replyWith(rig, "오늘 어땠어?")
        testScheduler.runCurrent()
        assertEquals(TurnState.Listening, rig.repo.state.value.turn)
        assertEquals(TurnSource.AutoListen, rig.repo.state.value.source)
        assertEquals(2, calls())
        testScheduler.advanceTimeBy(4_900)
        assertEquals(TurnState.Listening, rig.repo.state.value.turn)
        testScheduler.advanceTimeBy(200)
        assertEquals(TurnState.Idle, rig.repo.state.value.turn)
        assertNull(rig.repo.state.value.error) // no prompt, no error
        assertEquals(listOf("turn.start", "text", "turn.end"), types(rig.transport.sent)) // nothing was sent for the silent listening
        cs.cancel()
    }

    @Test fun `a statement does not open the microphone and an answer in the window is sent as listen_after_proactive`() = runTest {
        val cs = rigScope(testScheduler)
        val voice = ScriptedVoiceInput(text = "안녕")
        val rig = Rig(cs, voice)
        rig.repo.startTalk()
        replyWith(rig, "반가워요.")
        testScheduler.runCurrent()
        assertEquals(TurnState.Idle, rig.repo.state.value.turn)
        assertEquals(1, voice.listens)
        // question: the auto listen hears something and sends it with the follow-up trigger
        rig.transport.sent.clear()
        rig.repo.startTalk()
        replyWith(rig, "뭐 할래?")
        testScheduler.runCurrent()
        assertEquals(TurnState.Thinking, rig.repo.state.value.turn)
        assertTrue(rig.transport.sent.last { it.contains("turn.start") }.contains("listen_after_proactive"))
        cs.cancel()
    }

    @Test fun `PLEOS-79 a repeating voice answers a question once and never loops`() = runTest {
        val cs = rigScope(testScheduler)
        val voice = ScriptedVoiceInput(text = "같은 문장")
        val rig = Rig(cs, voice)
        rig.repo.startTalk()
        replyWith(rig, "뭐 할래?")
        testScheduler.runCurrent()
        assertEquals(TurnSource.AutoListen, rig.repo.state.value.source)
        assertEquals(TurnState.Thinking, rig.repo.state.value.turn)
        assertEquals(2, voice.listens)
        replyWith(rig, "또 물어볼까?") // the answer to the auto listen is again a question
        testScheduler.runCurrent()
        assertEquals(TurnState.Idle, rig.repo.state.value.turn)
        assertEquals(2, voice.listens) // no third listening
        cs.cancel()
    }

    @Test fun `the demo voice hears nothing in the automatic listening`() = runTest {
        val cs = rigScope(testScheduler)
        val voice = ScriptedVoiceInput(text = "같은 문장", silentAfterReply = true)
        val rig = Rig(cs, voice)
        rig.repo.startTalk()
        replyWith(rig, "뭐 할래?")
        testScheduler.runCurrent()
        assertEquals(TurnState.Idle, rig.repo.state.value.turn)
        assertNull(rig.repo.state.value.error)
        assertEquals(1, rig.transport.sent.count { it.contains("turn.start") })
        cs.cancel()
    }

    @Test fun `no auto listen while navigation is guiding`() = runTest {
        val cs = rigScope(testScheduler)
        val voice = ScriptedVoiceInput(text = "안녕")
        val rig = Rig(cs, voice)
        rig.vehicle.setSignals { it.copy(naviGuiding = true) }
        rig.repo.startTalk()
        replyWith(rig, "어디 가?")
        testScheduler.runCurrent()
        assertEquals(TurnState.Idle, rig.repo.state.value.turn)
        assertEquals(1, voice.listens)
        cs.cancel()
    }

    @Test fun `a proactive frame starts the character talking and its reply is read`() = runTest {
        val cs = rigScope(testScheduler)
        val rig = Rig(cs, ScriptedVoiceInput())
        val l = rig.transport.listener
        l.onFrame(ServerFrame.Proactive("p1", "wiper"))
        assertEquals(TurnState.Replying, rig.repo.state.value.turn)
        assertEquals(TurnSource.Proactive, rig.repo.state.value.source)
        l.onFrame(ServerFrame.ReplyStart("p1", AudioSpec.None))
        l.onFrame(ServerFrame.ReplyEnd("p1", "비 오네.", 100, 900))
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("비 오네."), rig.vehicle.spoken)
        assertEquals(TurnState.Idle, rig.repo.state.value.turn)
        cs.cancel()
    }

    @Test fun `a proactive frame while navigation guides is refused and cancelled`() = runTest {
        val cs = rigScope(testScheduler)
        val rig = Rig(cs, ScriptedVoiceInput())
        rig.vehicle.setSignals { it.copy(naviGuiding = true) }
        rig.transport.listener.onFrame(ServerFrame.Proactive("p1", "wiper"))
        rig.transport.listener.onFrame(ServerFrame.ReplyStart("p1", AudioSpec.None))
        rig.transport.listener.onFrame(ServerFrame.ReplyEnd("p1", "비 오네.", 100, 900))
        testScheduler.advanceUntilIdle()
        assertEquals(TurnState.Idle, rig.repo.state.value.turn)
        assertEquals(emptyList<String>(), rig.vehicle.spoken)
        assertTrue(rig.transport.sent.any { it.contains("turn.cancel") && it.contains("p1") })
        cs.cancel()
    }

    @Test fun `a proactive frame during a conversation or before consent is refused`() = runTest {
        val cs = rigScope(testScheduler)
        val rig = Rig(cs, ScriptedVoiceInput(text = "안녕"))
        rig.repo.startTalk()
        assertEquals(TurnState.Thinking, rig.repo.state.value.turn)
        rig.transport.listener.onFrame(ServerFrame.Proactive("p1", "door"))
        assertEquals(TurnState.Thinking, rig.repo.state.value.turn)
        assertEquals(TurnSource.Ptt, rig.repo.state.value.source)
        val cs2 = rigScope(testScheduler)
        val noConsent = Rig(cs2, ScriptedVoiceInput(), consented = false)
        assertTrue(noConsent.repo.state.value.needsOnboarding)
        noConsent.transport.listener.onFrame(ServerFrame.Proactive("p2", "door"))
        assertEquals(TurnState.Idle, noConsent.repo.state.value.turn)
        noConsent.repo.startTalk() // no conversation before consent
        assertEquals(TurnState.Idle, noConsent.repo.state.value.turn)
        cs.cancel()
        cs2.cancel()
    }

    @Test fun `vehicle context goes out on connect and on change, debounced`() = runTest {
        val cs = rigScope(testScheduler)
        val rig = Rig(cs, ScriptedVoiceInput())
        testScheduler.runCurrent()
        val first = rig.transport.sent.count { it.contains("\"type\":\"ctx\"") }
        assertTrue(first >= 1)
        rig.vehicle.setSignals { it.copy(wiper = "low") }
        rig.vehicle.setSignals { it.copy(wiper = "low", headlight = true) }
        testScheduler.advanceTimeBy(500)
        assertEquals(first, rig.transport.sent.count { it.contains("\"type\":\"ctx\"") })
        testScheduler.advanceTimeBy(600)
        assertEquals(first + 1, rig.transport.sent.count { it.contains("\"type\":\"ctx\"") })
        assertTrue(rig.transport.sent.last().contains("\"headlight\":true"))
        assertTrue(rig.transport.sent.last().contains("\"naviGuiding\":false"))
        cs.cancel()
    }
}
