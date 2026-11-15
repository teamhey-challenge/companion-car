package ai.hey.feature.settings

import ai.hey.designsystem.component.PhoneLinkUi
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
 * PLEOS-199: dumps every HeyText (bounds in dp, TextStyle) to `build/reports/design-diff/<name>@<w>x<h>.json` for
 * `ops/design-diff/screens.json`. 1251x900 is the real Pleos window, 1600x900 the studio canvas. Runs only with `-Pdesign-diff`;
 * nothing is recorded or verified.
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
    dump("car-settings-phone") { PhoneLinkScreen(PhoneLinkUi.Code("K7M2QX9P"), onBack = {}, onUnpair = {}, onRetry = {}) }
    dump("car-settings") {
        SettingsScreen(
            SettingsUi(name = "민수", phonePaired = true), onBack = {}, onProactive = {}, onMemoryEnabled = {}, onSpeech = {}, onName = {},
            onLook = {}, onOpenMemory = {}, onOpenPair = {}, onWithdraw = {},
        )
    }
    val noop = FriendsActions({}, {}, {}, {}, {}, {}, {})
    val ui = FriendsUi(
        friends = listOf(
            FriendRowUi("f1", "민수", arrivalOn = false, lastMessage = "오늘 발표 잘할 거야. 응원할게!"),
            FriendRowUi("f2", "지은", arrivalOn = true, lastMessage = "저녁에 보자"),
        ),
        friendsLoaded = true,
        code = FriendCodeUi.Shown("K7M2QX", "https://hey.lastmile.ltd/f/K7M2QX"),
    )
    dump("car-friends") { FriendsScreen(ui, FriendsView.List, noop) }
    dump("car-friends-code") { FriendsScreen(ui, FriendsView.Code, noop) }
}

class SettingsDesignDump1600Test {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = device(1600, 900), renderingMode = RenderingMode.NORMAL)

    @Before fun onlyWhenAsked() = Assume.assumeTrue("run with -Pdesign-diff", System.getProperty("design-diff") != null)

    @Test fun screens() = paparazzi.dumpScreens(1600, 900)
}

class SettingsDesignDump1251Test {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = device(1251, 900), renderingMode = RenderingMode.NORMAL)

    @Before fun onlyWhenAsked() = Assume.assumeTrue("run with -Pdesign-diff", System.getProperty("design-diff") != null)

    @Test fun screens() = paparazzi.dumpScreens(1251, 900)
}
