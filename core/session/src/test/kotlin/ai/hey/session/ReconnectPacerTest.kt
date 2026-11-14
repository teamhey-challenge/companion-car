package ai.hey.session

import ai.hey.model.HeyLog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeConnectivity : Connectivity {
    var onReady: (() -> Unit)? = null
    var onLost: (() -> Unit)? = null
    var watching = false
    override fun watch(onReady: () -> Unit, onLost: () -> Unit): AutoCloseable {
        this.onReady = onReady
        this.onLost = onLost
        watching = true
        return AutoCloseable { watching = false }
    }

    fun networkReturns() = onReady?.invoke()
    fun networkGoes() = onLost?.invoke()
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReconnectPacerTest {
    private val logs = ArrayList<String>()
    private val oldSink = HeyLog.sink

    @Before fun capture() { HeyLog.sink = { _, _, m -> logs += m } }

    @After fun restore() { HeyLog.sink = oldSink }

    @Test fun `backoff runs its full length when nothing interrupts`() = runTest {
        val pacer = ReconnectPacer({ currentTime }, FakeConnectivity()).also { it.start() }
        assertEquals(ReconnectTrigger.Backoff, pacer.pause(4)) // 8 s
        assertEquals(8_000L, currentTime)
        assertEquals("link: reconnect trigger=backoff after 8000 ms", logs.single())
    }

    @Test fun `network coming back cuts a 15 s pause at once`() = runTest {
        val net = FakeConnectivity()
        val pacer = ReconnectPacer({ currentTime }, net).also { it.start() }
        val result = async { pacer.pause(5) }
        delay(7_000)
        net.networkReturns()
        assertEquals(ReconnectTrigger.Network, result.await())
        assertEquals(7_000L, currentTime)
        assertEquals("link: reconnect trigger=network after 7000 ms", logs.single())
    }

    @Test fun `button cuts the pause and is logged as button`() = runTest {
        val pacer = ReconnectPacer({ currentTime }, FakeConnectivity()).also { it.start() }
        val result = async { pacer.pause(5) }
        delay(3_000)
        pacer.trigger(ReconnectTrigger.Button)
        assertEquals(ReconnectTrigger.Button, result.await())
        assertEquals(3_000L, currentTime)
        assertEquals("link: reconnect trigger=button after 3000 ms", logs.single())
    }

    @Test fun `online logs the time since the link went down`() = runTest {
        val pacer = ReconnectPacer({ currentTime }, FakeConnectivity()).also { it.start() }
        delay(1_000)
        pacer.markOnline()
        assertEquals("link: online after 1000 ms", logs.single())
    }

    @Test fun `a network event while online does not skip the next pause`() = runTest {
        val net = FakeConnectivity()
        val pacer = ReconnectPacer({ currentTime }, net).also { it.start() }
        pacer.markOnline()
        net.networkReturns()
        pacer.markDown()
        val before = currentTime
        assertEquals(ReconnectTrigger.Backoff, pacer.pause(1))
        assertEquals(before + 1_000L, currentTime)
    }

    @Test fun `a network event between two pauses cuts the next one at once`() = runTest {
        val net = FakeConnectivity()
        val pacer = ReconnectPacer({ currentTime }, net).also { it.start() }
        net.networkReturns()
        val before = currentTime
        assertEquals(ReconnectTrigger.Network, pacer.pause(5))
        assertEquals(before, currentTime)
    }

    @Test fun `stop unregisters the network callback`() = runTest {
        val net = FakeConnectivity()
        val pacer = ReconnectPacer({ currentTime }, net).also { it.start() }
        assertTrue(net.watching)
        pacer.stop()
        assertFalse(net.watching)
    }

    @Test fun `a lost network marks the link down so its return is a trigger even while the old socket looks open`() = runTest {
        val net = FakeConnectivity()
        val pacer = ReconnectPacer({ currentTime }, net).also { it.start() }
        pacer.markOnline()
        net.networkGoes()
        delay(20_000)
        net.networkReturns()
        assertEquals(ReconnectTrigger.Network, pacer.events.receive())
        pacer.markOnline()
        assertEquals("link: online after 20000 ms", logs.last())
    }

    @Test fun `while the network is gone the pause has no timeout and ends only when it returns`() = runTest {
        val net = FakeConnectivity()
        var gone = 0
        val pacer = ReconnectPacer({ currentTime }, net).also { it.onNetworkGone = { gone++ }; it.start() }
        net.networkGoes()
        assertEquals(1, gone)
        assertTrue(pacer.networkGone)
        val result = async { pacer.pause(5) }
        delay(60_000) // far past the 15 s backoff
        assertFalse(result.isCompleted)
        net.networkReturns()
        assertEquals(ReconnectTrigger.Network, result.await())
        assertFalse(pacer.networkGone)
        assertEquals(60_000L, pacer.networkBackAt)
    }
}
