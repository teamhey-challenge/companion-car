package ai.hey.feature.onboarding

import ai.hey.designsystem.component.LocalHeyStill
import ai.hey.designsystem.component.PhoneLinkUi
import ai.hey.designsystem.theme.HeyTheme
import ai.hey.designsystem.component.CharacterLook
import ai.hey.designsystem.component.TalkPhase
import ai.hey.model.OnboardingStep
import ai.hey.designsystem.testing.CarPaparazzi
import ai.hey.designsystem.testing.CarWindow
import org.junit.Rule
import org.junit.Test


private val consent: List<Pair<String, ConsentFormUi>> = listOf(
    "o2-default" to ConsentFormUi(),
    "o2-required-missing" to ConsentFormUi(voiceRequiredShown = true),
    "o2-saving" to ConsentFormUi(voice = true, memory = true, saving = true),
    "o2-error" to ConsentFormUi(voice = true, failed = true),
    "o2-ticked" to ConsentFormUi(voice = true, memory = true),
)

private val answers = ai.hey.model.OnboardingAnswers(name = "민수", trips = "출퇴근길", topics = "음악")

/** Every shot draws the settled picture: polaroids dropped, heyri faded in (the studio's screens are judged at rest). */
private fun CarPaparazzi.shot(name: String, dark: Boolean, content: @androidx.compose.runtime.Composable () -> Unit) =
    snapshot(name) { HeyTheme(dark) { androidx.compose.runtime.CompositionLocalProvider(LocalHeyStill provides true) { content() } } }

private fun CarPaparazzi.recordSteps(theme: String, dark: Boolean) {
    shot("step-title-$theme", dark) { TitleScreen(onNext = {}) }
    shot("step-whale-$theme", dark) { SpokenStepScreen(SpokenStepUi(OnboardingStep.WhaleIntro, line = "안녕, 나는 빛바다를 지키는 고래야. 여기 헤이리들은 길을 함께 달릴 사람 단짝을 찾고 있어. 너랑 꼭 맞는 헤이리를 찾아 줄게.")) }
    shot("step-lights-$theme", dark) { SpokenStepScreen(SpokenStepUi(OnboardingStep.Lights, line = "여기 빛 하나하나가 너의 헤이리가 될 수 있어.")) }
    shot("step-q1-listening-$theme", dark) { SpokenStepScreen(SpokenStepUi(OnboardingStep.Q1, line = "먼저, 뭐라고 불러 줄까?", phase = TalkPhase.Listening)) }
    shot("step-q2-heard-$theme", dark) { SpokenStepScreen(SpokenStepUi(OnboardingStep.Q2, line = "이 차로 주로 어디 다녀?", phase = TalkPhase.Thinking, heard = "회사랑 집")) }
    shot("step-q3-idle-$theme", dark) { SpokenStepScreen(SpokenStepUi(OnboardingStep.Q3, line = "차 안에서는 어떤 얘기가 좋아?")) }
    shot("step-found-$theme", dark) { SpokenStepScreen(SpokenStepUi(OnboardingStep.Found, line = "찾았다. 너랑 꼭 맞는 헤이리야. 길게 눌러서 만나 봐.")) }
    shot("step-summon-$theme", dark) { SummonScreen(summoned = false, onSummon = {}) }
    shot("step-summoned-$theme", dark) { SummonScreen(summoned = true, onSummon = {}) }
    shot("step-greeting-$theme", dark) { SpokenStepScreen(SpokenStepUi(OnboardingStep.Greeting, line = "반가워, 민수! 난 헤이리야.")) }
    shot("step-cards-$theme", dark) { KnownCardsScreen(answers = answers, line = "너에 대해 이만큼 알게 됐어.") }
    shot("step-cards-one-$theme", dark) { KnownCardsScreen(answers = ai.hey.model.OnboardingAnswers(name = "민수")) }
    shot("step-cards-memory-off-$theme", dark) { KnownCardsScreen(answers = answers, memoryEnabled = false) }
    shot("step-look-$theme", dark) { FirstLookScreen(look = CharacterLook(), onLook = {}, onDone = {}) }
    shot("step-qr-$theme", dark) { QrStepScreen(PhoneLinkUi.Code("K7M2QX9P"), onLater = {}, onDone = {}, onRetry = {}) }
    shot("step-qr-loading-$theme", dark) { QrStepScreen(PhoneLinkUi.Loading, onLater = {}, onDone = {}, onRetry = {}) }
    shot("step-qr-linked-$theme", dark) { QrStepScreen(PhoneLinkUi.Flash("민수"), onLater = {}, onDone = {}, onRetry = {}) }
    shot("step-qr-offline-$theme", dark) { QrStepScreen(PhoneLinkUi.Offline, onLater = {}, onDone = {}, onRetry = {}) }
}

private fun CarPaparazzi.recordAll() {
    listOf(true, false).forEach { dark ->
        val theme = if (dark) "dark" else "light"
        recordSteps(theme, dark)
        consent.forEach { (name, ui) -> shot("$name-$theme", dark) { OnboardingScreen(ui, {}, {}, {}, {}, {}) } }
    }
}

class Onboarding1600SnapshotTest {
    @get:Rule val paparazzi = CarPaparazzi(CarWindow.Full1600)

    @Test fun screens() = paparazzi.recordAll()
}

class Onboarding1280SnapshotTest {
    @get:Rule val paparazzi = CarPaparazzi(CarWindow.Small1280)

    @Test fun screens() = paparazzi.recordAll()
}

/** The real Pleos app window (1682 x 1210 px = 1251 x 900 design dp). */
class Onboarding1251SnapshotTest {
    @get:Rule val paparazzi = CarPaparazzi(CarWindow.Car1251)

    @Test fun screens() = paparazzi.recordAll()
}

/** Pleos multi-window, 1/2 width (840 x 1212 dp) and 1/3 width (560 x 1212 dp); drawn at the 900 dp design height. */
class Onboarding840SnapshotTest {
    @get:Rule val paparazzi = CarPaparazzi(CarWindow.Half840)

    @Test fun screens() = paparazzi.recordAll()
}

class Onboarding560SnapshotTest {
    @get:Rule val paparazzi = CarPaparazzi(CarWindow.Third560)

    @Test fun screens() = paparazzi.recordAll()
}
