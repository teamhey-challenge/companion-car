package ai.hey.car

import ai.hey.designsystem.theme.HeyTheme
import ai.hey.feature.home.DriveScreen
import ai.hey.feature.home.FaultAnnouncer
import ai.hey.feature.home.HomeFault
import ai.hey.feature.home.HomeScreen
import ai.hey.feature.home.Routes
import ai.hey.feature.home.gateRoute
import ai.hey.feature.home.homeUi
import ai.hey.feature.settings.SettingsScreen
import ai.hey.model.SpeechPriority
import ai.hey.session.SaveResult
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
import androidx.compose.runtime.remember
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
            // The driver pressed the microphone without RECORD_AUDIO: ask once, the answer goes back to the session.
            LaunchedEffect(state.turn is TurnState.NeedsPermission) {
                if (state.turn is TurnState.NeedsPermission) {
                    permissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                }
            }
            val ui = homeUi(state)
            val parked = state.isParked
            val nav = rememberNavController()
            val current by nav.currentBackStackEntryAsState()
            val route = current?.destination?.route

            // The driving gate (PRD 3.5): the route follows the car, never the other way round. Unknown motion is "driving".
            LaunchedEffect(route, parked) {
                gateRoute(route, parked, needsOnboarding = false)?.let { target ->
                    nav.navigate(target) { popUpTo(nav.graph.id) { inclusive = true }; launchSingleTop = true }
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
                        DriveScreen(ui, onTalk = repository::startTalk)
                    }
                    composable(Routes.Home) {
                        HomeScreen(
                            ui = ui,
                            dark = state.settings.dark,
                            reducedMotion = reducedMotion(),
                            onTalk = repository::startTalk,
                            onStop = repository::cancelTalk,
                            onMemory = { state.parked?.let { if (nav.graph.findNode(Routes.Memory) != null) nav.navigate(Routes.Memory) } },
                            onSettings = { state.parked?.let { nav.navigate(Routes.Settings) } },
                            onFaultAction = { fault -> onFaultAction(fault, repository::startTalk) },
                        )
                    }
                    composable(Routes.Settings) {
                        SettingsScreen(
                            settings = state.settings,
                            onChange = repository::updateSettings,
                            onClose = { nav.popBackStack() },
                            connection = state.connection.toStrip(),
                            wordmarkParty = 1,
                            clock = "",
                            saveFailed = state.settingsSave is SaveResult.Failed,
                        )
                    }
                }
            }
        }
    }

    /** Motion off in the system (animator scale 0) is reduced motion: the still image replaces Rive, bubbles stop. */
    private fun reducedMotion(): Boolean =
        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

    private fun onFaultAction(fault: HomeFault, retryTalk: () -> Unit) {
        when (fault) {
            HomeFault.Microphone -> startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
            )
            HomeFault.Audio -> (application as HeyApp).graph.vehicle.retrySpeech()
            HomeFault.Server, HomeFault.Recognition -> retryTalk()
            HomeFault.Offline, HomeFault.Connecting -> Unit
        }
    }

    private fun voiceText(fault: HomeFault): String = getString(
        when (fault) {
            HomeFault.Microphone -> R.string.voice_error_microphone
            HomeFault.Offline, HomeFault.Connecting -> R.string.voice_error_offline
            HomeFault.Server -> R.string.voice_error_server
            HomeFault.Recognition -> R.string.voice_error_recognition
            HomeFault.Audio -> R.string.voice_error_audio
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
