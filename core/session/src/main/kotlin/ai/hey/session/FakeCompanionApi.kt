package ai.hey.session

import ai.hey.model.CompanionSettings
import ai.hey.model.Friend
import ai.hey.model.FriendInvite
import ai.hey.model.ReceivedMessage
import ai.hey.model.ConsentRecord
import ai.hey.model.ConsentType
import ai.hey.model.MemoryItem
import ai.hey.model.MemoryKind
import ai.hey.model.MemoryPage
import ai.hey.model.PairingInfo
import ai.hey.model.PairingPhase
import ai.hey.model.PairingStatus
import ai.hey.model.VehicleSignals
import java.io.IOException
import kotlinx.coroutines.delay

/**
 * In-memory server for screen previews and tests (production never uses it). Set [failing] to make every call throw an
 * [IOException]; set [pairing] to what the phone side did.
 */
class FakeCompanionApi(
    memory: List<MemoryItem> = (1..45).map { MemoryItem("m$it", if (it % 5 == 0) MemoryKind.Summary else MemoryKind.Fact, "기억 $it", "2026-11-14T0${it % 10}:00:00Z") },
) : CompanionApi {
    @Volatile var failing = false

    /** The next [failNext] calls throw [failWith] (a transient outage), then calls succeed again. */
    @Volatile var failNext = 0
    @Volatile var failWith: IOException = IOException("fake blip")
    var postAttempts = 0

    /** Makes withdraw and unpair take this long (tests of the "request is running" state). */
    @Volatile var slowMillis = 0L
    @Volatile var pairing: PairingStatus = PairingStatus(PairingPhase.None)
    @Volatile var settings: CompanionSettings = CompanionSettings()
    val consentLog = ArrayList<Triple<ConsentType, Boolean, String>>()
    val memoryItems = memory.toMutableList()
    var pairingsCreated = 0
    var pairingPolls = 0
    var unpaired = 0
    val vehicleEvents = ArrayList<VehicleSignals>()
    val friendList = mutableListOf(Friend("f1", "민수", "2026-11-14", false), Friend("f2", "지은", "2026-11-15", true))
    var invitesCreated = 0

    private fun check() {
        if (failing) throw IOException("fake network down")
        if (failNext > 0) { failNext--; throw failWith }
    }

    /** What the fake server has recorded, last write wins per type. */
    val serverConsents = LinkedHashMap<ConsentType, ConsentRecord>()

    override suspend fun postConsent(type: ConsentType, version: String, granted: Boolean): List<ConsentRecord> {
        postAttempts++
        check()
        consentLog += Triple(type, granted, version)
        val rec = ConsentRecord(type, version, granted, "2026-11-15T00:00:00Z")
        serverConsents[type] = rec
        return listOf(rec)
    }

    override suspend fun consents(): List<ConsentRecord> {
        check()
        return serverConsents.values.toList()
    }

    override suspend fun withdrawConsent(type: ConsentType) {
        if (slowMillis > 0) delay(slowMillis)
        check()
        consentLog += Triple(type, false, "withdraw")
        serverConsents[type] = ConsentRecord(type, "withdraw", false, "2026-11-15T00:00:00Z")
        if (type == ConsentType.Record || type == ConsentType.Memory) memoryItems.clear()
    }

    override suspend fun createPairing(): PairingInfo {
        check()
        pairingsCreated++
        val code = if (pairingsCreated == 1) "ABCD2345" else "ABCD23" + (40 + pairingsCreated % 60) // every new code replaces the old one
        pairing = PairingStatus(PairingPhase.Pending, code, "2026-11-15T00:05:00Z")
        return PairingInfo(code, "2026-11-15T00:05:00Z")
    }

    override suspend fun currentPairing(): PairingStatus {
        check()
        pairingPolls++
        return pairing
    }

    override suspend fun deletePairing() {
        if (slowMillis > 0) delay(slowMillis)
        check()
        unpaired++
        pairing = PairingStatus(PairingPhase.None)
    }

    override suspend fun memory(cursor: String?, limit: Int): MemoryPage {
        check()
        val from = cursor?.toInt() ?: 0
        val to = minOf(memoryItems.size, from + limit)
        return MemoryPage(memoryItems.subList(from, to).toList(), if (to < memoryItems.size) to.toString() else null)
    }

    override suspend fun deleteMemory(id: String) {
        check()
        memoryItems.removeAll { it.id == id }
    }

    override suspend fun deleteAllMemory() {
        check()
        memoryItems.clear()
    }

    override suspend fun settings(): CompanionSettings {
        check()
        return settings
    }

    override suspend fun patchSettings(from: CompanionSettings, to: CompanionSettings): CompanionSettings {
        check()
        settings = to
        return to
    }

    override suspend fun vehicleEvent(atEpochMs: Long, signals: VehicleSignals) {
        check()
        vehicleEvents += signals
    }

    override suspend fun createFriendInvite(): FriendInvite {
        check()
        invitesCreated++
        return FriendInvite("K7M2QX", "https://hey.lastmile.ltd/f/K7M2QX", "2026-11-16T00:00:00Z")
    }

    /** Codes the fake server knows: code -> the friend that owns it. */
    val inviteOwners = mutableMapOf("ABC234" to Friend("f9", "서연", "2026-11-15", false))

    override suspend fun acceptFriendInvite(code: String): Friend {
        check()
        val f = inviteOwners[code.uppercase()] ?: throw HttpFailure(404)
        if (friendList.any { it.id == f.id }) throw HttpFailure(409)
        friendList += f
        return f
    }

    override suspend fun friends(): List<Friend> {
        check()
        return friendList.toList()
    }

    val messageList = mutableListOf(
        ReceivedMessage("m1", "f1", "민수", "오늘 발표 잘할 거야. 끝나고 맛있는 거 먹자. 응원할게!", "2026-11-15T08:40:00Z"),
        ReceivedMessage("m2", "f2", "지은", "저녁에 보자", "2026-11-15T07:10:00Z"),
    )
    val reported = ArrayList<String>()

    override suspend fun messages(): List<ReceivedMessage> {
        check()
        return messageList.toList()
    }

    override suspend fun reportMessage(id: String) {
        check()
        reported += id
        messageList.removeAll { it.id == id }
    }
}
