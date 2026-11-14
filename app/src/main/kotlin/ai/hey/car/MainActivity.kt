package ai.hey.car

import ai.hey.designsystem.theme.HeyTheme
import ai.hey.feature.memory.MemoryRoute
import ai.hey.feature.settings.ServerAddress
import ai.hey.feature.settings.SettingsRoute
import ai.hey.session.Panel
import ai.hey.session.TurnState
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    // One launcher for both runtime permissions; only an answer that contains the microphone goes back to the session.
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        voiceAnswer(result)?.let { (application as HeyApp).graph.repository.voicePermissionResult(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()
        SessionService.start(this)
        val repository = (application as HeyApp).graph.repository
        setContent {
            val state by repository.state.collectAsState()
            // The driver pressed the microphone without RECORD_AUDIO: ask once, the answer goes back to the session.
            LaunchedEffect(state.turn is TurnState.NeedsPermission) {
                if (state.turn is TurnState.NeedsPermission) {
                    permissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                }
            }
            val companion = (application as HeyApp).graph.companion
            var memoryOpen by remember { mutableStateOf(false) }
            HeyTheme(dark = state.settings.dark) {
                // Interim wiring until CarHome's navigation lands: C7 opens from the placeholder home, C6 from C7.
                if (state.panel == Panel.Settings && state.isParked) {
                    if (memoryOpen) {
                        BackHandler { memoryOpen = false }
                        MemoryRoute(state.companion, companion, onBack = { memoryOpen = false })
                    } else {
                        BackHandler(onBack = repository::closeSettings)
                        SettingsRoute(
                            state.companion,
                            companion,
                            onBack = repository::closeSettings,
                            onOpenMemory = { memoryOpen = true },
                            server = if (BuildConfig.DEBUG) {
                                ServerAddress(state.settings.serverUrl) { url -> repository.updateSettings { it.copy(serverUrl = url) } }
                            } else {
                                null
                            },
                        )
                    }
                } else {
                    memoryOpen = false
                    CompanionHome(state, repository::startTalk, repository::cancelTalk, onSettings = state.parked?.let { p -> p::openSettings })
                }
            }
        }
    }

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
