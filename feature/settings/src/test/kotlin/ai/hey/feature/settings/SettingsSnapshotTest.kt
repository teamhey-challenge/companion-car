package ai.hey.feature.settings

import ai.hey.designsystem.component.PageBackground
import ai.hey.designsystem.layout.carMargin
import ai.hey.designsystem.layout.fillSize
import androidx.compose.ui.Modifier
import ai.hey.designsystem.theme.HeyTheme
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import ai.hey.designsystem.component.CharacterLook
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

private val lookShots: List<Pair<String, CharacterLook>> = listOf(
    "c7-look-default" to CharacterLook(),
    "c7-look-bucket-scarf" to CharacterLook(bodyColor = 1, hat = 2, neck = 1),
    "c7-look-bare" to CharacterLook(bodyColor = 0, hat = 0, neck = 0),
)

private fun Paparazzi.recordAll() {
    shots.forEach { (name, shot) ->
        listOf(true, false).forEach { dark ->
            snapshot("$name-${if (dark) "dark" else "light"}") {
                HeyTheme(dark) { Screen(shot) }
            }
        }
    }
    // The 꾸미기 section sits below the first screenful of C7, so it gets its own frames (light/dark).
    lookShots.forEach { (name, look) ->
        listOf(true, false).forEach { dark ->
            snapshot("$name-${if (dark) "dark" else "light"}") {
                HeyTheme(dark) {
                    PageBackground(modifier = Modifier.fillSize().carMargin()) { LookSection(look, onLook = {}, enabled = true) }
                }
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
        onLook = {},
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
