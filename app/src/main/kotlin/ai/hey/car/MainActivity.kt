package ai.hey.car

import ai.hey.designsystem.theme.HeyTheme
import ai.hey.feature.home.DriveScreen
import ai.hey.feature.home.FaultAnnouncer
import ai.hey.session.CarFault
import ai.hey.feature.home.HomeScreen
import ai.hey.feature.home.Routes
import ai.hey.feature.home.gateRoute
import ai.hey.feature.home.homeUi
import ai.hey.feature.memory.MemoryRoute
import ai.hey.feature.settings.ServerAddress
import ai.hey.feature.settings.SettingsRoute
import ai.hey.model.SpeechPriority
import ai.hey.session.TurnState
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    // One launcher for both runtime permissions; only an answer that contains the microphone goes back to the session.
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        voiceAnswer(result)?.let { (application as HeyApp).graph.repository.voicePermissionResult(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()
        SessionService.start(this)
        val graph = (application as HeyApp).graph
        val repository = graph.repository
        setContent {
            val state by repository.state.collectAsState()
            // The driver pressed the microphone without RECORD_AUDIO: ask once while parked, the answer goes back to the
            // session. While driving no permission screen opens (PRD 4.5.7): the press ends in the Microphone fault, which
            // is announced by voice only.
            val needsMic = state.turn is TurnState.NeedsPermission
            LaunchedEffect(needsMic, state.isParked) {
                if (needsMic && state.isParked) {
                    permissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                } else if (needsMic) {
                    repository.voicePermissionResult(false)
                }
            }
            val ui = homeUi(state)
            val parked = state.isParked
            val nav = rememberNavController()
            val current by nav.currentBackStackEntryAsState()
            val route = current?.destination?.route

            // The driving gate (PRD 3.5): the route follows the car, never the other way round. Unknown motion is "driving".
            LaunchedEffect(route, parked, state.needsOnboarding) {
                gateRoute(route, parked, needsOnboarding = state.needsOnboarding)?.let { target ->
                    nav.navigate(target) { popUpTo(nav.graph.id) { inclusive = true }; launchSingleTop = true }
                }
            }
            // A press of 말하기 without consent is never swallowed: parked, it lands on C1 (driving keeps the gate's C5).
            LaunchedEffect(state.onboardingRequests) {
                if (state.onboardingRequests > 0 && parked && route != Routes.Onboarding) {
                    nav.navigate(Routes.Onboarding) { popUpTo(nav.graph.id) { inclusive = true }; launchSingleTop = true }
                }
            }
            // Errors while driving are voice only, once per cause (PRD 4.8): no banner, the screen stays C5.
            val announcer = remember {
                FaultAnnouncer { fault -> graph.vehicle.speak(voiceText(fault), SpeechPriority.High, "fault-${fault.name}") }
            }
            LaunchedEffect(ui.fault, parked) { announcer.onFault(ui.fault, parked) }

            HeyTheme(dark = state.settings.dark) {
                NavHost(nav, startDestination = Routes.Drive) {
                    composable(Routes.Drive) {
                        DriveScreen(ui, onTalk = repository::startTalk, onEndListening = repository::endListening)
                    }
                    composable(Routes.Onboarding) {
                        OnboardingRoute(
                            companion = graph.companion,
                            talk = repository,
                            onMicDenied = {
                                repository.microphoneDeniedAtOnboarding()
                                nav.navigate(Routes.Home) { popUpTo(nav.graph.id) { inclusive = true } }
                            },
                            onDone = { nav.navigate(Routes.Pair) },
                            onExit = { finish() },
                        )
                    }
                    composable(Routes.Pair) {
                        PairRoute(
                            companion = graph.companion,
                            talk = repository,
                            onDone = { nav.navigate(Routes.Home) { popUpTo(Routes.Pair) { inclusive = true } } },
                            onBack = { nav.navigate(Routes.Home) { popUpTo(Routes.Pair) { inclusive = true } } },
                            onDriving = { nav.navigate(Routes.Drive) { popUpTo(nav.graph.id) { inclusive = true } } },
                        )
                    }
                    composable(Routes.Home) {
                        HomeScreen(
                            ui = ui,
                            dark = state.settings.dark,
                            reducedMotion = reducedMotion(),
                            onTalk = repository::startTalk,
                            onStop = repository::cancelTalk,
                            onEndListening = repository::endListening,
                            onMemory = { state.parked?.let { if (nav.graph.findNode(Routes.Memory) != null) nav.navigate(Routes.Memory) } },
                            onSettings = { state.parked?.let { nav.navigate(Routes.Settings) } },
                            onFaultAction = { fault -> onFaultAction(fault, repository::startTalk) },
                        )
                    }
                    composable(Routes.Settings) {
                        // "해제" (PRD 4.7): once the link is gone the driver lands on C2 to pair again.
                        var wasPaired by remember { mutableStateOf(state.companion.paired) }
                        LaunchedEffect(state.companion.paired) {
                            if (wasPaired && !state.companion.paired) nav.navigate(Routes.Pair) { popUpTo(Routes.Home) }
                            wasPaired = state.companion.paired
                        }
                        SettingsRoute(
                            state = state.companion,
                            commands = graph.companion,
                            onBack = { nav.popBackStack() },
                            onOpenMemory = { nav.navigate(Routes.Memory) },
                            onOpenPair = { nav.navigate(Routes.Pair) },
                            // The address field exists in debug builds only.
                            server = if (BuildConfig.DEBUG) {
                                ServerAddress(state.settings.serverUrl) { url -> repository.updateSettings { it.copy(serverUrl = url) } }
                            } else {
                                null
                            },
                        )
                    }
                    composable(Routes.Memory) {
                        MemoryRoute(state = state.companion, commands = graph.companion, onBack = { nav.popBackStack() })
                    }
                }
            }
        }
    }

    /** Motion off in the system (animator scale 0) is reduced motion: the still image replaces Rive, bubbles stop. */
    private fun reducedMotion(): Boolean =
        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

    private fun onFaultAction(fault: CarFault, retryTalk: () -> Unit) {
        when (fault) {
            CarFault.Microphone -> startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
            )
            CarFault.Audio -> (application as HeyApp).graph.vehicle.retrySpeech()
            CarFault.Server, CarFault.Recognition -> retryTalk()
            CarFault.Offline, CarFault.Connecting -> Unit
        }
    }

    private fun voiceText(fault: CarFault): String = getString(
        when (fault) {
            CarFault.Microphone -> R.string.voice_error_microphone
            CarFault.Offline, CarFault.Connecting -> R.string.voice_error_offline
            CarFault.Server -> R.string.voice_error_server
            CarFault.Recognition -> R.string.voice_error_recognition
            CarFault.Audio -> R.string.voice_error_audio
        },
    )

    /**
     * The foreground-service notification is the driver's way back to the screen (a service cannot start an activity),
     * and on API 33+ it only shows once this runtime permission is granted. A denial changes nothing else.
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        permissions.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
    }
}
