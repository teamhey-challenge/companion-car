package ai.hey.feature.onboarding

import ai.hey.designsystem.component.LocalHeyStill
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

/** Every shot draws the settled picture: polaroids dropped, heyri faded in (the studio's screens are judged at rest). */
private fun Paparazzi.shot(name: String, dark: Boolean, content: @androidx.compose.runtime.Composable () -> Unit) =
    snapshot(name) { HeyTheme(dark) { androidx.compose.runtime.CompositionLocalProvider(LocalHeyStill provides true) { content() } } }

private fun Paparazzi.recordSteps(theme: String, dark: Boolean) {
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
    shot("step-qr-$theme", dark) { QrStepScreen(PairUi.Pending("K7M2QX9P"), onLater = {}, onRetry = {}) }
    shot("step-qr-expired-$theme", dark) { QrStepScreen(PairUi.Expired("K7M2QX9P"), onLater = {}, onRetry = {}) }
}

private fun Paparazzi.recordAll() {
    listOf(true, false).forEach { dark ->
        val theme = if (dark) "dark" else "light"
        recordSteps(theme, dark)
        c1.forEach { (name, ui) -> shot("$name-$theme", dark) { OnboardingScreen(ui, {}, {}, {}, {}, {}, {}) } }
        c2.forEach { (name, ui) -> shot("$name-$theme", dark) { PairScreen(ui, {}, {}, {}) } }
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

/** The 1/2 width window next to navigation (2560 px Connect-S half). */
class Onboarding840SnapshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = device(840, 900), renderingMode = RenderingMode.NORMAL)

    @Test fun screens() = paparazzi.recordAll()
}

/** The 1/3 width window (Connect-W). */
class Onboarding560SnapshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = device(560, 900), renderingMode = RenderingMode.NORMAL)

    @Test fun screens() = paparazzi.recordAll()
}
