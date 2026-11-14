package ai.hey.feature.home

import ai.hey.designsystem.theme.HeyTheme
import ai.hey.session.CarFault
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import org.junit.Rule
import org.junit.Test

private fun device(width: Int, height: Int) = DeviceConfig.PIXEL_C.copy(
    screenWidth = width,
    screenHeight = height,
    xdpi = 160,
    ydpi = 160,
    density = Density.MEDIUM,
    orientation = ScreenOrientation.LANDSCAPE,
)

private const val LONG_REPLY =
    "그랬구나, 오늘은 정말 긴 하루였겠다. 비도 오고 길도 막혔는데 여기까지 잘 와 줘서 고마워. 잠깐 숨 돌리고 가도 좋아, 나는 여기서 기다리고 있을게."

private val online = HomeUi(online = true)

/** Home states. The character is the still image here: Paparazzi has no Rive runtime, `reducedMotion = true` draws the still. */
private val homeShots: List<Pair<String, HomeUi>> = listOf(
    "c3-idle" to online,
    "c3-connecting" to HomeUi(online = false, fault = CarFault.Connecting),
    "c4-listening" to online.copy(phase = HomePhase.Listening),
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
)

private fun Paparazzi.recordAll() {
    listOf(true, false).forEach { dark ->
        val theme = if (dark) "dark" else "light"
        homeShots.forEach { (name, ui) ->
            snapshot("$name-$theme") {
                HeyTheme(dark) {
                    HomeScreen(ui, dark, reducedMotion = true, onTalk = {}, onStop = {}, onEndListening = {}, onMemory = {}, onSettings = {}, onFaultAction = {})
                }
            }
        }
        driveShots.forEach { (name, ui) ->
            snapshot("$name-$theme") {
                HeyTheme(dark) { DriveScreen(ui, onTalk = {}, onEndListening = {}) }
            }
        }
    }
}

class Home1600SnapshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = device(1600, 900), renderingMode = RenderingMode.NORMAL)

    @Test fun screens() = paparazzi.recordAll()
}
