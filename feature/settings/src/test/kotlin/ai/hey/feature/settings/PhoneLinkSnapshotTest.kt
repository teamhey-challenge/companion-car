package ai.hey.feature.settings

import ai.hey.designsystem.component.PhoneLinkUi
import ai.hey.designsystem.theme.HeyTheme
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import org.junit.Rule
import org.junit.Test

private fun phoneDevice(width: Int, height: Int) = DeviceConfig.PIXEL_C.copy(
    screenWidth = width,
    screenHeight = height,
    xdpi = 160,
    ydpi = 160,
    density = Density.MEDIUM,
    orientation = ScreenOrientation.LANDSCAPE,
)

private class PhoneShot(val ui: PhoneLinkUi, val unpairFailed: Boolean = false)

/** Unlinked (QR + code), linked (account name), the moment of linking and the server-out-of-reach error, plus a code-less wait. */
private val phoneShots: List<Pair<String, PhoneShot>> = listOf(
    "phone-unlinked" to PhoneShot(PhoneLinkUi.Code("K7M3Q9XD")),
    "phone-linked" to PhoneShot(PhoneLinkUi.Linked("민수")),
    "phone-linked-unpair-failed" to PhoneShot(PhoneLinkUi.Linked("민수"), unpairFailed = true),
    "phone-flash" to PhoneShot(PhoneLinkUi.Flash("민수")),
    "phone-error" to PhoneShot(PhoneLinkUi.Offline),
    "phone-loading" to PhoneShot(PhoneLinkUi.Loading),
)

private fun Paparazzi.recordPhone() {
    phoneShots.forEach { (name, shot) ->
        listOf(true, false).forEach { dark ->
            snapshot("$name-${if (dark) "dark" else "light"}") {
                HeyTheme(dark) {
                    PhoneLinkScreen(ui = shot.ui, onBack = {}, onUnpair = {}, onRetry = {}, unpairFailed = shot.unpairFailed)
                }
            }
        }
    }
}

class PhoneLink1600SnapshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = phoneDevice(1600, 900), renderingMode = RenderingMode.NORMAL)

    @Test fun screens() = paparazzi.recordPhone()
}

class PhoneLink840SnapshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = phoneDevice(840, 900), renderingMode = RenderingMode.NORMAL)

    @Test fun screens() = paparazzi.recordPhone()
}

class PhoneLink560SnapshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = phoneDevice(560, 900), renderingMode = RenderingMode.NORMAL)

    @Test fun screens() = paparazzi.recordPhone()
}
