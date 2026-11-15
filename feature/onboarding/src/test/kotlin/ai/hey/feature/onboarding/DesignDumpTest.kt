package ai.hey.feature.onboarding

import ai.hey.designsystem.component.CharacterLook
import ai.hey.designsystem.component.TalkPhase
import ai.hey.designsystem.gallery.DesignDump
import ai.hey.designsystem.theme.HeyTheme
import ai.hey.model.OnboardingAnswers
import ai.hey.model.OnboardingStep
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
 * PLEOS-199: dumps every HeyText of each onboarding step (bounds in dp, TextStyle) to `build/reports/design-diff/<name>@<w>x<h>.json`.
 * `ops/design-diff/screens.json` in pleos-challenge maps car/onboarding/1..8 to these names. 1251x900 is the real Pleos window
 * (1682x1210 px / designScale 1.3444), 1600x900 the studio canvas. Runs only with `-Pdesign-diff`; nothing is recorded or verified.
 */
private fun device(w: Int, h: Int) = DeviceConfig.PIXEL_C.copy(
    screenWidth = w, screenHeight = h, xdpi = 160, ydpi = 160, density = Density.MEDIUM, orientation = ScreenOrientation.LANDSCAPE,
)

private val answers = OnboardingAnswers(name = "민수", trips = "출퇴근길", topics = "음악")

private fun Paparazzi.dumpScreens(w: Int, h: Int) {
    fun dump(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        val dump = DesignDump("$name@${w}x$h", w, h, dark = false)
        snapshot("$name@${w}x$h") { HeyTheme(false) { dump.capture(content) } }
        dump.write()
    }
    dump("car-onboarding-1") { TitleScreen(onNext = {}) }
    dump("car-onboarding-2") { OnboardingScreen(ConsentFormUi(), {}, {}, {}, {}, {}, {}) }
    dump("car-onboarding-3") {
        SpokenStepScreen(SpokenStepUi(OnboardingStep.Q1, line = "먼저, 뭐라고 불러 줄까?", phase = TalkPhase.Listening))
    }
    dump("car-onboarding-4") { SummonScreen(summoned = false, onSummon = {}) }
    dump("car-onboarding-5") { SpokenStepScreen(SpokenStepUi(OnboardingStep.Greeting, line = "반가워, 민수! 난 헤이리야.")) }
    dump("car-onboarding-6") { KnownCardsScreen(answers = answers) }
    dump("car-onboarding-7") { FirstLookScreen(look = CharacterLook(), onLook = {}, onDone = {}) }
    dump("car-onboarding-8") { PairScreen(PairUi.Pending("K7M2QX9P"), {}, {}, {}) }
}

class OnboardingDesignDump1600Test {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = device(1600, 900), renderingMode = RenderingMode.NORMAL)

    @Before fun onlyWhenAsked() = Assume.assumeTrue("run with -Pdesign-diff", System.getProperty("design-diff") != null)

    @Test fun screens() = paparazzi.dumpScreens(1600, 900)
}

class OnboardingDesignDump1251Test {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = device(1251, 900), renderingMode = RenderingMode.NORMAL)

    @Before fun onlyWhenAsked() = Assume.assumeTrue("run with -Pdesign-diff", System.getProperty("design-diff") != null)

    @Test fun screens() = paparazzi.dumpScreens(1251, 900)
}
