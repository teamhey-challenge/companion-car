package ai.hey.car

import ai.hey.feature.onboarding.ConsentFormUi
import ai.hey.feature.onboarding.OnboardingFlow
import ai.hey.feature.onboarding.OnboardingOutcome
import ai.hey.feature.onboarding.OnboardingScreen
import ai.hey.feature.onboarding.PairScreen
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
import kotlinx.coroutines.delay

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
                val answer = CompletableDeferred<Boolean>()
                pending = answer
                launcher.launch(permission)
                answer.await()
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

/** C2 route: asks for a code while open, leaves for /home on "나중에" and (after a short hold) when the phone claimed it. */
@Composable
fun PairRoute(
    companion: CompanionRepository,
    talk: TalkRepository,
    onDone: () -> Unit,
    onBack: () -> Unit,
    onDriving: () -> Unit,
) {
    val state by companion.state.collectAsState()
    val talkState by talk.state.collectAsState()
    var lastCode by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        companion.openPairing()
        onDispose { companion.closePairing() }
    }
    // A QR login while moving is forbidden (review-rules): the car leaves the screen as soon as it is not parked.
    LaunchedEffect(talkState.driving) { if (talkState.driving != DrivingStatus.Parked) onDriving() }

    val pairing = state.pairing
    if (pairing is PairingUi.Pending) lastCode = pairing.pairCode
    LaunchedEffect(pairing is PairingUi.Claimed) {
        if (pairing is PairingUi.Claimed) {
            delay(CLAIMED_HOLD_MS)
            onDone()
        }
    }

    BackHandler(onBack = onBack)
    PairScreen(
        ui = when (pairing) {
            PairingUi.None, PairingUi.Creating -> PairUi.Loading
            is PairingUi.Pending -> PairUi.Pending(pairing.pairCode)
            PairingUi.Claimed -> PairUi.Claimed
            PairingUi.Expired -> PairUi.Expired(lastCode)
            PairingUi.Failed -> PairUi.Failed
        },
        onBack = onBack,
        onLater = onDone,
        onRetry = companion::openPairing,
    )
}
