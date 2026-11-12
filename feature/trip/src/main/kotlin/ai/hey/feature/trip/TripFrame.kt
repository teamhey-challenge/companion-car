package ai.hey.feature.trip

import ai.hey.designsystem.component.CarFrame
import ai.hey.designsystem.component.HeyBanner
import ai.hey.designsystem.component.HeyButton
import ai.hey.designsystem.component.HeyButtonSize
import ai.hey.designsystem.component.HeyButtonVariant
import ai.hey.designsystem.component.HeyDialog
import ai.hey.designsystem.component.HeyDialogAction
import ai.hey.designsystem.component.HeyLayered
import ai.hey.designsystem.component.HeyStatusTone
import ai.hey.designsystem.component.StatusStrip
import ai.hey.designsystem.icon.HeyIcons
import ai.hey.designsystem.layout.fillSize
import ai.hey.session.CarCommand
import ai.hey.session.CarCommands
import ai.hey.session.CarPhase
import ai.hey.session.CarUiState
import ai.hey.session.CommandKey
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/** The one frame every trip screen uses: status strip on top, content, action rail (hidden by the frame while driving). */
@Composable
internal fun TripFrame(
    state: CarUiState,
    nowMillis: Long,
    modifier: Modifier = Modifier,
    rail: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val view = state.view
    val driving = state.phase is CarPhase.Driving
    val label = stringResource(
        when {
            driving -> R.string.trip_status_driving
            view?.route != null -> R.string.trip_status_route_started
            else -> R.string.trip_status_parked
        },
    )
    CarFrame(
        statusStrip = {
            StatusStrip(
                wordmarkParty = view?.partySize ?: 1,
                drivingLabel = label,
                connection = state.connection.toStrip(),
                clock = clockText(nowMillis, clockZone()),
            )
        },
        driving = driving,
        modifier = modifier,
        actionRail = rail,
        content = content,
    )
}

/** Last command error, in the zone it belongs to (never a toast, never a navigation). */
@Composable
internal fun ErrorBanner(state: CarUiState, modifier: Modifier = Modifier) {
    val code = state.error ?: return
    HeyBanner(HeyStatusTone.Danger, errorText(code), modifier)
}

/**
 * "운행 끝내기": opens the confirm dialog. One button for every parked phase that lists [CarCommand.End]; where the
 * phase does not (D1, D2), the button is absent, not disabled.
 */
@Composable
internal fun EndTripButton(
    state: CarUiState,
    enabled: Set<CarCommand>,
    label: Int = R.string.trip_end_button,
    primary: Boolean = false,
    /** Quiet: a regular-size (76dp) ghost button for a column, instead of the large rail button. */
    quiet: Boolean = false,
) {
    if (CarCommand.End !in enabled) return
    HeyButton(
        stringResource(label),
        { state.parked?.openEndDialog() },
        variant = if (primary) HeyButtonVariant.Primary else HeyButtonVariant.Ghost,
        size = if (quiet) HeyButtonSize.Regular else HeyButtonSize.Large,
        icon = if (primary) HeyIcons.Check else HeyIcons.Close,
        enabled = state.online,
        loading = state.isLoading(CommandKey.End),
    )
}

/** The confirm dialog every end-trip button opens; the trip is only ended by its confirm button. */
@Composable
internal fun EndTripLayer(state: CarUiState, commands: CarCommands, content: @Composable () -> Unit) {
    HeyLayered(
        modifier = Modifier.fillSize(),
        overlay = if (state.endDialogOpen) {
            {
                HeyDialog(
                    title = stringResource(R.string.trip_end_dialog_title),
                    message = stringResource(R.string.trip_end_dialog_message),
                    confirm = HeyDialogAction(stringResource(R.string.trip_end_dialog_confirm)) { state.parked?.endTrip() },
                    dismiss = HeyDialogAction(stringResource(R.string.trip_end_dialog_cancel), commands::closeEndDialog),
                    modifier = Modifier.fillSize(),
                    danger = true,
                )
            }
        } else {
            null
        },
        content = content,
    )
}
