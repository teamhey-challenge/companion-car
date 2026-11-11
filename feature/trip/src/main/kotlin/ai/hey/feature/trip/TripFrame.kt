package ai.hey.feature.trip

import ai.hey.designsystem.component.CarFrame
import ai.hey.designsystem.component.HeyBanner
import ai.hey.designsystem.component.HeyStatusTone
import ai.hey.designsystem.component.StatusStrip
import ai.hey.model.DrivingState
import ai.hey.session.CarUiState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import java.time.ZoneId

private val KoreaZone: ZoneId = ZoneId.of("Asia/Seoul")

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
    val driving = state.driving == DrivingState.DRIVING
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
                clock = clockText(nowMillis, KoreaZone),
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
