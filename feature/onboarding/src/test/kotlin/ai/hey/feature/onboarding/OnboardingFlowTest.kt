package ai.hey.feature.onboarding

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingFlowTest {
    private val log = mutableListOf<String>()

    private fun flow(submitOk: Boolean = true, micGranted: Boolean = true) = OnboardingFlow(
        submitConsent = { memory -> log += "consent memory=$memory"; submitOk },
        requestPermission = { p -> log += "ask $p"; if (p == "MIC") micGranted else false },
        microphone = "MIC",
        vehicleSpeed = "SPEED",
    )

    @Test fun `unticked required consent sends nothing and asks nothing`() = runBlocking {
        assertEquals(OnboardingOutcome.VoiceRequired, flow().start(voice = false, memory = true))
        assertEquals(emptyList<String>(), log)
    }

    @Test fun `consent is recorded before any permission and the memory tick is passed on`() = runBlocking {
        assertEquals(OnboardingOutcome.Done, flow().start(voice = true, memory = false))
        assertEquals(listOf("consent memory=false", "ask MIC", "ask SPEED"), log)
    }

    @Test fun `a failed save asks no permission`() = runBlocking {
        assertEquals(OnboardingOutcome.NetworkError, flow(submitOk = false).start(voice = true, memory = true))
        assertEquals(listOf("consent memory=true"), log)
    }

    @Test fun `denied microphone ends on the microphone screen but speed is still asked`() = runBlocking {
        assertEquals(OnboardingOutcome.MicDenied, flow(micGranted = false).start(voice = true, memory = true))
        assertEquals(listOf("consent memory=true", "ask MIC", "ask SPEED"), log)
    }

    @Test fun `denied speed alone still goes on`() = runBlocking {
        assertEquals(OnboardingOutcome.Done, flow().start(voice = true, memory = true))
    }
}
