package ai.hey.car

import ai.hey.designsystem.theme.HeyTheme
import ai.hey.feature.settings.SettingsScreen
import ai.hey.feature.trip.TripScreen
import ai.hey.feature.trip.rememberNowMillis
import ai.hey.feature.trip.toStrip
import ai.hey.model.DrivingState
import ai.hey.session.Panel
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SessionService.start(this)
        val repository = (application as HeyApp).graph.repository
        setContent {
            val state by repository.state.collectAsState()
            val now by rememberNowMillis()
            HeyTheme(dark = state.settings.dark) {
                // Settings is only reachable while parked; the screen choice below ignores the panel while driving.
                if (state.panel == Panel.Settings && state.driving == DrivingState.PARKED) {
                    BackHandler(onBack = repository::closeSettings)
                    SettingsScreen(
                        settings = state.settings,
                        onChange = repository::updateSettings,
                        onClose = repository::closeSettings,
                        connection = state.connection.toStrip(),
                        wordmarkParty = state.view?.partySize ?: 1,
                        clock = ai.hey.feature.trip.clockLabel(now),
                    )
                } else {
                    TripScreen(state, repository, now)
                }
            }
        }
    }
}
