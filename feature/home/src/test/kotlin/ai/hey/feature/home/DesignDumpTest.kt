package ai.hey.feature.home

import ai.hey.designsystem.gallery.DesignDump
import ai.hey.designsystem.theme.HeyTheme
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * PLEOS-199: renders each screen that has a studio counterpart and dumps every HeyText (bounds in dp, TextStyle) to
 * `build/reports/design-diff/<name>.json`. `ops/design-diff/screens.json` in pleos-challenge maps screen codes to these names.
 * Runs only with `-Pdesign-diff`; the snapshots are never recorded or verified, so this is a report, not a gate on the app build.
 */
class HomeDesignDumpTest {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_C.copy(
            screenWidth = 1600, screenHeight = 900, xdpi = 160, ydpi = 160,
            density = Density.MEDIUM, orientation = ScreenOrientation.LANDSCAPE,
        ),
        renderingMode = RenderingMode.NORMAL,
    )

    @Before fun onlyWhenAsked() = Assume.assumeTrue("run with -Pdesign-diff", System.getProperty("design-diff") != null)

    private fun dump(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        val dump = DesignDump(name, 1600, 900, dark = false)
        paparazzi.snapshot(name) { HeyTheme(false) { dump.capture(content) } }
        dump.write()
    }

    @Test fun home() = dump("car-home") {
        HomeScreen(HomeUi(online = true), reducedMotion = true, onTalk = {}, onEndConversation = {}, onMemory = {}, onSettings = {}, onFaultAction = {})
    }

    @Test fun drive() = dump("car-drive") {
        DriveScreen(HomeUi(online = true), onTalk = {}, onEndConversation = {})
    }
}
