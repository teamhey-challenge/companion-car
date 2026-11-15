package ai.hey.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Mirrors companion-shared v0.4.0 `packages/protocol` (REST + settings frame). Wire names are the `wire` values. */
enum class ConsentType(val wire: String) {
    Terms("terms"), Privacy("privacy"), Record("record"), Memory("memory"), ExternalAi("external_ai");

    companion object {
        fun fromWire(s: String?): ConsentType? = entries.firstOrNull { it.wire == s }
    }
}

/** Proactive talking level (C7). Minimum gap between unprompted lines: low 20 min, normal 10, high 5 (PRD 7.7). */
enum class ProactiveLevel(val wire: String, val intervalMin: Int?) {
    Off("off", null), Low("low", 20), Normal("normal", 10), High("high", 5);

    companion object {
        fun fromWire(s: String?): ProactiveLevel? = entries.firstOrNull { it.wire == s }
    }
}

enum class SpeechStyle(val wire: String) {
    Banmal("banmal"), Jondaetmal("jondaetmal");

    companion object {
        fun fromWire(s: String?): SpeechStyle? = entries.firstOrNull { it.wire == s }
    }
}

/** Server-side settings (GET/PATCH /v1/settings, `settings` frame). Not [CarSettings], which stays on the device. */
data class CompanionSettings(
    val proactive: ProactiveLevel = ProactiveLevel.Normal,
    val memoryEnabled: Boolean = true,
    val speech: SpeechStyle = SpeechStyle.Banmal,
    val nickname: String? = null,
    /** Look (D-41, Rive values): bodyColor 0 aqua / 1 peach / null = follows the theme; hat 0 none / 1 beanie / 2 bucket; neck 0 none / 1 scarf. */
    val bodyColor: Int? = null,
    val hat: Int = 1,
    val neck: Int = 0,
)

fun parseCompanionSettings(o: JsonObject): CompanionSettings? {
    val d = CompanionSettings()
    val nick = o["nickname"]
    return CompanionSettings(
        proactive = ProactiveLevel.fromWire(o["proactive"]?.jsonPrimitive?.contentOrNull) ?: return null,
        memoryEnabled = o["memoryEnabled"]?.jsonPrimitive?.booleanOrNull ?: d.memoryEnabled,
        speech = SpeechStyle.fromWire(o["speech"]?.jsonPrimitive?.contentOrNull) ?: return null,
        nickname = if (nick is JsonPrimitive) nick.contentOrNull else null,
        bodyColor = (o["bodyColor"] as? JsonPrimitive)?.intOrNull?.takeIf { it in 0..1 },
        hat = (o["hat"] as? JsonPrimitive)?.intOrNull?.takeIf { it in 0..2 } ?: d.hat,
        neck = (o["neck"] as? JsonPrimitive)?.intOrNull?.takeIf { it in 0..1 } ?: d.neck,
    )
}

/** The PATCH body: only the fields that differ from [from]. */
fun settingsPatch(from: CompanionSettings, to: CompanionSettings): JsonObject = buildJsonObject {
    if (to.proactive != from.proactive) put("proactive", to.proactive.wire)
    if (to.memoryEnabled != from.memoryEnabled) put("memoryEnabled", to.memoryEnabled)
    if (to.speech != from.speech) put("speech", to.speech.wire)
    if (to.nickname != from.nickname) put("nickname", to.nickname)
    if (to.bodyColor != from.bodyColor) put("bodyColor", to.bodyColor)
    if (to.hat != from.hat) put("hat", to.hat)
    if (to.neck != from.neck) put("neck", to.neck)
}

enum class MemoryKind { Fact, Summary }

data class MemoryItem(val id: String, val kind: MemoryKind, val text: String, val createdAt: String)

data class MemoryPage(val items: List<MemoryItem>, val nextCursor: String?)

data class ConsentRecord(val type: ConsentType, val version: String, val granted: Boolean, val at: String)

enum class PairingPhase { None, Pending, Claimed, Expired }

/** GET /v1/pairings/current. [expiresAt] / [pairedAt] are ISO-8601 strings from the server. */
data class PairingStatus(
    val phase: PairingPhase,
    val pairCode: String? = null,
    val expiresAt: String? = null,
    val pairedAt: String? = null,
)

data class PairingInfo(val pairCode: String, val expiresAt: String)

/** GET /v1/friends item. [shareArrival] = tell this friend when the car arrives (default off). */
data class Friend(val id: String, val name: String, val since: String, val shareArrival: Boolean)

/** POST /v1/friends/invites: a 6-character code (24 h) and the link the QR carries. */
data class FriendInvite(val code: String, val url: String, val expiresAt: String)

/**
 * What the car reads from itself and tells the server in `ctx` frames (contract.md). Null = signal not available.
 * Coordinates are never part of it.
 */
data class VehicleSignals(
    val gear: String? = null,
    val speedKmh: Int? = null,
    val wiper: String? = null,
    val doorOpen: Boolean? = null,
    val rangeKm: Int? = null,
    val fuelType: String? = null,
    val headlight: Boolean? = null,
    val naviGuiding: Boolean = false,
    /** Remaining distance to the navigation destination in whole km; null without a destination. Never a name or coordinates. */
    val destKm: Int? = null,
    /** EV charge switch state; null when the car has no such signal. */
    val charging: Boolean? = null,
    /** The navigation destination's name the user set (never coordinates); null without a destination. */
    val destinationName: String? = null,
    /** True once navigation reported the destination reached (until the next route starts). */
    val arrived: Boolean = false,
)
