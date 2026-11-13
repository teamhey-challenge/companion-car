package ai.hey.car

import ai.hey.designsystem.component.CarFrame
import ai.hey.designsystem.component.EmptyPanel
import ai.hey.designsystem.component.HeyButton
import ai.hey.designsystem.component.HeyButtonSize
import ai.hey.designsystem.component.HeyConnection
import ai.hey.designsystem.component.StatusStrip
import ai.hey.designsystem.icon.HeyIcons
import ai.hey.designsystem.layout.HStack
import ai.hey.designsystem.layout.HeyAlign
import ai.hey.designsystem.layout.HeyArrange
import ai.hey.designsystem.layout.VStack
import ai.hey.designsystem.layout.fillSize
import ai.hey.designsystem.theme.HeyGap
import ai.hey.session.CarUiState
import ai.hey.session.ConnectionStatus
import ai.hey.session.TurnError
import ai.hey.session.TurnState
import ai.hey.vehicle.DrivingStatus
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

fun ConnectionStatus.toStrip(): HeyConnection = when (this) {
    ConnectionStatus.Online -> HeyConnection.Online
    ConnectionStatus.Connecting, ConnectionStatus.Reconnecting -> HeyConnection.Reconnecting
    ConnectionStatus.Registering, ConnectionStatus.RegistrationFailed, ConnectionStatus.Rejected -> HeyConnection.Offline
}

/** Placeholder home screen until the companion conversation screens (C1-C8) exist; only built from design-system parts. */
@Composable
fun CompanionHome(state: CarUiState, onTalk: () -> Unit, onCancel: () -> Unit, onSettings: (() -> Unit)?) {
    val busy = state.turn != TurnState.Idle
    CarFrame(
        statusStrip = { StatusStrip(wordmarkParty = 1, drivingLabel = null, connection = state.connection.toStrip()) },
        driving = state.driving != DrivingStatus.Parked,
        actionRail = onSettings?.let { open -> { HStack(HeyGap.G16) { HeyButton(stringResource(R.string.home_settings), open) } } },
    ) {
        val error = state.error
        val (title, message) = when {
            error != null -> stringResource(R.string.home_error_title) to stringResource(error.messageRes())
            state.reply.isNotEmpty() -> state.reply to state.heard
            state.turn == TurnState.Listening -> stringResource(R.string.home_listening) to null
            state.turn == TurnState.Thinking -> stringResource(R.string.home_thinking) to state.heard
            else -> stringResource(R.string.home_title) to null
        }
        VStack(HeyGap.G24, modifier = Modifier.fillSize(), align = HeyAlign.Center, arrange = HeyArrange.Center) {
            EmptyPanel(icon = HeyIcons.Mic, title = title, message = message)
            HeyButton(
                stringResource(if (busy) R.string.home_stop else R.string.home_talk),
                if (busy) onCancel else onTalk,
                size = HeyButtonSize.Large,
            )
        }
    }
}

private fun TurnError.messageRes(): Int = when (this) {
    TurnError.NotConnected -> R.string.home_error_not_connected
    TurnError.NoSpeech -> R.string.home_error_no_speech
    TurnError.PermissionDenied -> R.string.home_error_permission
    TurnError.SpeechUnavailable -> R.string.home_error_stt
    TurnError.ConnectionLost -> R.string.home_error_lost
    TurnError.Server -> R.string.home_error_server
}
