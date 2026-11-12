package ai.hey.feature.trip

import ai.hey.session.CarCommands
import ai.hey.session.CarPhase
import ai.hey.session.CarUiState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** The seven trip screens (D8 settings lives in :feature:settings and is chosen by the app). */
enum class TripScreenId { D1Waiting, D2Join, D3Card, D4Dropoff, D5Driving, D6Requests, D7Arrived }

/**
 * Screen choice is a pure function of the phase (docs/화면-정의서 "운행 상태 × 주행 상태"). Driving always wins:
 * whatever the driver was doing, only D5 remains. Exhaustive, with no else fallthrough.
 */
fun tripScreenFor(phase: CarPhase): TripScreenId = when (phase) {
    CarPhase.Driving -> TripScreenId.D5Driving
    is CarPhase.Parked -> when (phase) {
        is CarPhase.Waiting -> TripScreenId.D1Waiting
        is CarPhase.JoinRequest -> TripScreenId.D2Join
        is CarPhase.Card -> TripScreenId.D3Card
        is CarPhase.DropoffProposal -> TripScreenId.D4Dropoff
        is CarPhase.Requests -> TripScreenId.D6Requests
        is CarPhase.Arrived -> TripScreenId.D7Arrived
    }
}

fun tripScreenFor(state: CarUiState): TripScreenId = tripScreenFor(state.phase)

/**
 * The screen layer receives the safe [CarCommands] and the state. Hands-on commands exist only as
 * [CarUiState.parked], which is null while driving, and every parked button is enabled from its phase's
 * [CarPhase.Parked.enabled], so a screen cannot offer what the relay would reject.
 *
 * [nowMillis] is passed in so snapshots are deterministic; the app feeds it from [rememberNowMillis].
 */
@Composable
fun TripScreen(
    state: CarUiState,
    commands: CarCommands,
    nowMillis: Long,
    modifier: Modifier = Modifier,
) {
    when (val phase = state.phase) {
        CarPhase.Driving -> DrivingScreen(state, nowMillis, modifier)
        is CarPhase.Parked -> when (phase) {
            is CarPhase.Waiting -> WaitingScreen(state, commands, nowMillis, modifier)
            is CarPhase.JoinRequest -> JoinScreen(state, phase, commands, nowMillis, modifier)
            is CarPhase.Card -> CardScreen(state, phase, commands, nowMillis, modifier)
            is CarPhase.DropoffProposal -> DropoffScreen(state, phase, commands, nowMillis, modifier)
            is CarPhase.Requests -> RequestsScreen(state, phase, commands, nowMillis, modifier)
            is CarPhase.Arrived -> ArrivedScreen(state, phase, commands, nowMillis, modifier)
        }
    }
}
