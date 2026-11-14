package ai.hey.session

import ai.hey.model.CompanionSettings
import ai.hey.model.ConsentType
import ai.hey.model.HeyLog
import ai.hey.model.MemoryItem
import ai.hey.model.PairingPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException

private const val TAG = "Companion"

/** C2 phone connection. [Failed] = the code could not be made (AC-4.2-05) or the status could not be read. */
sealed interface PairingUi {
    /** C2 is not open or nothing is connected. */
    data object None : PairingUi
    data object Creating : PairingUi
    data class Pending(val pairCode: String, val expiresAt: String) : PairingUi

    /** The phone claimed the code (C2 shows "연결됐어요!"; C7 shows the unpair row). */
    data object Claimed : PairingUi
    data object Expired : PairingUi
    data object Failed : PairingUi
}

/** C1 "동의하고 시작". */
data class OnboardingUi(
    /** The consents are being recorded on the server (`txt-onboard-saving`). */
    val saving: Boolean = false,
    /** The last attempt failed (network, server): the checkboxes keep their state, a new press retries. */
    val failed: Boolean = false,
)

enum class SettingsSave { Idle, Saving, Failed }

/** C6. [items] arrive page by page; a delete is applied when the server said 204. */
data class MemoryState(
    val items: List<MemoryItem> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val failed: Boolean = false,
    /** Ids whose delete is in flight (row shows busy). */
    val deleting: Set<String> = emptySet(),
    val deleteFailed: Boolean = false,
    /** The first page has been read (so an empty list means "no memory", not "not read yet"). */
    val loaded: Boolean = false,
)

/** Everything C1/C2/C6/C7 read, besides [CarUiState]. [paired] = the phone claimed the code. */
data class CompanionState(
    val onboarding: OnboardingUi = OnboardingUi(),
    val pairing: PairingUi = PairingUi.None,
    val memory: MemoryState = MemoryState(),
    val settings: CompanionSettings = CompanionSettings(),
    val settingsSave: SettingsSave = SettingsSave.Idle,
) {
    val paired: Boolean get() = pairing == PairingUi.Claimed
}

/** The screens' view model-facing API for consents, pairing, memory and server settings. Safe in any driving phase except where the screen gates it. */
interface CompanionCommands {
    /** C1: records `terms`, `privacy`, `record`, `external_ai` (required) and `memory` per [memory]; on success the local mirror flips and [CarUiState.needsOnboarding] turns false. */
    fun submitConsent(memory: Boolean)

    /** C7 "녹음 동의 철회": withdraws `record` (server deletes memory); local mirror and memory list are cleared. */
    fun withdrawRecordConsent()

    /** C2 opened: creates a code and polls every 2 s until claimed/expired/closed. */
    fun openPairing()
    fun closePairing()

    /** C7 "해제": phone token off, memory kept. */
    fun unpair()

    /** C7 opened: reads the pairing status once (is a phone connected?) and the server settings. */
    fun refresh()

    fun loadMemory()
    fun loadMoreMemory()
    fun deleteMemory(id: String)
    fun deleteAllMemory()

    fun updateSettings(transform: (CompanionSettings) -> CompanionSettings)
}

/**
 * Owns the REST side of the companion (consents, pairing, memory, server settings). [online] says when the server can
 * be asked: calls made before are not queued but the state is refreshed on connect.
 */
class CompanionRepository(
    private val scope: CoroutineScope,
    private val api: CompanionApi,
    private val storage: CarStorage,
    private val pollMillis: Long = POLL_MILLIS,
    private val pageSize: Int = PAGE_SIZE,
) : CompanionCommands {
    private val _state = MutableStateFlow(CompanionState())
    val state: StateFlow<CompanionState> = _state.asStateFlow()

    private var poll: Job? = null
    private var nextCursor: String? = null

    /** Called by the talk repository when the socket is up or a `settings` frame came: adopt the server's value. */
    fun onServerSettings(settings: CompanionSettings) {
        _state.update { it.copy(settings = settings) }
    }

    /** Socket came online: read what the server knows (consents, settings, pairing), once. */
    fun onOnline() = refresh()

    /** The server is the source of truth for consents; the local mirror is only a cache for the start-up screen choice. */
    private suspend fun syncConsents() {
        val records = runCatching { api.consents() }.onFailure { log("consents", it) }.getOrNull() ?: return
        val granted = records.filter { it.granted }.map { it.type }.toSet()
        for (t in ConsentType.entries) {
            if ((t in granted) != (t in storage.consents.value.granted)) storage.setConsent(t, t in granted)
        }
    }

    override fun refresh() {
        scope.launch {
            syncConsents()
            runCatching { api.settings() }.onSuccess { s -> _state.update { it.copy(settings = s) } }.onFailure { log("settings", it) }
            if (poll?.isActive != true) {
                runCatching { api.currentPairing() }.onSuccess { p ->
                    _state.update { it.copy(pairing = if (p.phase == PairingPhase.Claimed) PairingUi.Claimed else if (it.pairing == PairingUi.Claimed) PairingUi.None else it.pairing) }
                }.onFailure { log("pairing", it) }
            }
        }
    }

    // ---------------------------------------------------------------- consent

    override fun submitConsent(memory: Boolean) {
        if (_state.value.onboarding.saving) return
        _state.update { it.copy(onboarding = OnboardingUi(saving = true)) }
        scope.launch {
            try {
                val required = listOf(ConsentType.Terms, ConsentType.Privacy, ConsentType.Record, ConsentType.ExternalAi)
                for (t in required) api.postConsent(t, CONSENT_VERSION, true)
                api.postConsent(ConsentType.Memory, CONSENT_VERSION, memory)
                for (t in required) storage.setConsent(t, true)
                storage.setConsent(ConsentType.Memory, memory)
                // AC-4.1-06: "기억 저장" off here is the same value as C7.
                val next = _state.value.settings.copy(memoryEnabled = memory)
                _state.update { it.copy(onboarding = OnboardingUi(), settings = next) }
                runCatching { api.patchSettings(CompanionSettings(memoryEnabled = !memory), next) }.onFailure { log("settings", it) }
            } catch (e: IOException) {
                log("consent", e)
                _state.update { it.copy(onboarding = OnboardingUi(failed = true)) }
            }
        }
    }

    override fun withdrawRecordConsent() {
        scope.launch {
            try {
                api.withdrawConsent(ConsentType.Record)
                storage.setConsent(ConsentType.Record, false)
                storage.setConsent(ConsentType.Memory, false)
                nextCursor = null
                _state.update { it.copy(memory = MemoryState(loaded = true), onboarding = OnboardingUi()) }
            } catch (e: IOException) {
                log("withdraw", e)
                _state.update { it.copy(onboarding = OnboardingUi(failed = true)) }
            }
        }
    }

    // ---------------------------------------------------------------- pairing

    override fun openPairing() {
        poll?.cancel()
        _state.update { it.copy(pairing = PairingUi.Creating) }
        poll = scope.launch {
            val info = try {
                api.createPairing()
            } catch (e: IOException) {
                log("createPairing", e)
                _state.update { it.copy(pairing = PairingUi.Failed) }
                return@launch
            }
            _state.update { it.copy(pairing = PairingUi.Pending(info.pairCode, info.expiresAt)) }
            while (isActive) {
                delay(pollMillis)
                val status = try {
                    api.currentPairing()
                } catch (e: IOException) {
                    log("pairing poll", e) // keep showing the code; the next tick retries
                    continue
                }
                when (status.phase) {
                    PairingPhase.Claimed -> return@launch _state.update { it.copy(pairing = PairingUi.Claimed) }
                    PairingPhase.Expired, PairingPhase.None -> return@launch _state.update { it.copy(pairing = PairingUi.Expired) }
                    PairingPhase.Pending -> Unit
                }
            }
        }
    }

    override fun closePairing() {
        poll?.cancel()
        poll = null
        _state.update { if (it.pairing is PairingUi.Claimed) it else it.copy(pairing = PairingUi.None) }
    }

    override fun unpair() {
        poll?.cancel()
        scope.launch {
            try {
                api.deletePairing()
                _state.update { it.copy(pairing = PairingUi.None) }
            } catch (e: IOException) {
                log("unpair", e)
            }
        }
    }

    // ---------------------------------------------------------------- memory

    override fun loadMemory() {
        nextCursor = null
        _state.update { it.copy(memory = MemoryState(loading = true)) }
        scope.launch {
            try {
                val page = api.memory(null, pageSize)
                nextCursor = page.nextCursor
                _state.update { it.copy(memory = MemoryState(items = page.items, hasMore = page.nextCursor != null, loaded = true)) }
            } catch (e: IOException) {
                log("memory", e)
                _state.update { it.copy(memory = MemoryState(failed = true)) }
            }
        }
    }

    override fun loadMoreMemory() {
        val cursor = nextCursor ?: return
        val m = _state.value.memory
        if (m.loadingMore || m.loading) return
        _state.update { it.copy(memory = m.copy(loadingMore = true, failed = false)) }
        scope.launch {
            try {
                val page = api.memory(cursor, pageSize)
                nextCursor = page.nextCursor
                _state.update { s -> s.copy(memory = s.memory.copy(items = s.memory.items + page.items.filter { n -> s.memory.items.none { it.id == n.id } }, hasMore = page.nextCursor != null, loadingMore = false)) }
            } catch (e: IOException) {
                log("memory more", e)
                _state.update { it.copy(memory = it.memory.copy(loadingMore = false, failed = true)) }
            }
        }
    }

    override fun deleteMemory(id: String) {
        if (id in _state.value.memory.deleting) return
        _state.update { it.copy(memory = it.memory.copy(deleting = it.memory.deleting + id, deleteFailed = false)) }
        scope.launch {
            try {
                api.deleteMemory(id)
                _state.update { it.copy(memory = it.memory.copy(items = it.memory.items.filterNot { m -> m.id == id }, deleting = it.memory.deleting - id)) }
            } catch (e: IOException) {
                log("delete memory", e)
                _state.update { it.copy(memory = it.memory.copy(deleting = it.memory.deleting - id, deleteFailed = true)) }
            }
        }
    }

    override fun deleteAllMemory() {
        _state.update { it.copy(memory = it.memory.copy(deleteFailed = false, loading = true)) }
        scope.launch {
            try {
                api.deleteAllMemory()
                nextCursor = null
                _state.update { it.copy(memory = MemoryState(loaded = true)) }
            } catch (e: IOException) {
                log("delete all memory", e)
                _state.update { it.copy(memory = it.memory.copy(loading = false, deleteFailed = true)) }
            }
        }
    }

    // ---------------------------------------------------------------- settings

    override fun updateSettings(transform: (CompanionSettings) -> CompanionSettings) {
        val before = _state.value.settings
        val after = transform(before)
        if (after == before) return
        // The screen shows the new value at once; a failed write puts the old one back and says so.
        _state.update { it.copy(settings = after, settingsSave = SettingsSave.Saving) }
        scope.launch {
            try {
                val saved = api.patchSettings(before, after)
                _state.update { it.copy(settings = saved, settingsSave = SettingsSave.Idle) }
            } catch (e: IOException) {
                log("settings save", e)
                _state.update { it.copy(settings = before, settingsSave = SettingsSave.Failed) }
            }
        }
    }

    private fun log(what: String, e: Throwable) =
        HeyLog.w(TAG, "$what failed: ${e.javaClass.simpleName}${(e as? HttpFailure)?.let { " HTTP ${it.code}" } ?: ""}")

    companion object {
        const val POLL_MILLIS = 2_000L
        const val PAGE_SIZE = 20
    }
}
