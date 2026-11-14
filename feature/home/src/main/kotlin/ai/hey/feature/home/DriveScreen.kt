package ai.hey.feature.home

import ai.hey.designsystem.component.CharacterSize
import ai.hey.designsystem.component.CharacterSlot
import ai.hey.designsystem.component.StopListeningButton
import ai.hey.designsystem.component.TalkButton
import ai.hey.designsystem.layout.HeyAlign
import ai.hey.designsystem.layout.HeyArrange
import ai.hey.designsystem.layout.VStack
import ai.hey.designsystem.layout.fillSize
import ai.hey.designsystem.layout.inset
import ai.hey.designsystem.theme.HeyGap
import ai.hey.designsystem.component.PageBackground
import ai.hey.designsystem.component.StatusBadge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay

/** How long a press may stay unanswered before C5 says "잠깐만요" (PRD 4.5.4). */
const val DRIVE_WAIT_MILLIS = 2_000L

/**
 * C5 while the car moves (or its motion is unknown): ONE still character image, one status line and one big button.
 * Nothing here moves (no Rive, no animation: D-31, PRD 3.5), there are no subtitles (D-25) and no way to another screen.
 * The one button is the D-24 button: idle starts listening; listening ends it and sends; while the character talks it is
 * "그만 듣기" and stops the voice and listens. Errors never change this screen (voice only, see FaultAnnouncer).
 */
@Composable
fun DriveScreen(ui: HomeUi, onTalk: () -> Unit, onEndListening: () -> Unit, modifier: Modifier = Modifier) {
    // "잠깐만요" shows when the driver has spoken (thinking) and nothing has answered for 2 s.
    var waiting by remember { mutableStateOf(false) }
    LaunchedEffect(ui.phase) {
        waiting = false
        if (ui.phase == HomePhase.Thinking) {
            delay(DRIVE_WAIT_MILLIS)
            waiting = true
        }
    }
    PageBackground(modifier = modifier.fillSize()) {
        VStack(
            gap = HeyGap.G24,
            modifier = Modifier.fillSize().inset(HeyGap.G32),
            align = HeyAlign.Center,
            arrange = HeyArrange.Center,
        ) {
            CharacterSlot(size = CharacterSize.Driving, live = null, contentDescription = stringResource(R.string.home_character))
            when {
                ui.phase == HomePhase.Listening || ui.phase == HomePhase.Speaking || (waiting && ui.phase == HomePhase.Thinking) ->
                    StatusBadge(phase = ui.phase.toTalk(), driving = true, animated = false)
            }
            if (ui.phase == HomePhase.Speaking) {
                StopListeningButton(onClick = onTalk, big = true)
            } else {
                TalkButton(
                    phase = ui.phase.toTalk(),
                    onClick = if (ui.phase == HomePhase.Listening) onEndListening else onTalk,
                    big = true,
                    driving = true,
                )
            }
        }
    }
}
