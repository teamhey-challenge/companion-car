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
 * `build/reports/design-diff/<name>@<w>x<h>.json`. `ops/design-diff/screens.json` in pleos-challenge maps screen codes to these names.
 * 1251x900 is what the Pleos emulator window really is (1682x1210 px / designScale 1.3444); 1600x900 is the studio canvas.
 * Runs only with `-Pdesign-diff`; the snapshots are never recorded or verified, so this is a report, not a gate on the app build.
 */
private fun device(w: Int, h: Int) = DeviceConfig.PIXEL_C.copy(
    screenWidth = w, screenHeight = h, xdpi = 160, ydpi = 160, density = Density.MEDIUM, orientation = ScreenOrientation.LANDSCAPE,
)

private fun Paparazzi.dumpScreens(w: Int, h: Int) {
    fun dump(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        val dump = DesignDump("$name@${w}x$h", w, h, dark = false)
        snapshot("$name@${w}x$h") { HeyTheme(false) { dump.capture(content) } }
        dump.write()
    }
    dump("car-home") {
        HomeScreen(HomeUi(online = true), reducedMotion = true, onTalk = {}, onEndConversation = {}, onMemory = {}, onFriends = {}, onSettings = {}, onFaultAction = {})
    }
    dump("car-home-trip-end") {
        HomeScreen(
            HomeUi(online = true, tripNote = "오늘도 같이 달렸어. 오늘 일기 써 뒀어, 폰에서 봐!", tripDiary = true, tripMomentDone = true),
            reducedMotion = true, onTalk = {}, onEndConversation = {}, onMemory = {}, onFriends = {}, onSettings = {}, onFaultAction = {},
        )
    }
    dump("car-home-talk") {
        HomeScreen(
            HomeUi(online = true, phase = HomePhase.Speaking, reply = "어제 준비하던 발표는 잘 마쳤어요?", mouth = 1),
            reducedMotion = true, onTalk = {}, onEndConversation = {}, onMemory = {}, onFriends = {}, onSettings = {}, onFaultAction = {},
        )
    }
    dump("car-error") {
        HomeScreen(
            HomeUi(online = false, fault = ai.hey.session.CarFault.Offline),
            reducedMotion = true, onTalk = {}, onEndConversation = {}, onMemory = {}, onFriends = {}, onSettings = {}, onFaultAction = {},
        )
    }
    dump("car-drive") { DriveScreen(HomeUi(online = true), onTalk = {}, onEndConversation = {}) }
}

class HomeDesignDump1600Test {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = device(1600, 900), renderingMode = RenderingMode.NORMAL)

    @Before fun onlyWhenAsked() = Assume.assumeTrue("run with -Pdesign-diff", System.getProperty("design-diff") != null)

    @Test fun screens() = paparazzi.dumpScreens(1600, 900)
}

class HomeDesignDump1251Test {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = device(1251, 900), renderingMode = RenderingMode.NORMAL)

    @Before fun onlyWhenAsked() = Assume.assumeTrue("run with -Pdesign-diff", System.getProperty("design-diff") != null)

    @Test fun screens() = paparazzi.dumpScreens(1251, 900)
}
