package ai.hey.session

import ai.hey.model.CarSettings
import ai.hey.model.CarView
import ai.hey.model.DropoffCandidate
import ai.hey.model.DrivingState
import ai.hey.model.ErrorCode

enum class ConnectionStatus {
    /** First launch: asking the relay for a car code. */
    Registering,

    /** The relay did not answer the registration; retrying with backoff. */
    RegistrationFailed,
    Connecting,
    Online,
    Reconnecting,

    /** The relay rejected the stored token (its data was wiped or the car was removed): the QR no longer works. */
    Rejected,
}

/** Which panel the driver opened on top of the trip screen. */
enum class Panel { None, Dropoff, Settings }

/** What the driver picked on D4. Valid only for the proposal version it was made against. */
data class DropoffDraft(
    val basedOnVersion: Int = 0,
    val candidateId: String? = null,
    val reasonIndex: Int? = null,
)

/** Entrance candidates for the current destination (GET /v1/places/{id}/entrances). */
data class Candidates(
    val forPlaceId: String? = null,
    val items: List<DropoffCandidate> = emptyList(),
    val loading: Boolean = false,
    val failed: Boolean = false,
)

/**
 * The only thing screens read. A value type: the same content compares equal, so a refresh that changes nothing
 * does not recompose. Server facts live in [view]; everything else is the car's own state or a UI-only flag.
 */
data class CarUiState(
    val connection: ConnectionStatus = ConnectionStatus.Registering,
    /** Last snapshot from the relay; null until the first one arrives. */
    val view: CarView? = null,
    val driving: DrivingState = DrivingState.DRIVING,
    /** URL printed in the QR (from registration). */
    val qrUrl: String? = null,
    val settings: CarSettings = CarSettings(),

    // ---- UI-only flags
    val panel: Panel = Panel.None,
    /** D6 was closed by the driver while this many items were actionable; it reopens when more arrive. */
    val requestsDismissedAt: Int = 0,
    val endDialogOpen: Boolean = false,
    val routeStarting: Boolean = false,
    val routeFailed: Boolean = false,
    /** Commands sent and not answered by a snapshot yet (buttons show loading). */
    val busy: Set<String> = emptySet(),
    val error: ErrorCode? = null,
    /** Conditions that changed in the latest card update (shown highlighted on D3). */
    val changedConditions: Set<ai.hey.model.ConditionId> = emptySet(),
    val lastSpokenKo: String? = null,
    val draft: DropoffDraft = DropoffDraft(),
    val candidates: Candidates = Candidates(),
    /** Epoch ms of the last trip.progress seen on the car, for the "출발할 때" caption on D5. */
    val progressAt: Long? = null,
) {
    val online: Boolean get() = connection == ConnectionStatus.Online

    val parked: Boolean get() = driving == DrivingState.PARKED

    /** Whether a command of this kind (by key prefix) is in flight. */
    fun isBusy(prefix: String): Boolean = busy.any { it == prefix || it.startsWith("$prefix:") }

    /** True while a button with this exact key is loading. */
    fun isLoading(key: String): Boolean = key in busy

    /** The draft, or an empty one when a newer proposal made it obsolete. */
    val activeDraft: DropoffDraft
        get() = if (draft.basedOnVersion == (view?.dropoff?.version ?: 0)) draft else DropoffDraft(view?.dropoff?.version ?: 0)
}

/**
 * Busy keys. A key stays in [CarUiState.busy] until a snapshot shows the effect of its command or the relay refuses
 * it; an unrelated snapshot never releases a button. Keys carry what they are waiting for (see TripRepository.settled).
 */
object Busy {
    const val JOIN = "join"
    const val RECHECK = "recheck"
    const val PROPOSE = "propose"
    const val END = "end"
    const val ARRIVE = "arrive"
    const val REPLY = "reply"
    fun join(joinRequestId: String) = "join:$joinRequestId"
    fun propose(previousVersion: Int) = "propose:$previousVersion"
    fun pin(version: Int) = "pin:$version"
    fun reply(requestId: String?, replyId: String, repliesBefore: Int) = "reply:${requestId.orEmpty()}:$replyId:$repliesBefore"
}
