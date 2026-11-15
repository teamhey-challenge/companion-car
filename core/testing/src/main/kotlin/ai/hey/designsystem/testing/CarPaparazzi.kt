package ai.hey.designsystem.testing

import ai.hey.designsystem.layout.designDensity
import ai.hey.designsystem.theme.HeyTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import org.junit.rules.TestRule

/**
 * A window the car shows the app in, in system dp (what `Configuration.screenWidthDp/HeightDp` report).
 * [Full1600] is the 16:9 design canvas of the mockups. [Half840] and [Third560] are the Pleos multi-window slots next to
 * navigation, which keep the full window height: 841x1210 dp measured on the emulator (docs/sdk/deep-dive/platform-surface.md 4.2)
 * and 840x1212 as the store screenshot size (docs/contest/review-rules.md). [designDensity] scales them to 900 design dp of
 * height, so the screen lays out in about 623 / 416 design dp wide, exactly like in the car.
 */
enum class CarWindow(val widthDp: Int, val heightDp: Int) {
    Full1600(1600, 900),

    /** The real Pleos app window: 1682 x 1210 px, which designScale turns into 1251 x 900 design dp (scale 1.3444). */
    Car1251(1682, 1210),
    Half840(840, 1212),
    Third560(560, 1212),
    Small1280(1280, 720),
}

/**
 * Paparazzi for one [CarWindow]. Every snapshot is drawn with the density MainActivity provides (the shared [designDensity]) and
 * the theme, so a narrow window is the narrow window of the car and not a 160 dpi canvas of the same pixel size. Feature
 * snapshot tests use this and never construct `Paparazzi` themselves (SnapshotWidthsTest scans for that).
 */
class CarPaparazzi(val window: CarWindow) : TestRule {
    private val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_C.copy(
            screenWidth = window.widthDp,
            screenHeight = window.heightDp,
            xdpi = 160,
            ydpi = 160,
            density = Density.MEDIUM,
            orientation = if (window.widthDp >= window.heightDp) ScreenOrientation.LANDSCAPE else ScreenOrientation.PORTRAIT,
        ),
        renderingMode = RenderingMode.NORMAL,
    )

    override fun apply(base: org.junit.runners.model.Statement, description: org.junit.runner.Description) =
        paparazzi.apply(base, description)

    fun snapshot(name: String, content: @Composable () -> Unit) {
        paparazzi.snapshot(name) {
            CompositionLocalProvider(LocalDensity provides designDensity(LocalDensity.current, window.widthDp, window.heightDp)) {
                content()
            }
        }
    }

    /** One snapshot per theme: `<name>-dark` and `<name>-light`. */
    fun snapshotBothThemes(name: String, content: @Composable () -> Unit) {
        listOf(true, false).forEach { dark ->
            snapshot("$name-${if (dark) "dark" else "light"}") { HeyTheme(dark) { content() } }
        }
    }
}
