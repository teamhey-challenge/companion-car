package ai.hey.session

import ai.hey.model.AudioSpec
import ai.hey.model.ServerFrame
import ai.hey.vehicle.FakeVehicleGateway
import ai.hey.vehicle.ScriptedVoiceInput
import ai.hey.vehicle.VoiceInput
import ai.hey.vehicle.VoiceResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

private fun types(sent: List<String>) = sent.map { Regex("\"type\":\"([^\"]+)\"").find(it)!!.groupValues[1] }

@OptIn(ExperimentalCoroutinesApi::class)
class TalkRepositoryTest {
    private class Rig(scope: CoroutineScope, voice: VoiceInput) {
        val vehicle = FakeVehicleGateway(scope)
        lateinit var transport: FakeTransport
        val repo = TalkRepository(
            scope = scope,
            vehicle = vehicle,
            storage = InMemoryCarStorage(),
            voiceInput = voice,
            transport = { FakeTransport(it).also { t -> transport = t } },
            newTurnId = { "t1" },
        ).also { it.start() }
    }

    private fun rigScope(testScheduler: kotlinx.coroutines.test.TestCoroutineScheduler) =
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
        assertEquals(TurnState.Idle, rig.repo.state.value.turn)
        testScheduler.advanceUntilIdle()
        // audio:none -> the car reads the text with its own TTS
        assertEquals(listOf("반가워요"), rig.vehicle.spoken)
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
        rig.vehicle.setDriving(true)
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

    @Test fun `reconnect backoff is 1 2 4 8 15 then stays at 15`() {
        assertEquals(listOf(1000L, 2000L, 4000L, 8000L, 15000L, 15000L, 15000L), (1..7).map { backoffMillis(it) })
    }
}
