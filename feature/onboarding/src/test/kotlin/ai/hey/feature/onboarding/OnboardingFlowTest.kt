package ai.hey.feature.onboarding

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class OnboardingFlowTest {
    private val log = mutableListOf<String>()

    private fun flow(micGranted: Boolean = true, speedGranted: Boolean = false) = OnboardingFlow(
        requestPermission = { p -> log += "ask $p"; if (p == "MIC") micGranted else speedGranted },
        microphone = "MIC",
        vehicleSpeed = "SPEED",
    )

    @Test fun `unticked required consent cannot start`() = assertFalse(canStart(false))

    @Test fun `microphone is asked before speed`() = runBlocking {
        assertEquals(OnboardingOutcome.Done, flow().afterConsent())
        assertEquals(listOf("ask MIC", "ask SPEED"), log)
    }

    @Test fun `denied microphone ends on the microphone screen but speed is still asked`() = runBlocking {
        assertEquals(OnboardingOutcome.MicDenied, flow(micGranted = false).afterConsent())
        assertEquals(listOf("ask MIC", "ask SPEED"), log)
    }

    @Test fun `denied speed alone still goes on`() = runBlocking {
        assertEquals(OnboardingOutcome.Done, flow(micGranted = true, speedGranted = false).afterConsent())
    }
}
