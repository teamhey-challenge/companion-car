package ai.hey.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** The one Json configuration used for every wire message. */
val HeyJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

// ---------------------------------------------------------------- client -> server payloads (car side)

@Serializable data class JoinDecision(val joinRequestId: String)
@Serializable data class RouteFailedData(val code: String)
@Serializable data class DropoffProposeData(val point: LatLng, val label: String, val reasonId: DropoffReasonId? = null)
@Serializable data class DropoffDecideData(val version: Int, val decision: DriverDecision)
@Serializable data class RequestSpokenData(val requestId: String)
@Serializable data class ReplySendData(val requestId: String? = null, val replyId: QuickReplyId)
@Serializable data class DrivingStateData(val driving: Boolean)

/** `{}` payload (card.recheck, trip.arrive, trip.end). */
@Serializable class EmptyData

/** Message types the car sends. */
object ClientType {
    const val JOIN_APPROVE = "join.approve"
    const val JOIN_DECLINE = "join.decline"
    const val CARD_RECHECK = "card.recheck"
    const val ROUTE_STARTED = "route.started"
    const val ROUTE_FAILED = "route.failed"
    const val TRIP_PROGRESS = "trip.progress"
    const val DROPOFF_PROPOSE = "dropoff.propose"
    const val DROPOFF_DECIDE = "dropoff.decide"
    const val REQUEST_SPOKEN = "request.spoken"
    const val REPLY_SEND = "reply.send"
    const val DRIVING_STATE = "driving.state"
    const val TRIP_ARRIVE = "trip.arrive"
    const val TRIP_END = "trip.end"
}

/** `{ type, id, ts, data }` ready to put on the socket. */
fun envelopeJson(type: String, id: String, ts: Long, data: JsonElement): String =
    buildJsonObject {
        put("type", type)
        put("id", id)
        put("ts", ts)
        put("data", data)
    }.toString()

inline fun <reified T> envelopeOf(type: String, id: String, ts: Long, data: T): String =
    envelopeJson(type, id, ts, HeyJson.encodeToJsonElement(data))

// ---------------------------------------------------------------- server -> car

sealed interface ServerMessage {
    val id: String

    data class Snapshot(override val id: String, val view: CarView) : ServerMessage
    data class Say(override val id: String, val data: SayData) : ServerMessage
    data class Notice(override val id: String, val data: NoticeData) : ServerMessage
    data class Error(override val id: String, val data: ErrorData) : ServerMessage
    data class Ack(override val id: String) : ServerMessage
}

/** Parses one text frame from the relay; unknown or malformed frames give null (never throw). */
fun parseServerMessage(raw: String): ServerMessage? = try {
    val obj = HeyJson.parseToJsonElement(raw).jsonObject
    val type = obj.getValue("type").jsonPrimitive.content
    val id = obj.getValue("id").jsonPrimitive.content
    if (type == "ack") {
        ServerMessage.Ack(id)
    } else {
        val data = obj.getValue("data")
        when (type) {
            "trip.snapshot" -> {
                // The relay sends role-specific snapshots; a car only ever receives role "car".
                val role = (data as? JsonObject)?.get("role")?.jsonPrimitive?.contentOrNull
                if (role == "car") ServerMessage.Snapshot(id, HeyJson.decodeFromJsonElement<CarView>(data)) else null
            }
            "say" -> ServerMessage.Say(id, HeyJson.decodeFromJsonElement(data))
            "notice" -> ServerMessage.Notice(id, HeyJson.decodeFromJsonElement(data))
            "error" -> ServerMessage.Error(id, HeyJson.decodeFromJsonElement(data))
            else -> null
        }
    }
} catch (e: Exception) {
    null
}
