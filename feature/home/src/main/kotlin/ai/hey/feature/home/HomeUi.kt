package ai.hey.feature.home

import ai.hey.session.CarUiState
import ai.hey.session.ConnectionStatus
import ai.hey.session.TurnError
import ai.hey.session.TurnState

/** What the character is doing, as the screens (and the Rive `state` property) see it. */
enum class HomePhase { Idle, Listening, Thinking, Speaking }

/** The one thing that keeps a talk from working. Declaration order is the C8 priority (PRD 4.8.5): microphone > connection > sound. */
enum class HomeFault { Microphone, Offline, Connecting, Server, Recognition, Audio }

/** Everything C3/C4/C5/C8 draw. Pure value; built from the session state by [homeUi]. */
data class HomeUi(
    val phase: HomePhase = HomePhase.Idle,
    /** Null = nothing is wrong. */
    val fault: HomeFault? = null,
    /** True while the server link is up; the talk button is not pressable before (AC-4.3-03). */
    val online: Boolean = false,
    /** What the driver said this turn (C4 `txt-talk-user`). */
    val heard: String? = null,
    /** The character's text so far (C4 caption; never shown while driving, D-25). */
    val reply: String = "",
    /** Mouth openness 0..3 from the playback loudness (D-13). */
    val mouth: Int = 0,
) {
    /** C4 is C3 with a turn on it. */
    val inTalk: Boolean get() = phase != HomePhase.Idle || reply.isNotEmpty() || heard != null
}

/** Highest-priority fault wins; one banner at a time. */
fun List<HomeFault>.topFault(): HomeFault? = minByOrNull { it.ordinal }

fun homeUi(s: CarUiState): HomeUi {
    val faults = buildList {
        if (s.turn is TurnState.NeedsPermission || s.error == TurnError.PermissionDenied) add(HomeFault.Microphone)
        when (s.connection) {
            ConnectionStatus.Online -> Unit
            ConnectionStatus.Connecting, ConnectionStatus.Reconnecting, ConnectionStatus.Registering -> add(HomeFault.Connecting)
            ConnectionStatus.RegistrationFailed, ConnectionStatus.Rejected -> add(HomeFault.Offline)
        }
        when (s.error) {
            TurnError.Server -> add(HomeFault.Server)
            TurnError.NoSpeech, TurnError.SpeechUnavailable -> add(HomeFault.Recognition)
            TurnError.ConnectionLost -> add(HomeFault.Offline)
            else -> Unit
        }
    }
    val phase = when (s.turn) {
        TurnState.Listening -> HomePhase.Listening
        TurnState.Thinking -> HomePhase.Thinking
        TurnState.Replying -> HomePhase.Speaking
        TurnState.Idle, TurnState.NeedsPermission -> HomePhase.Idle
    }
    return HomeUi(
        phase = phase,
        fault = faults.topFault(),
        online = s.online,
        heard = s.heard,
        reply = s.reply,
        mouth = if (phase == HomePhase.Speaking) s.mouth else 0,
    )
}
