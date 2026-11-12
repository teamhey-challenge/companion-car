package ai.hey.feature.trip

import ai.hey.session.CarCommands
import ai.hey.session.CarPhase
import ai.hey.session.CarUiState
import ai.hey.session.ParkedCommands
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
        is CarPhase.BoardingCard -> TripScreenId.D3Card
        is CarPhase.DropoffProposal -> TripScreenId.D4Dropoff
        is CarPhase.Requests -> TripScreenId.D6Requests
        is CarPhase.Arrived -> TripScreenId.D7Arrived
    }
}

fun tripScreenFor(state: CarUiState): TripScreenId = tripScreenFor(state.phase)

/**
 * The screen layer receives safe commands plus a [ParkedCommands] token. The token is only ever obtained from a
 * parked phase, so a driving branch has no way to compile a hands-on call.
 *
 * [nowMillis] is passed in so snapshots are deterministic; the app feeds it from [rememberNowMillis].
 */
@Composable
fun TripScreen(
    state: CarUiState,
    commands: CarCommands,
    parked: ParkedCommands,
    nowMillis: Long,
    modifier: Modifier = Modifier,
) {
    when (val phase = state.phase) {
        CarPhase.Driving -> DrivingScreen(state, nowMillis, modifier)
        is CarPhase.Parked -> {
            val parkedCommands = phase.requireParked(parked)
            when (phase) {
                is CarPhase.Waiting -> WaitingScreen(state, parkedCommands, commands, nowMillis, modifier)
                is CarPhase.JoinRequest -> JoinScreen(state, phase, parkedCommands, commands, nowMillis, modifier)
                is CarPhase.BoardingCard -> CardScreen(state, phase, parkedCommands, commands, nowMillis, modifier)
                is CarPhase.DropoffProposal -> DropoffScreen(state, phase, parkedCommands, commands, nowMillis, modifier)
                is CarPhase.Requests -> RequestsScreen(state, phase, parkedCommands, commands, nowMillis, modifier)
                is CarPhase.Arrived -> ArrivedScreen(state, phase, parkedCommands, commands, nowMillis, modifier)
            }
        }
    }
}
