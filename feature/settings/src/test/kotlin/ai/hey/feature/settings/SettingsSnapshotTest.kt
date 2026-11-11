package ai.hey.feature.settings

import ai.hey.designsystem.component.HeyConnection
import ai.hey.designsystem.theme.HeyTheme
import ai.hey.model.CarSettings
import ai.hey.model.PresetId
import ai.hey.model.SpeechRate
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

private val shots: List<Pair<String, CarSettings>> = listOf(
    "d8-settings" to CarSettings(plateSuffix = "1234"),
    "d8-settings-changed" to CarSettings(
        enabledPresets = PresetId.entries.toSet() - PresetId.Slower - PresetId.Trunk - PresetId.Window,
        speechGapSec = 30,
        speechRate = SpeechRate.Fast,
        plateSuffix = "1234",
        relayUrl = "https://hey-relay.example.com",
    ),
    "d8-settings-min-gap" to CarSettings(speechGapSec = 4, speechRate = SpeechRate.Slow, plateSuffix = "서울12가3456"),
)

private fun Paparazzi.recordAll() {
    shots.forEach { (name, settings) ->
        listOf(true, false).forEach { dark ->
            snapshot("$name-${if (dark) "dark" else "light"}") {
                HeyTheme(dark) {
                    SettingsScreen(
                        settings = settings,
                        onChange = {},
                        onClose = {},
                        connection = HeyConnection.Online,
                        wordmarkParty = 1,
                        clock = "오후 11:42",
                    )
                }
            }
        }
    }
}

class Settings1600SnapshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = device(1600, 900), renderingMode = RenderingMode.NORMAL)

    @Test fun screens() = paparazzi.recordAll()
}

class Settings1280SnapshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = device(1280, 720), renderingMode = RenderingMode.NORMAL)

    @Test fun screens() = paparazzi.recordAll()
}
