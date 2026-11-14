package ai.hey.session

import ai.hey.model.CompanionSettings
import ai.hey.model.ConsentType
import ai.hey.model.PairingPhase
import ai.hey.model.PairingStatus
import ai.hey.model.ProactiveLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CompanionRepositoryTest {
    private fun scope(s: TestCoroutineScheduler) = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(s))

    private class Rig(scope: CoroutineScope, val api: FakeCompanionApi = FakeCompanionApi()) {
        val storage = InMemoryCarStorage()
        val repo = CompanionRepository(scope, api, storage)
    }

    @Test fun `pairing polls every 2 s and ends on claimed`() = runTest {
        val cs = scope(testScheduler)
        val rig = Rig(cs)
        rig.repo.openPairing()
        assertEquals(PairingUi.Pending("ABCD2345", "2026-11-15T00:05:00Z"), rig.repo.state.value.pairing)
        advanceTimeBy(6_100)
        assertEquals(3, rig.api.pairingPolls)
        assertTrue(rig.repo.state.value.pairing is PairingUi.Pending)
        rig.api.pairing = PairingStatus(PairingPhase.Claimed, pairedAt = "x")
        advanceTimeBy(2_100)
        assertEquals(PairingUi.Claimed, rig.repo.state.value.pairing)
        assertTrue(rig.repo.state.value.paired)
        val polls = rig.api.pairingPolls
        advanceTimeBy(10_000)
        assertEquals(polls, rig.api.pairingPolls) // polling stopped
        cs.cancel()
    }

    @Test fun `pairing code that expires shows expired and closing C2 stops polling`() = runTest {
        val cs = scope(testScheduler)
        val rig = Rig(cs)
        rig.repo.openPairing()
        rig.api.pairing = PairingStatus(PairingPhase.Expired)
        advanceTimeBy(2_100)
        assertEquals(PairingUi.Expired, rig.repo.state.value.pairing)
        rig.repo.openPairing() // retry makes a new code
        assertEquals(2, rig.api.pairingsCreated)
        rig.repo.closePairing()
        val polls = rig.api.pairingPolls
        advanceTimeBy(10_000)
        assertEquals(polls, rig.api.pairingPolls)
        assertEquals(PairingUi.None, rig.repo.state.value.pairing)
        cs.cancel()
    }

    @Test fun `a failed code request shows Failed and a retry works`() = runTest {
        val cs = scope(testScheduler)
        val rig = Rig(cs)
        rig.api.failing = true
        rig.repo.openPairing()
        assertEquals(PairingUi.Failed, rig.repo.state.value.pairing)
        rig.api.failing = false
        rig.repo.openPairing()
        assertTrue(rig.repo.state.value.pairing is PairingUi.Pending)
        cs.cancel()
    }

    @Test fun `consent is mirrored locally only after the server accepted it`() = runTest {
        val cs = scope(testScheduler)
        val rig = Rig(cs)
        rig.api.failing = true
        rig.repo.submitConsent(memory = true)
        assertTrue(rig.repo.state.value.onboarding.failed)
        assertFalse(rig.storage.consents.value.recordDone)
        rig.api.failing = false
        rig.repo.submitConsent(memory = false)
        assertTrue(rig.storage.consents.value.recordDone)
        assertFalse(ConsentType.Memory in rig.storage.consents.value.granted)
        // AC-4.1-06: memory off at onboarding is the C7 value
        assertFalse(rig.repo.state.value.settings.memoryEnabled)
        assertFalse(rig.api.settings.memoryEnabled)
        cs.cancel()
    }

    @Test fun `withdrawing record clears memory and the local consent`() = runTest {
        val cs = scope(testScheduler)
        val rig = Rig(cs)
        rig.repo.submitConsent(memory = true)
        rig.repo.loadMemory()
        assertEquals(20, rig.repo.state.value.memory.items.size)
        rig.repo.withdrawRecordConsent()
        assertFalse(rig.storage.consents.value.recordDone)
        assertTrue(rig.repo.state.value.memory.items.isEmpty())
        assertTrue(rig.api.memoryItems.isEmpty())
        cs.cancel()
    }

    @Test fun `memory pages load and delete removes the row only after the server said ok`() = runTest {
        val cs = scope(testScheduler)
        val rig = Rig(cs)
        rig.repo.loadMemory()
        rig.repo.loadMoreMemory()
        rig.repo.loadMoreMemory()
        assertEquals(45, rig.repo.state.value.memory.items.size)
        assertFalse(rig.repo.state.value.memory.hasMore)
        rig.api.failing = true
        rig.repo.deleteMemory("m1")
        assertTrue(rig.repo.state.value.memory.deleteFailed)
        assertEquals(45, rig.repo.state.value.memory.items.size)
        rig.api.failing = false
        rig.repo.deleteMemory("m1")
        assertEquals(44, rig.repo.state.value.memory.items.size)
        rig.repo.deleteAllMemory()
        assertTrue(rig.repo.state.value.memory.items.isEmpty())
        assertTrue(rig.repo.state.value.memory.loaded)
        cs.cancel()
    }

    @Test fun `settings change shows at once and is rolled back when the save fails`() = runTest {
        val cs = scope(testScheduler)
        val rig = Rig(cs)
        rig.api.failing = true
        rig.repo.updateSettings { it.copy(proactive = ProactiveLevel.Off) }
        assertEquals(CompanionSettings(), rig.repo.state.value.settings)
        assertEquals(SettingsSave.Failed, rig.repo.state.value.settingsSave)
        rig.api.failing = false
        rig.repo.updateSettings { it.copy(proactive = ProactiveLevel.High) }
        assertEquals(ProactiveLevel.High, rig.api.settings.proactive)
        assertEquals(SettingsSave.Idle, rig.repo.state.value.settingsSave)
        // a settings frame from the phone replaces the value
        rig.repo.onServerSettings(CompanionSettings(proactive = ProactiveLevel.Low))
        assertEquals(5, ProactiveLevel.High.intervalMin)
        assertEquals(ProactiveLevel.Low, rig.repo.state.value.settings.proactive)
        cs.cancel()
    }
}
