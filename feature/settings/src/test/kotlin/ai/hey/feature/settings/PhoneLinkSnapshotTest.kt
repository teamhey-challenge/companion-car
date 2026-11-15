package ai.hey.feature.settings

import ai.hey.designsystem.component.PhoneLinkUi
import ai.hey.designsystem.testing.CarPaparazzi
import ai.hey.designsystem.testing.CarWindow
import ai.hey.designsystem.theme.HeyTheme
import org.junit.Rule
import org.junit.Test


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

private fun CarPaparazzi.recordPhone() {
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
    @get:Rule val paparazzi = CarPaparazzi(CarWindow.Full1600)

    @Test fun screens() = paparazzi.recordPhone()
}

class PhoneLink840SnapshotTest {
    @get:Rule val paparazzi = CarPaparazzi(CarWindow.Half840)

    @Test fun screens() = paparazzi.recordPhone()
}

class PhoneLink560SnapshotTest {
    @get:Rule val paparazzi = CarPaparazzi(CarWindow.Third560)

    @Test fun screens() = paparazzi.recordPhone()
}

class PhoneLink1251SnapshotTest {
    @get:Rule val paparazzi = CarPaparazzi(CarWindow.Car1251)

    @Test fun screens() = paparazzi.recordPhone()
}
