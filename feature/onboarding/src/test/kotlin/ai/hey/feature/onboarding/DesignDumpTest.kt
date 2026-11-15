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
 * PLEOS-199: dumps every HeyText of each onboarding step (bounds in dp, TextStyle) to `build/reports/design-diff/<name>.json`.
 * `ops/design-diff/screens.json` in pleos-challenge maps car/onboarding/1..8 to these names. Runs only with `-Pdesign-diff`;
 * nothing is recorded or verified.
 */
class OnboardingDesignDumpTest {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_C.copy(
            screenWidth = 1600, screenHeight = 900, xdpi = 160, ydpi = 160,
            density = Density.MEDIUM, orientation = ScreenOrientation.LANDSCAPE,
        ),
        renderingMode = RenderingMode.NORMAL,
    )

    @Before fun onlyWhenAsked() = Assume.assumeTrue("run with -Pdesign-diff", System.getProperty("design-diff") != null)

    private val answers = OnboardingAnswers(name = "민수", trips = "출퇴근길", topics = "음악")

    private fun dump(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        val dump = DesignDump(name, 1600, 900, dark = false)
        paparazzi.snapshot(name) { HeyTheme(false) { dump.capture(content) } }
        dump.write()
    }

    @Test fun o1() = dump("car-onboarding-1") { TitleScreen(onNext = {}) }

    @Test fun o2() = dump("car-onboarding-2") { OnboardingScreen(ConsentFormUi(), {}, {}, {}, {}, {}, {}) }

    @Test fun o3() = dump("car-onboarding-3") {
        SpokenStepScreen(SpokenStepUi(OnboardingStep.Q1, line = "먼저, 뭐라고 불러 줄까?", phase = TalkPhase.Listening))
    }

    @Test fun o4() = dump("car-onboarding-4") { SummonScreen(summoned = false, onSummon = {}) }

    @Test fun o5() = dump("car-onboarding-5") {
        SpokenStepScreen(SpokenStepUi(OnboardingStep.Greeting, line = "반가워, 민수! 난 헤이리야."))
    }

    @Test fun o6() = dump("car-onboarding-6") { KnownCardsScreen(answers = answers) }

    @Test fun o7() = dump("car-onboarding-7") { FirstLookScreen(look = CharacterLook(), onLook = {}, onDone = {}) }

    @Test fun o8() = dump("car-onboarding-8") { PairScreen(PairUi.Pending("K7M2QX9P"), {}, {}, {}) }
}
