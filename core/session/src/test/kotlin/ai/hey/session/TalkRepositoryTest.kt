package ai.hey.session

import ai.hey.model.AudioSpec
import ai.hey.model.ConsentType
import ai.hey.model.HeyLog
import ai.hey.model.ServerFrame
import ai.hey.vehicle.FakeVehicleGateway
import ai.hey.vehicle.OtherAudioProbe
import ai.hey.vehicle.FocusGate
import ai.hey.vehicle.PlaybackEnd
import ai.hey.vehicle.PlaybackListener
import ai.hey.vehicle.ReplyAudio
import ai.hey.vehicle.ReplyVoice
import ai.hey.model.OnboardingStep
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeTransport(val listener: TalkTransport.Listener) : TalkTransport {
    val sent = ArrayList<String>()
    var open = true
    override fun connect(serverUrl: String) = listener.onConnection(ConnectionStatus.Online)
    override fun reconnectNow() = Unit
    override fun send(text: String): Boolean = open.also { if (it) sent += text }
    override fun close() = Unit
}

private class FakeReplyAudio : ReplyAudio {
    override var listener: PlaybackListener? = null
    var begins = 0
    var writes = 0
    var finishes = 0
    var stops = 0
    var lastVoice: ReplyVoice? = null
    override fun begin(spec: AudioSpec, voice: ReplyVoice) { begins++; lastVoice = voice }
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
        otherAudio: OtherAudioProbe? = null,
        step: ai.hey.model.OnboardingStep = ai.hey.model.OnboardingStep.Done,
        driving: Boolean = false,
    ) {
        val vehicle = FakeVehicleGateway(scope, speechMillis = speechMillis).also { if (driving) it.setDriving(true) }
        lateinit var transport: FakeTransport
        lateinit var storage: InMemoryCarStorage
        val repo = TalkRepository(
            scope = scope,
            vehicle = vehicle,
            storage = InMemoryCarStorage(initialConsents = if (consented) ConsentMirror(setOf(ConsentType.Record)) else ConsentMirror(), initialStep = step).also { storage = it },
            voiceInput = voice,
            transport = { FakeTransport(it).also { t -> transport = t } },
            newTurnId = { "t1" },
            audio = audio,
            speechFocus = focus,
            autoListenMillis = autoListenMillis,
            ctxDebounceMillis = 1_000,
            otherAudio = otherAudio,
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

    @Test fun `PLEOS-135 consent_required from the server ends the turn and asks for onboarding, not a server fault`() = runTest {
        val cs = rigScope(testScheduler)
        val rig = Rig(cs, ScriptedVoiceInput(text = "안녕"))
        rig.repo.startTalk()
        rig.transport.listener.onFrame(ServerFrame.Error("consent_required", "t1", null))
        assertEquals(TurnState.Idle, rig.repo.state.value.turn)
        assertNull(rig.repo.state.value.error)
        assertEquals(1, rig.repo.state.value.onboardingRequests)
        cs.cancel()
    }

    @Test fun `PLEOS-137 the driver's words and the reply stay out of the log unless content logging is on`() = runTest {
        val lines = ArrayList<String>()
        val oldSink = HeyLog.sink
        HeyLog.sink = { _, _, m -> lines += m }
        try {
            for (content in listOf(false, true)) {
                lines.clear()
                HeyLog.contentEnabled = content
                val cs = rigScope(testScheduler)
                val rig = Rig(cs, ScriptedVoiceInput(text = "비밀스런문장"))
                rig.repo.startTalk()
                rig.transport.listener.onFrame(ServerFrame.ReplyEnd("t1", "비밀답변", 100, 900))
                testScheduler.advanceUntilIdle()
                val joined = lines.joinToString("\n")
                assertTrue(joined, joined.contains("chars=6"))
                assertEquals(joined, content, joined.contains("비밀스런문장") || joined.contains("비밀답변"))
                cs.cancel()
            }
        } finally {
            HeyLog.contentEnabled = false
            HeyLog.sink = oldSink
        }
    }

    @Test fun `leaving P does not cut listening or the turn`() = runTest {
        val cs = rigScope(testScheduler)
        val gate = CompletableDeferred<Unit>()
        val voice = object : VoiceInput {
            override fun hasPermission() = true
            private var calls = 0
            override suspend fun listen(): VoiceResult {
                if (++calls > 1) kotlinx.coroutines.awaitCancellation() // the automatic listening after the reply hears nothing
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
        val rig = Rig(cs, ScriptedVoiceInput(text = "안녕", silentAfterReply = true), audio = audio)
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
        val rig = Rig(cs, ScriptedVoiceInput(text = "안녕", silentAfterReply = true), audio = audio)
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

    @Test fun `PLEOS-194 the end-conversation button cancels listening, thinking and speaking`() = runTest {
        val cs = rigScope(testScheduler)
        val (hang, _) = askThenHang()
        val rig = Rig(cs, hang)
        // thinking: the sentence is sent, the button ends it and tells the server
        rig.repo.startTalk()
        assertEquals(TurnState.Thinking, rig.repo.state.value.turn)
        rig.repo.cancelTalk()
        assertEquals(TurnState.Idle, rig.repo.state.value.turn)
        assertNull(rig.repo.state.value.heard)
        assertTrue(rig.transport.sent.any { it.contains("turn.cancel") })
        // speaking: the voice stops and the screen is idle again
        val audio = FakeReplyAudio()
        val rig2 = Rig(cs, ScriptedVoiceInput(text = "안녕", silentAfterReply = true), audio = audio)
        rig2.repo.startTalk()
        rig2.transport.listener.onFrame(ServerFrame.ReplyStart("t1", AudioSpec.Pcm(24_000, 1)))
        assertEquals(TurnState.Replying, rig2.repo.state.value.turn)
        rig2.repo.cancelTalk()
        assertEquals(TurnState.Idle, rig2.repo.state.value.turn)
        assertEquals("", rig2.repo.state.value.reply)
        assertEquals(1, audio.stops)
        // listening: the microphone closes without sending anything
        val rig3 = Rig(cs, askThenHang().first)
        rig3.repo.startTalk()
        rig3.transport.listener.onFrame(ServerFrame.ReplyStart("t1", AudioSpec.None))
        rig3.transport.listener.onFrame(ServerFrame.ReplyEnd("t1", "오늘 어땠어?", 100, 900))
        testScheduler.runCurrent()
        assertEquals(TurnState.Listening, rig3.repo.state.value.turn)
        rig3.transport.sent.clear()
        rig3.repo.cancelTalk()
        assertEquals(TurnState.Idle, rig3.repo.state.value.turn)
        assertEquals(emptyList<String>(), types(rig3.transport.sent))
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
        // D-14: a lost focus is not a fault: no audio-fault flag, no C8, the turn is simply over, the focus is let go
        assertFalse(rig.repo.state.value.audioFailed)
        assertNull(rig.repo.state.value.fault)
        assertEquals(TurnState.Idle, rig.repo.state.value.turn)
        assertFalse(focus.held)
        cs.cancel()
    }

    @Test fun `a turn without reply start ends with the server fault and frees the talk button`() = runTest {
        val cs = rigScope(testScheduler)
        val rig = Rig(cs, ScriptedVoiceInput(text = "안녕"))
        rig.repo.startTalk()
        assertEquals(TurnState.Thinking, rig.repo.state.value.turn)
        testScheduler.advanceTimeBy(REPLY_START_TIMEOUT_MILLIS - 1)
        assertEquals(TurnState.Thinking, rig.repo.state.value.turn)
        testScheduler.advanceTimeBy(2)
        assertEquals(TurnState.Idle, rig.repo.state.value.turn)
        assertEquals(TurnError.Server, rig.repo.state.value.error)
        assertEquals(CarFault.Server, rig.repo.state.value.fault)
        assertTrue(types(rig.transport.sent).contains("turn.cancel"))
        rig.repo.startTalk() // the button works again
        assertEquals(TurnState.Thinking, rig.repo.state.value.turn)
        cs.cancel()
    }

    @Test fun `a reply that goes quiet without reply end ends the turn, a streaming reply keeps it alive`() = runTest {
        val cs = rigScope(testScheduler)
        val rig = Rig(cs, ScriptedVoiceInput(text = "안녕"))
        rig.repo.startTalk()
        val l = rig.transport.listener
        l.onFrame(ServerFrame.ReplyStart("t1", AudioSpec.None))
        testScheduler.advanceTimeBy(REPLY_END_TIMEOUT_MILLIS - 1_000)
        l.onFrame(ServerFrame.ReplyDelta("t1", "음")) // sign of life re-arms the timer
        testScheduler.advanceTimeBy(REPLY_END_TIMEOUT_MILLIS - 1_000)
        assertEquals(TurnState.Replying, rig.repo.state.value.turn)
        testScheduler.advanceTimeBy(2_000)
        assertEquals(TurnState.Idle, rig.repo.state.value.turn)
        assertEquals(CarFault.Server, rig.repo.state.value.fault)
        cs.cancel()
    }

    @Test fun `a reply end in time cancels the watchdog`() = runTest {
        val cs = rigScope(testScheduler)
        val rig = Rig(cs, ScriptedVoiceInput(text = "안녕", silentAfterReply = true))
        rig.repo.startTalk()
        rig.transport.listener.onFrame(ServerFrame.ReplyStart("t1", AudioSpec.None))
        rig.transport.listener.onFrame(ServerFrame.ReplyEnd("t1", "반가워요", 100, 900))
        testScheduler.advanceTimeBy(REPLY_END_TIMEOUT_MILLIS * 2)
        assertNull(rig.repo.state.value.error)
        cs.cancel()
    }

    @Test fun `a proactive line is dropped while another app plays audio and goes through once it stops`() = runTest {
        val cs = rigScope(testScheduler)
        var playing = true
        val rig = Rig(cs, ScriptedVoiceInput(), otherAudio = { playing })
        rig.transport.listener.onFrame(ServerFrame.Proactive("p1", "wiper"))
        assertEquals(TurnState.Idle, rig.repo.state.value.turn)
        assertTrue(rig.transport.sent.any { it.contains("turn.cancel") && it.contains("p1") })
        playing = false
        rig.transport.listener.onFrame(ServerFrame.Proactive("p2", "wiper"))
        assertEquals(TurnState.Replying, rig.repo.state.value.turn)
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

    private fun replyWith(rig: Rig, text: String) = replyWith(rig, "t1", text)

    private fun replyWith(rig: Rig, id: String, text: String) {
        rig.transport.listener.onFrame(ServerFrame.ReplyStart(id, AudioSpec.None))
        rig.transport.listener.onFrame(ServerFrame.ReplyEnd(id, text, 100, 900))
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

    @Test fun `D-48 4 a statement also keeps the microphone open and the answer in the window is sent as listen_after_proactive`() = runTest {
        val cs = rigScope(testScheduler)
        val voice = ScriptedVoiceInput(text = "안녕")
        val rig = Rig(cs, voice)
        rig.repo.startTalk() // one tap: the end of the speech sends by itself
        testScheduler.runCurrent()
        assertEquals(TurnState.Thinking, rig.repo.state.value.turn)
        assertTrue(rig.transport.sent.last { it.contains("turn.start") }.contains("\"ptt\""))
        rig.transport.sent.clear()
        replyWith(rig, "반가워요.")
        testScheduler.runCurrent()
        assertEquals(TurnSource.AutoListen, rig.repo.state.value.source)
        assertEquals(TurnState.Thinking, rig.repo.state.value.turn)
        assertEquals(2, voice.listens)
        assertTrue(rig.transport.sent.last { it.contains("turn.start") }.contains("listen_after_proactive"))
        cs.cancel()
    }

    @Test fun `D-48 4 a proactive line is followed by listening without a tap and the answer is sent`() = runTest {
        val cs = rigScope(testScheduler)
        val voice = ScriptedVoiceInput(text = "응 괜찮아")
        val rig = Rig(cs, voice)
        rig.transport.listener.onFrame(ServerFrame.Proactive("p1", "wiper"))
        replyWith(rig, "p1", "비 오네, 와이퍼 켰어.")
        testScheduler.runCurrent()
        assertEquals(1, voice.listens)
        assertEquals(TurnState.Thinking, rig.repo.state.value.turn)
        assertTrue(rig.transport.sent.last { it.contains("turn.start") }.contains("listen_after_proactive"))
        cs.cancel()
    }

    @Test fun `D-48 4 the automatic listening ends quietly after 5 s without speech`() = runTest {
        val cs = rigScope(testScheduler)
        val (voice, calls) = askThenHang()
        val rig = Rig(cs, voice)
        rig.repo.startTalk()
        replyWith(rig, "응, 알겠어.")
        testScheduler.runCurrent()
        assertEquals(TurnState.Listening, rig.repo.state.value.turn)
        testScheduler.advanceTimeBy(5_100)
        assertEquals(TurnState.Idle, rig.repo.state.value.turn)
        assertEquals(2, calls())
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
        rig.vehicle.setDriving(true)
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

    @Test fun `a friend_message while driving plays the server audio at once and renders no text`() = runTest {
        val cs = rigScope(testScheduler)
        val audio = FakeReplyAudio()
        val rig = Rig(cs, ScriptedVoiceInput(), audio = audio)
        rig.vehicle.setDriving(true)
        rig.vehicle.setSignals { it.copy(naviGuiding = true) } // guidance running: a friend line is still spoken (the audio focus decides)
        val l = rig.transport.listener
        l.onFrame(ServerFrame.FriendMessage("fm1", "민수", "저녁에 보자"))
        assertEquals(TurnState.Replying, rig.repo.state.value.turn)
        l.onFrame(ServerFrame.ReplyStart("fm1", AudioSpec.Pcm(24_000, 1)))
        l.onAudio(ByteArray(960))
        l.onFrame(ServerFrame.ReplyEnd("fm1", "민수가 그러는데, 저녁에 보자", 100, 900))
        assertEquals(1, audio.begins)
        assertEquals(1, audio.writes)
        assertEquals(1, audio.finishes)
        assertNull(rig.repo.state.value.friendNote)
        assertEquals("", rig.repo.state.value.reply)
        assertEquals(TurnSource.Friend, rig.repo.state.value.source)
        cs.cancel()
    }

    @Test fun `a friend_message while parked plays the audio and renders the text for a few seconds`() = runTest {
        val cs = rigScope(testScheduler)
        val audio = FakeReplyAudio()
        val rig = Rig(cs, ScriptedVoiceInput(), audio = audio)
        rig.vehicle.setDriving(false)
        val l = rig.transport.listener
        l.onFrame(ServerFrame.FriendMessage("fm1", "민수", "저녁에 보자"))
        l.onFrame(ServerFrame.ReplyStart("fm1", AudioSpec.Pcm(24_000, 1)))
        l.onFrame(ServerFrame.ReplyEnd("fm1", "민수가 그러는데, 저녁에 보자", 100, 900))
        assertEquals(1, audio.begins)
        assertEquals(FriendNote("민수", "저녁에 보자"), rig.repo.state.value.friendNote)
        testScheduler.advanceTimeBy(FRIEND_NOTE_MILLIS + 1)
        assertNull(rig.repo.state.value.friendNote)
        cs.cancel()
    }

    @Test fun `the text of a friend_message goes away when the car starts moving`() = runTest {
        val cs = rigScope(testScheduler)
        val rig = Rig(cs, ScriptedVoiceInput(), audio = FakeReplyAudio())
        rig.vehicle.setDriving(false)
        rig.transport.listener.onFrame(ServerFrame.FriendMessage("fm1", "민수", "저녁에 보자"))
        assertEquals(FriendNote("민수", "저녁에 보자"), rig.repo.state.value.friendNote)
        rig.vehicle.setDriving(true)
        assertNull(rig.repo.state.value.friendNote)
        cs.cancel()
    }

    @Test fun `friend_arrived is spoken only and never opens the microphone`() = runTest {
        val cs = rigScope(testScheduler)
        val voice = ScriptedVoiceInput(text = "안녕")
        val rig = Rig(cs, voice)
        rig.vehicle.setDriving(false)
        val l = rig.transport.listener
        l.onFrame(ServerFrame.FriendArrived("fa1", "지은"))
        l.onFrame(ServerFrame.ReplyStart("fa1", AudioSpec.None))
        l.onFrame(ServerFrame.ReplyEnd("fa1", "지은이 도착했대?", 100, 900))
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("지은이 도착했대?"), rig.vehicle.spoken)
        assertNull(rig.repo.state.value.friendNote)
        assertEquals(0, voice.listens) // the line ends with a question mark, yet the microphone stays closed
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

    @Test fun `a range proactive at a parked stop is spoken although a destination is set, any other reason stays refused`() = runTest {
        val cs = rigScope(testScheduler)
        val rig = Rig(cs, ScriptedVoiceInput())
        rig.vehicle.setDriving(false)
        rig.vehicle.setSignals { it.copy(naviGuiding = true, destKm = 42) }
        testScheduler.runCurrent()
        rig.transport.listener.onFrame(ServerFrame.Proactive("p1", "wiper"))
        assertEquals(TurnState.Idle, rig.repo.state.value.turn)
        rig.transport.listener.onFrame(ServerFrame.Proactive("p2", "range"))
        assertEquals(TurnState.Replying, rig.repo.state.value.turn)
        rig.transport.listener.onFrame(ServerFrame.ReplyStart("p2", AudioSpec.None))
        rig.transport.listener.onFrame(ServerFrame.ReplyEnd("p2", "목적지까지 42km인데 남은 거리가 30km야.", 100, 900))
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("목적지까지 42km인데 남은 거리가 30km야."), rig.vehicle.spoken)
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

    @Test fun `the distance to the navigation destination goes out in ctx only while there is one, never a name or coordinates`() = runTest {
        val cs = rigScope(testScheduler)
        val rig = Rig(cs, ScriptedVoiceInput())
        testScheduler.runCurrent()
        assertTrue(rig.transport.sent.last { it.contains("\"type\":\"ctx\"") }.let { !it.contains("destKm") })
        rig.vehicle.setSignals { it.copy(naviGuiding = true, destKm = 42) }
        testScheduler.advanceTimeBy(1100)
        val sent = rig.transport.sent.last { it.contains("\"type\":\"ctx\"") }
        assertTrue(sent.contains("\"destKm\":42"))
        assertTrue(!sent.contains("lat") && !sent.contains("lon") && !sent.contains("name"))
        rig.vehicle.setSignals { it.copy(naviGuiding = false, destKm = null) }
        testScheduler.advanceTimeBy(1100)
        assertTrue(!rig.transport.sent.last { it.contains("\"type\":\"ctx\"") }.contains("destKm"))
        cs.cancel()
    }

    // ---------------------------------------------------------------- D-48 onboarding step machine

    private fun onboardingFrame(rig: Rig, step: String, id: String, text: String, whale: Boolean = true) {
        rig.transport.listener.onFrame(ServerFrame.Onboarding(step, id, text, whale))
        rig.transport.listener.onFrame(ServerFrame.ReplyStart(id, AudioSpec.None))
        rig.transport.listener.onFrame(ServerFrame.ReplyEnd(id, text, 100, 900))
    }

    private fun asked(rig: Rig, step: String) = rig.transport.sent.count { it.contains("\"type\":\"onboarding\"") && it.contains("\"$step\"") }

    @Test fun `D-48 the whale intro is asked at once, spoken, and moves on to the consent sheet`() = runTest {
        val cs = rigScope(testScheduler)
        val rig = Rig(cs, ScriptedVoiceInput(text = "뚱"), step = OnboardingStep.WhaleIntro, consented = false)
        testScheduler.runCurrent()
        // the intro comes BEFORE the consent sheet: it needs no consent
        assertEquals(1, asked(rig, "whale_intro"))
        onboardingFrame(rig, "whale_intro", "o1", "여기는 바다 같은 곳이야")
        testScheduler.advanceUntilIdle()
        assertEquals(OnboardingStep.Consent, rig.storage.onboardingStep.value)
        assertEquals("여기는 바다 같은 곳이야", rig.repo.state.value.onboarding.line)
        assertTrue(rig.repo.state.value.onboarding.whale)
        cs.cancel()
    }

    @Test fun `D-48 a question opens the microphone by itself, sends the answer with reply and moves on after the whale's answer line`() = runTest {
        val cs = rigScope(testScheduler)
        val voice = ScriptedVoiceInput(text = "뚱")
        val rig = Rig(cs, voice, step = OnboardingStep.Q1)
        testScheduler.runCurrent()
        onboardingFrame(rig, "q1", "o1", "먼저, 뭐라고 불러 줄까?")
        testScheduler.runCurrent()
        assertEquals(1, voice.listens) // no tap
        assertTrue(rig.transport.sent.any { it.contains("onboarding.answer") && it.contains("\"q1\"") && it.contains("뚱") && it.contains("\"reply\":true") })
        rig.transport.listener.onFrame(ServerFrame.OnboardingAck("q1", true, "뚱"))
        onboardingFrame(rig, "ack", "o2", "뚱, 반가워.")
        testScheduler.runCurrent()
        assertEquals(OnboardingStep.Q2, rig.storage.onboardingStep.value)
        assertEquals("뚱", rig.repo.state.value.onboarding.answers.name)
        cs.cancel()
    }

    @Test fun `D-48 an answer the server could not use is not asked again - the whale says skip and the answer is dropped`() = runTest {
        val cs = rigScope(testScheduler)
        val rig = Rig(cs, ScriptedVoiceInput(text = "음"), step = OnboardingStep.Q2)
        testScheduler.runCurrent()
        onboardingFrame(rig, "q2", "o1", "이 차로 주로 어디 다녀?")
        testScheduler.runCurrent()
        rig.transport.listener.onFrame(ServerFrame.OnboardingAck("q2", false, null, skipped = true))
        onboardingFrame(rig, "skip", "o2", "괜찮아, 다음에 알려 줘.")
        testScheduler.runCurrent()
        assertEquals(1, asked(rig, "q2"))
        assertEquals(OnboardingStep.Q3, rig.storage.onboardingStep.value)
        assertNull(rig.repo.state.value.onboarding.answers.trips)
        cs.cancel()
    }

    @Test fun `D-48 the skip button on a listening question says the skip line and moves on`() = runTest {
        val cs = rigScope(testScheduler)
        val rig = Rig(cs, hangingVoice(), step = OnboardingStep.Q3)
        testScheduler.runCurrent()
        onboardingFrame(rig, "q3", "o1", "차 안에서는 어떤 얘기가 좋아?")
        testScheduler.runCurrent()
        assertEquals(TurnState.Listening, rig.repo.state.value.turn)
        rig.repo.skipOnboardingQuestion()
        testScheduler.runCurrent()
        assertEquals(1, asked(rig, "skip"))
        onboardingFrame(rig, "skip", "o2", "괜찮아, 다음에 알려 줘.")
        testScheduler.runCurrent()
        assertEquals(OnboardingStep.Found, rig.storage.onboardingStep.value)
        cs.cancel()
    }

    private fun hangingVoice(): VoiceInput = object : VoiceInput {
        override fun hasPermission() = true
        override suspend fun listen(): VoiceResult { kotlinx.coroutines.awaitCancellation() }
        override fun release() = Unit
    }

    @Test fun `D-48 spoken steps run while driving and the long press waits for park and is not lost`() = runTest {
        val cs = rigScope(testScheduler)
        val voice = ScriptedVoiceInput(text = "회사랑 집")
        val rig = Rig(cs, voice, step = OnboardingStep.Q2, driving = true)
        testScheduler.runCurrent()
        onboardingFrame(rig, "q2", "o1", "이 차로 주로 어디 다녀?")
        testScheduler.runCurrent()
        assertEquals(1, voice.listens) // listening opened while driving
        rig.transport.listener.onFrame(ServerFrame.OnboardingAck("q2", true, null))
        onboardingFrame(rig, "ack", "o2", "그렇구나.")
        testScheduler.runCurrent()
        assertEquals(OnboardingStep.Q3, rig.storage.onboardingStep.value)
        assertEquals("회사랑 집", rig.repo.state.value.onboarding.answers.trips)
        // the long press needs the driver's hand: parked-only, nothing is asked for it while driving, and it is kept
        rig.storage.setOnboardingStep(OnboardingStep.Summon)
        testScheduler.runCurrent()
        assertEquals(0, asked(rig, "greeting"))
        assertEquals(OnboardingStep.Summon, rig.repo.state.value.onboardingStep)
        rig.vehicle.setDriving(false)
        testScheduler.runCurrent()
        assertEquals(OnboardingStep.Summon, rig.storage.onboardingStep.value) // still waits for the press itself
        cs.cancel()
    }

    @Test fun `D-48 resume after driving - a press step waits while the car moves and its line is asked when parked`() = runTest {
        val cs = rigScope(testScheduler)
        val rig = Rig(cs, ScriptedVoiceInput(), step = OnboardingStep.Dress, driving = true)
        testScheduler.runCurrent()
        assertEquals(0, asked(rig, "dress"))
        rig.vehicle.setDriving(false)
        testScheduler.runCurrent()
        assertEquals(1, asked(rig, "dress"))
        assertEquals(OnboardingStep.Dress, rig.storage.onboardingStep.value) // the press finishes it, not the line
        cs.cancel()
    }

    @Test fun `D-48 a restart resumes at the stored step and the long press is finished from the screen`() = runTest {
        val cs = rigScope(testScheduler)
        val rig = Rig(cs, ScriptedVoiceInput(), step = OnboardingStep.Summon)
        testScheduler.runCurrent()
        assertEquals(OnboardingStep.Summon, rig.repo.state.value.onboardingStep)
        assertEquals(0, rig.transport.sent.count { it.contains("\"type\":\"onboarding\"") })
        rig.repo.finishOnboardingStep(OnboardingStep.Summon)
        testScheduler.runCurrent()
        assertEquals(OnboardingStep.Greeting, rig.storage.onboardingStep.value)
        assertEquals(1, asked(rig, "greeting")) // a spoken step: asked at once
        cs.cancel()
    }

    @Test fun `D-48 the greeting text comes in reply end and reaches the subtitle`() = runTest {
        val cs = rigScope(testScheduler)
        val rig = Rig(cs, ScriptedVoiceInput(), step = OnboardingStep.Greeting, speechMillis = 10_000)
        testScheduler.runCurrent()
        rig.transport.listener.onFrame(ServerFrame.Onboarding("greeting", "g1", "", false))
        rig.transport.listener.onFrame(ServerFrame.ReplyStart("g1", AudioSpec.None))
        rig.transport.listener.onFrame(ServerFrame.ReplyEnd("g1", "반가워, 민수!", 100, 900))
        assertEquals("반가워, 민수!", rig.repo.state.value.onboarding.line)
        assertFalse(rig.repo.state.value.onboarding.whale)
        cs.cancel()
    }

    @Test fun `D-48 a line that never arrives is tried twice and the flow goes on - no endless re-request`() = runTest {
        val cs = rigScope(testScheduler)
        val rig = Rig(cs, ScriptedVoiceInput(), step = OnboardingStep.WhaleIntro)
        testScheduler.advanceUntilIdle() // no frame ever comes back
        assertEquals(2, asked(rig, "whale_intro"))
        assertEquals(OnboardingStep.Consent, rig.storage.onboardingStep.value)
        cs.cancel()
    }

    @Test fun `D-48 a missing microphone permission gives up the questions only, the rest of the flow still runs`() = runTest {
        val cs = rigScope(testScheduler)
        val rig = Rig(cs, ScriptedVoiceInput(permission = false), step = OnboardingStep.Q1)
        testScheduler.runCurrent()
        onboardingFrame(rig, "q1", "o1", "먼저, 뭐라고 불러 줄까?")
        testScheduler.runCurrent()
        assertEquals(OnboardingStep.Found, rig.storage.onboardingStep.value)
        cs.cancel()
    }

    @Test fun `D-48 the whale line is played in the whale voice and a driver turn is dry`() = runTest {
        val cs = rigScope(testScheduler)
        val audio = FakeReplyAudio()
        val rig = Rig(cs, ScriptedVoiceInput(), audio = audio, step = OnboardingStep.Lights)
        testScheduler.runCurrent()
        rig.transport.listener.onFrame(ServerFrame.Onboarding("lights", "o1", "여기 빛 하나하나가", whale = true))
        rig.transport.listener.onFrame(ServerFrame.ReplyStart("o1", AudioSpec.Pcm(24_000, 1)))
        assertEquals(ReplyVoice.Whale, audio.lastVoice)
        cs.cancel()
        val dryScope = rigScope(testScheduler)
        val dryAudio = FakeReplyAudio()
        val dry = Rig(dryScope, ScriptedVoiceInput(text = "안녕"), audio = dryAudio)
        dry.repo.startTalk()
        dry.transport.listener.onFrame(ServerFrame.ReplyStart("t1", AudioSpec.Pcm(24_000, 1)))
        assertEquals(ReplyVoice.Heyri, dryAudio.lastVoice)
        dryScope.cancel()
    }

    @Test fun `D-48 a tap on the talk button during the first-run flow does not open a second turn`() = runTest {
        val cs = rigScope(testScheduler)
        val rig = Rig(cs, ScriptedVoiceInput(text = "안녕"), step = OnboardingStep.Q1)
        testScheduler.runCurrent()
        rig.repo.startTalk()
        testScheduler.runCurrent()
        assertTrue(rig.transport.sent.none { it.contains("turn.start") })
        cs.cancel()
    }

    @Test fun `D-48 a proactive line is refused while the first-run flow runs`() = runTest {
        val cs = rigScope(testScheduler)
        val rig = Rig(cs, ScriptedVoiceInput(), step = OnboardingStep.Dress)
        rig.transport.listener.onFrame(ServerFrame.Proactive("p1", "wiper"))
        assertEquals(TurnState.Idle, rig.repo.state.value.turn)
        assertTrue(rig.transport.sent.any { it.contains("turn.cancel") && it.contains("p1") })
        cs.cancel()
    }

    // ---------------------------------------------------------------- H1: a friend line is never lost

    @Test fun `friend_message during auto-listen is read and never cancelled`() = runTest {
        val cs = rigScope(testScheduler)
        val (voice, _) = askThenHang()
        val rig = Rig(cs, voice)
        rig.vehicle.setDriving(false)
        rig.repo.startTalk()
        replyWith(rig, "t1", "오늘 어땠어?")
        testScheduler.runCurrent()
        assertEquals(TurnState.Listening, rig.repo.state.value.turn)
        assertEquals(TurnSource.AutoListen, rig.repo.state.value.source)
        rig.transport.listener.onFrame(ServerFrame.FriendMessage("fm1", "민수", "저녁에 보자"))
        assertEquals(TurnSource.Friend, rig.repo.state.value.source)
        assertEquals(FriendNote("민수", "저녁에 보자"), rig.repo.state.value.friendNote)
        replyWith(rig, "fm1", "민수가 그러는데, 저녁에 보자")
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("오늘 어땠어?", "민수가 그러는데, 저녁에 보자"), rig.vehicle.spoken)
        assertTrue(rig.transport.sent.none { it.contains("turn.cancel") })
        cs.cancel()
    }

    @Test fun `friend_message while the character talks waits and is read after it`() = runTest {
        val cs = rigScope(testScheduler)
        val rig = Rig(cs, ScriptedVoiceInput(silentAfterReply = true, text = "안녕"))
        rig.vehicle.setDriving(true)
        rig.repo.startTalk()
        testScheduler.runCurrent()
        rig.transport.listener.onFrame(ServerFrame.ReplyStart("t1", AudioSpec.None))
        rig.transport.listener.onFrame(ServerFrame.FriendMessage("fm1", "민수", "저녁에 보자"))
        assertTrue(rig.transport.sent.none { it.contains("turn.cancel") })
        rig.transport.listener.onFrame(ServerFrame.ReplyEnd("t1", "응", 100, 900))
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("응", "민수가 그러는데, 저녁에 보자"), rig.vehicle.spoken)
        assertTrue(rig.transport.sent.none { it.contains("turn.cancel") })
        cs.cancel()
    }
}
