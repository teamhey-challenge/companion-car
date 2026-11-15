package ai.hey.feature.memory

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
 * dumps every HeyText of C6 (bounds in dp, TextStyle) to `build/reports/design-diff/car-memory@<w>x<h>.json` for
 * `ops/design-diff/screens.json`. 1251x900 is the real Pleos window, 1600x900 the studio canvas. Runs only with `-Pdesign-diff`.
 */
private fun device(w: Int, h: Int) = DeviceConfig.PIXEL_C.copy(
    screenWidth = w, screenHeight = h, xdpi = 160, ydpi = 160, density = Density.MEDIUM, orientation = ScreenOrientation.LANDSCAPE,
)

private fun Paparazzi.dumpScreens(w: Int, h: Int) {
    val rows = listOf(
        MemoryRowUi("1", MemoryKindUi.Fact, "비 오는 날, 잔잔한 음악을 좋아해요.", "11월 13일"),
        MemoryRowUi("2", MemoryKindUi.Summary, "퇴근길에는 조용한 대화를 좋아해요.", "11월 12일"),
        MemoryRowUi("3", MemoryKindUi.Fact, "금요일에 중요한 발표가 있어요.", "11월 11일"),
    )
    val dump = DesignDump("car-memory@${w}x$h", w, h, dark = false)
    snapshot("car-memory@${w}x$h") {
        HeyTheme(false) { dump.capture { MemoryScreen(MemoryListUi.Items(rows), onBack = {}, onRetry = {}, onDelete = {}, onDeleteAll = {}) } }
    }
    dump.write()
}

class MemoryDesignDump1600Test {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = device(1600, 1300), renderingMode = RenderingMode.NORMAL)

    @Before fun onlyWhenAsked() = Assume.assumeTrue("run with -Pdesign-diff", System.getProperty("design-diff") != null)

    @Test fun screens() = paparazzi.dumpScreens(1600, 900)
}

class MemoryDesignDump1251Test {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = device(1251, 1300), renderingMode = RenderingMode.NORMAL)

    @Before fun onlyWhenAsked() = Assume.assumeTrue("run with -Pdesign-diff", System.getProperty("design-diff") != null)

    @Test fun screens() = paparazzi.dumpScreens(1251, 900)
}
