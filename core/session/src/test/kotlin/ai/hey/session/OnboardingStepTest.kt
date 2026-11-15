package ai.hey.session

import ai.hey.model.OnboardingStep
import ai.hey.model.OnboardingAnswers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingStepTest {
    @Test fun `the order is the planner's - title, whale, consent, lights, questions, found, long press, greeting, memory, dress, phone, farewell`() {
        val order = generateSequence(OnboardingStep.Title) { if (it == OnboardingStep.Done) null else it.next() }.toList()
        assertEquals(
            listOf(
                OnboardingStep.Title, OnboardingStep.WhaleIntro, OnboardingStep.Consent, OnboardingStep.Lights,
                OnboardingStep.Q1, OnboardingStep.Q2, OnboardingStep.Q3, OnboardingStep.Found, OnboardingStep.Summon,
                OnboardingStep.Greeting, OnboardingStep.Memory, OnboardingStep.Dress, OnboardingStep.Qr, OnboardingStep.Farewell,
                OnboardingStep.Done,
            ),
            order,
        )
        assertEquals(OnboardingStep.Done, OnboardingStep.Done.next())
    }

    @Test fun `spoken steps run while driving, steps that need a press wait until parked`() {
        val spokenWhileDriving = listOf(
            OnboardingStep.WhaleIntro, OnboardingStep.Lights, OnboardingStep.Q1, OnboardingStep.Q2, OnboardingStep.Q3,
            OnboardingStep.Found, OnboardingStep.Greeting, OnboardingStep.Memory, OnboardingStep.Farewell,
        )
        for (s in spokenWhileDriving) {
            assertTrue("$s while driving", s.canRun(parked = false))
            assertTrue("$s parked", s.canRun(parked = true))
        }
        val pressSteps = listOf(OnboardingStep.Title, OnboardingStep.Consent, OnboardingStep.Summon, OnboardingStep.Dress, OnboardingStep.Qr)
        for (s in pressSteps) {
            assertFalse("$s while driving", s.canRun(parked = false))
            assertTrue("$s parked", s.canRun(parked = true))
        }
        assertFalse(OnboardingStep.Done.canRun(parked = true))
    }

    @Test fun `only the whale steps carry the whale's voice and the steps that need no consent are fixed lines`() {
        assertEquals(
            listOf(OnboardingStep.WhaleIntro, OnboardingStep.Lights, OnboardingStep.Q1, OnboardingStep.Q2, OnboardingStep.Q3, OnboardingStep.Found),
            OnboardingStep.entries.filter { it.whale },
        )
        // the questions and the greeting use what the driver said: they need the consent
        for (s in listOf(OnboardingStep.Q1, OnboardingStep.Q2, OnboardingStep.Q3, OnboardingStep.Greeting)) assertFalse("$s", s.consentFree)
        assertTrue(OnboardingStep.WhaleIntro.consentFree)
    }

    @Test fun `a stored name resumes at that step and unknown or missing means done`() {
        assertEquals(OnboardingStep.Q3, OnboardingStep.fromName("Q3"))
        assertEquals(OnboardingStep.Done, OnboardingStep.fromName(null))
        assertEquals(OnboardingStep.Done, OnboardingStep.fromName("gone"))
    }

    @Test fun `the wire names are the ones the server speaks`() {
        assertEquals(
            listOf("whale_intro", "lights", "q1", "q2", "q3", "found", "greeting", "memory", "dress", "qr", "done"),
            OnboardingStep.entries.mapNotNull { it.wire },
        )
    }

    @Test fun `a skipped question leaves no answer`() {
        assertFalse(OnboardingAnswers().any)
        assertTrue(OnboardingAnswers(trips = "출퇴근").any)
    }
}
