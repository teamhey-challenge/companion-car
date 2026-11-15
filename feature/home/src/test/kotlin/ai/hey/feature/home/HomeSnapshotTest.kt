package ai.hey.feature.home

import ai.hey.designsystem.theme.HeyTheme
import ai.hey.session.CarFault
import ai.hey.designsystem.testing.CarPaparazzi
import ai.hey.designsystem.testing.CarWindow
import org.junit.Rule
import org.junit.Test


private const val LONG_REPLY =
    "그랬구나, 오늘은 정말 긴 하루였겠다. 비도 오고 길도 막혔는데 여기까지 잘 와 줘서 고마워. 잠깐 숨 돌리고 가도 좋아, 나는 여기서 기다리고 있을게."

private val online = HomeUi(online = true)

/** Home states. The character is the still image here: Paparazzi has no Rive runtime, `reducedMotion = true` draws the still. */
private val homeShots: List<Pair<String, HomeUi>> = listOf(
    "c3-idle" to online,
    "c3-connecting" to HomeUi(online = false, fault = CarFault.Connecting),
    "c4-proactive" to online.copy(phase = HomePhase.Listening, source = ai.hey.session.TurnSource.AutoListen, reply = "비 오네, 와이퍼 켰어. 오늘 발표 날이죠?"),
    "c4-listening" to online.copy(phase = HomePhase.Listening),
    "c4-followup" to online.copy(phase = HomePhase.Listening, source = ai.hey.session.TurnSource.AutoListen),
    "c4-thinking" to online.copy(phase = HomePhase.Thinking, heard = "오늘 날씨 어때?"),
    "c4-speaking" to online.copy(phase = HomePhase.Speaking, heard = "오늘 날씨 어때?", reply = "비가 올 것 같아. 우산 챙겼어?", mouth = 2),
    "c4-long" to online.copy(phase = HomePhase.Speaking, heard = "오늘 너무 힘들었어", reply = LONG_REPLY, mouth = 1),
    "c8-offline" to HomeUi(online = false, fault = CarFault.Offline),
    "c8-microphone" to online.copy(fault = CarFault.Microphone),
    "c8-server" to online.copy(fault = CarFault.Server),
    "c8-recognition" to online.copy(fault = CarFault.Recognition),
    "c8-audio" to online.copy(fault = CarFault.Audio),
)

private val driveShots: List<Pair<String, HomeUi>> = listOf(
    "c5-idle" to online,
    "c5-listening" to online.copy(phase = HomePhase.Listening),
    "c5-thinking" to online.copy(phase = HomePhase.Thinking),
    "c5-speaking" to online.copy(phase = HomePhase.Speaking, reply = LONG_REPLY),
    // A friend's line while driving: character, reading state and "읽기 멈추기" only; the text never reaches the screen (D-47).
    "c5-friend-reading" to online.copy(phase = HomePhase.Speaking, source = ai.hey.session.TurnSource.Friend, mouth = 2),
)

/** C5 with the live rig allowed (ocean background and status wave move; Paparazzi has no Rive runtime so the slot shows the still). */
private val driveMotionShots: List<Pair<String, HomeUi>> = listOf(
    "c5-motion-idle" to online,
    "c5-motion-listening" to online.copy(phase = HomePhase.Listening),
    "c5-motion-speaking" to online.copy(phase = HomePhase.Speaking, reply = LONG_REPLY),
    // A proactive line is parked-only: driving shows no text, only the listening state (PLEOS-194, D-25).
    "c5-proactive" to online.copy(phase = HomePhase.Listening, source = ai.hey.session.TurnSource.AutoListen, reply = "비 오네, 와이퍼 켰어. 오늘 발표 날이죠?"),
)

private fun CarPaparazzi.recordAll() {
    listOf(true, false).forEach { dark ->
        val theme = if (dark) "dark" else "light"
        homeShots.forEach { (name, ui) ->
            snapshot("$name-$theme") {
                HeyTheme(dark) {
                    HomeScreen(ui, reducedMotion = true, onTalk = {}, onEndConversation = {}, onMemory = {}, onFriends = {}, onSettings = {}, onFaultAction = {})
                }
            }
        }
        driveShots.forEach { (name, ui) ->
            snapshot("$name-$theme") {
                HeyTheme(dark) { DriveScreen(ui, onTalk = {}, onEndConversation = {}, reducedMotion = true) }
            }
        }
        driveMotionShots.forEach { (name, ui) ->
            snapshot("$name-$theme") {
                HeyTheme(dark) { DriveScreen(ui, onTalk = {}, onEndConversation = {}, reducedMotion = false) }
            }
        }
    }
}

class Home1600SnapshotTest {
    @get:Rule val paparazzi = CarPaparazzi(CarWindow.Full1600)

    @Test fun screens() = paparazzi.recordAll()
}

/** Pleos multi-window: C3/C4/C5 at 1/2 (840 dp) and 1/3 (560 dp) of the screen width, at the 900 dp design height. */
private val multiShots: List<Pair<String, HomeUi>> = listOf(
    "c3-idle" to online,
    "c3-friend-note" to online.copy(friendNote = "민수: 오늘 저녁에 같이 밥 먹자"),
    "c4-speaking" to online.copy(phase = HomePhase.Speaking, heard = "오늘 날씨 어때?", reply = "비가 올 것 같아. 우산 챙겼어?", mouth = 2),
    "c4-long" to online.copy(phase = HomePhase.Speaking, heard = "오늘 너무 힘들었어", reply = LONG_REPLY, mouth = 1),
    "c8-offline" to HomeUi(online = false, fault = CarFault.Offline),
)

private fun CarPaparazzi.recordMulti() {
    listOf(true, false).forEach { dark ->
        val theme = if (dark) "dark" else "light"
        multiShots.forEach { (name, ui) ->
            snapshot("$name-$theme") {
                HeyTheme(dark) {
                    HomeScreen(ui, reducedMotion = true, onTalk = {}, onEndConversation = {}, onMemory = {}, onFriends = {}, onSettings = {}, onFaultAction = {})
                }
            }
        }
        driveShots.forEach { (name, ui) ->
            snapshot("$name-$theme") {
                HeyTheme(dark) { DriveScreen(ui, onTalk = {}, onEndConversation = {}) }
            }
        }
    }
}

/** The 1/2 width window next to navigation (840 x 1212 dp, the Connect-S half). */
class Home840SnapshotTest {
    @get:Rule val paparazzi = CarPaparazzi(CarWindow.Half840)

    @Test fun screens() = paparazzi.recordMulti()
}

/** The 1/3 width window (560 x 1212 dp, Connect-W). */
class Home560SnapshotTest {
    @get:Rule val paparazzi = CarPaparazzi(CarWindow.Third560)

    @Test fun screens() = paparazzi.recordMulti()
}

/** The real Pleos app window (1682 x 1210 px = 1251 x 900 design dp). */
class Home1251SnapshotTest {
    @get:Rule val paparazzi = CarPaparazzi(CarWindow.Car1251)

    @Test fun screens() = paparazzi.recordMulti()
}

/** A friend's message is a parked subtitle on C3 at the full width too. */
class HomeFriendNoteSnapshotTest {
    @get:Rule val paparazzi = CarPaparazzi(CarWindow.Full1600)

    @Test fun screens() {
        listOf(true, false).forEach { dark ->
            paparazzi.snapshot("c3-friend-note-${if (dark) "dark" else "light"}") {
                HeyTheme(dark) {
                    HomeScreen(
                        online.copy(friendNote = "민수: 오늘 저녁에 같이 밥 먹자"),
                        reducedMotion = true, onTalk = {}, onEndConversation = {}, onMemory = {}, onFriends = {}, onSettings = {}, onFaultAction = {},
                    )
                }
            }
        }
    }
}
