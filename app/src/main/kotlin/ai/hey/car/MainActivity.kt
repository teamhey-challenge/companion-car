package ai.hey.car

import ai.hey.designsystem.theme.HeyTheme
import ai.hey.feature.settings.SettingsScreen
import ai.hey.feature.trip.TripScreen
import ai.hey.feature.trip.clockLabel
import ai.hey.feature.trip.rememberNowMillis
import ai.hey.feature.trip.toStrip
import ai.hey.model.TripState
import ai.hey.session.Panel
import ai.hey.session.SaveResult
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()
        SessionService.start(this)
        val repository = (application as HeyApp).graph.repository
        setContent {
            val state by repository.state.collectAsState()
            val now by rememberNowMillis()
            HeyTheme(dark = state.settings.dark) {
                // Settings only while parked, and a connection request always wins over it: its 5 minutes keep running.
                val joinWaiting = state.view?.state == TripState.REQUESTED && state.view?.pendingJoin != null
                val showSettings = state.panel == Panel.Settings && state.isParked && !joinWaiting
                if (showSettings) {
                    BackHandler(onBack = repository::closeSettings)
                    SettingsScreen(
                        settings = state.settings,
                        onChange = repository::updateSettings,
                        onClose = repository::closeSettings,
                        connection = state.connection.toStrip(),
                        wordmarkParty = state.view?.partySize ?: 1,
                        clock = clockLabel(now),
                        saveFailed = state.settingsSave is SaveResult.Failed,
                    )
                } else {
                    TripScreen(state, repository, now)
                }
            }
        }
    }

    /**
     * The foreground-service notification is the driver's way back to the screen (a service cannot start an activity),
     * and on API 33+ it only shows once this runtime permission is granted.
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    }
}
