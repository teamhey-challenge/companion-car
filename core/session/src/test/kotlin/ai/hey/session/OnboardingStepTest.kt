package ai.hey.session

import ai.hey.model.OnboardingStep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingStepTest {
    @Test fun `the order is whale, three questions, summon, greeting, cards, look, phone, done`() {
        val order = generateSequence(OnboardingStep.WhaleIntro) { if (it == OnboardingStep.Done) null else it.next() }.toList()
        assertEquals(
            listOf(
                OnboardingStep.WhaleIntro, OnboardingStep.Q1, OnboardingStep.Q2, OnboardingStep.Q3, OnboardingStep.Summon,
                OnboardingStep.Greeting, OnboardingStep.Cards, OnboardingStep.Look, OnboardingStep.Pair, OnboardingStep.Done,
            ),
            order,
        )
        assertEquals(OnboardingStep.Done, OnboardingStep.Done.next())
    }

    @Test fun `spoken steps run while driving, parked-only steps wait until parked`() {
        for (s in listOf(OnboardingStep.WhaleIntro, OnboardingStep.Q1, OnboardingStep.Q2, OnboardingStep.Q3, OnboardingStep.Greeting)) {
            assertTrue("$s while driving", s.canRun(parked = false))
            assertTrue("$s parked", s.canRun(parked = true))
        }
        for (s in listOf(OnboardingStep.Summon, OnboardingStep.Cards, OnboardingStep.Look, OnboardingStep.Pair)) {
            assertFalse("$s while driving", s.canRun(parked = false))
            assertTrue("$s parked", s.canRun(parked = true))
        }
        assertFalse(OnboardingStep.Done.canRun(parked = true))
    }

    @Test fun `a stored name resumes at that step and unknown or missing means done`() {
        assertEquals(OnboardingStep.Q3, OnboardingStep.fromName("Q3"))
        assertEquals(OnboardingStep.Done, OnboardingStep.fromName(null))
        assertEquals(OnboardingStep.Done, OnboardingStep.fromName("gone"))
    }

    @Test fun `the wire names are the ones the server speaks`() {
        assertEquals(
            listOf("whale_intro", "q1", "q2", "q3", "greeting"),
            OnboardingStep.entries.mapNotNull { it.wire },
        )
    }
}
