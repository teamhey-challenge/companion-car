package ai.hey.session

import ai.hey.model.HeyLog
import ai.hey.model.ServerFrame
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

private class IdleConnectivity : Connectivity {
    override fun watch(onReady: () -> Unit, onLost: () -> Unit): AutoCloseable = AutoCloseable { }
}

private class FakeSocket : WebSocket {
    var closedWith: Int? = null
    override fun request(): Request = Request.Builder().url("http://localhost/").build()
    override fun queueSize(): Long = 0
    override fun send(text: String): Boolean = true
    override fun send(bytes: ByteString): Boolean = true
    override fun close(code: Int, reason: String?): Boolean { closedWith = code; return true }
    override fun cancel() {}
}

/** An OkHttpClient whose sockets are fakes: the test plays the server (open, goodbye, silent death). */
private class FakeSocketClient : OkHttpClient() {
    val listeners = ArrayList<WebSocketListener>()
    val sockets = ArrayList<FakeSocket>()
    override fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket {
        val s = FakeSocket()
        listeners += listener
        sockets += s
        listener.onOpen(s, Response.Builder().request(request).protocol(okhttp3.Protocol.HTTP_1_1).code(101).message("").build())
        return s
    }
}

private class FakeApi : TalkApi {
    var sessions = 0
    override suspend fun register(serverUrl: String, installId: String) = "tok"
    override suspend fun session(serverUrl: String, carToken: String): TalkSession = TalkSession("s${++sessions}", "t$sessions")
    override fun socketUrl(serverUrl: String, ticket: String) = "http://localhost/v1/talk?ticket=$ticket"
}

@OptIn(ExperimentalCoroutinesApi::class)
class HalfOpenSocketTest {
    private val oldSink = HeyLog.sink

    @Before fun quiet() { HeyLog.sink = { _, _, _ -> } }

    @After fun restore() { HeyLog.sink = oldSink }

    @Test fun `the real client pings every 5 s so a pong missing by the next ping fails the socket within 10 s`() {
        assertEquals(TimeUnit.SECONDS.toMillis(5), newHttpClient().pingIntervalMillis.toLong())
    }

    private class Rec : TalkTransport.Listener {
        val states = ArrayList<ConnectionStatus>()
        override fun onConnection(status: ConnectionStatus) { states += status }
        override fun onFrame(frame: ServerFrame) {}
        override fun onAudio(pcm: ByteArray) {}
    }

    private fun kotlinx.coroutines.test.TestScope.link(client: FakeSocketClient, api: FakeApi, rec: Rec) = TalkLink(
        api, client, InMemoryCarStorage().also { }, backgroundScope, rec, "t",
        ReconnectPacer({ currentTime }, IdleConnectivity()), { currentTime },
    )

    @Test fun `a ping failure on a half-open socket reconnects with the first backoff step`() = runTest {
        val client = FakeSocketClient()
        val api = FakeApi()
        val rec = Rec()
        val link = link(client, api, rec)
        link.connect("http://localhost")
        runCurrent()
        assertEquals(1, client.sockets.size)
        assertEquals(ConnectionStatus.Online, rec.states.last())

        // OkHttp's pinger gives up: onFailure (the same callback a missing pong produces).
        client.listeners[0].onFailure(client.sockets[0], java.io.IOException("sent ping but didn't receive pong"), null)
        runCurrent()
        assertEquals(1, client.sockets.size) // paused for the 1 s backoff
        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(2, client.sockets.size)
        assertEquals(ConnectionStatus.Online, rec.states.last())
        link.close()
    }

    @Test fun `a server goodbye is answered and the link reconnects`() = runTest {
        val client = FakeSocketClient()
        val rec = Rec()
        val link = link(client, FakeApi(), rec)
        link.connect("http://localhost")
        runCurrent()

        client.listeners[0].onClosing(client.sockets[0], 1001, "going away")
        assertEquals(1001, client.sockets[0].closedWith)
        client.listeners[0].onClosed(client.sockets[0], 1001, "going away")
        runCurrent()
        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(2, client.sockets.size)
        assertTrue(rec.states.last() == ConnectionStatus.Online)
        link.close()
    }
}
