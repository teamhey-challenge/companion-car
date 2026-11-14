package ai.hey.feature.onboarding

import ai.hey.designsystem.theme.HeyTheme
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

private val c1: List<Pair<String, ConsentFormUi>> = listOf(
    "c1-default" to ConsentFormUi(),
    "c1-required-missing" to ConsentFormUi(voiceRequiredShown = true),
    "c1-saving" to ConsentFormUi(voice = true, memory = true, saving = true),
    "c1-error" to ConsentFormUi(voice = true, failed = true),
)

private val c2: List<Pair<String, PairUi>> = listOf(
    "c2-loading" to PairUi.Loading,
    "c2-pending" to PairUi.Pending("K7M2QX9P"),
    "c2-partial" to PairUi.Pending("K7M2QX9P", phoneEntered = true),
    "c2-claimed" to PairUi.Claimed,
    "c2-expired" to PairUi.Expired("K7M2QX9P"),
    "c2-failed" to PairUi.Failed,
)

private fun Paparazzi.recordAll() {
    listOf(true, false).forEach { dark ->
        val theme = if (dark) "dark" else "light"
        c1.forEach { (name, ui) ->
            snapshot("$name-$theme") {
                HeyTheme(dark) { OnboardingScreen(ui, {}, {}, {}, {}, {}, {}) }
            }
        }
        c2.forEach { (name, ui) ->
            snapshot("$name-$theme") { HeyTheme(dark) { PairScreen(ui, {}, {}, {}) } }
        }
    }
}

class Onboarding1600SnapshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = device(1600, 900), renderingMode = RenderingMode.NORMAL)

    @Test fun screens() = paparazzi.recordAll()
}

class Onboarding1280SnapshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = device(1280, 720), renderingMode = RenderingMode.NORMAL)

    @Test fun screens() = paparazzi.recordAll()
}
