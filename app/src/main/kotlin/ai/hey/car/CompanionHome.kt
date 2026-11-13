package ai.hey.car

import ai.hey.designsystem.component.CarFrame
import ai.hey.designsystem.component.EmptyPanel
import ai.hey.designsystem.component.HeyButton
import ai.hey.designsystem.component.HeyConnection
import ai.hey.designsystem.component.StatusStrip
import ai.hey.designsystem.icon.HeyIcons
import ai.hey.session.ConnectionStatus
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

fun ConnectionStatus.toStrip(): HeyConnection = when (this) {
    ConnectionStatus.Online -> HeyConnection.Online
    ConnectionStatus.Connecting, ConnectionStatus.Reconnecting -> HeyConnection.Reconnecting
    ConnectionStatus.Registering, ConnectionStatus.RegistrationFailed, ConnectionStatus.Rejected, ConnectionStatus.ProtocolMismatch -> HeyConnection.Offline
}

/** Placeholder home screen until the companion conversation screen exists; only built from design-system parts. */
@Composable
fun CompanionHome(connection: HeyConnection, driving: Boolean, onSettings: (() -> Unit)?) {
    CarFrame(
        statusStrip = { StatusStrip(wordmarkParty = 1, drivingLabel = null, connection = connection) },
        driving = driving,
        actionRail = onSettings?.let { open -> { HeyButton(stringResource(R.string.home_settings), open) } },
    ) {
        EmptyPanel(icon = HeyIcons.Mic, title = stringResource(R.string.home_title))
    }
}
