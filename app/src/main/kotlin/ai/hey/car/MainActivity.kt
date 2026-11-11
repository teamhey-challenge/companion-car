package ai.hey.car

import ai.hey.designsystem.component.CarFrame
import ai.hey.designsystem.component.HeyConnection
import ai.hey.designsystem.component.StatusStrip
import ai.hey.designsystem.theme.HeyTheme
import ai.hey.feature.trip.TripScreen
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.res.stringResource

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HeyTheme(dark = true) {
                CarFrame(
                    statusStrip = {
                        StatusStrip(
                            wordmarkParty = 1,
                            drivingLabel = stringResource(R.string.status_parked),
                            connection = HeyConnection.Offline,
                        )
                    },
                    driving = false,
                    content = { TripScreen() },
                )
            }
        }
    }
}
