package ai.hey.feature.trip

import ai.hey.designsystem.theme.HeyTheme
import ai.hey.session.CarUiState
import ai.hey.session.NoCommands
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

private const val SPEC = "spec:width=1600dp,height=900dp,dpi=160"

@Composable
private fun Shown(state: CarUiState, dark: Boolean = true) {
    HeyTheme(dark) { TripScreen(state, NoCommands, TripSamples.NOW) }
}

@Preview(name = "D1 waiting", device = SPEC) @Composable private fun D1Waiting() = Shown(TripSamples.waiting)
@Preview(name = "D1 offline", device = SPEC) @Composable private fun D1Offline() = Shown(TripSamples.waitingOffline)
@Preview(name = "D1 register failed", device = SPEC) @Composable private fun D1RegisterFailed() = Shown(TripSamples.registerFailed)
@Preview(name = "D2 join parked", device = SPEC) @Composable private fun D2Join() = Shown(TripSamples.join)
@Preview(name = "D3 no card", device = SPEC) @Composable private fun D3NoCard() = Shown(TripSamples.cardWaiting)
@Preview(name = "D3 card", device = SPEC) @Composable private fun D3Card() = Shown(TripSamples.cardOk)
@Preview(name = "D3 four conditions", device = SPEC) @Composable private fun D3Four() = Shown(TripSamples.cardFourConditions)
@Preview(name = "D3 long", device = SPEC) @Composable private fun D3Long() = Shown(TripSamples.cardLong)
@Preview(name = "D3 route failed", device = SPEC) @Composable private fun D3Failed() = Shown(TripSamples.cardRouteFailed)
@Preview(name = "D3 route stale", device = SPEC) @Composable private fun D3Stale() = Shown(TripSamples.cardRouteStale)
@Preview(name = "D4 default", device = SPEC) @Composable private fun D4Default() = Shown(TripSamples.dropoffDefault)
@Preview(name = "D4 selected", device = SPEC) @Composable private fun D4Selected() = Shown(TripSamples.dropoffSelected)
@Preview(name = "D4 none", device = SPEC) @Composable private fun D4None() = Shown(TripSamples.dropoffNone)
@Preview(name = "D4 pin", device = SPEC) @Composable private fun D4Pin() = Shown(TripSamples.dropoffPin)
@Preview(name = "D4 ok", device = SPEC) @Composable private fun D4Ok() = Shown(TripSamples.dropoffOk)
@Preview(name = "D5 none", device = SPEC) @Composable private fun D5None() = Shown(TripSamples.drivingNone)
@Preview(name = "D5 request", device = SPEC) @Composable private fun D5Request() = Shown(TripSamples.drivingRequest)
@Preview(name = "D5 stale", device = SPEC) @Composable private fun D5Stale() = Shown(TripSamples.drivingStale)
@Preview(name = "D6 requests", device = SPEC) @Composable private fun D6() = Shown(TripSamples.requestsPlain)
@Preview(name = "D6 many", device = SPEC) @Composable private fun D6Many() = Shown(TripSamples.requestsMany)
@Preview(name = "D6 pin", device = SPEC) @Composable private fun D6Pin() = Shown(TripSamples.requestsPin)
@Preview(name = "D7 agreed", device = SPEC) @Composable private fun D7() = Shown(TripSamples.arrivedAgreed)
@Preview(name = "D7 none", device = SPEC) @Composable private fun D7None() = Shown(TripSamples.arrivedNoAgreement)
@Preview(name = "D7 dialog", device = SPEC) @Composable private fun D7Dialog() = Shown(TripSamples.arrivedDialog)
@Preview(name = "D1 light", device = SPEC) @Composable private fun D1Light() = Shown(TripSamples.waiting, dark = false)
