package ai.hey.feature.home

import ai.hey.session.CarFault
import ai.hey.session.TurnSource
import ai.hey.designsystem.component.CharacterSize
import ai.hey.designsystem.component.CharacterSlot
import ai.hey.designsystem.component.ErrorKind
import ai.hey.designsystem.component.ErrorPanel
import ai.hey.designsystem.component.HeaderBar
import ai.hey.designsystem.component.HeyButton
import ai.hey.designsystem.component.HeyButtonSize
import ai.hey.designsystem.component.HeyButtonVariant
import ai.hey.designsystem.component.HeyConnection
import ai.hey.designsystem.component.HeyText
import ai.hey.designsystem.component.AssetSlot
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import ai.hey.designsystem.component.HeyStatusPill
import ai.hey.designsystem.component.HeyStatusTone
import ai.hey.designsystem.component.HeyTextAlign
import ai.hey.designsystem.component.OceanBackground
import ai.hey.designsystem.component.PageHeading
import ai.hey.designsystem.component.StatusBadge
import ai.hey.designsystem.component.Subtitle
import ai.hey.designsystem.component.TalkButton
import ai.hey.designsystem.component.TalkPhase
import ai.hey.designsystem.icon.HeyIcons
import ai.hey.designsystem.layout.HStack
import ai.hey.designsystem.layout.HeyAlign
import ai.hey.designsystem.layout.HeyArrange
import ai.hey.designsystem.layout.VStack
import ai.hey.designsystem.layout.carCaptionTop
import ai.hey.designsystem.layout.fillSize
import ai.hey.designsystem.layout.carBodyTop
import ai.hey.designsystem.layout.carGutter
import ai.hey.designsystem.layout.carMargin
import ai.hey.designsystem.layout.carSideColumn
import ai.hey.designsystem.layout.carSideColumnWidth
import ai.hey.designsystem.layout.carStageMax
import ai.hey.designsystem.layout.fillHeight
import ai.hey.designsystem.layout.heroWidth
import ai.hey.designsystem.theme.HeyGap
import ai.hey.designsystem.theme.HeyTextVariant
import ai.hey.designsystem.theme.HeyTheme
import ai.hey.designsystem.theme.HeyTone
import ai.hey.designsystem.layout.HeyWindow
import ai.hey.designsystem.layout.carMarginCompact
import ai.hey.designsystem.layout.fillWidth
import ai.hey.designsystem.layout.isCompactWindow
import ai.hey.designsystem.layout.isNarrowWindow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

internal fun HomePhase.toTalk(): TalkPhase = when (this) {
    HomePhase.Idle -> TalkPhase.Idle
    HomePhase.Listening -> TalkPhase.Listening
    HomePhase.Thinking -> TalkPhase.Thinking
    HomePhase.Speaking -> TalkPhase.Speaking
}

internal fun CarFault.toKind(): ErrorKind = when (this) {
    CarFault.Microphone -> ErrorKind.Microphone
    CarFault.Offline -> ErrorKind.Offline
    CarFault.Connecting -> ErrorKind.Connecting
    CarFault.Server -> ErrorKind.Server
    CarFault.Recognition -> ErrorKind.Recognition
    CarFault.Audio -> ErrorKind.Audio
}


/** The character on C3/C4/C5: the live Rive rig, or the still image while it loads, fails, or motion is reduced. */
@Composable
internal fun HomeCharacter(
    ui: HomeUi,
    reducedMotion: Boolean,
    size: CharacterSize,
    description: String,
    modifier: Modifier = Modifier,
    whileDriving: Boolean = false,
) {
    val dark = HeyTheme.dark
    var riveFailed by remember { mutableStateOf(false) }
    val live: (@Composable () -> Unit)? = if (reducedMotion || riveFailed) null else {
        {
            RiveCharacter(
                riveState = riveStateFor(ui),
                mouth = ui.mouth,
                bodyColor = ui.look.body(dark),
                hat = ui.look.hat,
                neck = ui.look.neck,
                whileDriving = whileDriving,
                modifier = Modifier.fillSize(),
                onFailure = { riveFailed = true },
            )
        }
    }
    CharacterSlot(size = size, modifier = modifier, look = ui.look, live = live, contentDescription = description)
}

/**
 * C3 parked home, C4 conversation (a turn is on it) and C8 errors (a fault replaces the left column) on one screen,
 * like the routes in PRD 3.2: `/home` with C4 and C8 as states. Never composed while the car moves (the NavHost shows
 * [DriveScreen] then), so subtitles here are the parked-only subtitles of D-25.
 */
@Composable
fun HomeScreen(
    ui: HomeUi,
    reducedMotion: Boolean,
    onTalk: () -> Unit,
    onEndConversation: () -> Unit,
    onMemory: () -> Unit,
    onSettings: () -> Unit,
    onFaultAction: (CarFault) -> Unit,
    modifier: Modifier = Modifier,
) {
    OceanBackground(modifier = modifier.fillSize(), animated = !reducedMotion) {
        // Multi-window (Pleos 1/2 and 1/3 width next to navigation): below ~1000 dp the two columns stack, and below ~700 dp the
        // header drops its state label and uses the regular button size. The full-width layout (1600 dp mockup grid) is unchanged.
        HeyWindow(Modifier.fillSize()) { maxWidth, maxHeight ->
            val compact = isCompactWindow(maxWidth, maxHeight)
            val narrow = isNarrowWindow(maxWidth)
            VStack(HeyGap.G24, modifier = Modifier.fillSize().then(if (compact) Modifier.carMarginCompact() else Modifier.carMargin())) {
                HeaderBar(
                    title = stringResource(R.string.home_title),
                    stateLabel = if (narrow) null else stringResource(R.string.home_parked),
                    trailing = {
                        val size = if (compact) HeyButtonSize.Regular else HeyButtonSize.Header
                        HeyButton(stringResource(R.string.home_memory), onMemory, variant = HeyButtonVariant.Soft, size = size, icon = HeyIcons.Book)
                        HeyButton(stringResource(R.string.home_settings), onSettings, variant = HeyButtonVariant.Soft, size = size, icon = HeyIcons.Gear)
                    },
                )
                if (compact) {
                    VStack(HeyGap.G24, modifier = Modifier.fillSize(), align = HeyAlign.Center) {
                        VStack(HeyGap.G8, modifier = Modifier.grow().fillWidth(), align = HeyAlign.Center, arrange = HeyArrange.Center) {
                            HomeStage(
                                ui = ui,
                                reducedMotion = reducedMotion,
                                size = CharacterSize.Compact,
                                description = stringResource(if (ui.inTalk) R.string.home_character else R.string.home_character_idle),
                            )
                        }
                        VStack(HeyGap.G24, modifier = Modifier.fillWidth(), arrange = HeyArrange.SpaceBetween) {
                            HomeLeft(ui, onTalk, onEndConversation, onFaultAction)
                        }
                    }
                } else {
                    // Mockup grid: the body starts 48 under the header row gap; left column 713 wide, right column 679, 48 between.
                    HStack(HeyGap.G32, modifier = Modifier.fillSize().carBodyTop()) {
                        VStack(HeyGap.G24, modifier = Modifier.grow().fillSize().carGutter(), arrange = HeyArrange.SpaceBetween) {
                            HomeLeft(ui, onTalk, onEndConversation, onFaultAction)
                        }
                        VStack(HeyGap.G8, modifier = Modifier.carSideColumn(maxWidth).fillHeight(), align = HeyAlign.Center, arrange = HeyArrange.Center) {
                            HomeStage(
                                ui = ui,
                                reducedMotion = reducedMotion,
                                size = CharacterSize.Stage,
                                modifier = Modifier.carStageMax(carSideColumnWidth(maxWidth)),
                                description = stringResource(if (ui.inTalk) R.string.home_character else R.string.home_character_idle),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** How long the diary-writing moment of the trip end lasts before the farewell line is shown (screens-and-flows 2: 2~3 s). */
const val TRIP_DIARY_MOMENT_MILLIS = 2_500L

/**
 * The character column. At the trip end with a diary (memory saving on) it first shows the diary-writing moment for
 * [TRIP_DIARY_MOMENT_MILLIS] as a named, empty asset slot (D-49: the art comes later, no stand-in picture), then the
 * character again with the farewell line beside it.
 */
@Composable
private fun HomeStage(ui: HomeUi, reducedMotion: Boolean, size: CharacterSize, description: String) {
    var writing by remember(ui.tripNote) { mutableStateOf(ui.tripNote != null && ui.tripDiary && !ui.tripMomentDone) }
    LaunchedEffect(ui.tripNote) {
        if (writing) {
            delay(TRIP_DIARY_MOMENT_MILLIS)
            writing = false
        }
    }
    if (writing) AssetSlot(name = "heyri-writing-diary", size = size, contentDescription = stringResource(R.string.trip_end_writing))
    else HomeCharacter(ui = ui, reducedMotion = reducedMotion, size = size, description = description)
}

@Composable
private fun ai.hey.designsystem.layout.HeyStackScope.HomeLeft(
    ui: HomeUi,
    onTalk: () -> Unit,
    onEndConversation: () -> Unit,
    onFaultAction: (CarFault) -> Unit,
) {
    val fault = ui.fault
    val talking = ui.phase != HomePhase.Idle
    val faultShown = !talking && fault != null
    // A running turn is shown first: a fault that appears mid-turn replaces it only once the turn has ended (C4 -> C8 -> C3).
    when {
        // C8: the action is THE bottom button (mockup shows one pill at the talk button's place), so no talk button below it.
        faultShown -> ErrorPanel(kind = fault!!.toKind(), onAction = { onFaultAction(fault) }, parked = true, modifier = Modifier.fillSize())
        talking || ui.inTalk -> {
            VStack(HeyGap.G24) {
                StatusBadge(phase = ui.phase.toTalk(), connection = HeyConnection.Online)
                // PLEOS-194 captions (parked only): listening says what the app does ("말을 마치면 알아서 보내요", after an answer
                // "더 하고 싶은 말 있어요?"); thinking shows the driver's words, speaking the character's own reply.
                VStack(HeyGap.G16, modifier = Modifier.carCaptionTop()) {
                    if (ui.phase == HomePhase.Listening) {
                        val followUp = ui.source == TurnSource.AutoListen
                        if (ui.proactivePrompt) {
                            // The character spoke first: its line stays up as the prompt above the listening hint.
                            Subtitle(text = ui.reply, hint = stringResource(R.string.talk_listening_hint))
                        } else {
                            Subtitle(
                                text = stringResource(if (followUp) R.string.talk_followup_caption else R.string.talk_listening_caption),
                                hint = stringResource(if (followUp) R.string.talk_followup_hint else R.string.talk_listening_hint),
                            )
                        }
                    } else {
                        if (ui.phase == HomePhase.Thinking) ui.heard?.let { Subtitle(text = "“$it”") }
                        if (ui.reply.isNotEmpty()) Subtitle(text = ui.reply)
                    }
                    ui.friendNote?.let { Subtitle(text = it) }
                }
            }
        }
        else -> {
            VStack(HeyGap.G8) {
                if (ui.tripNote != null) {
                    Subtitle(text = ui.tripNote)
                    if (ui.tripDiary) HeyStatusPill(stringResource(R.string.trip_end_diary), HeyStatusTone.Accent)
                } else {
                    PageHeading(title = stringResource(R.string.home_hint), hero = true, modifier = Modifier.heroWidth())
                }
                ui.friendNote?.let { Subtitle(text = it) }
                if (ui.phoneHint) Subtitle(text = stringResource(R.string.home_phone_hint))
                if (!ui.online) StatusBadge(phase = TalkPhase.Idle, connection = HeyConnection.Reconnecting)
            }
        }
    }
    // PLEOS-194: idle is "말하기"; in every active phase (listening, thinking, speaking) the same button is "대화 마치기" and
    // ends the conversation (cancels listening and playback). It never sends: the end of the speech sends by itself.
    if (faultShown) return
    TalkButton(
        phase = ui.phase.toTalk(),
        onClick = if (ui.phase == HomePhase.Idle) onTalk else onEndConversation,
        enabled = talking || ui.inTalk || ui.online,
    )
}
