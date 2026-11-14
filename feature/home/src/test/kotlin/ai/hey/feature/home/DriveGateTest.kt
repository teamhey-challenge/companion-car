package ai.hey.feature.home

import ai.hey.session.CarFault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DriveGateTest {
    @Test fun `leaving park sends every parked screen to drive`() {
        for (r in Routes.parkedOnly) assertEquals(r, Routes.Drive, gateRoute(r, parked = false, needsOnboarding = false))
        assertNull(gateRoute(Routes.Drive, parked = false, needsOnboarding = false))
    }

    @Test fun `unknown start has no current route and lands on drive`() {
        assertEquals(Routes.Drive, gateRoute(null, parked = false, needsOnboarding = false))
    }

    @Test fun `back in park drive returns to home or onboarding`() {
        assertEquals(Routes.Home, gateRoute(Routes.Drive, parked = true, needsOnboarding = false))
        assertEquals(Routes.Onboarding, gateRoute(Routes.Drive, parked = true, needsOnboarding = true))
    }

    @Test fun `parked screens stay and missing consent forces onboarding`() {
        assertNull(gateRoute(Routes.Memory, parked = true, needsOnboarding = false))
        assertEquals(Routes.Onboarding, gateRoute(Routes.Home, parked = true, needsOnboarding = true))
        assertNull(gateRoute(Routes.Onboarding, parked = true, needsOnboarding = true))
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

    @Test fun `the top fault is whatever the session reports, drive and home read the same value`() {
        assertEquals(HomeUi(fault = CarFault.Microphone).fault, CarFault.Microphone)
    }
}
