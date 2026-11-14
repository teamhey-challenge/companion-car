package ai.hey.session

import ai.hey.model.CompanionSettings
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
    @Volatile var pairing: PairingStatus = PairingStatus(PairingPhase.None)
    @Volatile var settings: CompanionSettings = CompanionSettings()
    val consentLog = ArrayList<Triple<ConsentType, Boolean, String>>()
    val memoryItems = memory.toMutableList()
    var pairingsCreated = 0
    var pairingPolls = 0
    var unpaired = 0
    val vehicleEvents = ArrayList<VehicleSignals>()

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
        check()
        consentLog += Triple(type, false, "withdraw")
        serverConsents[type] = ConsentRecord(type, "withdraw", false, "2026-11-15T00:00:00Z")
        if (type == ConsentType.Record || type == ConsentType.Memory) memoryItems.clear()
    }

    override suspend fun createPairing(): PairingInfo {
        check()
        pairingsCreated++
        pairing = PairingStatus(PairingPhase.Pending, "ABCD2345", "2026-11-15T00:05:00Z")
        return PairingInfo("ABCD2345", "2026-11-15T00:05:00Z")
    }

    override suspend fun currentPairing(): PairingStatus {
        check()
        pairingPolls++
        return pairing
    }

    override suspend fun deletePairing() {
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
}
