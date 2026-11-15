package ai.hey.feature.home
import ai.hey.session.TurnSource

import ai.hey.designsystem.component.CharacterSize
import ai.hey.designsystem.component.CharacterSlot
import ai.hey.designsystem.component.OceanBackground
import ai.hey.designsystem.component.StatusBadge
import ai.hey.designsystem.component.TalkButton
import ai.hey.designsystem.component.TalkPhase
import ai.hey.designsystem.layout.HeyAlign
import ai.hey.designsystem.layout.VStack
import ai.hey.designsystem.layout.driveStatusSlot
import ai.hey.designsystem.layout.driveTop
import ai.hey.designsystem.layout.fillSize
import ai.hey.designsystem.theme.HeyGap
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
 * C5 while the car moves (or its motion is unknown): the same animated Rive character as C3 (cancels idle motion,
 * mouth from the voice level, talk states; still image while Rive loads, fails or motion is reduced), one status line and one
 * big button. Rive is capped at 30 fps (see [RiveCharacter]); the ocean background and the status wave move like C3.
 * There are no subtitles and no way to another screen. The one button is "말하기" while idle and "대화 마치기" in every
 * active phase: it ends the conversation. Errors never change this screen (voice only, see FaultAnnouncer).
 */
@Composable
fun DriveScreen(
    ui: HomeUi,
    onTalk: () -> Unit,
    onEndConversation: () -> Unit,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = true,
) {
    // "잠깐만요" shows when the driver has spoken (thinking) and nothing has answered for 2 s.
    var waiting by remember { mutableStateOf(false) }
    LaunchedEffect(ui.phase) {
        waiting = false
        if (ui.phase == HomePhase.Thinking) {
            delay(DRIVE_WAIT_MILLIS)
            waiting = true
        }
    }
    OceanBackground(modifier = modifier.fillSize(), animated = !reducedMotion) {
        VStack(
            gap = HeyGap.G4,
            modifier = Modifier.fillSize().driveTop(),
            align = HeyAlign.Center,
        ) {
            HomeCharacter(ui = ui, reducedMotion = reducedMotion, size = CharacterSize.Driving, description = stringResource(R.string.home_character), whileDriving = true)
            // The status line has a fixed slot so the button never moves between phases (mockup C5).
            VStack(HeyGap.G4, modifier = Modifier.driveStatusSlot(), align = HeyAlign.Center) {
                when {
                    ui.phase == HomePhase.Listening || ui.phase == HomePhase.Speaking ->
                        StatusBadge(phase = ui.phase.toTalk(), driving = true, animated = !reducedMotion)
                    waiting -> StatusBadge(phase = TalkPhase.Thinking, driving = true, animated = !reducedMotion)
                    ui.fault != null -> DriveFaultIcon(ui.fault)
                }
            }
            // A friend's line being read: the one button stops the reading; nothing else of it is on screen.
            val friendLine = ui.source == TurnSource.Friend && ui.phase != HomePhase.Idle
            TalkButton(
                phase = ui.phase.toTalk(),
                onClick = if (ui.phase == HomePhase.Idle) onTalk else onEndConversation,
                driving = true,
                label = if (friendLine) stringResource(R.string.drive_friend_stop) else null,
            )
        }
    }
}
