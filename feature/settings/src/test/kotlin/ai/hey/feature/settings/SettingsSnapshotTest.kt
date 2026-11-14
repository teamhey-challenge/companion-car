package ai.hey.feature.settings

import ai.hey.designsystem.theme.HeyTheme
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import androidx.compose.runtime.Composable
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

private class Shot(val settings: SettingsUi, val notice: String? = null, val server: ServerAddress? = null)

private val shots: List<Pair<String, Shot>> = listOf(
    "c7-settings" to Shot(SettingsUi(phonePaired = true)),
    "c7-unpaired" to Shot(SettingsUi()),
    "c7-proactive-off" to Shot(SettingsUi(proactive = ProactiveUi.Off, memoryEnabled = false, speech = SpeechUi.Jondaetmal)),
    "c7-saving" to Shot(SettingsUi(phonePaired = true, saving = setOf(SettingField.Memory))),
    "c7-save-failed" to Shot(SettingsUi(phonePaired = true), notice = "저장하지 못했어요"),
    "c7-withdraw-failed" to Shot(SettingsUi(phonePaired = true, accountFailure = AccountFailureUi.Withdraw)),
    "c7-unpair-failed" to Shot(SettingsUi(phonePaired = true, accountFailure = AccountFailureUi.Unpair)),
    "c7-debug-server" to Shot(SettingsUi(), server = ServerAddress("https://hey.lastmile.ltd/api") {}),
)

private fun Paparazzi.recordAll() {
    shots.forEach { (name, shot) ->
        listOf(true, false).forEach { dark ->
            snapshot("$name-${if (dark) "dark" else "light"}") {
                HeyTheme(dark) { Screen(shot) }
            }
        }
    }
}

@Composable
private fun Screen(shot: Shot) {
    SettingsScreen(
        settings = shot.settings,
        onBack = {},
        onProactive = {},
        onMemoryEnabled = {},
        onSpeech = {},
        onOpenMemory = {},
        onOpenPair = {},
        onUnpair = {},
        onWithdraw = {},
        notice = shot.notice,
        server = shot.server,
    )
}

class Settings1600SnapshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = device(1600, 900), renderingMode = RenderingMode.NORMAL)

    @Test fun screens() = paparazzi.recordAll()
}

class Settings1280SnapshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = device(1280, 720), renderingMode = RenderingMode.NORMAL)

    @Test fun screens() = paparazzi.recordAll()
}
