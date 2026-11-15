package ai.hey.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** The one Json configuration used for every wire message. */
val HeyJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

/**
 * The /v1/talk wire contract (companion-shared packages/protocol v0.2.0, developer.md 6.2): text frames are JSON with a
 * `type`, binary frames are server -> app TTS audio only.
 */
object Wire {
    const val PROTOCOL_V = 1
    const val MAX_TEXT_CHARS = 500
    const val TRIGGER_PTT = "ptt"
    const val TRIGGER_AFTER_PROACTIVE = "listen_after_proactive"
}

/** App -> server text frames, already encoded. */
object ClientFrames {
    fun hello(appVersion: String): String = obj { put("type", "hello"); put("v", Wire.PROTOCOL_V); put("appVersion", appVersion) }

    fun turnStart(turnId: String, trigger: String = Wire.TRIGGER_PTT): String =
        obj { put("type", "turn.start"); put("turnId", turnId); put("trigger", trigger) }

    fun text(turnId: String, text: String): String = obj { put("type", "text"); put("turnId", turnId); put("text", text) }

    fun turnEnd(turnId: String): String = obj { put("type", "turn.end"); put("turnId", turnId) }

    fun turnCancel(turnId: String): String = obj { put("type", "turn.cancel"); put("turnId", turnId) }

    /** Vehicle context; null values are left out. Keys follow contract.md. */
    fun ctx(s: VehicleSignals): String = obj {
        put("type", "ctx")
        put(
            "ctx",
            buildJsonObject {
                s.gear?.let { put("gear", it) }
                s.speedKmh?.let { put("speedKmh", it) }
                s.wiper?.let { put("wiper", it) }
                s.doorOpen?.let { put("doorOpen", it) }
                s.driverDoorOpen?.let { put("driverDoorOpen", it) }
                s.rangeKm?.let { put("rangeKm", it) }
                s.fuelType?.let { put("fuelType", it) }
                s.headlight?.let { put("headlight", it) }
                s.destKm?.let { put("destKm", it) }
                s.charging?.let { put("charging", it) }
                s.destinationName?.let { put("destinationName", it) }
                put("arrived", s.arrived)
                put("naviGuiding", s.naviGuiding)
            },
        )
    }

    private fun obj(build: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): String = buildJsonObject(build).toString()
}

/** `reply.start.audio`: PCM s16le from the server, or `none` (the car reads the text with the Pleos TTS). */
sealed interface AudioSpec {
    data object None : AudioSpec
    data class Pcm(val sampleRate: Int, val channels: Int) : AudioSpec
}

/** Server -> app text frames. */
sealed interface ServerFrame {
    data class ReplyStart(val turnId: String, val audio: AudioSpec) : ServerFrame
    data class ReplyDelta(val turnId: String, val text: String) : ServerFrame
    data class ReplyEnd(val turnId: String, val text: String, val firstTokenMs: Long?, val totalMs: Long) : ServerFrame
    data class Proactive(val id: String, val reason: String) : ServerFrame

    /** A friend's message: the spoken line follows as `reply.*` + PCM with turn id [id]; [text] is only shown when parked. */
    data class FriendMessage(val id: String, val fromName: String, val text: String) : ServerFrame

    /** A friend arrived (spoken only): same audio path as [FriendMessage]. */
    data class FriendArrived(val id: String, val fromName: String, val place: String?) : ServerFrame
    data class Settings(val settings: CompanionSettings) : ServerFrame

    /** The pairing status changed on the server (a phone or the car ended it: [phase] None). */
    data class Pairing(val phase: PairingPhase) : ServerFrame
    data class Error(val code: String, val turnId: String?, val message: String?) : ServerFrame
}

/** Parses one text frame from the server; unknown or malformed frames give null (never throw). */
fun parseServerFrame(raw: String): ServerFrame? {
    return try {
        parseServerFrameOrThrow(raw)
    } catch (e: Exception) {
        null
    }
}

private fun parseServerFrameOrThrow(raw: String): ServerFrame? {
    val o = HeyJson.parseToJsonElement(raw).jsonObject
    fun s(k: String) = o[k]?.jsonPrimitive?.contentOrNull
    return when (s("type")) {
        "reply.start" -> {
            val audio = o["audio"]
            val spec = when {
                audio is JsonPrimitive && audio.contentOrNull == "none" -> AudioSpec.None
                audio is JsonObject && audio["format"]?.jsonPrimitive?.contentOrNull == "pcm_s16le" -> AudioSpec.Pcm(
                    sampleRate = audio["sampleRate"]?.jsonPrimitive?.intOrNull ?: return null,
                    channels = audio["channels"]?.jsonPrimitive?.intOrNull ?: 1,
                )
                else -> return null
            }
            ServerFrame.ReplyStart(s("turnId") ?: return null, spec)
        }
        "reply.delta" -> ServerFrame.ReplyDelta(s("turnId") ?: return null, s("text") ?: return null)
        "reply.end" -> ServerFrame.ReplyEnd(
            turnId = s("turnId") ?: return null,
            text = s("text") ?: return null,
            firstTokenMs = o["firstTokenMs"]?.jsonPrimitive?.longOrNull,
            totalMs = o["totalMs"]?.jsonPrimitive?.longOrNull ?: 0,
        )
        "proactive" -> ServerFrame.Proactive(s("id") ?: return null, s("reason").orEmpty())
        "friend_message" -> ServerFrame.FriendMessage(s("id") ?: return null, s("fromName").orEmpty(), s("text") ?: return null)
        "friend_arrived" -> ServerFrame.FriendArrived(s("id") ?: return null, s("fromName").orEmpty(), s("place"))
        "settings" -> ServerFrame.Settings(parseCompanionSettings(o["settings"]?.jsonObject ?: return null) ?: return null)
        "pairing" -> ServerFrame.Pairing(
            when (s("status")) {
                "none" -> PairingPhase.None
                "pending" -> PairingPhase.Pending
                "claimed" -> PairingPhase.Claimed
                "expired" -> PairingPhase.Expired
                else -> return null
            },
        )
        "error" -> ServerFrame.Error(s("code") ?: return null, s("turnId"), s("message"))
        else -> null
    }
}
