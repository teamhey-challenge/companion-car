package ai.hey.session

import ai.hey.model.HeyConfig
import ai.hey.model.HeyJson
import ai.hey.model.HeyLog
import ai.hey.model.ClientFrames
import ai.hey.model.ServerFrame
import ai.hey.model.parseServerFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val TAG = "TalkLink"

fun newHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .pingInterval(20, TimeUnit.SECONDS)
    .build()

/** An HTTP answer that was not a success. The body is never kept: it may echo what was sent. */
class HttpFailure(val code: Int) : IOException("HTTP $code")

/** What /v1/sessions hands out: one single-use WebSocket ticket (60 s). */
class TalkSession(val sessionId: String, val wsTicket: String) {
    override fun toString(): String = "TalkSession(sessionId=$sessionId)"
}

/** The two HTTP calls before the socket: register this install, then ask for a session ticket. */
interface TalkApi {
    /** POST /v1/cars/register with the provision secret; returns the car token. 403 = wrong or missing secret. */
    suspend fun register(serverUrl: String, installId: String, provisionSecret: String): String

    /** POST /v1/sessions with the car token. 401 = the token is not known (any more). */
    suspend fun session(serverUrl: String, carToken: String): TalkSession

    /** The WebSocket address of /v1/talk for [ticket]. Contains the ticket: never log it. */
    fun socketUrl(serverUrl: String, ticket: String): String
}

class HttpTalkApi(private val http: OkHttpClient = newHttpClient()) : TalkApi {
    private val json = "application/json".toMediaType()

    private fun endpoint(serverUrl: String, path: String) = serverUrl.trimEnd('/').toHttpUrl().newBuilder().addPathSegments(path).build()

    private suspend fun call(request: Request): String = withContext(Dispatchers.IO) {
        val call = http.newCall(request)
        // A cancelled attempt (network came back) must not sit out the 10 s connect timeout on the dead network.
        coroutineContext[Job]?.invokeOnCompletion { if (it != null) call.cancel() }
        call.execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw HttpFailure(res.code)
            text
        }
    }

    override suspend fun register(serverUrl: String, installId: String, provisionSecret: String): String {
        val body = buildJsonObject { put("installId", installId) }.toString().toRequestBody(json)
        val request = Request.Builder().url(endpoint(serverUrl, "v1/cars/register")).post(body)
            .header("X-Provision-Secret", provisionSecret).build()
        return decodeBody { HeyJson.parseToJsonElement(call(request)).jsonObject.getValue("carToken").jsonPrimitive.content }
    }

    override suspend fun session(serverUrl: String, carToken: String): TalkSession {
        val request = Request.Builder().url(endpoint(serverUrl, "v1/sessions")).post("{}".toRequestBody(json))
            .header("Authorization", "Bearer $carToken").build()
        return decodeBody {
            val o = HeyJson.parseToJsonElement(call(request)).jsonObject
            TalkSession(o.getValue("sessionId").jsonPrimitive.content, o.getValue("wsTicket").jsonPrimitive.content)
        }
    }

    override fun socketUrl(serverUrl: String, ticket: String): String =
        endpoint(serverUrl, "v1/talk").newBuilder().addQueryParameter("ticket", ticket).build().toString()
}

/** What the repository needs from the connection. [TalkLink] is the real one; tests plug in a recording fake. */
interface TalkTransport {
    interface Listener {
        fun onConnection(status: ConnectionStatus)
        fun onFrame(frame: ServerFrame)

        /** One binary frame: raw PCM s16le of the current reply, in order. */
        fun onAudio(pcm: ByteArray)
    }

    fun connect(serverUrl: String)

    /** The C8 "다시 연결" button: skip the pause before the next attempt (PLEOS-94). */
    fun reconnectNow()

    /** True when the frame went onto an open socket. */
    fun send(text: String): Boolean
    fun close()
}

/** Reconnect delay after [failures] failed attempts in a row (1 = first retry): 1, 2, 4, 8, 15, 15, ... seconds. */
fun backoffMillis(failures: Int, backoffSec: List<Long> = HeyConfig.RECONNECT_BACKOFF_SEC): Long =
    backoffSec[(failures - 1).coerceIn(0, backoffSec.lastIndex)] * 1000

/**
 * One connection to /v1/talk: register once per install (car token kept on the device), ask for a single-use ticket for
 * every (re)connect, open the socket and send `hello`. A lost socket or a failed step is retried after 1/2/4/8/15 s.
 * Nothing secret is logged: the provision secret, the car token and the ticket stay out of every message.
 */
class TalkLink(
    private val api: TalkApi,
    private val http: OkHttpClient,
    private val storage: CarStorage,
    private val scope: CoroutineScope,
    private val listener: TalkTransport.Listener,
    private val appVersion: String,
    /** Reads the provision secret from the device (a 600 file); null when it is not there. Called only to register. */
    private val provisionSecret: () -> String?,
    /** Paces the reconnects: the 1/2/4/8/15 s backoff, cut short by the network coming back or the C8 button. */
    private val pacer: ReconnectPacer,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) : TalkTransport {
    @Volatile private var socket: WebSocket? = null
    private var loop: Job? = null

    /** Fired by the pacer when the default network goes; ends the attempt (or open socket) at once. */
    private val gone = Channel<Unit>(Channel.CONFLATED)

    override fun connect(serverUrl: String) {
        close()
        pacer.onNetworkGone = { gone.trySend(Unit) }
        pacer.start()
        loop = scope.launch { run(serverUrl) }
    }

    override fun reconnectNow() = pacer.trigger(ReconnectTrigger.Button)

    private companion object {
        /** A button press drops an attempt only when it has hung this long (a fresh one is already doing what the button asks). */
        const val BUTTON_DROPS_AFTER_MS = 2_000L

        /** A "network up/validated" event drops an attempt that has hung this long: it was started while the network was not ready. */
        const val NETWORK_DROPS_AFTER_MS = 1_500L
    }

    private suspend fun run(serverUrl: String) {
        var failures = 0
        while (scope.isActive) {
            if (pacer.networkGone) {
                // No network: nothing to try. C8 offline now; the pause ends when the network (or the button) is back.
                listener.onConnection(ConnectionStatus.RegistrationFailed)
                pacer.pause(failures.coerceAtLeast(1))
                continue
            }
            gone.tryReceive()
            listener.onConnection(if (failures == 0) ConnectionStatus.Connecting else ConnectionStatus.Reconnecting)
            val startedAt = clock()
            val attempt = scope.async { attempt(serverUrl) }
            var opened: Boolean? = null
            while (opened == null) {
                select<Unit> {
                    attempt.onAwait { opened = it }
                    gone.onReceive {
                        HeyLog.i(TAG, "link: network lost, attempt dropped after ${clock() - startedAt} ms")
                        attempt.cancel()
                        socket?.cancel()
                        opened = false
                    }
                    pacer.events.onReceive { trigger ->
                        val now = clock()
                        // Stale: it began before the network came back (it sits on the dead network), or a press found it hung.
                        val stale = when (trigger) {
                            ReconnectTrigger.Network -> startedAt < pacer.networkBackAt || now - startedAt >= NETWORK_DROPS_AFTER_MS
                            ReconnectTrigger.Button -> now - startedAt >= BUTTON_DROPS_AFTER_MS
                            ReconnectTrigger.Backoff -> false
                        }
                        if (stale) {
                            HeyLog.i(TAG, "link: reconnect trigger=${trigger.label} after ${now - startedAt} ms (attempt dropped)")
                            attempt.cancel()
                            socket?.cancel()
                            opened = false
                        }
                    }
                }
            }
            socket = null
            if (!scope.isActive) return
            failures = if (opened == true) 1 else failures + 1
            pacer.markDown()
            if (attempt.isCancelled) continue
            pacer.pause(failures)
        }
    }

    /** One connect: ticket, socket, then waits until the socket is gone. True when the socket had opened. */
    private suspend fun attempt(serverUrl: String): Boolean {
        val closed = CompletableDeferred<Unit>()
        var opened = false
        try {
            // A pooled connection from before the network went would hang until its read timeout: every attempt starts fresh.
            http.connectionPool.evictAll()
            val t0 = clock()
            val session = openSession(serverUrl)
            val ticketMs = clock() - t0
            socket = http.newWebSocket(Request.Builder().url(api.socketUrl(serverUrl, session.wsTicket)).build(), object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    opened = true
                    HeyLog.i(TAG, "link: ticket ${ticketMs} ms, socket open ${clock() - t0 - ticketMs} ms")
                    webSocket.send(ClientFrames.hello(appVersion))
                    pacer.markOnline()
                    listener.onConnection(ConnectionStatus.Online)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    parseServerFrame(text)?.let(listener::onFrame) ?: HeyLog.w(TAG, "unreadable frame ignored")
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) = listener.onAudio(bytes.toByteArray())

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    closed.complete(Unit)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    HeyLog.w(TAG, "socket failed: ${t.javaClass.simpleName} ${response?.code ?: ""}".trim())
                    closed.complete(Unit)
                }
            })
            closed.await()
        } catch (e: HttpFailure) {
            HeyLog.w(TAG, "connect step refused: HTTP ${e.code}")
            listener.onConnection(if (e.code == 401 || e.code == 403) ConnectionStatus.Rejected else ConnectionStatus.RegistrationFailed)
        } catch (e: IOException) {
            HeyLog.w(TAG, "connect step failed: ${e.javaClass.simpleName}")
            listener.onConnection(ConnectionStatus.RegistrationFailed)
        }
        return opened
    }

    /** Car token from the device, or a fresh registration (needs the provision secret); a refused token is forgotten. */
    private suspend fun openSession(serverUrl: String): TalkSession {
        val saved = storage.loadCarToken(serverUrl)
        if (saved != null) {
            try {
                return api.session(serverUrl, saved)
            } catch (e: HttpFailure) {
                if (e.code != 401) throw e
                storage.forgetCarToken(serverUrl)
            }
        }
        val secret = provisionSecret()?.takeIf { it.isNotBlank() } ?: throw HttpFailure(403)
        val token = api.register(serverUrl, storage.installId(), secret)
        storage.saveCarToken(serverUrl, token)
        return api.session(serverUrl, token)
    }

    override fun send(text: String): Boolean = socket?.send(text) == true

    override fun close() {
        loop?.cancel()
        loop = null
        pacer.stop()
        socket?.close(1000, null)
        socket = null
    }
}
