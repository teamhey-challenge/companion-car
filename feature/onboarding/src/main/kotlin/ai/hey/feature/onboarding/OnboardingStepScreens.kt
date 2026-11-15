package ai.hey.feature.onboarding

import ai.hey.designsystem.component.CharacterLook
import ai.hey.designsystem.component.CharacterSize
import ai.hey.designsystem.component.CharacterSlot
import ai.hey.designsystem.component.GlassButton
import ai.hey.designsystem.component.HeyFadeIn
import ai.hey.designsystem.component.OnbAlign
import ai.hey.designsystem.component.OnbCaption
import ai.hey.designsystem.component.OnbDressChoice
import ai.hey.designsystem.component.OnbDressGrid
import ai.hey.designsystem.component.OnbHoldRing
import ai.hey.designsystem.component.OnbLights
import ai.hey.designsystem.component.OnbMeetTitle
import ai.hey.designsystem.component.OnbPanelText
import ai.hey.designsystem.component.OnbPanelTitle
import ai.hey.designsystem.component.OnbPolaroid
import ai.hey.designsystem.component.OnbPolaroids
import ai.hey.designsystem.component.OnbProgress
import ai.hey.designsystem.component.OnbScene
import ai.hey.designsystem.component.OnbSceneScope
import ai.hey.designsystem.component.OnbShot
import ai.hey.designsystem.component.OnbSide
import ai.hey.designsystem.component.OnbSidePanel
import ai.hey.designsystem.component.OnbSwatch
import ai.hey.designsystem.component.OnbTitle
import ai.hey.designsystem.component.TalkPhase
import ai.hey.designsystem.layout.HStack
import ai.hey.designsystem.layout.HeyAlign
import ai.hey.designsystem.layout.HeyArrange
import ai.hey.designsystem.layout.HeyFiller
import ai.hey.designsystem.layout.HeyWindow
import ai.hey.designsystem.layout.VStack
import ai.hey.designsystem.layout.carCharacterBand
import ai.hey.designsystem.layout.carTitleTop
import ai.hey.designsystem.layout.fillSize
import ai.hey.designsystem.layout.fillWidth
import ai.hey.designsystem.layout.insetBottom
import ai.hey.designsystem.layout.isCompactWindow
import ai.hey.designsystem.layout.sceneRing
import ai.hey.designsystem.theme.HeyGap
import ai.hey.designsystem.theme.HeyTheme
import ai.hey.model.OnboardingAnswers
import ai.hey.model.OnboardingStep
import androidx.compose.runtime.Composable
import ai.hey.designsystem.layout.insetTop
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/*
 * D-48 onboarding screens (PLEOS-190), built from the studio's design: one living scene (video over its still) with the
 * lights, subtitle layer, hold ring, polaroids and the side panels of design-system parts on top. They only draw what the step
 * machine in the session module decided. Spoken steps work while driving too, but then the app shows the driving screen, so
 * these screens exist for the parked car only (subtitles are shown here and never while driving).
 */

/** What a spoken step shows: the line the voice is saying (subtitle) and what the microphone is doing. */
data class SpokenStepUi(
    val step: OnboardingStep,
    /** The text of the spoken line; empty until the server sent it (then the step's own fixed text is shown). */
    val line: String = "",
    val phase: TalkPhase = TalkPhase.Idle,
    /** What the driver answered to the question (shown while it is checked). */
    val heard: String? = null,
    /** How heyri looks (greeting and farewell show the character). */
    val look: CharacterLook = CharacterLook(),
)

/** The fixed text of each spoken step: what shows until (or instead of) the server's line (D-54: never stuck on the network). */
private fun OnboardingStep.titleRes(): Int = when (this) {
    OnboardingStep.WhaleIntro -> R.string.step_whale_title
    OnboardingStep.Lights -> R.string.step_lights_title
    OnboardingStep.Q1 -> R.string.step_q1_title
    OnboardingStep.Q2 -> R.string.step_q2_title
    OnboardingStep.Q3 -> R.string.step_q3_title
    OnboardingStep.Found -> R.string.step_found_title
    OnboardingStep.Greeting -> R.string.step_greeting_title
    OnboardingStep.Farewell -> R.string.step_farewell_title
    else -> R.string.step_whale_title
}

/** The scene of a step: which shot plays under it. The cuts are chained, each starts where the one before ended. */
private fun OnboardingStep.shot(): OnbShot = when (this) {
    OnboardingStep.Title -> OnbShot.Title
    OnboardingStep.WhaleIntro -> OnbShot.Intro
    OnboardingStep.Found -> OnbShot.Match
    OnboardingStep.Greeting, OnboardingStep.Memory, OnboardingStep.Dress, OnboardingStep.Qr, OnboardingStep.Farewell -> OnbShot.Sea
    else -> OnbShot.Whale
}

/** One window onto the scene; [compact] is true in a 1/2 or 1/3 window (stacked layout, small character, full-width panels). */
@Composable
private fun OnbStage(shot: OnbShot, modifier: Modifier = Modifier, content: @Composable OnbSceneScope.(compact: Boolean) -> Unit) {
    HeyWindow(modifier) { width, height ->
        val compact = isCompactWindow(width, height)
        OnbScene(shot, Modifier.fillSize()) { content(compact) }
    }
}

/**
 * Heyri in the scene: centred, or on the left half while a panel is open on the right ([beside]). In compact windows the
 * character is the small one, in a band above the panel, or the compact one in the middle when no panel is open.
 */
@Composable
private fun OnbSceneScope.HeyriInScene(look: CharacterLook, compact: Boolean, beside: Boolean, description: String) {
    val still = @Composable { size: CharacterSize -> HeyFadeIn { CharacterSlot(size, look = look, contentDescription = description) } }
    when {
        compact && beside -> VStack(HeyGap.G16, modifier = Modifier.place(OnbAlign.TopCenter).fillWidth(), align = HeyAlign.Center) { still(CharacterSize.Small) }
        compact -> VStack(HeyGap.G16, modifier = Modifier.place(OnbAlign.Center).fillWidth(), align = HeyAlign.Center) { still(CharacterSize.Compact) }
        beside -> HStack(HeyGap.G32, modifier = Modifier.place(OnbAlign.Center).fillSize()) {
            VStack(HeyGap.G16, modifier = Modifier.grow(), align = HeyAlign.Center, arrange = HeyArrange.Center) { still(CharacterSize.Stage) }
            HeyFiller()
        }
        else -> VStack(HeyGap.G16, modifier = Modifier.place(OnbAlign.Center).fillWidth(), align = HeyAlign.Center) { still(CharacterSize.Stage) }
    }
}

/** O1 title: "Hey" over the coast road and one press on "다음" (parked only: it needs a press). */
@Composable
fun TitleScreen(onNext: () -> Unit, modifier: Modifier = Modifier) {
    OnbStage(OnboardingStep.Title.shot(), modifier) { compact ->
        OnbTitle(
            stringResource(R.string.step_title_name), stringResource(R.string.step_title_subtitle),
            Modifier.place(OnbAlign.TopCenter).carTitleTop(), compact = compact,
        )
        GlassButton(stringResource(R.string.step_next), onNext, Modifier.place(OnbAlign.BottomCenter).insetBottom(HeyGap.G32))
    }
}

/** How many of the 48 lights are lit at each step: each answer narrows the field of possible heyris (studio: 40, 16, 7, 3). */
private fun OnboardingStep.lights(): Int = when (this) {
    OnboardingStep.Lights, OnboardingStep.Q1 -> 40
    OnboardingStep.Q2 -> 16
    OnboardingStep.Q3 -> 7
    OnboardingStep.Found -> 3
    else -> 0
}

/**
 * The whale (or heyri, for the greeting and the farewell) speaks: the scene plays, the subtitle shows the line. A question has
 * the listening pill with "넘어갈래" while the microphone is open. While the car moves the driving screen replaces this one; the
 * voice goes on.
 */
@Composable
fun SpokenStepScreen(ui: SpokenStepUi, onSkip: () -> Unit = {}, modifier: Modifier = Modifier) {
    val step = ui.step
    val heyri = step == OnboardingStep.Greeting || step == OnboardingStep.Farewell
    OnbStage(step.shot(), modifier) { compact ->
        if (step.lights() > 0) OnbLights(step.lights(), Modifier.fillSize())
        if (step.isQuestion || step == OnboardingStep.Lights) {
            val answered = when (step) {
                OnboardingStep.Q1 -> 0f
                OnboardingStep.Q2 -> 1f
                OnboardingStep.Q3 -> 2f
                else -> 0f
            }
            val half = if (ui.phase == TalkPhase.Listening) 0.5f else 0f
            OnbProgress(stringResource(R.string.step_summon_title), (answered + half) / 3f, Modifier.place(OnbAlign.TopCenter).insetTop(HeyGap.G32))
        }
        if (heyri) {
            HeyriInScene(ui.look, compact, beside = false, description = stringResource(R.string.onboard_character))
            if (step == OnboardingStep.Greeting) {
                OnbMeetTitle(
                    stringResource(R.string.onb_meet_name),
                    Modifier.place(OnbAlign.TopCenter).insetTop(HeyGap.G32), compact = compact,
                )
            }
        }
        OnbCaption(
            text = ui.line.ifBlank { stringResource(step.titleRes()) },
            modifier = Modifier.place(OnbAlign.BottomCenter),
            heard = ui.heard,
            listening = step.isQuestion && ui.phase == TalkPhase.Listening,
            listeningLabel = stringResource(R.string.onb_listening),
            skipLabel = stringResource(R.string.step_skip),
            onSkip = if (step.isQuestion) onSkip else null,
            compact = compact,
        )
    }
}

/** How long the hold on the ring takes (studio HOLD_MS). */
private const val HOLD_MILLIS = 1_800L

/**
 * O4 (parked only): the golden ring itself is pressed. A plain tap does nothing; holding makes the halo swell and, at the end,
 * calls [onSummon]; the accessibility action does the same. Once [summoned] the camera dives through the ring (the dive video).
 */
@Composable
fun SummonScreen(summoned: Boolean, onSummon: () -> Unit, modifier: Modifier = Modifier) {
    OnbStage(if (summoned) OnbShot.Dive else OnbShot.Ring, modifier) { compact ->
        if (!summoned) {
            OnbHoldRing(
                label = stringResource(R.string.step_summon_button),
                holdMillis = HOLD_MILLIS,
                onComplete = onSummon,
                modifier = Modifier.place(OnbAlign.Center).sceneRing(),
            )
            OnbCaption(stringResource(R.string.step_summon_button), Modifier.place(OnbAlign.BottomCenter), compact = compact)
        }
    }
}

/** What the "너를 이만큼 알아" step shows (D-52: memory saving off shows guidance and never the cards). */
enum class KnownCardsMode { Cards, Empty, MemoryOff }

fun knownCardsMode(memoryEnabled: Boolean, answers: OnboardingAnswers): KnownCardsMode = when {
    !memoryEnabled -> KnownCardsMode.MemoryOff
    answers.any -> KnownCardsMode.Cards
    else -> KnownCardsMode.Empty
}

/**
 * O6 "너를 이만큼 알아": one polaroid per answer the driver gave (a skipped question has no polaroid; the picture of each is
 * fixed, only the caption comes from the answer, D-54). Heyri says the line while the polaroids drop, so there is no button.
 * With memory saving off the polaroids are replaced by "기억 저장을 켜면 이렇게 기억해요".
 */
@Composable
fun KnownCardsScreen(
    answers: OnboardingAnswers,
    modifier: Modifier = Modifier,
    memoryEnabled: Boolean = true,
    line: String = "",
    look: CharacterLook = CharacterLook(),
) {
    val mode = knownCardsMode(memoryEnabled, answers)
    val polaroids = listOfNotNull(
        answers.name?.let { OnbPolaroid(0, stringResource(R.string.step_card_name, it)) },
        answers.trips?.let { OnbPolaroid(1, stringResource(R.string.step_card_trips, it)) },
        answers.topics?.let { OnbPolaroid(2, stringResource(R.string.step_card_topics, it)) },
    )
    OnbStage(OnbShot.Sea, modifier) { compact ->
        if (mode == KnownCardsMode.Cards) {
            OnbPolaroids(stringResource(R.string.step_cards_title), polaroids)
        } else {
            HeyriInScene(look, compact, beside = true, description = stringResource(R.string.onboard_character))
            OnbSidePanel(OnbSide.End, compact, modifier = if (compact) Modifier.carCharacterBand() else Modifier) {
                OnbPanelTitle(stringResource(R.string.step_cards_title), compact = compact)
                OnbPanelText(stringResource(if (mode == KnownCardsMode.MemoryOff) R.string.step_cards_memory_off else R.string.step_cards_empty))
            }
        }
        if (line.isNotBlank()) OnbCaption(line, Modifier.place(OnbAlign.BottomCenter), compact = compact)
    }
}

/** (f) The first look (O7, parked only): heyri steps to the left and the five choices (body colour 2, hat 2, neck 1) show on the right. */
@Composable
fun FirstLookScreen(
    look: CharacterLook,
    onLook: (CharacterLook) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    line: String = "",
) {
    val dark = HeyTheme.dark
    OnbStage(OnbShot.Sea, modifier) { compact ->
        HeyriInScene(look, compact, beside = true, description = stringResource(R.string.onboard_character))
        OnbSidePanel(
            side = OnbSide.End,
            compact = compact,
            modifier = if (compact) Modifier.carCharacterBand() else Modifier,
            footer = { GlassButton(stringResource(R.string.onb_dress_done), onDone) },
        ) {
            OnbPanelTitle(stringResource(R.string.onb_dress_title), compact = compact)
            OnbDressGrid(
                listOf(
                    OnbDressChoice(stringResource(R.string.onb_dress_aqua), OnbSwatch.Aqua, look.body(dark) == 0) { onLook(look.copy(bodyColor = 0)) },
                    OnbDressChoice(stringResource(R.string.onb_dress_peach), OnbSwatch.Peach, look.body(dark) == 1) { onLook(look.copy(bodyColor = 1)) },
                    OnbDressChoice(stringResource(R.string.step_look_beanie), OnbSwatch.Beanie, look.hat == 1) { onLook(look.copy(hat = if (look.hat == 1) 0 else 1)) },
                    OnbDressChoice(stringResource(R.string.step_look_bucket), OnbSwatch.Bucket, look.hat == 2) { onLook(look.copy(hat = if (look.hat == 2) 0 else 2)) },
                    OnbDressChoice(stringResource(R.string.step_look_scarf), OnbSwatch.Scarf, look.neck == 1) { onLook(look.copy(neck = if (look.neck == 1) 0 else 1)) },
                ),
            )
        }
        if (line.isNotBlank()) OnbCaption(line, Modifier.place(OnbAlign.BottomCenter), narrow = !compact, compact = compact)
    }
}
