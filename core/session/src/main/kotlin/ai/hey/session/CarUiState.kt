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

    /** The server refused this car (HTTP 403): retrying cannot help until it is fixed. */
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

    /** A friend's message or arrival (`friend_message` / `friend_arrived`): spoken at once, never opens the microphone, text only when parked. */
    Friend,

    /** onboarding: a spoken step (whale line, whale question, heyri's first greeting) and the listening for the answer to it. */
    Onboarding,
}

/**
 * The part of the first-run flow a screen cannot derive from the step: the text of the line being spoken (subtitle; it is shown
 * while driving too only as the screen's own concern, the driving screen never shows subtitles), whose voice it is, and what the
 * driver answered to the three questions (null = not answered; that polaroid and that part of the greeting are dropped).
 */
data class OnboardingView(
    val line: String = "",
    /** True while the whale speaks (the light ring around it), false for heyri. */
    val whale: Boolean = false,
    val answers: ai.hey.model.OnboardingAnswers = ai.hey.model.OnboardingAnswers(),
    /** The microphone was refused at a question: the screen says so with "마이크 허용" / "넘어가기" (TalkRepository.onboardingMicChoice). */
    val micBlocked: Boolean = false,
)

/** What the first-run runner is asked to do: which step, and whether the link is up. Internal to the repository. */
internal data class OnboardingRun(val step: ai.hey.model.OnboardingStep, val run: Boolean, val offline: Boolean)

/** The ids of the whale's short answer lines (`step: "ack"` after an accepted answer, `step: "skip"` after an unusable one). */
internal val ANSWER_LINE_STEPS = setOf("ack", "skip")
/** A friend's message as shown on C3 while parked (subtitle); never set while driving. */
data class FriendNote(val fromName: String, val text: String)

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
    val source: TurnSource = TurnSource.Ptt,
    /** The last server or local voice output failed (focus refused, speaker dropped the text); cleared by the next turn. */
    val audioFailed: Boolean = false,
    val panel: Panel = Panel.None,
    val turn: TurnState = TurnState.Idle,
    /** What the driver said in this turn (the final Pleos STT sentence). */
    val heard: String? = null,
    /** The reply text of this turn, growing while it streams. */
    val reply: String = "",
    /** The friend message on screen (parked only, cleared a few seconds after it was read or when the car moves). */
    val friendNote: FriendNote? = null,
    /** `friend_more`: more friend messages wait in the inbox (set while driving, shown parked until the friends list is opened). */
    val friendMore: String? = null,
    /** Trip end (`trip_done`): the character's one-line farewell, parked only; cleared after [TRIP_NOTE_MILLIS] or when the car moves. */
    val tripNote: String? = null,
    /** Where the first-run flow is; [OnboardingStep.Done] when it is over or was never started. */
    val onboardingStep: ai.hey.model.OnboardingStep = ai.hey.model.OnboardingStep.Done,
    /** What the first-run screens draw besides the step: the spoken line, whose voice, the answers. */
    val onboarding: OnboardingView = OnboardingView(),
    /** §6.1: the first-run phone link was skipped; home shows "폰 연결하면 일기를 볼 수 있어" once ([TalkRepository.phoneHintShown]). */
    val phoneHint: Boolean = false,
    val error: TurnError? = null,
    /** Mouth openness 0..3 of the character while a voice plays. */
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
