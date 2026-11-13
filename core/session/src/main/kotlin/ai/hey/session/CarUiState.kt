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
    val panel: Panel = Panel.None,
    val turn: TurnState = TurnState.Idle,
    /** What the driver said in this turn (the final Pleos STT sentence). */
    val heard: String? = null,
    /** The reply text of this turn, growing while it streams. */
    val reply: String = "",
    val error: TurnError? = null,
    /** The repository's hands-on commands; reachable only through [parked]. */
    internal val hands: ParkedCommands? = null,
) {
    val online: Boolean get() = connection == ConnectionStatus.Online

    val isParked: Boolean get() = driving == DrivingStatus.Parked

    /** Hands-on commands, or null unless the car is parked. */
    val parked: ParkedCommands? get() = if (isParked) hands else null
}
