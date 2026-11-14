package ai.hey.vehicle

import org.junit.Assert.assertEquals
import org.junit.Test

class NaviGuidanceTest {
    private val seen = ArrayList<Boolean>()
    private val g = NaviGuidance { seen += it }
    private val guiding get() = seen.lastOrNull() ?: false

    @Test fun `the emulator's repeated onRouteStarted with no destination never means guiding`() {
        repeat(5) {
            g.routeStarted()
            g.destination(false)
            g.routing(false)
        }
        assertEquals(false, guiding)
        assertEquals(emptyList<Boolean>(), seen)
    }

    @Test fun `a real route with a destination is guiding and repeated callbacks keep it`() {
        g.routeStarted()
        g.destination(true)
        assertEquals(true, guiding)
        g.routeStarted()
        g.destination(true)
        g.routing(true)
        assertEquals(listOf(true), seen)
    }

    @Test fun `route ended or cancelled or arrived ends guiding`() {
        for (stop in listOf("onRouteEnded", "onRouteCancelled", "onDestinationArrived")) {
            seen.clear()
            g.routeStarted(); g.destination(true)
            g.routeStopped(stop)
            assertEquals(listOf(true, false), seen)
        }
    }

    @Test fun `ROUTE_DRIVE status needs a destination too and leaving it ends guiding`() {
        g.status(true)
        assertEquals(false, guiding)
        g.destination(true)
        assertEquals(true, guiding)
        g.status(false)
        assertEquals(false, guiding)
        g.status(true) // destination was dropped with the route: a new status alone is not enough
        assertEquals(false, guiding)
    }

    @Test fun `a real route sequence ends guiding after the destination arrives, and the stopped route never revives it`() {
        // sequence: started, ended, ROUTE_DRIVE, route state (ROUTE_GUIDING), destination
        g.routeStarted()
        g.routeStopped("onRouteEnded")
        g.status(true)
        g.routing(true) // naviMode == ROUTE_GUIDING
        assertEquals(false, guiding)
        g.destination(true)
        assertEquals(listOf(true), seen)
        g.routeStopped("onRouteCancelled")
        assertEquals(listOf(true, false), seen)
    }
}
