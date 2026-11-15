package ai.hey.feature.onboarding

import ai.hey.designsystem.component.AssetSlot
import ai.hey.designsystem.component.CarFrame
import ai.hey.designsystem.component.CharacterLook
import ai.hey.designsystem.component.CharacterSize
import ai.hey.designsystem.component.CharacterSlot
import ai.hey.designsystem.component.ChoiceRow
import ai.hey.designsystem.component.GlassButton
import ai.hey.designsystem.component.HeaderBar
import ai.hey.designsystem.component.HeyFadeIn
import ai.hey.designsystem.component.HeySegmentOption
import ai.hey.designsystem.component.HeyText
import ai.hey.designsystem.component.MemoryRow
import ai.hey.designsystem.component.PageHeading
import ai.hey.designsystem.component.StatusBadge
import ai.hey.designsystem.component.TalkPhase
import ai.hey.designsystem.layout.HStack
import ai.hey.designsystem.layout.HeyAlign
import ai.hey.designsystem.layout.VStack
import ai.hey.designsystem.layout.fillWidth
import ai.hey.designsystem.theme.HeyGap
import ai.hey.designsystem.theme.HeyTheme
import ai.hey.designsystem.theme.HeyTextVariant
import ai.hey.designsystem.theme.HeyTone
import ai.hey.model.OnboardingAnswers
import ai.hey.model.OnboardingStep
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/*
 * D-48 onboarding screens after C1. Thin on purpose (D-49): they only draw what the step machine in the session module
 * decided, from design-system parts. Art that has not arrived (the whale picture) is an empty, named [AssetSlot]; swap that one
 * line for the real picture later without touching any logic. Spoken steps (whale, questions, greeting) work while driving too,
 * but then the app shows C5, so these screens exist for the parked car only.
 */

/** What a spoken step shows: the line the voice is saying (subtitle, parked only) and what the microphone is doing. */
data class SpokenStepUi(
    val step: OnboardingStep,
    /** The text of the spoken line; empty until the server sent it (then the step's own fixed text is shown). */
    val line: String = "",
    val phase: TalkPhase = TalkPhase.Idle,
    /** What the driver answered to the question (shown while it is checked). */
    val heard: String? = null,
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

/** O1 title: "Hey" and one press on "다음" (parked only: it needs a press). */
@Composable
fun TitleScreen(onNext: () -> Unit, modifier: Modifier = Modifier) {
    CarFrame(
        modifier = modifier,
        statusStrip = { HeaderBar(title = stringResource(R.string.onboard_title_bar), stateLabel = stringResource(R.string.onboard_header)) },
        driving = false,
        actionRail = { GlassButton(stringResource(R.string.step_next), onNext) },
    ) {
        HStack(HeyGap.G32, align = HeyAlign.Center, modifier = Modifier.fillWidth()) {
            VStack(HeyGap.G16, modifier = Modifier.grow(1.07f)) {
                PageHeading(title = stringResource(R.string.step_title_name), subtitle = stringResource(R.string.step_title_subtitle))
            }
            VStack(HeyGap.G16, modifier = Modifier.grow(1f), align = HeyAlign.Center) {
                // ASSET SLOT: title_coast (night coast road, lighthouse, jellyfish lights over the sea).
                AssetSlot(name = stringResource(R.string.asset_title_coast), size = CharacterSize.Hero, contentDescription = stringResource(R.string.asset_title_coast_description))
            }
        }
    }
}

/**
 * The whale (or heyri, for the greeting and the farewell) speaks: the screen shows the line and the listening state. A question
 * has "넘어갈래" while the microphone is open. While the car moves the driving screen replaces this one; the voice goes on.
 */
@Composable
fun SpokenStepScreen(ui: SpokenStepUi, onSkip: () -> Unit = {}, modifier: Modifier = Modifier) {
    val heyri = !ui.step.whale
    CarFrame(
        modifier = modifier,
        statusStrip = { HeaderBar(title = stringResource(R.string.onboard_title_bar), stateLabel = stringResource(R.string.onboard_header)) },
        driving = false,
        actionRail = if (ui.step.isQuestion && ui.phase == TalkPhase.Listening) {
            { GlassButton(stringResource(R.string.step_skip), onSkip) }
        } else {
            null
        },
    ) {
        HStack(HeyGap.G32, align = HeyAlign.Center, modifier = Modifier.fillWidth()) {
            VStack(HeyGap.G16, modifier = Modifier.grow(1.07f)) {
                PageHeading(title = ui.line.ifBlank { stringResource(ui.step.titleRes()) })
                if (ui.step.isQuestion && ui.phase == TalkPhase.Idle) {
                    HeyText(stringResource(R.string.step_answer_hint), tone = HeyTone.Muted)
                }
                ui.heard?.let { HeyText("“$it”", variant = HeyTextVariant.Heading) }
                StatusBadge(phase = ui.phase)
            }
            VStack(HeyGap.G16, modifier = Modifier.grow(1f), align = HeyAlign.Center) {
                if (heyri) {
                    HeyFadeIn { CharacterSlot(size = CharacterSize.Hero, contentDescription = stringResource(R.string.onboard_character)) }
                } else {
                    // ASSET SLOT: whale_loop / whale_still (the whale; planner delivers the art).
                    AssetSlot(name = stringResource(R.string.asset_whale), size = CharacterSize.Hero, contentDescription = stringResource(R.string.asset_whale_description))
                }
            }
        }
    }
}

/** (c) Parked only: a long press brings heyri; a plain tap does nothing, the accessibility action does the same as the press. */
@Composable
fun SummonScreen(summoned: Boolean, onSummon: () -> Unit, modifier: Modifier = Modifier) {
    CarFrame(
        modifier = modifier,
        statusStrip = { HeaderBar(title = stringResource(R.string.onboard_title_bar), stateLabel = stringResource(R.string.onboard_header)) },
        driving = false,
        actionRail = if (summoned) null else {
            { GlassButton(stringResource(R.string.step_summon_button), onClick = onSummon, onLongPress = onSummon) }
        },
    ) {
        HStack(HeyGap.G32, align = HeyAlign.Center, modifier = Modifier.fillWidth()) {
            VStack(HeyGap.G16, modifier = Modifier.grow(1.07f)) {
                PageHeading(title = stringResource(R.string.step_summon_title), subtitle = stringResource(R.string.step_summon_subtitle))
            }
            VStack(HeyGap.G16, modifier = Modifier.grow(1f), align = HeyAlign.Center) {
                if (summoned) {
                    HeyFadeIn { CharacterSlot(size = CharacterSize.Hero, contentDescription = stringResource(R.string.onboard_character)) }
                } else {
                    // ASSET SLOT: where heyri appears (the Rive appear animation replaces this when the rig has one).
                    AssetSlot(name = stringResource(R.string.asset_summon), size = CharacterSize.Hero, contentDescription = stringResource(R.string.asset_summon_description))
                }
            }
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
 * O6 "너를 이만큼 알아": one card per answer the driver gave (a skipped question has no card). Heyri says the line while the cards
 * show, so there is no button ([onNext] null); with a button it is the view-only fallback. With memory saving off the cards are
 * replaced by "기억 저장을 켜면 이렇게 기억해요".
 */
@Composable
fun KnownCardsScreen(
    answers: OnboardingAnswers,
    modifier: Modifier = Modifier,
    memoryEnabled: Boolean = true,
    onNext: (() -> Unit)? = null,
) {
    val mode = knownCardsMode(memoryEnabled, answers)
    val cards = listOfNotNull(
        answers.name?.let { stringResource(R.string.step_card_name, it) },
        answers.trips?.let { stringResource(R.string.step_card_trips, it) },
        answers.topics?.let { stringResource(R.string.step_card_topics, it) },
    )
    CarFrame(
        modifier = modifier,
        statusStrip = { HeaderBar(title = stringResource(R.string.onboard_title_bar), stateLabel = stringResource(R.string.onboard_header)) },
        driving = false,
        actionRail = onNext?.let { next -> { GlassButton(stringResource(R.string.step_next), next) } },
    ) {
        VStack(HeyGap.G16, modifier = Modifier.fillWidth()) {
            PageHeading(
                title = stringResource(R.string.step_cards_title),
                subtitle = stringResource(
                    when (mode) {
                        KnownCardsMode.MemoryOff -> R.string.step_cards_memory_off
                        KnownCardsMode.Empty -> R.string.step_cards_empty
                        KnownCardsMode.Cards -> R.string.step_cards_subtitle
                    },
                ),
            )
            if (mode == KnownCardsMode.Cards) {
                // ASSET SLOTS: polaroid_1..3 (fixed pictures, only the captions come from the answers).
                cards.forEachIndexed { i, text ->
                    MemoryRow(kind = stringResource(R.string.step_cards_kind), text = text, onDelete = {}, deleteLabel = "", tint = i, enabled = false, modifier = Modifier.fillWidth())
                }
            }
        }
    }
}

/** (f) The first look: the five existing options (body colour 2, hat 2, neck 1), saved by the owner on every change. */
@Composable
fun FirstLookScreen(look: CharacterLook, onLook: (CharacterLook) -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val dark = HeyTheme.dark
    CarFrame(
        modifier = modifier,
        statusStrip = { HeaderBar(title = stringResource(R.string.onboard_title_bar), stateLabel = stringResource(R.string.onboard_header)) },
        driving = false,
        actionRail = { GlassButton(stringResource(R.string.step_look_done), onDone) },
    ) {
        HStack(HeyGap.G32, align = HeyAlign.Center, modifier = Modifier.fillWidth()) {
            VStack(HeyGap.G16, modifier = Modifier.grow(1.07f)) {
                PageHeading(title = stringResource(R.string.step_look_title), subtitle = stringResource(R.string.step_look_subtitle))
                ChoiceRow(
                    options = listOf(HeySegmentOption(0, stringResource(R.string.step_look_aqua)), HeySegmentOption(1, stringResource(R.string.step_look_peach))),
                    selected = look.body(dark),
                    onSelect = { onLook(look.copy(bodyColor = it)) },
                    label = stringResource(R.string.step_look_body),
                    modifier = Modifier.fillWidth(),
                )
                ChoiceRow(
                    options = listOf(
                        HeySegmentOption(0, stringResource(R.string.step_look_none)),
                        HeySegmentOption(1, stringResource(R.string.step_look_beanie)),
                        HeySegmentOption(2, stringResource(R.string.step_look_bucket)),
                    ),
                    selected = look.hat,
                    onSelect = { onLook(look.copy(hat = it)) },
                    label = stringResource(R.string.step_look_hat),
                    modifier = Modifier.fillWidth(),
                )
                ChoiceRow(
                    options = listOf(HeySegmentOption(0, stringResource(R.string.step_look_none)), HeySegmentOption(1, stringResource(R.string.step_look_scarf))),
                    selected = look.neck,
                    onSelect = { onLook(look.copy(neck = it)) },
                    label = stringResource(R.string.step_look_neck),
                    modifier = Modifier.fillWidth(),
                )
            }
            VStack(HeyGap.G16, modifier = Modifier.grow(1f), align = HeyAlign.Center) {
                CharacterSlot(size = CharacterSize.Hero, look = look, contentDescription = stringResource(R.string.onboard_character))
            }
        }
    }
}
