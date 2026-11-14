package ai.hey.car

import ai.hey.designsystem.component.HeyConnection
import ai.hey.session.ConnectionStatus

fun ConnectionStatus.toStrip(): HeyConnection = when (this) {
    ConnectionStatus.Online -> HeyConnection.Online
    ConnectionStatus.Connecting, ConnectionStatus.Reconnecting -> HeyConnection.Reconnecting
    ConnectionStatus.Registering, ConnectionStatus.RegistrationFailed, ConnectionStatus.Rejected -> HeyConnection.Offline
}
