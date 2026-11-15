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
import ai.hey.feature.settings.FriendsRoute
import ai.hey.feature.settings.PhoneLinkRoute
import ai.hey.feature.settings.ServerAddress
import ai.hey.feature.settings.SettingsRoute
import ai.hey.model.OnboardingStep
import ai.hey.model.SpeechPriority
import ai.hey.session.TurnState
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.content.res.Configuration
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

/** Width in dp of the 1600x900 design canvas every car mockup (hey-companion-ui) is drawn on. */
private const val DESIGN_WIDTH_DP = 1600f
private const val DESIGN_HEIGHT_DP = 900f

/** Pixels per design dp relative to the system dp: full width for 16:9 and wider windows, full 900 dp height for narrower ones. */
internal fun designScale(widthDp: Int, heightDp: Int): Float =
    if (widthDp * DESIGN_HEIGHT_DP >= heightDp * DESIGN_WIDTH_DP) widthDp / DESIGN_WIDTH_DP else heightDp / DESIGN_HEIGHT_DP

class MainActivity : ComponentActivity() {
    // One launcher for both runtime permissions; only an answer that contains the microphone goes back to the session.
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        voiceAnswer(result)?.let { (application as HeyApp).graph.repository.voicePermissionResult(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No permission dialog at launch: runtime permissions are asked only while parked (see the end of this class).
        SessionService.start(this)
        val graph = (application as HeyApp).graph
        val repository = graph.repository
        setContent {
            val state by repository.state.collectAsState()
            // The driver pressed the microphone without RECORD_AUDIO: ask once while parked, the answer goes back to the
            // session. While driving no permission screen opens (PRD 4.5.7): the press ends in the Microphone fault, which
            // is announced by voice only.
            val needsMic = state.turn is TurnState.NeedsPermission
            val askedNotifications = remember { getSharedPreferences("hey_permissions", MODE_PRIVATE) }
            LaunchedEffect(state.isParked, state.needsOnboarding, state.onboardingStep) {
                val ask = shouldAskNotifications(
                    parked = state.isParked,
                    consented = !state.needsOnboarding,
                    onboardingDone = state.onboardingStep == OnboardingStep.Done,
                    alreadyAsked = askedNotifications.getBoolean("asked_notifications", false),
                    granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED,
                    sdk = android.os.Build.VERSION.SDK_INT,
                )
                if (ask) {
                    askedNotifications.edit().putBoolean("asked_notifications", true).apply()
                    permissions.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                }
            }
            LaunchedEffect(needsMic, state.isParked) {
                if (needsMic && mayAskPermission(state.isParked)) {
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
            // D-58: a car without the record consent is in the flow too (it lands on the O2 consent sheet), there is no other screen.
            val flowActive = state.onboardingStep != OnboardingStep.Done || state.needsOnboarding
            LaunchedEffect(route, parked, flowActive) {
                gateRoute(route, parked, flowActive = flowActive)?.let { target ->
                    nav.navigate(target) { popUpTo(nav.graph.id) { inclusive = true }; launchSingleTop = true }
                }
            }
            // Errors while driving are voice only, once per cause (PRD 4.8): no banner, the screen stays C5.
            val announcer = remember {
                FaultAnnouncer { fault -> graph.speakFault(voiceText(fault), "fault-${fault.name}") }
            }
            LaunchedEffect(ui.fault, parked) { announcer.onFault(ui.fault, parked) }
            // A permission dialog (system, not distraction-optimized) that was open when the gear left P would keep covering C5 under
            // the system's driving block (AC-3.5-05, PLEOS-73 evidence): bring this activity back above it. OnboardingRoute treats the
            // request as denied, so nothing waits for the hidden dialog.
            LaunchedEffect(parked) {
                if (!parked) {
                    startActivity(
                        Intent(this@MainActivity, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    )
                }
            }

            // Design reference (hey-companion-ui, ui-spec): every car screen is laid out at 1600x900 dp. A 2560x1440 or 1280x720
            // window is scaled to that so type, buttons and the character keep the mockup proportions (DesignParity #1).
            // Multi-window (Pleos 1/2 and 1/3 width next to navigation) is narrower than 16:9: those windows keep the 900 dp design
            // HEIGHT instead, so a 1280x1440 half is 800x900 dp and a 853x1440 third is 533x900 dp (type stays readable, the screens
            // switch to their stacked layout below 1000 dp).
            val density = LocalDensity.current
            val config = LocalConfiguration.current
            val designDensity = remember(config.screenWidthDp, config.screenHeightDp, density.density) {
                Density(density = designScale(config.screenWidthDp, config.screenHeightDp) * density.density, fontScale = 1f)
            }
            // D-26: the car follows the system day/night setting (the mockup default is the light sea theme).
            val dark = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            CompositionLocalProvider(LocalDensity provides designDensity) {
            HeyTheme(dark = dark) {
                NavHost(nav, startDestination = Routes.Drive) {
                    composable(Routes.Drive) {
                        DriveScreen(ui, onTalk = repository::startTalk, onEndConversation = repository::cancelTalk, reducedMotion = drivingStill())
                    }
                    composable(Routes.Flow) {
                        OnboardingFlowRoute(
                            companion = graph.companion,
                            talk = repository,
                            onMicDenied = repository::microphoneDeniedAtOnboarding,
                            onExit = { finish() },
                        )
                    }
                    composable(Routes.Home) {
                        // §6.1: after a skipped O8 the home says once that a linked phone shows the diary; it stays 8 s, or until the phone is linked.
                        LaunchedEffect(ui.phoneHint) {
                            if (ui.phoneHint) {
                                delay(8_000)
                                repository.phoneHintShown()
                            }
                        }
                        HomeScreen(
                            ui = ui,
                            reducedMotion = reducedMotion(),
                            onTalk = repository::startTalk,
                            onEndConversation = repository::cancelTalk,
                            onMemory = { state.parked?.let { if (nav.graph.findNode(Routes.Memory) != null) nav.navigate(Routes.Memory) } },
                            onFriends = { state.parked?.let { nav.navigate(Routes.Friends) } },
                            onSettings = { state.parked?.let { nav.navigate(Routes.Settings) } },
                            onFaultAction = { fault -> onFaultAction(fault, repository::startTalk, repository::reconnectNow) },
                        )
                    }
                    composable(Routes.Settings) {
                        SettingsRoute(
                            state = state.companion,
                            commands = graph.companion,
                            onBack = { nav.popBackStack() },
                            onOpenMemory = { nav.navigate(Routes.Memory) },
                            onOpenPair = { nav.navigate(Routes.PhoneLink) },
                            // The address field exists in debug builds only.
                            server = if (BuildConfig.DEBUG) {
                                ServerAddress(state.settings.serverUrl) { url -> repository.updateSettings { it.copy(serverUrl = url) } }
                            } else {
                                null
                            },
                        )
                    }
                    composable(Routes.PhoneLink) {
                        // Parked only (the gate leaves for C5 when the car moves, so no QR is ever drawn while driving); each visit asks for a fresh code.
                        PhoneLinkRoute(
                            state = state.companion,
                            commands = graph.companion,
                            parked = state.isParked,
                            onBack = { nav.popBackStack() },
                        )
                    }
                    composable(Routes.Memory) {
                        MemoryRoute(state = state.companion, commands = graph.companion, onBack = { nav.popBackStack() })
                    }
                    composable(Routes.Friends) {
                        // Parked only (the gate leaves it for Drive). The read-aloud is a short spoken line, like the fault lines.
                        FriendsRoute(
                            state = state.companion,
                            commands = graph.companion,
                            onBack = { nav.popBackStack() },
                            onReadAloud = { text, sender -> graph.speakFault("$sender: $text", "friend-reread") },
                        )
                    }
                }
            }
            }
        }
    }

    /** C5 motion (PLEOS-195): the app is on screen. Off while stopped/hidden, the still character replaces the Rive rig. */
    private var appShown by mutableStateOf(false)

    /** Debug/settings input: the runtime says the motion budget is exceeded, C5 shows the still. Nothing sets it automatically yet. */
    private var motionBudgetExceeded by mutableStateOf(false)

    override fun onStart() {
        super.onStart()
        appShown = true
    }

    override fun onStop() {
        appShown = false
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        val app = application as HeyApp
        app.graph.repository.refreshVoicePermission()
        motionBudgetExceeded = getSharedPreferences("hey_debug", MODE_PRIVATE).getBoolean("motion_budget_exceeded", false)
    }

    /** Motion off in the system (animator scale 0) is reduced motion: the still image replaces Rive, bubbles stop. */
    private fun reducedMotion(): Boolean =
        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

    /** C5 draws the live rig by default (D-52 3); still when the OS reduces motion, the app is hidden, or the budget input is on. */
    private fun drivingStill(): Boolean = reducedMotion() || !appShown || motionBudgetExceeded

    private fun onFaultAction(fault: CarFault, retryTalk: () -> Unit, reconnect: () -> Unit) {
        when (fault) {
            CarFault.Microphone -> startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
            )
            CarFault.Audio -> (application as HeyApp).graph.vehicle.retrySpeech()
            CarFault.Server, CarFault.Recognition -> retryTalk()
            CarFault.Offline, CarFault.Connecting -> reconnect()
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

    // Runtime permissions are asked only while parked (PLEOS-90, [mayAskPermission]): the microphone on a press, notifications once after
    // consent and onboarding ([shouldAskNotifications]); a denial of the latter is kept and the D-52 notifications are skipped silently.
}
