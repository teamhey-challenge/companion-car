package ai.hey.feature.home

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
        val said = ArrayList<HomeFault>()
        val a = FaultAnnouncer { said += it }
        a.onFault(HomeFault.Offline, parked = true)
        assertEquals(emptyList<HomeFault>(), said)
        a.onFault(HomeFault.Offline, parked = false)
        a.onFault(HomeFault.Offline, parked = false)
        assertEquals(listOf(HomeFault.Offline), said)
        a.onFault(HomeFault.Microphone, parked = false)
        assertEquals(listOf(HomeFault.Offline, HomeFault.Microphone), said)
        a.onFault(null, parked = false)
        a.onFault(HomeFault.Offline, parked = false)
        assertEquals(3, said.size)
    }

    @Test fun `top fault priority is microphone then connection then sound`() {
        assertEquals(HomeFault.Microphone, listOf(HomeFault.Audio, HomeFault.Offline, HomeFault.Microphone).topFault())
        assertEquals(HomeFault.Offline, listOf(HomeFault.Audio, HomeFault.Offline).topFault())
    }
}
