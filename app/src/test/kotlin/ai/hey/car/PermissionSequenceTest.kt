package ai.hey.car

import ai.hey.vehicle.DrivingStatus
import ai.hey.vehicle.VehicleGear
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PermissionSequenceTest {
    private val mic = "MIC"
    private val notif = "NOTIF"
    private val speed = "SPEED"

    private class Memory : PermissionProgress {
        val done = mutableSetOf<String>()
        override fun asked(permission: String) = permission in done
        override fun markAsked(permission: String) { done += permission }
    }

    private val log = mutableListOf<String>()
    private val granted = mutableSetOf<String>()
    private val progress = Memory()
    private val results = ArrayDeque<PermissionResult>()

    private fun sequence(awaitParked: suspend () -> Unit = {}) = PermissionSequence(
        steps = listOf(mic, notif, speed),
        microphone = mic,
        isGranted = { it in granted },
        progress = progress,
        awaitParked = awaitParked,
        ask = { p ->
            log += "ask $p"
            val r = results.removeFirstOrNull() ?: PermissionResult.Granted
            if (r == PermissionResult.Granted) granted += p
            r
        },
    )

    @Test fun `asks microphone then notifications then vehicle speed, one at a time`() = runTest {
        assertTrue(sequence().run())
        assertEquals(listOf("ask MIC", "ask NOTIF", "ask SPEED"), log)
    }

    @Test fun `a refusal is kept and the sequence goes on`() = runTest {
        results += PermissionResult.Denied
        assertFalse(sequence().run())
        assertEquals(listOf("ask MIC", "ask NOTIF", "ask SPEED"), log)
    }

    @Test fun `a cancelled request is not a refusal and is asked again`() = runTest {
        results += PermissionResult.Cancelled
        assertTrue(sequence().run())
        assertEquals(listOf("ask MIC", "ask MIC", "ask NOTIF", "ask SPEED"), log)
    }

    @Test fun `after a process death the sequence resumes at the first undone step`() = runTest {
        granted += mic
        progress.markAsked(mic)
        progress.markAsked(notif) // refused earlier: kept, not asked again
        assertTrue(sequence().run())
        assertEquals(listOf("ask SPEED"), log)
    }

    @Test fun `process killed while a dialog is open resumes at that step with a new sequence`() = runTest {
        val gate = CompletableDeferred<PermissionResult>()
        val first = PermissionSequence(
            steps = listOf(mic, notif, speed), microphone = mic, isGranted = { it in granted }, progress = progress,
            awaitParked = {},
            ask = { p ->
                log += "ask $p"
                if (p == notif) gate.await() else { granted += p; PermissionResult.Granted }
            },
        )
        val run = launch { first.run() }
        runCurrent()
        assertEquals(listOf("ask MIC", "ask NOTIF"), log)
        run.cancel() // the process dies here: only `granted` (the system) and `progress` (prefs) survive
        runCurrent()
        log.clear()
        // the next launch builds a brand new sequence over the same stored state and asks exactly the unanswered steps
        assertTrue(sequence().run())
        assertEquals(listOf("ask NOTIF", "ask SPEED"), log)
        // and a third launch has nothing left to ask
        log.clear()
        assertTrue(sequence().run())
        assertEquals(emptyList<String>(), log)
    }

    @Test fun `already finished sequence asks nothing`() = runTest {
        granted += listOf(mic, notif, speed)
        assertTrue(sequence().run())
        assertEquals(emptyList<String>(), log)
    }

    @Test fun `nothing is asked until the car is parked`() = runTest {
        val parked = CompletableDeferred<Unit>()
        val run = async { sequence { parked.await() }.run() }
        runCurrent()
        assertEquals(emptyList<String>(), log)
        parked.complete(Unit)
        assertTrue(run.await())
    }

    @Test fun `two runs at once never ask the same step twice`() = runTest {
        val s = sequence()
        val a = async { s.run() }
        val b = async { s.run() }
        a.await(); b.await()
        assertEquals(listOf("ask MIC", "ask NOTIF", "ask SPEED"), log)
    }

    // --- the parked blip that swallowed the dialog -----------------------------------------------------------------

    @Test fun `parked to unknown blip does not cancel the open dialog`() = runTest {
        val moving = MutableStateFlow(false)
        val answer = CompletableDeferred<PermissionResult>()
        val out = async { cancelWhenDriving(moving, DRIVING_CANCEL_HOLD_MS) { answer.await() } }
        runCurrent()
        // Unknown without a gear is not "moving"; and even a real Driving shorter than the hold is a blip
        moving.value = isTrulyMoving(VehicleGear.Park, DrivingStatus.Unknown)
        advanceTimeBy(2_000); runCurrent()
        moving.value = true
        advanceTimeBy(DRIVING_CANCEL_HOLD_MS - 100); runCurrent()
        moving.value = false
        advanceTimeBy(5_000); runCurrent()
        assertFalse(out.isCompleted)
        answer.complete(PermissionResult.Granted)
        assertEquals(PermissionResult.Granted, out.await())
    }

    @Test fun `gear D held for the hold time cancels, not refuses`() = runTest {
        val moving = MutableStateFlow(false)
        val answer = CompletableDeferred<PermissionResult>()
        val out = async { cancelWhenDriving(moving, DRIVING_CANCEL_HOLD_MS) { answer.await() } }
        runCurrent()
        moving.value = isTrulyMoving(VehicleGear.Drive, DrivingStatus.Parked)
        advanceTimeBy(DRIVING_CANCEL_HOLD_MS + 1); runCurrent()
        assertNull(out.await())
    }

    @Test fun `trulyMoving needs gear D or R or a Driving report, never Unknown alone`() {
        assertFalse(isTrulyMoving(VehicleGear.Park, DrivingStatus.Unknown))
        assertFalse(isTrulyMoving(VehicleGear.Unknown, DrivingStatus.Unknown))
        assertFalse(isTrulyMoving(VehicleGear.Park, DrivingStatus.Parked))
        assertTrue(isTrulyMoving(VehicleGear.Drive, DrivingStatus.Unknown))
        assertTrue(isTrulyMoving(VehicleGear.Reverse, DrivingStatus.Parked))
        assertTrue(isTrulyMoving(VehicleGear.Park, DrivingStatus.Driving))
    }

    // --- one dialog at a time -----------------------------------------------------------------------------------------

    @Test fun `a second ask for another permission waits for the open dialog`() = runTest {
        val launched = mutableListOf<String>()
        val dialogs = PermissionDialogs { launched += it }
        val first = async { dialogs.ask(mic) }
        runCurrent()
        val second = async { dialogs.ask(notif) }
        runCurrent()
        assertEquals(listOf(mic), launched)
        dialogs.onResult(mapOf(mic to true))
        assertEquals(PermissionResult.Granted, first.await())
        runCurrent()
        assertEquals(listOf(mic, notif), launched)
        dialogs.onResult(mapOf(notif to false))
        assertEquals(PermissionResult.Denied, second.await())
    }

    @Test fun `an asker that gave up leaves the dialog open and the next ask shares it`() = runTest {
        val launched = mutableListOf<String>()
        val dialogs = PermissionDialogs { launched += it }
        val gaveUp = launch { dialogs.ask(mic) }
        runCurrent()
        gaveUp.cancel()
        runCurrent()
        val again = async { dialogs.ask(mic) }
        runCurrent()
        assertEquals(listOf(mic), launched)
        dialogs.onResult(mapOf(mic to true))
        assertEquals(PermissionResult.Granted, again.await())
    }

    @Test fun `an empty system result is a cancel`() = runTest {
        val dialogs = PermissionDialogs { }
        val r = async { dialogs.ask(mic) }
        runCurrent()
        dialogs.onResult(emptyMap())
        assertEquals(PermissionResult.Cancelled, r.await())
    }
}
