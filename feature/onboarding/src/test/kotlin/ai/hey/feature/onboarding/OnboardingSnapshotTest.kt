package ai.hey.feature.onboarding

import ai.hey.designsystem.theme.HeyTheme
import ai.hey.designsystem.component.CharacterLook
import ai.hey.designsystem.component.TalkPhase
import ai.hey.model.OnboardingStep
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

private val c1: List<Pair<String, ConsentFormUi>> = listOf(
    "c1-default" to ConsentFormUi(),
    "c1-required-missing" to ConsentFormUi(voiceRequiredShown = true),
    "c1-saving" to ConsentFormUi(voice = true, memory = true, saving = true),
    "c1-error" to ConsentFormUi(voice = true, failed = true),
)

private val c2: List<Pair<String, PairUi>> = listOf(
    "c2-loading" to PairUi.Loading,
    "c2-pending" to PairUi.Pending("K7M2QX9P"),
    "c2-partial" to PairUi.Pending("K7M2QX9P", phoneEntered = true),
    "c2-claimed" to PairUi.Claimed,
    "c2-expired" to PairUi.Expired("K7M2QX9P"),
    "c2-failed" to PairUi.Failed,
)
private val answers = ai.hey.model.OnboardingAnswers(name = "민수", trips = "출퇴근길", topics = "음악")

private fun Paparazzi.recordSteps(theme: String, dark: Boolean) {
    snapshot("step-title-$theme") { HeyTheme(dark) { TitleScreen(onNext = {}) } }
    snapshot("step-whale-$theme") { HeyTheme(dark) { SpokenStepScreen(SpokenStepUi(OnboardingStep.WhaleIntro, line = "안녕, 나는 빛바다를 지키는 고래야.")) } }
    snapshot("step-q1-listening-$theme") { HeyTheme(dark) { SpokenStepScreen(SpokenStepUi(OnboardingStep.Q1, line = "먼저, 뭐라고 불러 줄까?", phase = TalkPhase.Listening)) } }
    snapshot("step-q2-heard-$theme") { HeyTheme(dark) { SpokenStepScreen(SpokenStepUi(OnboardingStep.Q2, line = "이 차로 주로 어디 다녀?", phase = TalkPhase.Thinking, heard = "회사랑 집")) } }
    snapshot("step-q3-idle-$theme") { HeyTheme(dark) { SpokenStepScreen(SpokenStepUi(OnboardingStep.Q3, line = "차 안에서는 어떤 얘기가 좋아?")) } }
    snapshot("step-summon-$theme") { HeyTheme(dark) { SummonScreen(summoned = false, onSummon = {}) } }
    snapshot("step-summoned-$theme") { HeyTheme(dark) { SummonScreen(summoned = true, onSummon = {}) } }
    snapshot("step-greeting-$theme") { HeyTheme(dark) { SpokenStepScreen(SpokenStepUi(OnboardingStep.Greeting, line = "반가워, 민수! 난 헤이리야.")) } }
    snapshot("step-cards-$theme") { HeyTheme(dark) { KnownCardsScreen(answers = answers) } }
    snapshot("step-cards-memory-off-$theme") { HeyTheme(dark) { KnownCardsScreen(answers = answers, memoryEnabled = false) } }
    snapshot("step-look-$theme") { HeyTheme(dark) { FirstLookScreen(look = CharacterLook(), onLook = {}, onDone = {}) } }
}

private fun Paparazzi.recordAll() {
    listOf(true, false).forEach { dark ->
        val theme = if (dark) "dark" else "light"
        recordSteps(theme, dark)
        c1.forEach { (name, ui) ->
            snapshot("$name-$theme") {
                HeyTheme(dark) { OnboardingScreen(ui, {}, {}, {}, {}, {}, {}) }
            }
        }
        c2.forEach { (name, ui) ->
            snapshot("$name-$theme") { HeyTheme(dark) { PairScreen(ui, {}, {}, {}) } }
        }
    }
}

class Onboarding1600SnapshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = device(1600, 900), renderingMode = RenderingMode.NORMAL)

    @Test fun screens() = paparazzi.recordAll()
}

class Onboarding1280SnapshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = device(1280, 720), renderingMode = RenderingMode.NORMAL)

    @Test fun screens() = paparazzi.recordAll()
}
