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

    /** D-48 onboarding: "speak this step now" (the wire names of [OnboardingStep] plus the whale's `skip`). */
    fun onboarding(step: String): String = obj { put("type", "onboarding"); put("step", step) }

    /** The recognized answer to a whale question (`q1`..`q3`); `reply` asks the server for the whale's short answer line. */
    fun onboardingAnswer(step: String, text: String): String =
        obj { put("type", "onboarding.answer"); put("step", step); put("text", text); put("reply", true) }

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

    /** The trip ended (D-52 2): only the car notification centre uses it; [text] is the server's one-line farewell. */
    data class TripDone(val id: String, val text: String, val date: String, val at: String, val carLine: String? = null) : ServerFrame

    /** A friend arrived (spoken only): same audio path as [FriendMessage]. D-52: the frame never carries, and the app never reads, a place. */
    data class FriendArrived(val id: String, val fromName: String) : ServerFrame

    /** After three friend messages read while driving the server keeps the rest in the inbox and says so once ([text] is the spoken line, audio follows with turn id [id]). */
    data class FriendMore(val id: String, val text: String) : ServerFrame

    /**
     * The spoken onboarding line follows as `reply.*` + PCM with turn id [id], like a friend line. [whale] is `voice: "whale"`
     * (the car adds the reverb to that playback; heyri stays dry). Also sent as `step: "ack"` (the whale's short answer to a
     * question) and `step: "skip"` (an answer that was not understood).
     */
    data class Onboarding(val step: String, val id: String, val text: String, val whale: Boolean = false) : ServerFrame

    /** The server's verdict on an answer; [skipped] = it was not usable and the server goes on without asking again. */
    data class OnboardingAck(val step: String, val ok: Boolean, val name: String?, val skipped: Boolean = false) : ServerFrame

    data class Settings(val settings: CompanionSettings) : ServerFrame

    /**
     * The pairing status changed on the server: a phone linked ([phase] Claimed, with the Google given name in [googleName],
     * null when unknown) or a phone or the car ended the link ([phase] None).
     */
    data class Pairing(val phase: PairingPhase, val googleName: String? = null) : ServerFrame
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
        "trip_done" -> ServerFrame.TripDone(s("id") ?: return null, s("text") ?: return null, s("date").orEmpty(), s("at").orEmpty(), s("carLine")?.takeIf { it.isNotBlank() })
        "friend_arrived" -> ServerFrame.FriendArrived(s("id") ?: return null, s("fromName").orEmpty())
        "friend_more" -> ServerFrame.FriendMore(s("id") ?: return null, s("text").orEmpty())
        "onboarding" -> ServerFrame.Onboarding(s("step") ?: return null, s("id") ?: return null, s("text").orEmpty(), whale = s("voice") == "whale")
        "onboarding.ack" -> ServerFrame.OnboardingAck(
            step = s("step") ?: return null,
            ok = o["ok"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: return null,
            name = s("name"),
            skipped = o["skipped"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false,
        )

        "settings" -> ServerFrame.Settings(parseCompanionSettings(o["settings"]?.jsonObject ?: return null) ?: return null)
        "pairing" -> ServerFrame.Pairing(
            when (s("status")) {
                "none" -> PairingPhase.None
                "pending" -> PairingPhase.Pending
                "claimed" -> PairingPhase.Claimed
                "expired" -> PairingPhase.Expired
                else -> return null
            },
            googleName = s("googleName"),
        )
        "error" -> ServerFrame.Error(s("code") ?: return null, s("turnId"), s("message"))
        else -> null
    }
}
