package ai.hey.vehicle

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The charge switch is polled, but only while the car is parked: a drive must not read it at all. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChargeWatchTest {
    private val gear = MutableStateFlow(VehicleGear.Drive)
    private val driving = MutableStateFlow<DrivingStatus>(DrivingStatus.Driving)
    private var reads = 0
    private var charging = false
    private val reported = ArrayList<Boolean>()

    private fun watch(
        read: (onResult: (Boolean) -> Unit) -> Unit = { onResult ->
            reads++
            onResult(charging)
        },
    ) = ChargeWatch(
        intervalMs = 10_000L,
        gear = gear,
        driving = driving,
        read = read,
        onChange = { reported += it },
    )

    @Test fun `a driving car never reads the charge switch`() = runTest {
        backgroundScope.launch { watch().run() }
        advanceTimeBy(120_000)
        runCurrent()
        assertEquals(0, reads)
        assertEquals(emptyList<Boolean>(), reported)
    }

    @Test fun `a charge start while parked is detected on the next read`() = runTest {
        backgroundScope.launch { watch().run() }
        gear.value = VehicleGear.Park
        runCurrent()
        assertEquals(1, reads) // parked now: read at once, not one interval later
        assertEquals(listOf(false), reported)

        charging = true
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(2, reads)
        assertEquals(listOf(false, true), reported)

        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(3, reads)
        assertEquals(listOf(false, true), reported) // one charge start is reported once
    }

    @Test fun `driving off stops the poll and clears charging for the next session`() = runTest {
        gear.value = VehicleGear.Park
        charging = true
        backgroundScope.launch { watch().run() }
        runCurrent()
        assertEquals(listOf(true), reported)

        gear.value = VehicleGear.Drive
        runCurrent()
        val readWhileParked = reads
        assertEquals(listOf(true, false), reported) // the cable is out before a car moves off
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(readWhileParked, reads)

        gear.value = VehicleGear.Park
        runCurrent()
        assertEquals(readWhileParked + 1, reads)
        assertEquals(listOf(true, false, true), reported) // the new session's start is its own event
    }

    @Test fun `without a gear signal the car's own restriction state decides`() = runTest {
        gear.value = VehicleGear.Unknown
        driving.value = DrivingStatus.Parked
        backgroundScope.launch { watch().run() }
        runCurrent()
        assertEquals(1, reads)

        driving.value = DrivingStatus.Driving
        runCurrent()
        val readWhileParked = reads
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(readWhileParked, reads)

        driving.value = DrivingStatus.Unknown // not "probably parked": no read
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(readWhileParked, reads)
    }

    @Test fun `a car whose switch never answers reports nothing, not even when it drives off`() = runTest {
        gear.value = VehicleGear.Park
        backgroundScope.launch { watch(read = { /* the SDK never answers: no EV battery */ }).run() }
        runCurrent()
        advanceTimeBy(30_000)
        runCurrent()
        gear.value = VehicleGear.Drive
        runCurrent()
        assertEquals(emptyList<Boolean>(), reported)
    }

    @Test fun `only P counts as parked when the gear is known`() {
        assertTrue(chargePollAllowed(VehicleGear.Park, DrivingStatus.Unknown))
        assertFalse(chargePollAllowed(VehicleGear.Drive, DrivingStatus.Parked))
        assertFalse(chargePollAllowed(VehicleGear.Reverse, DrivingStatus.Parked))
        assertFalse(chargePollAllowed(VehicleGear.Neutral, DrivingStatus.Parked))
        assertTrue(chargePollAllowed(VehicleGear.Unknown, DrivingStatus.Parked))
        assertFalse(chargePollAllowed(VehicleGear.Unknown, DrivingStatus.Driving))
        assertFalse(chargePollAllowed(VehicleGear.Unknown, DrivingStatus.Unknown))
    }
}
