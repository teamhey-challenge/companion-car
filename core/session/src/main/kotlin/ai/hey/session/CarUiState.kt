package ai.hey.session

import ai.hey.model.CarSettings
import ai.hey.vehicle.DrivingStatus

enum class ConnectionStatus {
    /** Nothing tried yet. */
    Registering,

    /** Registering or asking for a session ticket failed (server or network down); retrying with backoff. */
    RegistrationFailed,
    Connecting,
    Online,
    Reconnecting,

    /** The server refused this car (no provision secret on the device, or a wrong one): retrying cannot help until it is fixed. */
    Rejected,
}

/** The one turn of the conversation that is on screen. */
sealed interface TurnState {
    data object Idle : TurnState

    /** RECORD_AUDIO is not granted: the activity asks for it, the answer arrives through voicePermissionResult. */
    data object NeedsPermission : TurnState

    /** The microphone is open (Pleos STT). */
    data object Listening : TurnState

    /** The sentence went to the server; waiting for the reply. */
    data object Thinking : TurnState

    /** The reply text is arriving (and, from stage 2 on, being played). */
    data object Replying : TurnState
}

enum class Panel { None, Settings }

/** What went wrong with the last turn, as a type the screen can word. */
enum class TurnError { NotConnected, NoSpeech, PermissionDenied, SpeechUnavailable, ConnectionLost, Server }

/** Who started the turn that is on screen. */
enum class TurnSource {
    /** The driver pressed the button. */
    Ptt,

    /** The character spoke first (`proactive` frame); the turn is its line. */
    Proactive,

    /** The microphone opened by itself for 5 s after a line that ended with a question mark (F4-F6). */
    AutoListen,
}

/** A persistent problem C8 shows (null = nothing wrong). The screen words it; while driving only a spoken notice is made. */
enum class CarFault { Offline, Connecting, Microphone, Server, Recognition, Audio }

/**
 * The only thing screens read. A value type: the same content compares equal, so a refresh that changes nothing does
 * not recompose.
 */
data class CarUiState(
    val connection: ConnectionStatus = ConnectionStatus.Registering,
    /** The car's own motion. [DrivingStatus.Unknown] fails closed and is treated as driving. */
    val driving: DrivingStatus = DrivingStatus.Unknown,
    val settings: CarSettings = CarSettings(),
    /** Outcome of the last settings write; [SaveResult.Failed] makes D8 say so (the value stays on screen). */
    val settingsSave: SaveResult = SaveResult.Saved,
    /** Consents, phone connection, memory and server settings (C1, C2, C6, C7). */
    val companion: CompanionState = CompanionState(),
    /** True until the required consent is recorded: the app shows /onboarding and starts no conversation. */
    val needsOnboarding: Boolean = true,
    /** Counts presses of the talk button refused for missing consent; the shell answers each with C1. */
    val onboardingRequests: Int = 0,
    val source: TurnSource = TurnSource.Ptt,
    /** The last server or local voice output failed (focus refused, speaker dropped the text); cleared by the next turn. */
    val audioFailed: Boolean = false,
    val panel: Panel = Panel.None,
    val turn: TurnState = TurnState.Idle,
    /** What the driver said in this turn (the final Pleos STT sentence). */
    val heard: String? = null,
    /** The reply text of this turn, growing while it streams. */
    val reply: String = "",
    val error: TurnError? = null,
    /** Mouth openness 0..3 of the character while a voice plays (D-13). */
    val mouth: Int = 0,
    /** The repository's hands-on commands; reachable only through [parked]. */
    internal val hands: ParkedCommands? = null,
) {
    val online: Boolean get() = connection == ConnectionStatus.Online

    val isParked: Boolean get() = driving == DrivingStatus.Parked

    /** Hands-on commands, or null unless the car is parked. */
    val parked: ParkedCommands? get() = if (isParked) hands else null

    /** The one thing C8 should say, most urgent first. */
    val fault: CarFault?
        get() = when {
            turn == TurnState.NeedsPermission || error == TurnError.PermissionDenied -> CarFault.Microphone
            connection == ConnectionStatus.Connecting || connection == ConnectionStatus.Reconnecting -> CarFault.Connecting
            !online -> CarFault.Offline
            error == TurnError.Server -> CarFault.Server
            error == TurnError.ConnectionLost || error == TurnError.NotConnected -> CarFault.Offline
            error == TurnError.NoSpeech || error == TurnError.SpeechUnavailable -> CarFault.Recognition
            audioFailed -> CarFault.Audio
            else -> null
        }
}
