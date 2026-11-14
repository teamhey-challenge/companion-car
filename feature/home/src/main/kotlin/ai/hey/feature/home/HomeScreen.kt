package ai.hey.feature.home

import ai.hey.designsystem.component.CharacterSize
import ai.hey.designsystem.component.CharacterSlot
import ai.hey.designsystem.component.ErrorKind
import ai.hey.designsystem.component.ErrorPanel
import ai.hey.designsystem.component.HeaderBar
import ai.hey.designsystem.component.HeyButton
import ai.hey.designsystem.component.HeyButtonSize
import ai.hey.designsystem.component.HeyButtonVariant
import ai.hey.designsystem.component.HeyConnection
import ai.hey.designsystem.component.HeyText
import ai.hey.designsystem.component.HeyTextAlign
import ai.hey.designsystem.component.OceanBackground
import ai.hey.designsystem.component.PageHeading
import ai.hey.designsystem.component.StatusBadge
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

internal fun HomeFault.toKind(): ErrorKind = when (this) {
    HomeFault.Microphone -> ErrorKind.Microphone
    HomeFault.Offline -> ErrorKind.Offline
    HomeFault.Connecting -> ErrorKind.Connecting
    HomeFault.Server -> ErrorKind.Server
    HomeFault.Recognition -> ErrorKind.Recognition
    HomeFault.Audio -> ErrorKind.Audio
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
    onStop: () -> Unit,
    onMemory: () -> Unit,
    onSettings: () -> Unit,
    onFaultAction: (HomeFault) -> Unit,
    modifier: Modifier = Modifier,
) {
    OceanBackground(modifier = modifier.fillSize(), animated = !reducedMotion) {
        VStack(HeyGap.G24, modifier = Modifier.fillSize().inset(HeyGap.G32)) {
            HeaderBar(
                title = stringResource(R.string.home_title),
                backLabel = "",
                trailing = {
                    HeyButton(stringResource(R.string.home_memory), onMemory, variant = HeyButtonVariant.Secondary, icon = HeyIcons.Book)
                    HeyButton(stringResource(R.string.home_settings), onSettings, variant = HeyButtonVariant.Secondary, icon = HeyIcons.Gear)
                },
            )
            HStack(HeyGap.G32, modifier = Modifier.fillSize()) {
                VStack(HeyGap.G24, modifier = Modifier.grow().fillSize(), arrange = HeyArrange.SpaceBetween) {
                    HomeLeft(ui, onTalk, onStop, onFaultAction)
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
    onStop: () -> Unit,
    onFaultAction: (HomeFault) -> Unit,
) {
    val fault = ui.fault
    val talking = ui.phase != HomePhase.Idle
    // A running turn is shown first: a fault that appears mid-turn replaces it only once the turn has ended (C4 -> C8 -> C3).
    when {
        !talking && fault != null -> {
            ErrorPanel(kind = fault.toKind(), onAction = { onFaultAction(fault) })
            TalkButton(phase = TalkPhase.Idle, onClick = onTalk, enabled = ui.online && fault != HomeFault.Microphone)
        }
        talking || ui.inTalk -> {
            VStack(HeyGap.G16) {
                StatusBadge(phase = ui.phase.toTalk(), connection = HeyConnection.Online)
                ui.heard?.let { HeyText(it, tone = HeyTone.Muted, variant = HeyTextVariant.Body, maxLines = 2) }
                if (ui.reply.isNotEmpty()) Subtitle(text = ui.reply)
            }
            HStack(HeyGap.G16, align = HeyAlign.Center) {
                TalkButton(phase = ui.phase.toTalk(), onClick = onTalk, enabled = ui.phase == HomePhase.Idle)
                if (ui.phase == HomePhase.Speaking || ui.phase == HomePhase.Listening) {
                    HeyButton(stringResource(R.string.home_stop), onStop, variant = HeyButtonVariant.Secondary, size = HeyButtonSize.Large, icon = HeyIcons.Pause)
                }
            }
        }
        else -> {
            VStack(HeyGap.G8) {
                PageHeading(title = stringResource(R.string.home_hint))
                if (!ui.online) StatusBadge(phase = TalkPhase.Idle, connection = HeyConnection.Reconnecting)
            }
            TalkButton(phase = TalkPhase.Idle, onClick = onTalk, enabled = ui.online)
        }
    }
}
