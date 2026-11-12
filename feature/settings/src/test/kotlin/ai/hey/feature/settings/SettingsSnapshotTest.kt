package ai.hey.feature.settings

import ai.hey.designsystem.component.HeyConnection
import ai.hey.designsystem.theme.HeyTheme
import ai.hey.model.CarSettings
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

private class Shot(val settings: CarSettings, val saveFailed: Boolean = false)

private val shots: List<Pair<String, Shot>> = listOf(
    "d8-settings" to Shot(CarSettings(plateSuffix = "1234")),
    "d8-settings-changed" to Shot(CarSettings(speechGapSec = 30, plateSuffix = "1234", relayUrl = "https://hey-relay.example.com", dark = false)),
    "d8-settings-min-gap" to Shot(CarSettings(speechGapSec = 4, plateSuffix = "서울12가3456")),
    "d8-settings-save-failed" to Shot(CarSettings(speechGapSec = 12, plateSuffix = "1234"), saveFailed = true),
)

private fun Paparazzi.recordAll() {
    shots.forEach { (name, shot) ->
        listOf(true, false).forEach { dark ->
            snapshot("$name-${if (dark) "dark" else "light"}") {
                HeyTheme(dark) {
                    SettingsScreen(
                        settings = shot.settings,
                        onChange = {},
                        onClose = {},
                        connection = HeyConnection.Online,
                        wordmarkParty = 1,
                        clock = "오후 11:42",
                        saveFailed = shot.saveFailed,
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
