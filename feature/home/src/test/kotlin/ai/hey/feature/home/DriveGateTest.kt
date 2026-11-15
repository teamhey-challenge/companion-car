package ai.hey.feature.home

import ai.hey.session.CarFault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DriveGateTest {
    @Test fun `leaving park sends every parked screen to drive`() {
        for (r in Routes.parkedOnly) assertEquals(r, Routes.Drive, gateRoute(r, parked = false))
        assertNull(gateRoute(Routes.Drive, parked = false))
    }

    @Test fun `unknown start has no current route and lands on drive`() {
        assertEquals(Routes.Drive, gateRoute(null, parked = false))
    }

    @Test fun `back in park drive returns to home`() {
        assertEquals(Routes.Home, gateRoute(Routes.Drive, parked = true))
        assertEquals(Routes.Home, gateRoute(null, parked = true))
    }

    @Test fun `parked screens stay`() {
        assertNull(gateRoute(Routes.Memory, parked = true))
        assertNull(gateRoute(Routes.Home, parked = true))
        assertNull(gateRoute(Routes.Settings, parked = true))
    }

    @Test fun `an active first-run flow or a missing consent wins over every other screen, driving still shows drive`() {
        assertEquals(Routes.Flow, gateRoute(Routes.Drive, parked = true, flowActive = true))
        assertEquals(Routes.Flow, gateRoute(Routes.Home, parked = true, flowActive = true))
        assertEquals(Routes.Flow, gateRoute(Routes.Settings, parked = true, flowActive = true))
        assertNull(gateRoute(Routes.Flow, parked = true, flowActive = true))
        assertEquals(Routes.Drive, gateRoute(Routes.Flow, parked = false, flowActive = true))
        assertNull(gateRoute(Routes.Drive, parked = false, flowActive = true))
        // the flow ended: leave it for home
        assertEquals(Routes.Home, gateRoute(Routes.Flow, parked = true, flowActive = false))
    }

    @Test fun `a fault is spoken once per cause while driving and never while parked`() {
        val said = ArrayList<CarFault>()
        val a = FaultAnnouncer { said += it }
        a.onFault(CarFault.Offline, parked = true)
        assertEquals(emptyList<CarFault>(), said)
        a.onFault(CarFault.Offline, parked = false)
        a.onFault(CarFault.Offline, parked = false)
        assertEquals(listOf(CarFault.Offline), said)
        a.onFault(CarFault.Microphone, parked = false)
        assertEquals(listOf(CarFault.Offline, CarFault.Microphone), said)
        a.onFault(null, parked = false)
        a.onFault(CarFault.Offline, parked = false)
        assertEquals(3, said.size)
    }

    @Test fun `a turn that heard nothing is spoken by the character even while parked`() {
        val said = ArrayList<CarFault>()
        val a = FaultAnnouncer { said += it }
        a.onFault(CarFault.Recognition, parked = true)
        assertEquals(listOf(CarFault.Recognition), said)
    }
}
