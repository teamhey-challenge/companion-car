package ai.hey.feature.trip

import ai.hey.model.DrivingState
import ai.hey.model.TripState
import ai.hey.model.actionableCount
import ai.hey.session.CarCommands
import ai.hey.session.CarUiState
import ai.hey.session.Panel
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** The seven trip screens (D8 settings lives in :feature:settings and is chosen by the app). */
enum class TripScreenId { D1Waiting, D2Join, D3Card, D4Dropoff, D5Driving, D6Requests, D7Arrived }

/**
 * Screen choice is a pure function of the state (docs/화면-정의서 "운행 상태 × 주행 상태"). Driving always
 * wins: whatever the driver was doing, only D5 remains.
 */
fun tripScreenFor(state: CarUiState): TripScreenId {
    if (state.driving == DrivingState.DRIVING) return TripScreenId.D5Driving
    val view = state.view ?: return TripScreenId.D1Waiting
    return when (view.state) {
        TripState.IDLE, TripState.ENDED -> TripScreenId.D1Waiting
        TripState.REQUESTED -> if (view.pendingJoin != null) TripScreenId.D2Join else TripScreenId.D1Waiting
        TripState.CONNECTED, TripState.CARD_SENT ->
            if (state.panel == Panel.Dropoff && view.card != null) TripScreenId.D4Dropoff else TripScreenId.D3Card
        TripState.IN_TRIP -> when {
            state.panel == Panel.Dropoff && view.card != null -> TripScreenId.D4Dropoff
            view.actionableCount > state.requestsDismissedAt -> TripScreenId.D6Requests
            else -> TripScreenId.D3Card
        }
        TripState.ARRIVED -> TripScreenId.D7Arrived
    }
}

/** [nowMillis] is passed in so snapshots are deterministic; the app feeds it from [rememberNowMillis]. */
@Composable
fun TripScreen(
    state: CarUiState,
    commands: CarCommands,
    nowMillis: Long,
    modifier: Modifier = Modifier,
) {
    when (tripScreenFor(state)) {
        TripScreenId.D1Waiting -> WaitingScreen(state, commands, nowMillis, modifier)
        TripScreenId.D2Join -> JoinScreen(state, commands, nowMillis, modifier)
        TripScreenId.D3Card -> CardScreen(state, commands, nowMillis, modifier)
        TripScreenId.D4Dropoff -> DropoffScreen(state, commands, nowMillis, modifier)
        TripScreenId.D5Driving -> DrivingScreen(state, nowMillis, modifier)
        TripScreenId.D6Requests -> RequestsScreen(state, commands, nowMillis, modifier)
        TripScreenId.D7Arrived -> ArrivedScreen(state, commands, nowMillis, modifier)
    }
}
