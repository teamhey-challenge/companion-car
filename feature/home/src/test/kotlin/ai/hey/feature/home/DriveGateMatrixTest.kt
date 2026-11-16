package ai.hey.feature.home

import ai.hey.model.OnboardingStep
import ai.hey.session.CarUiState
import ai.hey.vehicle.DrivingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The whole driving gate as a table: 7 routes x 3 motion states x 2 consent states = 42 cells, each with its expected result
 * written out by hand (not derived from the code under test). PRD 3.5: anything not positively parked is driving.
 */
class DriveGateMatrixTest {
    private val flow = Routes.Flow
    private val home = Routes.Home
    private val drive = Routes.Drive
    private val memory = Routes.Memory
    private val settings = Routes.Settings
    private val pair = Routes.PhoneLink
    private val none: String? = null

    private fun state(driving: DrivingStatus, consent: Boolean) =
        CarUiState(driving = driving, needsOnboarding = !consent, onboardingStep = OnboardingStep.Done)

    // current route, then the result for: Parked+consent, Parked+no consent, Driving+consent, Driving+no consent, Unknown+consent, Unknown+no consent
    private val table: List<Pair<String?, List<String?>>> = listOf(
        drive to listOf(home, flow, none, none, none, none),
        flow to listOf(home, none, drive, drive, drive, drive),
        pair to listOf(none, flow, drive, drive, drive, drive),
        home to listOf(none, flow, drive, drive, drive, drive),
        memory to listOf(none, flow, drive, drive, drive, drive),
        settings to listOf(none, flow, drive, drive, drive, drive),
        none to listOf(home, flow, drive, drive, drive, drive),
    )

    private val columns = listOf(
        DrivingStatus.Parked to true, DrivingStatus.Parked to false,
        DrivingStatus.Driving to true, DrivingStatus.Driving to false,
        DrivingStatus.Unknown to true, DrivingStatus.Unknown to false,
    )

    @Test fun `the table covers 42 cells`() {
        assertEquals(7, table.size)
        table.forEach { assertEquals(columns.size, it.second.size) }
        assertEquals(42, table.size * columns.size)
    }

    @Test fun `every cell of route x motion x consent gives the gated route`() {
        var cells = 0
        for ((route, expected) in table) {
            columns.forEachIndexed { i, (driving, consent) ->
                assertEquals("route=$route driving=$driving consent=$consent", expected[i], gateRoute(route, state(driving, consent)))
                cells++
            }
        }
        assertEquals(42, cells)
    }

    @Test fun `the invariants hold for every cell`() {
        val routes = Routes.parkedOnly + Routes.Drive + null
        for (route in routes) for ((driving, consent) in columns) {
            val s = state(driving, consent)
            val target = gateRoute(route, s)
            val landed = target ?: route
            if (driving != DrivingStatus.Parked) {
                assertEquals("moving or unknown always ends on drive: $route $driving", Routes.Drive, landed)
            } else {
                assertNotEquals("parked never stays on drive", Routes.Drive, landed)
                if (!consent) assertEquals("no consent ends on the flow: $route", Routes.Flow, landed)
                if (consent && route != Routes.Flow) assertTrue("parked screens with consent are never forced away: $route", target == null || route == Routes.Drive || route == null)
            }
        }
    }

    @Test fun `only a positively known park counts as parked`() {
        assertTrue(state(DrivingStatus.Parked, true).isParked)
        assertTrue(!state(DrivingStatus.Driving, true).isParked)
        assertTrue(!state(DrivingStatus.Unknown, true).isParked)
    }

    @Test fun `an unfinished flow step keeps the flow even with the consent on the server`() {
        val s = CarUiState(driving = DrivingStatus.Parked, needsOnboarding = false, onboardingStep = OnboardingStep.Lights)
        assertEquals(Routes.Flow, gateRoute(Routes.Home, s))
    }
}
