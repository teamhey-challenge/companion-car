package ai.hey.feature.settings

import ai.hey.designsystem.component.PageBackground
import ai.hey.designsystem.layout.carMargin
import ai.hey.designsystem.layout.fillSize
import androidx.compose.ui.Modifier
import ai.hey.designsystem.theme.HeyTheme
import ai.hey.designsystem.testing.CarPaparazzi
import ai.hey.designsystem.testing.CarWindow
import ai.hey.designsystem.component.CharacterLook
import androidx.compose.runtime.Composable
import org.junit.Rule
import org.junit.Test


private class Shot(val settings: SettingsUi, val notice: String? = null, val server: ServerAddress? = null)

private val shots: List<Pair<String, Shot>> = listOf(
    "c7-settings" to Shot(SettingsUi(name = "민수", phonePaired = true)),
    "c7-name-unknown" to Shot(SettingsUi()),
    "c7-unpaired" to Shot(SettingsUi()),
    "c7-proactive-off" to Shot(SettingsUi(proactive = ProactiveUi.Off, memoryEnabled = false, speech = SpeechUi.Jondaetmal)),
    "c7-saving" to Shot(SettingsUi(phonePaired = true, saving = setOf(SettingField.Memory))),
    "c7-save-failed" to Shot(SettingsUi(phonePaired = true), notice = "저장하지 못했어요"),
    "c7-withdraw-failed" to Shot(SettingsUi(phonePaired = true, accountFailure = AccountFailureUi.Withdraw)),
    "c7-debug-server" to Shot(SettingsUi(), server = ServerAddress("https://hey.lastmile.ltd/api") {}),
)

private val lookShots: List<Pair<String, CharacterLook>> = listOf(
    "c7-look-default" to CharacterLook(),
    "c7-look-bucket-scarf" to CharacterLook(bodyColor = 1, hat = 2, neck = 1),
    "c7-look-bare" to CharacterLook(bodyColor = 0, hat = 0, neck = 0),
)

private fun CarPaparazzi.recordAll() {
    // The name editor (draft + the error line) sits at the top of C7 and is only reachable by tapping, so it gets its own frames.
    listOf("c7-name-edit" to "하늘", "c7-name-edit-error" to "12345").forEach { (name, draft) ->
        listOf(true, false).forEach { dark ->
            snapshot("$name-${if (dark) "dark" else "light"}") {
                HeyTheme(dark) {
                    PageBackground(modifier = Modifier.fillSize().carMargin()) {
                        NameSection("민수", enabled = true, onSave = {}, initialEditing = true, initialDraft = draft)
                    }
                }
            }
        }
    }
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
        onName = {},
        onLook = {},
        onOpenMemory = {},
        onOpenPair = {},
        onWithdraw = {},
        notice = shot.notice,
        server = shot.server,
    )
}

class Settings1600SnapshotTest {
    @get:Rule val paparazzi = CarPaparazzi(CarWindow.Full1600)

    @Test fun screens() = paparazzi.recordAll()
}

/** Pleos multi-window, 1/2 width (840 x 1212 dp) and 1/3 width (560 x 1212 dp); drawn at the 900 dp design height. */
class Settings840SnapshotTest {
    @get:Rule val paparazzi = CarPaparazzi(CarWindow.Half840)

    @Test fun screens() = paparazzi.recordAll()
}

class Settings560SnapshotTest {
    @get:Rule val paparazzi = CarPaparazzi(CarWindow.Third560)

    @Test fun screens() = paparazzi.recordAll()
}

class Settings1280SnapshotTest {
    @get:Rule val paparazzi = CarPaparazzi(CarWindow.Small1280)

    @Test fun screens() = paparazzi.recordAll()
}

/** The real Pleos app window (1682 x 1210 px = 1251 x 900 design dp). */
class Settings1251SnapshotTest {
    @get:Rule val paparazzi = CarPaparazzi(CarWindow.Car1251)

    @Test fun screens() = paparazzi.recordAll()
}
