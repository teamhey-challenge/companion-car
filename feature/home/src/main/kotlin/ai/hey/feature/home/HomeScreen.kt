package ai.hey.feature.home

import ai.hey.session.CarFault
import ai.hey.designsystem.component.CharacterSize
import ai.hey.designsystem.component.CharacterSlot
import ai.hey.designsystem.component.ErrorKind
import ai.hey.designsystem.component.ErrorPanel
import ai.hey.designsystem.component.HeaderBar
import ai.hey.designsystem.component.HeyButton
import ai.hey.designsystem.component.HeyButtonVariant
import ai.hey.designsystem.component.HeyConnection
import ai.hey.designsystem.component.HeyText
import ai.hey.designsystem.component.HeyTextAlign
import ai.hey.designsystem.component.OceanBackground
import ai.hey.designsystem.component.PageHeading
import ai.hey.designsystem.component.StatusBadge
import ai.hey.designsystem.component.StopListeningButton
import ai.hey.designsystem.component.Subtitle
import ai.hey.designsystem.component.TalkButton
import ai.hey.designsystem.component.TalkPhase
import ai.hey.designsystem.icon.HeyIcons
import ai.hey.designsystem.layout.HStack
import ai.hey.designsystem.layout.HeyAlign
import ai.hey.designsystem.layout.HeyArrange
import ai.hey.designsystem.layout.VStack
import ai.hey.designsystem.layout.fillSize
import ai.hey.designsystem.layout.inset
import ai.hey.designsystem.theme.HeyGap
import ai.hey.designsystem.theme.HeyTextVariant
import ai.hey.designsystem.theme.HeyTone
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

internal fun HomePhase.toTalk(): TalkPhase = when (this) {
    HomePhase.Idle -> TalkPhase.Idle
    HomePhase.Listening -> TalkPhase.Listening
    HomePhase.Thinking -> TalkPhase.Thinking
    HomePhase.Speaking -> TalkPhase.Speaking
}

internal fun CarFault.toKind(): ErrorKind = when (this) {
    CarFault.Microphone -> ErrorKind.Microphone
    CarFault.Offline -> ErrorKind.Offline
    CarFault.Connecting -> ErrorKind.Connecting
    CarFault.Server -> ErrorKind.Server
    CarFault.Recognition -> ErrorKind.Recognition
    CarFault.Audio -> ErrorKind.Audio
}

/** The character on C3/C4: the live Rive rig, or the still image while it loads, fails, or motion is reduced. */
@Composable
internal fun HomeCharacter(ui: HomeUi, dark: Boolean, reducedMotion: Boolean, size: CharacterSize, description: String, modifier: Modifier = Modifier) {
    var riveFailed by remember { mutableStateOf(false) }
    val live: (@Composable () -> Unit)? = if (reducedMotion || riveFailed) null else {
        {
            RiveCharacter(
                riveState = riveStateFor(ui),
                mouth = ui.mouth,
                bodyColor = if (dark) 1 else 0,
                modifier = Modifier.fillSize(),
                onFailure = { riveFailed = true },
            )
        }
    }
    CharacterSlot(size = size, modifier = modifier, live = live, contentDescription = description)
}

/**
 * C3 parked home, C4 conversation (a turn is on it) and C8 errors (a fault replaces the left column) on one screen,
 * like the routes in PRD 3.2: `/home` with C4 and C8 as states. Never composed while the car moves (the NavHost shows
 * [DriveScreen] then), so subtitles here are the parked-only subtitles of D-25.
 */
@Composable
fun HomeScreen(
    ui: HomeUi,
    dark: Boolean,
    reducedMotion: Boolean,
    onTalk: () -> Unit,
    onEndListening: () -> Unit,
    onMemory: () -> Unit,
    onSettings: () -> Unit,
    onFaultAction: (CarFault) -> Unit,
    modifier: Modifier = Modifier,
) {
    OceanBackground(modifier = modifier.fillSize(), animated = !reducedMotion) {
        VStack(HeyGap.G24, modifier = Modifier.fillSize().inset(HeyGap.G32)) {
            HeaderBar(
                title = stringResource(R.string.home_title),
                stateLabel = stringResource(R.string.home_parked),
                trailing = {
                    HeyButton(stringResource(R.string.home_memory), onMemory, variant = HeyButtonVariant.Soft, icon = HeyIcons.Book)
                    HeyButton(stringResource(R.string.home_settings), onSettings, variant = HeyButtonVariant.Soft, icon = HeyIcons.Gear)
                },
            )
            HStack(HeyGap.G32, modifier = Modifier.fillSize()) {
                VStack(HeyGap.G24, modifier = Modifier.grow().fillSize(), arrange = HeyArrange.SpaceBetween) {
                    HomeLeft(ui, onTalk, onEndListening, onFaultAction)
                }
                HomeCharacter(
                    ui = ui,
                    dark = dark,
                    reducedMotion = reducedMotion,
                    size = CharacterSize.Stage,
                    description = stringResource(if (ui.inTalk) R.string.home_character else R.string.home_character_idle),
                )
            }
        }
    }
}

@Composable
private fun ai.hey.designsystem.layout.HeyStackScope.HomeLeft(
    ui: HomeUi,
    onTalk: () -> Unit,
    onEndListening: () -> Unit,
    onFaultAction: (CarFault) -> Unit,
) {
    val fault = ui.fault
    val talking = ui.phase != HomePhase.Idle
    val faultShown = !talking && fault != null
    // A running turn is shown first: a fault that appears mid-turn replaces it only once the turn has ended (C4 -> C8 -> C3).
    when {
        faultShown -> ErrorPanel(kind = fault!!.toKind(), onAction = { onFaultAction(fault) })
        talking || ui.inTalk -> {
            VStack(HeyGap.G16) {
                StatusBadge(phase = ui.phase.toTalk(), connection = HeyConnection.Online)
                ui.heard?.let { HeyText(it, tone = HeyTone.Muted, variant = HeyTextVariant.Body, maxLines = 2) }
                if (ui.reply.isNotEmpty()) Subtitle(text = ui.reply)
            }
        }
        else -> {
            VStack(HeyGap.G8) {
                PageHeading(title = stringResource(R.string.home_hint), hero = true)
                if (!ui.online) StatusBadge(phase = TalkPhase.Idle, connection = HeyConnection.Reconnecting)
            }
        }
    }
    // ONE talk button at ONE place in the tree for every state but Speaking: a press-and-hold that starts a turn changes the phase,
    // and a button that moved to another `when` branch would be disposed under the finger, ending the listen at once.
    // While the character talks it is "그만 듣기" (D-24: stops the voice and listens); the hold is long over by then.
    if (ui.phase == HomePhase.Speaking) {
        StopListeningButton(onClick = onTalk)
    } else {
        TalkButton(
            phase = ui.phase.toTalk(),
            onClick = onTalk,
            onHoldStart = onTalk,
            onHoldEnd = onEndListening,
            enabled = when {
                faultShown -> ui.online && fault != CarFault.Microphone
                talking || ui.inTalk -> ui.phase == HomePhase.Idle || ui.phase == HomePhase.Listening
                else -> ui.online
            },
        )
    }
}
