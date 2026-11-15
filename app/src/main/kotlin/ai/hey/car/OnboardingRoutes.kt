package ai.hey.car

import ai.hey.designsystem.component.CharacterLook
import ai.hey.designsystem.component.TalkPhase
import ai.hey.feature.onboarding.FirstLookScreen
import ai.hey.feature.onboarding.KnownCardsScreen
import ai.hey.feature.onboarding.SpokenStepScreen
import ai.hey.feature.onboarding.SpokenStepUi
import ai.hey.feature.onboarding.SummonScreen
import ai.hey.feature.onboarding.TitleScreen
import ai.hey.model.OnboardingStep
import ai.hey.session.TurnState
import ai.hey.feature.onboarding.ConsentFormUi
import ai.hey.feature.onboarding.OnboardingFlow
import ai.hey.feature.onboarding.OnboardingOutcome
import ai.hey.feature.onboarding.OnboardingScreen
import ai.hey.feature.onboarding.PairScreen
import ai.hey.feature.onboarding.QrStepScreen
import ai.hey.feature.onboarding.PairUi
import ai.hey.session.CompanionRepository
import ai.hey.session.PairingUi
import ai.hey.session.TalkRepository
import ai.hey.vehicle.DrivingStatus
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.flow.first

private const val PRIVACY_URL = "https://hey.lastmile.ltd/privacy"
private const val CAR_SPEED = "android.car.permission.CAR_SPEED"

/** How long "연결됐어요!" stays before C2 leaves for /home (PRD 4.2.5). */
private const val CLAIMED_HOLD_MS = 1500L

/**
 * C1 route. Consent goes to the server first; only after the server said yes (needsOnboarding turns false) are the
 * runtime permissions asked. Back leaves the app without recording anything (AC-4.1-03).
 */
@Composable
fun OnboardingRoute(
    companion: CompanionRepository,
    talk: TalkRepository,
    onMicDenied: () -> Unit,
    onDone: () -> Unit,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val companionState by companion.state.collectAsState()
    val talkState by talk.state.collectAsState()
    var voice by remember { mutableStateOf(false) }
    var memory by remember { mutableStateOf(false) }
    var requiredShown by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }

    var pending by remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pending?.complete(granted)
    }
    val flow = remember {
        OnboardingFlow(
            requestPermission = { permission ->
                // A permission dialog open when gear D arrives gets covered by the system's driving block: wait for Park.
                talk.state.first { mayAskPermission(it.isParked) }
                val answer = CompletableDeferred<Boolean>()
                pending = answer
                launcher.launch(permission)
                // The gear leaves P while the dialog is open: MainActivity brings the app back above it and the answer is
                // "no", so the flow goes on instead of waiting for a hidden dialog; the driver can be asked again while parked.
                coroutineScope {
                    val left = async { talk.state.first { !it.isParked }; false }
                    val result = select<Boolean> {
                        answer.onAwait { it }
                        left.onAwait { it }
                    }
                    left.cancel()
                    result
                }
            },
            microphone = Manifest.permission.RECORD_AUDIO,
            vehicleSpeed = CAR_SPEED,
        )
    }

    BackHandler(onBack = onExit)

    // The server has recorded the consent: now (and only now) the runtime permissions.
    LaunchedEffect(started, talkState.needsOnboarding) {
        if (started && !talkState.needsOnboarding) {
            when (flow.afterConsent()) {
                OnboardingOutcome.Done -> onDone()
                OnboardingOutcome.MicDenied -> onMicDenied()
            }
        }
    }

    OnboardingScreen(
        ui = ConsentFormUi(
            voice = voice,
            memory = memory,
            saving = companionState.onboarding.saving || (started && !talkState.needsOnboarding),
            failed = companionState.onboarding.failed,
            voiceRequiredShown = requiredShown,
        ),
        onVoiceChange = { voice = it; if (it) requiredShown = false },
        onMemoryChange = { memory = it },
        onStart = { started = true; companion.submitConsent(memory) },
        onStartWithoutVoice = { requiredShown = true },
        onDecline = { voice = false; requiredShown = true },
        onPolicy = {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: ActivityNotFoundException) {
                // No browser on the head unit: the notice on this screen is the full text; the phone page has the same one.
            }
        },
    )
}

/**
 * The first-run flow (D-48, D-52): one screen per stored step, in the planner's order. The spoken steps are driven by the talk
 * repository (they run while driving too, then C5 is on screen); this route draws them and handles the press steps: title, the
 * consent sheet, the long press, dress-up and the phone link. A drive in the middle leaves this route (the gate) and the stored
 * step is picked up again when parked.
 */
@Composable
fun OnboardingFlowRoute(companion: CompanionRepository, talk: TalkRepository, onMicDenied: () -> Unit, onExit: () -> Unit) {
    val talkState by talk.state.collectAsState()
    val companionState by companion.state.collectAsState()
    val step = talkState.onboardingStep
    val phase = when (talkState.turn) {
        TurnState.Listening -> TalkPhase.Listening
        TurnState.Thinking -> TalkPhase.Thinking
        TurnState.Replying -> TalkPhase.Speaking
        else -> TalkPhase.Idle
    }
    val line = talkState.onboarding.line
    val look = companionState.settings.let { CharacterLook(it.bodyColor, it.hat, it.neck) }
    when (step) {
        OnboardingStep.Title -> TitleScreen(onNext = { talk.finishOnboardingStep(OnboardingStep.Title) })
        OnboardingStep.Consent -> OnboardingRoute(
            companion = companion,
            talk = talk,
            onMicDenied = {
                onMicDenied()
                talk.finishOnboardingStep(OnboardingStep.Consent)
            },
            onDone = { talk.finishOnboardingStep(OnboardingStep.Consent) },
            onExit = onExit,
        )
        OnboardingStep.WhaleIntro, OnboardingStep.Lights, OnboardingStep.Q1, OnboardingStep.Q2, OnboardingStep.Q3,
        OnboardingStep.Found, OnboardingStep.Greeting, OnboardingStep.Farewell,
        -> SpokenStepScreen(
            ui = SpokenStepUi(step = step, line = line, phase = phase, heard = talkState.heard, look = look),
            onSkip = talk::skipOnboardingQuestion,
        )
        OnboardingStep.Summon -> {
            var summoned by remember { mutableStateOf(false) }
            LaunchedEffect(summoned) {
                if (summoned) {
                    delay(SUMMON_HOLD_MS)
                    talk.finishOnboardingStep(OnboardingStep.Summon)
                }
            }
            SummonScreen(summoned = summoned, onSummon = { summoned = true })
        }
        OnboardingStep.Memory -> KnownCardsScreen(
            answers = talkState.onboarding.answers,
            memoryEnabled = companionState.settings.memoryEnabled,
            line = line,
            look = look,
        )
        OnboardingStep.Dress -> FirstLookScreen(
            look = look,
            onLook = { l -> companion.updateSettings { it.copy(bodyColor = l.bodyColor, hat = l.hat, neck = l.neck) } },
            onDone = { talk.finishOnboardingStep(OnboardingStep.Dress) },
            line = line,
        )
        OnboardingStep.Qr -> {
            // O8 skipped ("나중에 할게") without a phone: home reminds once (§6.1).
            val end = {
                if (!companion.state.value.paired) talk.skippedPhoneLink()
                talk.finishOnboardingStep(OnboardingStep.Qr)
            }
            PairRoute(companion = companion, talk = talk, onDone = end, onBack = end, onDriving = {}, inFlow = true)
        }
        OnboardingStep.Done -> Unit
    }
}

/** How long the dive through the ring plays (dive.mp4, about 5 s) before the flow goes on to heyri's greeting. */
private const val SUMMON_HOLD_MS = 5_000L


/** C2 route: asks for a code while open, leaves for /home on "나중에" and (after a short hold) when the phone claimed it. */
@Composable
fun PairRoute(
    companion: CompanionRepository,
    talk: TalkRepository,
    onDone: () -> Unit,
    onBack: () -> Unit,
    onDriving: () -> Unit,
    /** O8 of the first-run flow: drawn on the onboarding scene instead of the plain C2 page. */
    inFlow: Boolean = false,
) {
    val state by companion.state.collectAsState()
    val talkState by talk.state.collectAsState()

    DisposableEffect(Unit) {
        companion.openPairing()
        onDispose { companion.closePairing() }
    }
    // A QR login while moving is forbidden (review-rules): the car leaves the screen as soon as it is not parked.
    LaunchedEffect(talkState.driving) { if (talkState.driving != DrivingStatus.Parked) onDriving() }

    val pairing = state.pairing
    LaunchedEffect(pairing is PairingUi.Claimed) {
        if (pairing is PairingUi.Claimed) {
            delay(CLAIMED_HOLD_MS)
            onDone()
        }
    }

    BackHandler(onBack = onBack)
    val ui = when (pairing) {
        PairingUi.None, PairingUi.Creating -> PairUi.Loading
        is PairingUi.Pending -> PairUi.Pending(pairing.pairCode)
        is PairingUi.Claimed -> PairUi.Claimed
        PairingUi.Failed -> PairUi.Failed
    }
    if (inFlow) {
        val s = state.settings
        QrStepScreen(ui = ui, onLater = onDone, onRetry = companion::openPairing, look = CharacterLook(s.bodyColor, s.hat, s.neck))
    } else {
        PairScreen(ui = ui, onBack = onBack, onLater = onDone, onRetry = companion::openPairing)
    }
}
