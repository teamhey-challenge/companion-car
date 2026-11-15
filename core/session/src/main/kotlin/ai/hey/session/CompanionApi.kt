package ai.hey.session

import ai.hey.model.CompanionSettings
import ai.hey.model.Friend
import ai.hey.model.FriendInvite
import ai.hey.model.ReceivedMessage
import ai.hey.model.ConsentRecord
import ai.hey.model.ConsentType
import ai.hey.model.HeyJson
import ai.hey.model.MemoryItem
import ai.hey.model.MemoryKind
import ai.hey.model.MemoryPage
import ai.hey.model.PairingInfo
import ai.hey.model.PairingPhase
import ai.hey.model.PairingStatus
import ai.hey.model.VehicleSignals
import ai.hey.model.parseCompanionSettings
import ai.hey.model.settingsPatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Instant

/** The car's REST calls to the companion server (contract.md "REST", companion-shared v0.4.0). Every call can throw [HttpFailure] or [IOException]. */
interface CompanionApi {
    suspend fun postConsent(type: ConsentType, version: String, granted: Boolean): List<ConsentRecord>
    suspend fun consents(): List<ConsentRecord>

    /** Withdrawing `record` or `memory` makes the server delete this car's memory and summaries at once. */
    suspend fun withdrawConsent(type: ConsentType)

    suspend fun createPairing(): PairingInfo
    suspend fun currentPairing(): PairingStatus

    /** Phone token becomes invalid, memory stays. */
    suspend fun deletePairing()

    suspend fun memory(cursor: String?, limit: Int): MemoryPage
    suspend fun deleteMemory(id: String)
    suspend fun deleteAllMemory()

    suspend fun settings(): CompanionSettings
    suspend fun patchSettings(from: CompanionSettings, to: CompanionSettings): CompanionSettings

    /** POST /v1/vehicle-events; the server drops coordinate keys, the car never has any. */
    suspend fun vehicleEvent(atEpochMs: Long, signals: VehicleSignals)

    /** POST /v1/friends/invites */
    suspend fun createFriendInvite(): FriendInvite

    /** GET /v1/friends (a plain JSON array). */
    suspend fun friends(): List<Friend>

    /** GET /v1/messages: what friends sent me, newest first (the sent ones are dropped here). */
    suspend fun messages(): List<ReceivedMessage>

    /** POST /v1/messages/{id}/report: the server hides the message (D-53). */
    suspend fun reportMessage(id: String)
}

/** Every companion REST call is the driver waiting at C6/C7: the whole call (connect + read) ends within this (PLEOS-148). */
const val REST_TIMEOUT_MS = 4_000L

class HttpCompanionApi(
    private val storage: CarStorage,
    private val http: OkHttpClient = newHttpClient(),
    /** A known-dead link fails a call at once, without waiting for the timeout. */
    private val network: OnlineCheck = OnlineCheck { true },
    timeoutMs: Long = REST_TIMEOUT_MS,
) : CompanionApi {
    private val json = "application/json".toMediaType()
    private val restHttp = http.newBuilder()
        .connectTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        .readTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        .callTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        .build()

    private suspend fun call(method: String, path: String, body: JsonObject? = null, array: JsonArray? = null, query: Map<String, String> = emptyMap()): String? =
        withContext(Dispatchers.IO) {
            if (!network.isOnline()) throw IOException("offline")
            val server = storage.settings.value.serverUrl
            val token = storage.loadCarToken(server) ?: throw HttpFailure(401)
            val url = server.trimEnd('/').toHttpUrl().newBuilder().addPathSegments(path).apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
            val payload = (array?.toString() ?: body?.toString())?.toRequestBody(json)
            val request = Request.Builder().url(url).header("Authorization", "Bearer $token")
                .method(method, payload ?: if (method == "POST" || method == "PATCH") "{}".toRequestBody(json) else null).build()
            restHttp.newCall(request).execute().use { res ->
                val text = res.body?.string().orEmpty()
                if (!res.isSuccessful) throw HttpFailure(res.code)
                text.ifBlank { null }
            }
        }

    private fun parse(text: String?) = HeyJson.parseToJsonElement(text ?: throw IOException("empty body"))

    private fun consentList(o: JsonObject): List<ConsentRecord> = o.getValue("consents").jsonArray.mapNotNull {
        val c = it.jsonObject
        ConsentRecord(
            type = ConsentType.fromWire(c["type"]?.jsonPrimitive?.contentOrNull) ?: return@mapNotNull null,
            version = c["version"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            granted = c["granted"]?.jsonPrimitive?.contentOrNull == "true",
            at = c["at"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
    }

    override suspend fun postConsent(type: ConsentType, version: String, granted: Boolean): List<ConsentRecord> = decodeBody {
        consentList(
            parse(
                call(
                    "POST", "v1/consents",
                    buildJsonObject { put("type", type.wire); put("version", version); put("granted", granted); put("channel", "car") },
                ),
            ).jsonObject,
        )
    }

    override suspend fun consents(): List<ConsentRecord> = decodeBody { consentList(parse(call("GET", "v1/consents")).jsonObject) }

    override suspend fun withdrawConsent(type: ConsentType) {
        call("DELETE", "v1/consents/${type.wire}")
    }

    override suspend fun createPairing(): PairingInfo = decodeBody {
        val o = parse(call("POST", "v1/pairings")).jsonObject
        PairingInfo(o.getValue("pairCode").jsonPrimitive.content, o.getValue("expiresAt").jsonPrimitive.content)
    }

    override suspend fun currentPairing(): PairingStatus {
        val o = decodeBody { parse(call("GET", "v1/pairings/current")).jsonObject }
        fun s(k: String) = decodeBody { o[k]?.jsonPrimitive?.contentOrNull }
        val phase = when (s("status")) {
            "pending" -> PairingPhase.Pending
            "claimed" -> PairingPhase.Claimed
            "expired" -> PairingPhase.Expired
            else -> PairingPhase.None
        }
        return PairingStatus(phase, s("pairCode"), s("expiresAt"), s("pairedAt"), s("googleName"))
    }

    override suspend fun deletePairing() {
        call("DELETE", "v1/pairings/current")
    }

    override suspend fun memory(cursor: String?, limit: Int): MemoryPage {
        val q = buildMap { put("limit", limit.toString()); cursor?.let { put("cursor", it) } }
        val o = decodeBody { parse(call("GET", "v1/memory", query = q)).jsonObject }
        val items = decodeBody { o.getValue("items").jsonArray }.mapNotNull {
            val m = decodeBody { it.jsonObject }
            MemoryItem(
                id = m["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                kind = if (m["kind"]?.jsonPrimitive?.contentOrNull == "summary") MemoryKind.Summary else MemoryKind.Fact,
                text = m["text"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                createdAt = m["createdAt"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        }
        return MemoryPage(items, o["nextCursor"]?.let { c -> (c as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull })
    }

    override suspend fun deleteMemory(id: String) {
        call("DELETE", "v1/memory/$id")
    }

    override suspend fun deleteAllMemory() {
        call("DELETE", "v1/memory")
    }

    private fun settingsOf(text: String?): CompanionSettings = parseCompanionSettings(parse(text).jsonObject) ?: throw IOException("unreadable settings")

    override suspend fun settings(): CompanionSettings = decodeBody { settingsOf(call("GET", "v1/settings")) }

    override suspend fun patchSettings(from: CompanionSettings, to: CompanionSettings): CompanionSettings =
        decodeBody { settingsOf(call("PATCH", "v1/settings", settingsPatch(from, to))) }

    override suspend fun createFriendInvite(): FriendInvite = decodeBody {
        val o = parse(call("POST", "v1/friends/invites")).jsonObject
        FriendInvite(
            o.getValue("code").jsonPrimitive.content,
            o.getValue("url").jsonPrimitive.content,
            o["expiresAt"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
    }

    override suspend fun friends(): List<Friend> = decodeBody {
        parse(call("GET", "v1/friends")).jsonArray.mapNotNull {
            val f = it.jsonObject
            Friend(
                id = f["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                name = f["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                since = f["since"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                shareArrival = f["shareArrival"]?.jsonPrimitive?.contentOrNull == "true",
            )
        }
    }

    override suspend fun messages(): List<ReceivedMessage> = decodeBody {
        parse(call("GET", "v1/messages")).jsonObject.getValue("messages").jsonArray.mapNotNull {
            val m = it.jsonObject
            if (m["direction"]?.jsonPrimitive?.contentOrNull != "in") return@mapNotNull null
            ReceivedMessage(
                id = m["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                friendId = m["friendId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                friendName = m["friendName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                text = m["text"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                at = m["at"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        }
    }

    override suspend fun reportMessage(id: String) {
        call("POST", "v1/messages/$id/report")
    }

    override suspend fun vehicleEvent(atEpochMs: Long, signals: VehicleSignals) {
        val ctx = HeyJson.parseToJsonElement(ai.hey.model.ClientFrames.ctx(signals)).jsonObject.getValue("ctx").jsonObject
        call("POST", "v1/vehicle-events", array = buildJsonArray { add(buildJsonObject { put("at", Instant.ofEpochMilli(atEpochMs).toString()); put("ctx", ctx) }) })
    }
}

/** PLEOS-141: a 200 body that is not the expected JSON (proxy page, wrong deploy) is a failed call, never an uncaught crash. */
internal inline fun <T> decodeBody(block: () -> T): T =
    try {
        block()
    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
        throw e // IllegalStateException subtype: cancellation must pass through
    } catch (e: SerializationException) {
        throw IOException("bad body", e)
    } catch (e: IllegalArgumentException) {
        throw IOException("bad body", e)
    } catch (e: IllegalStateException) {
        throw IOException("bad body", e)
    } catch (e: NoSuchElementException) {
        throw IOException("bad body", e)
    }
