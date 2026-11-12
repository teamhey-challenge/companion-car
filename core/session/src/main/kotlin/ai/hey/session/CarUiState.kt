package ai.hey.session

import ai.hey.model.CarSettings
import ai.hey.model.CarView
import ai.hey.model.DropoffCandidate
import ai.hey.model.ErrorCode
import ai.hey.model.QuickReplyId
import ai.hey.vehicle.DrivingStatus
import ai.hey.vehicle.RouteStartState
import ai.hey.vehicle.SpeechDropReason

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

    /** The relay speaks another protocol major than this app (HTTP 426). Retrying cannot help: the app needs an update. */
    ProtocolMismatch,
}

/** Which panel the driver opened on top of the trip screen. [Reply] is D6 opened by the driver to speak first (no open question needed). */
enum class Panel { None, Dropoff, Settings, Reply }

/**
 * The driver's voice reply (D6), parked only. Listening -> Confirm(text) -> sent; nothing is sent before the driver
 * confirms the recognized sentence. [Idle] is also what a moving car gets: the microphone closes.
 */
sealed interface VoiceState {
    data object Idle : VoiceState
    /** RECORD_AUDIO is not granted: the activity asks for it, the answer arrives through voicePermissionResult. */
    data object NeedsPermission : VoiceState
    data object Listening : VoiceState
    /** The confirmed sentence reached the relay (the snapshot shows the new reply). */
    data object Sent : VoiceState
    /** The recognized sentence waits for the driver; [failure] is why the last send was refused (e.g. translation down), shown inside the dialog. */
    data class Confirm(val textKo: String, val failure: ai.hey.model.ErrorCode? = null) : VoiceState
    /** Nothing recognized (or the permission was refused / the recognizer failed); the driver can try again. */
    data class Failed(val reason: Reason) : VoiceState
    enum class Reason { NoSpeech, PermissionDenied, Unavailable }
}

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
)

/** One command a button can start. Typed, so a typo cannot create a key that never clears. */
sealed interface CommandKey {
    data class Join(val joinRequestId: String) : CommandKey
    data object Recheck : CommandKey
    data class Propose(val previousVersion: Int) : CommandKey
    /** [newestReplyBefore]: the newest reply when the tap happened; a different newest reply is the effect (the relay caps the list, so a count would stall). [requestId] is null for a driver-first reply. */
    data class Reply(val requestId: String?, val replyId: QuickReplyId, val newestReplyBefore: ai.hey.model.ReplyItem?) : CommandKey
    /** The confirmed voice sentence; settled like a reply (the newest reply changes). */
    data class Voice(val newestReplyBefore: ai.hey.model.ReplyItem?) : CommandKey
    data object End : CommandKey
    data object Arrive : CommandKey
}

/**
 * One command's lifecycle. [Idle] is the absence of an entry in [CarUiState.commands]; the map holds only
 * [InFlight] and [Failed]. Three rules clear an entry: a snapshot shows the effect, the relay refuses it with the
 * envelope's ref (then [Failed] until the error banner expires), or the safety timeout fires.
 */
sealed interface CommandState {
    data object Idle : CommandState
    data class InFlight(val envelopeId: String) : CommandState
    data class Failed(val code: String) : CommandState
}

/** A vehicle problem worth showing on D1, with a retry. */
sealed interface VehicleIssue {
    data class Navi(val code: String) : VehicleIssue
    data class Speech(val reason: SpeechDropReason) : VehicleIssue
}

/**
 * The only thing screens read. A value type: the same content compares equal, so a refresh that changes nothing
 * does not recompose. Server facts live in [view]; everything else is the car's own state or a UI-only flag.
 */
data class CarUiState(
    val connection: ConnectionStatus = ConnectionStatus.Registering,
    /** Last snapshot from the relay; null until the first one arrives. */
    val view: CarView? = null,
    /** The car's own motion. [DrivingStatus.Unknown] fails closed and is treated as driving. */
    val driving: DrivingStatus = DrivingStatus.Unknown,
    /** URL printed in the QR (from registration). */
    val qrUrl: String? = null,
    val settings: CarSettings = CarSettings(),
    /** Outcome of the last settings write; [SaveResult.Failed] makes D8 say so (the value stays on screen). */
    val settingsSave: SaveResult = SaveResult.Saved,

    // ---- UI-only flags
    val panel: Panel = Panel.None,
    /** D6 was closed by the driver while this many items were actionable; it reopens when more arrive. */
    val requestsDismissedAt: Int = 0,
    /** D6: the driver-first quick replies ("기사님이 먼저") are unfolded. */
    val driverFirstOpen: Boolean = false,
    val voice: VoiceState = VoiceState.Idle,
    val endDialogOpen: Boolean = false,
    /** Every command that is in flight or recently failed, by typed key. */
    val commands: Map<CommandKey, CommandState> = emptyMap(),
    val error: ErrorCode? = null,
    /** Vehicle services that are unavailable right now (D1 banners). */
    val issues: Set<VehicleIssue> = emptySet(),
    /** Conditions that changed in the latest card update (shown highlighted on D3). */
    val changedConditions: Set<ai.hey.model.ConditionId> = emptySet(),
    val lastSpokenKo: String? = null,
    val draft: DropoffDraft = DropoffDraft(),
    val candidates: Candidates = Candidates(),
    /** Epoch ms of the last trip.progress seen on the car, for the "출발할 때" caption on D5. */
    val progressAt: Long? = null,
    /** Milliseconds this device's clock runs ahead of the relay's (from snapshot `ts`), so countdowns use the relay's deadlines. */
    val clockOffsetMs: Long = 0,
    /** The car's own route-start attempt (navigation preview confirm, failure); reset when the trip changes. */
    val route: RouteStartState = RouteStartState.Idle,
    /** The repository's hands-on commands; reachable only through [parked]. */
    internal val hands: ParkedCommands? = null,
) {
    val online: Boolean get() = connection == ConnectionStatus.Online

    /** The one state machine; screens read this, never the raw fields. */
    val phase: CarPhase get() = phaseOf(view, driving, LocalUi(panel, requestsDismissedAt, route))

    val isParked: Boolean get() = phase is CarPhase.Parked

    /**
     * Hands-on commands, or null unless the car is parked. The repository creates them; no other code can, so a driving
     * screen has nothing to call. Which of them the relay accepts right now is [CarPhase.Parked.enabled].
     */
    val parked: ParkedCommands? get() = if (isParked) hands else null

    fun commandState(key: CommandKey): CommandState = commands[key] ?: CommandState.Idle

    fun isLoading(key: CommandKey): Boolean = commandState(key) is CommandState.InFlight

    val routeFailed: Boolean get() = route is RouteStartState.Failed
    val routeFailureCode: String? get() = (route as? RouteStartState.Failed)?.code

    val joinInFlight: Boolean get() = commands.any { (key, state) -> key is CommandKey.Join && state is CommandState.InFlight }
    val proposeInFlight: Boolean get() = commands.any { (key, state) -> key is CommandKey.Propose && state is CommandState.InFlight }
    val voiceInFlight: Boolean get() = commands.any { (key, state) -> key is CommandKey.Voice && state is CommandState.InFlight }

    /** The reply that is being sent, for the quick-reply grid's loading state. */
    val replyInFlight: CommandKey.Reply?
        get() = commands.entries
            .firstOrNull { (key, state) -> key is CommandKey.Reply && state is CommandState.InFlight }
            ?.key as? CommandKey.Reply

    /** The draft, or an empty one when a newer proposal made it obsolete. */
    val activeDraft: DropoffDraft
        get() = if (draft.basedOnVersion == (view?.dropoff?.version ?: 0)) draft else DropoffDraft(view?.dropoff?.version ?: 0)
}
