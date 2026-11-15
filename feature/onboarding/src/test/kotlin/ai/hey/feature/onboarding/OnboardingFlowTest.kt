package ai.hey.feature.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class OnboardingFlowTest {
    @Test fun `unticked required consent cannot start`() = assertFalse(canStart(false))

    @Test fun `memory off shows the guidance and never the cards, even when answers are known`() {
        val known = ai.hey.model.OnboardingAnswers(name = "민수", trips = "출퇴근")
        assertEquals(KnownCardsMode.MemoryOff, knownCardsMode(memoryEnabled = false, answers = known))
        assertEquals(KnownCardsMode.MemoryOff, knownCardsMode(memoryEnabled = false, answers = ai.hey.model.OnboardingAnswers()))
        assertEquals(KnownCardsMode.Cards, knownCardsMode(memoryEnabled = true, answers = known))
        // every question skipped: no polaroid at all
        assertEquals(KnownCardsMode.Empty, knownCardsMode(memoryEnabled = true, answers = ai.hey.model.OnboardingAnswers()))
    }
}
