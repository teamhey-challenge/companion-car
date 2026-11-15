package ai.hey.feature.home

import ai.hey.designsystem.theme.HeyTheme
import ai.hey.session.CarFault
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

private val online = HomeUi(online = true)

/** PLEOS-200: trip end (`car/home#trip-end`) and the parked error variants of `car/error` (screens-and-flows 2, 4.9). */
private val parkedShots: List<Pair<String, HomeUi>> = listOf(
    "s-trip-end-writing" to online.copy(tripNote = "오늘도 같이 달렸어. 오늘 일기 써 뒀어, 폰에서 봐!", tripDiary = true),
    "s-trip-end" to online.copy(tripNote = "오늘도 같이 달렸어. 오늘 일기 써 뒀어, 폰에서 봐!", tripDiary = true, tripMomentDone = true),
    "s-trip-end-no-diary" to online.copy(tripNote = "오늘도 같이 달렸어."),
    "s-err-offline" to HomeUi(online = false, fault = CarFault.Offline),
    "s-err-microphone" to online.copy(fault = CarFault.Microphone),
    "s-err-server" to online.copy(fault = CarFault.Server),
    "s-err-recognition" to online.copy(fault = CarFault.Recognition),
    "s-err-audio" to online.copy(fault = CarFault.Audio),
)

/** Driving variants: the screen stays the drive screen, one icon says what is wrong (voice is spoken, not drawn). */
private val drivingShots: List<Pair<String, HomeUi>> = listOf(
    "s-drive-err-offline" to HomeUi(online = false, fault = CarFault.Offline),
    "s-drive-err-microphone" to online.copy(fault = CarFault.Microphone),
    "s-drive-err-server" to online.copy(fault = CarFault.Server),
    "s-drive-err-audio" to online.copy(fault = CarFault.Audio),
)

private fun Paparazzi.recordStates() {
    listOf(true, false).forEach { dark ->
        val theme = if (dark) "dark" else "light"
        parkedShots.forEach { (name, ui) ->
            snapshot("$name-$theme") {
                HeyTheme(dark) {
                    HomeScreen(ui, reducedMotion = true, onTalk = {}, onEndConversation = {}, onMemory = {}, onSettings = {}, onFaultAction = {})
                }
            }
        }
        drivingShots.forEach { (name, ui) ->
            snapshot("$name-$theme") {
                HeyTheme(dark) { DriveScreen(ui, onTalk = {}, onEndConversation = {}) }
            }
        }
    }
}

class States1600SnapshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = device(1600, 900), renderingMode = RenderingMode.NORMAL)

    @Test fun screens() = paparazzi.recordStates()
}

class States560SnapshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = device(560, 900), renderingMode = RenderingMode.NORMAL)

    @Test fun screens() = paparazzi.recordStates()
}

/** The real car window: 1682x1210 px at designScale 1.3444 = 1251x900 dp. */
class States1251SnapshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = device(1251, 900), renderingMode = RenderingMode.NORMAL)

    @Test fun screens() = paparazzi.recordStates()
}
