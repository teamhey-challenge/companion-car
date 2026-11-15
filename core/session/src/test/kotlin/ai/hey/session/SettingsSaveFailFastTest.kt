package ai.hey.session

import ai.hey.model.CarSettings
import ai.hey.model.CompanionSettings
import ai.hey.model.ProactiveLevel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.net.ServerSocket

/** C6/C7 REST calls on a dead link fail at once, and on a silent server within the timeout. */
class SettingsSaveFailFastTest {
    private val patch = CompanionSettings() to CompanionSettings(proactive = ProactiveLevel.High)

    private suspend fun expectIo(block: suspend () -> Unit) {
        try {
            block()
            fail("expected IOException")
        } catch (e: IOException) {
            assertFalse(e is HttpFailure)
        }
    }

    @Test fun `offline fails every call at once without touching the network`() = runBlocking {
        // Port 9 on a closed socket would also fail, but the point is: no connect, no timeout, no token lookup needed.
        val api = HttpCompanionApi(InMemoryCarStorage(), network = OnlineCheck { false })
        val started = System.nanoTime()
        expectIo { api.patchSettings(patch.first, patch.second) }
        expectIo { api.deleteMemory("m1") }
        expectIo { api.deleteAllMemory() }
        expectIo { api.memory(null, 20) }
        expectIo { api.deletePairing() }
        assertTrue((System.nanoTime() - started) / 1_000_000 < 500)
    }

    @Test fun `a server that accepts but never answers fails within the timeout`() = runBlocking {
        ServerSocket(0).use { silent ->
            val storage = InMemoryCarStorage(CarSettings(serverUrl = "http://127.0.0.1:${silent.localPort}/api"))
            storage.saveCarToken(storage.settings.value.serverUrl, "tok")
            val api = HttpCompanionApi(storage, timeoutMs = 500)
            val started = System.nanoTime()
            expectIo { api.patchSettings(patch.first, patch.second) }
            expectIo { api.deleteMemory("m1") }
            val tookMs = (System.nanoTime() - started) / 1_000_000
            assertTrue("took $tookMs ms", tookMs < 3_000)
        }
    }

    @Test fun `the default timeout is at most 5 s`() {
        assertTrue(REST_TIMEOUT_MS <= 5_000)
        assertEquals(4_000L, REST_TIMEOUT_MS)
    }
}
