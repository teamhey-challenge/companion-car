package ai.hey.session

import ai.hey.model.HeyConfig
import ai.hey.model.HeyJson
import ai.hey.model.HeyLog
import ai.hey.model.ClientFrames
import ai.hey.model.ServerFrame
import ai.hey.model.parseServerFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        http.newCall(request).execute().use { res ->
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
    private val backoffSec: List<Long> = HeyConfig.RECONNECT_BACKOFF_SEC,
) : TalkTransport {
    @Volatile private var socket: WebSocket? = null
    private var loop: Job? = null

    override fun connect(serverUrl: String) {
        close()
        loop = scope.launch { run(serverUrl) }
    }

    private suspend fun run(serverUrl: String) {
        var failures = 0
        while (scope.isActive) {
            listener.onConnection(if (failures == 0) ConnectionStatus.Connecting else ConnectionStatus.Reconnecting)
            val closed = CompletableDeferred<Unit>()
            var opened = false
            try {
                val session = openSession(serverUrl)
                socket = http.newWebSocket(Request.Builder().url(api.socketUrl(serverUrl, session.wsTicket)).build(), object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        opened = true
                        webSocket.send(ClientFrames.hello(appVersion))
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
                failures = if (opened) 1 else failures + 1
            } catch (e: HttpFailure) {
                HeyLog.w(TAG, "connect step refused: HTTP ${e.code}")
                listener.onConnection(if (e.code == 401 || e.code == 403) ConnectionStatus.Rejected else ConnectionStatus.RegistrationFailed)
                failures++
            } catch (e: IOException) {
                HeyLog.w(TAG, "connect step failed: ${e.javaClass.simpleName}")
                listener.onConnection(ConnectionStatus.RegistrationFailed)
                failures++
            }
            socket = null
            if (!scope.isActive) return
            delay(backoffMillis(failures, backoffSec))
        }
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
        socket?.close(1000, null)
        socket = null
    }
}
