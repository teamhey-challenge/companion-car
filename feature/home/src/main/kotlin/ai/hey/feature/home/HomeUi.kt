package ai.hey.feature.home

import ai.hey.designsystem.component.CharacterLook
import ai.hey.session.CarFault
import ai.hey.session.CarUiState
import ai.hey.session.TurnSource
import ai.hey.session.TurnState

/** What the character is doing, as the screens (and the Rive `state` property) see it. */
enum class HomePhase { Idle, Listening, Thinking, Speaking }

/** Everything C3/C4/C5/C8 draw. Pure value; built from the session state by [homeUi]. */
data class HomeUi(
    val phase: HomePhase = HomePhase.Idle,
    /** The one persistent problem (session priority: microphone > connection > sound); null = nothing is wrong. */
    val fault: CarFault? = null,
    /** True while the server link is up; the talk button is not pressable before (AC-4.3-03). */
    val online: Boolean = false,
    /** What the driver said this turn (C4 `txt-talk-user`). */
    val heard: String? = null,
    /** The character's text so far (C4 caption; never shown while driving, D-25). */
    val reply: String = "",
    /** Mouth openness 0..3 from the playback loudness (D-13). */
    val mouth: Int = 0,
    val source: TurnSource = TurnSource.Ptt,
    /** What the character wears (D-41); C3/C4 pass it to Rive, C5/C8/fallback draw the matching still. */
    val look: CharacterLook = CharacterLook(),
    /** A friend's message shown as a subtitle: set only while parked (the session never fills it while driving). */
    val friendNote: String? = null,
) {
    /** C4 is C3 with a turn on it. */
    val inTalk: Boolean get() = phase != HomePhase.Idle || reply.isNotEmpty() || heard != null

    /**
     * The automatic listening that follows the character's own opening line (PLEOS-194 "선제 발화"): the driver said nothing in
     * this turn ([heard] is null) and the line is still [reply]. A listening after a driver turn is the follow-up instead.
     */
    val proactivePrompt: Boolean get() = phase == HomePhase.Listening && source == TurnSource.AutoListen && heard == null && reply.isNotEmpty()
}

fun homeUi(s: CarUiState): HomeUi {
    val phase = when (s.turn) {
        TurnState.Listening -> HomePhase.Listening
        TurnState.Thinking -> HomePhase.Thinking
        TurnState.Replying -> HomePhase.Speaking
        TurnState.Idle, TurnState.NeedsPermission -> HomePhase.Idle
    }
    return HomeUi(
        phase = phase,
        fault = s.fault,
        online = s.online,
        heard = s.heard,
        reply = s.reply,
        mouth = if (phase == HomePhase.Speaking) s.mouth else 0,
        source = s.source,
        look = CharacterLook(s.companion.settings.bodyColor, s.companion.settings.hat, s.companion.settings.neck),
        friendNote = s.friendNote?.takeIf { s.isParked }?.let { "${it.fromName}: ${it.text}" },
    )
}
