package ai.hey.vehicle

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InjectableVoiceInputTest {
    private fun rig(): Pair<InjectableVoiceInput, ScriptedVoiceInput> {
        val real = ScriptedVoiceInput(text = "진짜 마이크")
        return InjectableVoiceInput(real, ScriptedVoiceInput(silentAfterReply = true)) to real
    }

    @Test
    fun `nothing injected listens to the real recognizer`() = runTest {
        val (input, real) = rig()
        assertEquals(VoiceResult.Text("진짜 마이크"), input.listen())
        assertEquals(VoiceResult.Text("진짜 마이크"), input.listen())
        assertEquals(2, real.listens)
    }

    @Test
    fun `one-shot override is consumed by the next listen only`() = runTest {
        val (input, real) = rig()
        input.inject("오늘 비 온대")
        assertTrue(input.injected)
        assertEquals(VoiceResult.Text("오늘 비 온대"), input.listen())
        assertFalse(input.injected)
        assertEquals(VoiceResult.Text("진짜 마이크"), input.listen())
        assertEquals(1, real.listens)
    }

    @Test
    fun `empty one-shot hears nothing once`() = runTest {
        val (input, _) = rig()
        input.inject("")
        assertEquals(VoiceResult.NoSpeech, input.listen())
        assertEquals(VoiceResult.Text("진짜 마이크"), input.listen())
    }

    @Test
    fun `sticky override repeats until useReal`() = runTest {
        val (input, real) = rig()
        input.inject("배고파", sticky = true)
        assertEquals(VoiceResult.Text("배고파"), input.listen())
        assertEquals(VoiceResult.Text("배고파"), input.listen())
        input.useReal()
        assertEquals(VoiceResult.Text("진짜 마이크"), input.listen())
        assertEquals(1, real.listens)
    }

    @Test
    fun `listening after a reply is silent while injected and real otherwise`() = runTest {
        val (input, _) = rig()
        assertEquals(VoiceResult.Text("진짜 마이크"), input.listenAfterReply())
        input.inject("배고파", sticky = true)
        assertEquals(VoiceResult.NoSpeech, input.listenAfterReply())
        input.useReal()
        assertEquals(VoiceResult.Text("진짜 마이크"), input.listenAfterReply())
    }

    @Test
    fun `permission is always the real one, injected sentence or not`() = runTest {
        val (input, real) = rig()
        real.permission = false
        assertFalse(input.hasPermission())
        input.inject("안녕")
        assertFalse(input.hasPermission())
        assertEquals(VoiceResult.PermissionDenied, input.listen())
        assertTrue(input.injected) // the refused press did not use up the sentence
        real.permission = true
        assertTrue(input.hasPermission())
        assertEquals(VoiceResult.Text("안녕"), input.listen())
    }
}
