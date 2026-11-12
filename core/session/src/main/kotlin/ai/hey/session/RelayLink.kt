package ai.hey.session

import ai.hey.model.AuthMessage
import ai.hey.model.CreateCarRequest
import ai.hey.model.CreateCarResponse
import ai.hey.protocol.ProtocolVersion
import ai.hey.model.HeyConfig
import ai.hey.model.HeyJson
import ai.hey.model.HeyLog
import ai.hey.model.ServerMessage
import ai.hey.model.envelopeJson
import ai.hey.model.parseServerMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

private const val TAG = "RelayLink"
private const val SEEN_MAX = 500

fun newHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .pingInterval(20, TimeUnit.SECONDS)
    .build()

private const val PROTOCOL_MISMATCH_STATUS = 426
private const val CLOSE_UNAUTHORIZED = 4401
private const val CLOSE_PROTOCOL_MISMATCH = 4426

/** The relay answered 426 PROTOCOL_MISMATCH: this app and the relay have different protocol majors. Never retried. */
class ProtocolMismatchException(message: String) : IOException(message)

/** First-launch registration: POST /v1/cars. Throws [ProtocolMismatchException] on 426, IOException on any other failure. */
suspend fun registerCar(
    http: OkHttpClient,
    baseUrl: String,
    plateSuffix: String,
    protocolVersion: String = ProtocolVersion.CURRENT,
    provisionSecret: String = "",
): CreateCarResponse =
    withContext(Dispatchers.IO) {
        val url = baseUrl.toHttpUrl().newBuilder().addPathSegments("v1/cars").build()
        val body = HeyJson.encodeToString(CreateCarRequest.serializer(), CreateCarRequest(plateSuffix))
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(body).header(ProtocolVersion.HEADER, protocolVersion)
            .apply { if (provisionSecret.isNotEmpty()) header("Authorization", "Provision $provisionSecret") }.build()
        http.newCall(request).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (res.code == PROTOCOL_MISMATCH_STATUS) throw ProtocolMismatchException("register refused: protocol mismatch")
            if (!res.isSuccessful) throw IOException("register failed: HTTP ${res.code}")
            HeyJson.decodeFromString(CreateCarResponse.serializer(), text)
        }
    }

/** What the repository needs from the connection. [RelayLink] is the real one; tests plug in a recording fake. */
interface RelayTransport {
    interface Listener {
        /** Called on every connect (true) and loss (false). */
        fun onConnection(connected: Boolean)

        /** The relay answered 401 on upgrade: the stored registration is no longer valid. Reconnecting stops. */
        fun onTokenRejected()

        /** The relay answered 426 PROTOCOL_MISMATCH on upgrade: another protocol major. Reconnecting stops (no retry loop). */
        fun onProtocolMismatch()

        fun onMessage(message: ServerMessage)
    }

    /** Opens (or reopens with new coordinates) the connection. */
    fun connect(baseUrl: String, token: String)

    fun close()

    /**
     * Queues an envelope. [supersedeKey]: an unacked envelope with the same key is replaced (only the last
     * driving.state matters). Returns the envelope id.
     */
    fun send(type: String, data: JsonElement, supersedeKey: String? = null, id: String = UUID.randomUUID().toString()): String
}

/**
 * One WebSocket to the relay with the delivery rules of docs/architecture.md 3.2:
 * every received envelope is acked and processed once (dedupe by id); every sent envelope is kept until acked and
 * sent again every [ackTimeoutMs] (and after a reconnect); a lost socket is reopened after 1/2/4/8/15 s.
 * The token is passed to the socket URL only; it is never logged.
 */
class RelayLink(
    private val http: OkHttpClient,
    private val scope: CoroutineScope,
    private val listener: RelayTransport.Listener,
    private val ackTimeoutMs: Long = HeyConfig.WS_ACK_TIMEOUT_MS,
    private val backoffSec: List<Long> = HeyConfig.RECONNECT_BACKOFF_SEC,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val protocolVersion: String = ProtocolVersion.CURRENT,
) : RelayTransport {
    private class Outgoing(val id: String, val key: String?, val json: String, var sentAt: Long)

    private val lock = Any()
    private val outbox = LinkedHashMap<String, Outgoing>()
    private val seen = LinkedHashSet<String>()
    private var socket: WebSocket? = null

    @Volatile private var tokenRejected = false
    @Volatile private var protocolMismatch = false

    /** The relay refuses a bad first frame by closing 4401 (token) or 4426 (protocol major): neither is retried. */
    private fun noteClose(code: Int) {
        if (code == CLOSE_UNAUTHORIZED) tokenRejected = true
        if (code == CLOSE_PROTOCOL_MISMATCH) protocolMismatch = true
    }

    private var job: Job? = null

    override fun connect(baseUrl: String, token: String) {
        val previous = job
        job = scope.launch {
            previous?.cancelAndJoinQuietly()
            run(baseUrl, token)
        }
    }

    override fun close() {
        job?.cancel()
        job = null
        synchronized(lock) { socket?.cancel(); socket = null }
    }

    override fun send(type: String, data: JsonElement, supersedeKey: String?, id: String): String {
        val json = envelopeJson(type, id, clock(), data)
        val ws: WebSocket?
        synchronized(lock) {
            if (supersedeKey != null) outbox.values.removeAll { it.key == supersedeKey }
            outbox[id] = Outgoing(id, supersedeKey, json, 0L)
            ws = socket
            if (ws != null) outbox.getValue(id).sentAt = clock()
        }
        ws?.send(json)
        return id
    }

    private suspend fun Job.cancelAndJoinQuietly() {
        cancel()
        try {
            join()
        } catch (_: Throwable) {
        }
    }

    private suspend fun run(baseUrl: String, token: String) {
        // The token is never part of the URL (access logs); it goes in the first frame after the upgrade.
        val url = baseUrl.toHttpUrl().newBuilder().addPathSegments("v1/ws").build()
        val authFrame = HeyJson.encodeToString(AuthMessage.serializer(), AuthMessage(token = token, protocol = protocolVersion))
        var failures = 0
        while (scope.isActive) {
            val closed = CompletableDeferred<Unit>()
            val authed = java.util.concurrent.atomic.AtomicBoolean(false)
            tokenRejected = false
            protocolMismatch = false
            val ws = http.newWebSocket(
                Request.Builder().url(url).header(ProtocolVersion.HEADER, protocolVersion).build(),
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        // The relay sends nothing until the auth frame is accepted, and drops anything sent before it.
                        webSocket.send(authFrame)
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        // The first message from the relay is the proof that auth was accepted: only now the link is up.
                        if (authed.compareAndSet(false, true)) {
                            synchronized(lock) {
                                socket = webSocket
                                // Everything unacked goes out again on a fresh socket.
                                outbox.values.forEach { it.sentAt = clock(); webSocket.send(it.json) }
                            }
                            listener.onConnection(true)
                        }
                        receive(webSocket, text)
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        noteClose(code)
                        webSocket.close(code, reason)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        noteClose(code)
                        closed.complete(Unit)
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        HeyLog.w(TAG, "socket failure: ${t.javaClass.simpleName}")
                        if (response?.code == 401) tokenRejected = true
                        if (response?.code == PROTOCOL_MISMATCH_STATUS) protocolMismatch = true
                        closed.complete(Unit)
                    }
                },
            )
            var ticker: Job? = null
            try {
                ticker = scope.launch { resendLoop() }
                closed.await()
            } finally {
                ticker?.cancel()
                withContext(NonCancellable) {
                    synchronized(lock) { if (socket === ws) socket = null }
                    ws.cancel()
                }
            }
            if (protocolMismatch) {
                // Another protocol major: every retry gets the same 426.
                listener.onProtocolMismatch()
                return
            }
            if (tokenRejected) {
                // The relay does not know this token (data wiped, car removed): retrying cannot help.
                listener.onTokenRejected()
                return
            }
            if (authed.get()) {
                failures = 0
                listener.onConnection(false)
            }
            val wait = backoffSec[minOf(failures, backoffSec.lastIndex)]
            failures++
            delay(wait * 1000)
        }
    }

    private suspend fun resendLoop() {
        while (true) {
            delay(ackTimeoutMs)
            val now = clock()
            synchronized(lock) {
                val ws = socket ?: return@synchronized
                outbox.values.filter { now - it.sentAt >= ackTimeoutMs }.forEach {
                    it.sentAt = now
                    ws.send(it.json)
                }
            }
        }
    }

    private fun receive(webSocket: WebSocket, text: String) {
        val message = parseServerMessage(text) ?: return
        if (message is ServerMessage.Ack) {
            synchronized(lock) { outbox.remove(message.id) }
            return
        }
        webSocket.send(
            kotlinx.serialization.json.buildJsonObject {
                put("type", kotlinx.serialization.json.JsonPrimitive("ack"))
                put("id", kotlinx.serialization.json.JsonPrimitive(message.id))
            }.toString(),
        )
        val fresh = synchronized(lock) {
            val added = seen.add(message.id)
            if (seen.size > SEEN_MAX) seen.remove(seen.first())
            added
        }
        if (fresh) listener.onMessage(message)
    }

    /** Unacked envelope count, for tests. */
    val unacked: Int get() = synchronized(lock) { outbox.size }
}
