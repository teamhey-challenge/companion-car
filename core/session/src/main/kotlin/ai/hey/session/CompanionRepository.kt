package ai.hey.session

import ai.hey.model.CompanionSettings
import ai.hey.model.Friend
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

/** Delays before retry 1 and 2 of an idempotent consent call. */
private val CONSENT_RETRY_DELAYS_MS = longArrayOf(1_000, 2_000)

/**
 * Consent POSTs upsert per (install, type) on the server and DELETE re-withdraws the same row, so repeating them is safe.
 * Retries after an [IOException] or HTTP 5xx; a 4xx is final.
 */
private suspend fun <T> withRetry(block: suspend () -> T): T {
    var attempt = 0
    while (true) {
        try {
            return block()
        } catch (e: IOException) {
            val permanent = e is HttpFailure && e.code < 500
            if (permanent || attempt >= CONSENT_RETRY_DELAYS_MS.size) throw e
            delay(CONSENT_RETRY_DELAYS_MS[attempt++])
        }
    }
}

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

/** C7 account actions that failed and are shown again with a retry (PRD 4.7: a privacy action never fails silently). */
enum class AccountFailure { None, Withdraw, Unpair }

/** The C7 account request that is running; its dialog shows work and blocks both buttons until it ends (Pleos: >2 s needs a working state). */
enum class SettingsAction { None, Unpairing, Withdrawing }

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

/** C7 "내 친구 코드": the invite the car shows (QR + 6-character code), 24 h valid. */
sealed interface InviteUi {
    data object None : InviteUi
    data object Creating : InviteUi
    data class Shown(val code: String, val url: String, val expiresAt: String) : InviteUi
    data object Failed : InviteUi
}

/** C7 friend section: read-only list plus the per-friend 도착 알리기 switch. [saving] = ids whose PATCH is in flight. */
data class FriendsState(
    val items: List<Friend> = emptyList(),
    val loaded: Boolean = false,
    val failed: Boolean = false,
    val saving: Set<String> = emptySet(),
    val invite: InviteUi = InviteUi.None,
)

/** Everything C1/C2/C6/C7 read, besides [CarUiState]. [paired] = the phone claimed the code. */
data class CompanionState(
    val onboarding: OnboardingUi = OnboardingUi(),
    val pairing: PairingUi = PairingUi.None,
    val memory: MemoryState = MemoryState(),
    val settings: CompanionSettings = CompanionSettings(),
    val settingsSave: SettingsSave = SettingsSave.Idle,
    /** The last "녹음 동의 철회" / "폰 연결 해제" request failed; cleared when the next one starts or succeeds. */
    val accountFailure: AccountFailure = AccountFailure.None,
    val settingsAction: SettingsAction = SettingsAction.None,
    val friends: FriendsState = FriendsState(),
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

    /** C7 opened: reads the friend list. */
    fun loadFriends()

    /** C7 "내 친구 코드": asks the server for a new invite (parked screen only). */
    fun createInvite()
    fun closeInvite()

    /** C7 per-friend "도착 알리기". */
    fun setShareArrival(id: String, share: Boolean)
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

    /**
     * A `pairing` frame came on the socket: C7 and C2 follow at once instead of waiting for the next poll/refresh.
     * Claimed -> shown as connected; anything else while connected -> not connected. A pending code that C2 is showing
     * is left to its own poll (the frame cannot tell the code just created from the one that was removed).
     */
    fun onServerPairing(phase: PairingPhase) {
        _state.update {
            when {
                phase == PairingPhase.Claimed -> it.copy(pairing = PairingUi.Claimed)
                it.pairing == PairingUi.Claimed -> it.copy(pairing = PairingUi.None)
                else -> it
            }
        }
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

    // ---------------------------------------------------------------- friends

    override fun loadFriends() {
        scope.launch {
            try {
                val list = api.friends()
                _state.update { it.copy(friends = it.friends.copy(items = list, loaded = true, failed = false)) }
            } catch (e: IOException) {
                log("friends", e)
                _state.update { it.copy(friends = it.friends.copy(failed = true)) }
            }
        }
    }

    override fun createInvite() {
        if (_state.value.friends.invite == InviteUi.Creating) return
        _state.update { it.copy(friends = it.friends.copy(invite = InviteUi.Creating)) }
        scope.launch {
            try {
                val i = api.createFriendInvite()
                _state.update { it.copy(friends = it.friends.copy(invite = InviteUi.Shown(i.code, i.url, i.expiresAt))) }
            } catch (e: IOException) {
                log("invite", e)
                _state.update { it.copy(friends = it.friends.copy(invite = InviteUi.Failed)) }
            }
        }
    }

    override fun closeInvite() = _state.update { it.copy(friends = it.friends.copy(invite = InviteUi.None)) }

    override fun setShareArrival(id: String, share: Boolean) {
        if (id in _state.value.friends.saving) return
        _state.update { it.copy(friends = it.friends.copy(saving = it.friends.saving + id)) }
        scope.launch {
            try {
                api.setShareArrival(id, share)
                _state.update { s ->
                    s.copy(friends = s.friends.copy(items = s.friends.items.map { f -> if (f.id == id) f.copy(shareArrival = share) else f }, saving = s.friends.saving - id))
                }
            } catch (e: IOException) {
                log("share arrival", e)
                _state.update { it.copy(friends = it.friends.copy(saving = it.friends.saving - id, failed = true)) }
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
                for (t in required) withRetry { api.postConsent(t, CONSENT_VERSION, true) }
                withRetry { api.postConsent(ConsentType.Memory, CONSENT_VERSION, memory) }
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
        if (_state.value.settingsAction != SettingsAction.None) return
        _state.update { it.copy(accountFailure = AccountFailure.None, settingsAction = SettingsAction.Withdrawing) }
        scope.launch {
            try {
                withRetry { api.withdrawConsent(ConsentType.Record) }
                storage.setConsent(ConsentType.Record, false)
                storage.setConsent(ConsentType.Memory, false)
                nextCursor = null
                _state.update { it.copy(memory = MemoryState(loaded = true), onboarding = OnboardingUi(), settingsAction = SettingsAction.None) }
            } catch (e: IOException) {
                log("withdraw", e)
                _state.update { it.copy(accountFailure = AccountFailure.Withdraw, settingsAction = SettingsAction.None) }
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
        if (_state.value.settingsAction != SettingsAction.None) return
        poll?.cancel()
        _state.update { it.copy(accountFailure = AccountFailure.None, settingsAction = SettingsAction.Unpairing) }
        scope.launch {
            try {
                api.deletePairing()
                _state.update { it.copy(pairing = PairingUi.None, settingsAction = SettingsAction.None) }
            } catch (e: IOException) {
                log("unpair", e)
                _state.update { it.copy(accountFailure = AccountFailure.Unpair, settingsAction = SettingsAction.None) }
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
        // The server stores and recalls memory only while the memory consent is granted (PLEOS-129): turning it on
        // after C1 left it off must grant it first.
        val needsMemoryConsent = after.memoryEnabled && !before.memoryEnabled && ConsentType.Memory !in storage.consents.value.granted
        // The screen shows the new value at once; a failed write puts the old one back and says so.
        _state.update { it.copy(settings = after, settingsSave = SettingsSave.Saving) }
        scope.launch {
            try {
                if (needsMemoryConsent) {
                    api.postConsent(ConsentType.Memory, CONSENT_VERSION, true)
                    storage.setConsent(ConsentType.Memory, true)
                }
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
