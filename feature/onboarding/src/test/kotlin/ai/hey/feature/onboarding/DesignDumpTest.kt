package ai.hey.feature.onboarding

import ai.hey.designsystem.component.CharacterLook
import ai.hey.designsystem.component.PhoneLinkUi
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

private val answers = OnboardingAnswers(name = "지우", trips = "출퇴근길", topics = "캠핑")

private fun Paparazzi.dumpScreens(w: Int, h: Int) {
    fun dump(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        val dump = DesignDump("$name@${w}x$h", w, h, dark = false)
        snapshot("$name@${w}x$h") { HeyTheme(false) { androidx.compose.runtime.CompositionLocalProvider(ai.hey.designsystem.component.LocalHeyStill provides true) { dump.capture(content) } } }
        dump.write()
    }
    dump("car-onboarding-1") { TitleScreen(onNext = {}) }
    dump("car-onboarding-2") { OnboardingScreen(ConsentFormUi(), {}, {}, {}, {}, {}, {}) }
    dump("car-onboarding-3") {
        SpokenStepScreen(SpokenStepUi(OnboardingStep.Lights, line = "여기 빛 하나하나가 너의 헤이리가 될 수 있어. 이제 너에 대해 알려 줘."))
    }
    dump("car-onboarding-4") { SummonScreen(summoned = false, onSummon = {}) }
    dump("car-onboarding-5") {
        SpokenStepScreen(SpokenStepUi(OnboardingStep.Greeting, line = "지우야! 불빛 보고 바로 올라왔어. 출퇴근길에 매일 같이 다니고, 캠핑 얘기 많이 하자."))
    }
    dump("car-onboarding-6") { KnownCardsScreen(answers = answers, line = "너에 대해 이만큼 알게 됐어. 이건 기억해 둘게, 언제든 지울 수 있어.") }
    dump("car-onboarding-7") { FirstLookScreen(look = CharacterLook(), onLook = {}, onDone = {}, line = "나 어떤 모습이 좋아? 골라 줘.") }
    dump("car-onboarding-8") {
        QrStepScreen(PhoneLinkUi.Code("K7M2QX9P"), onLater = {}, onDone = {}, onRetry = {}, line = "폰에서도 나랑 얘기하고 싶으면 이걸 찍어 줘. 나중에 해도 돼.")
    }
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
