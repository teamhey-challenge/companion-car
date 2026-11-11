package ai.hey.vehicle

import ai.hey.model.SpeechPriority
import ai.hey.model.SpeechRate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SpeechQueueTest {
    private class Rig(val scope: TestScope, gapSec: Int = 8, queueMax: Int = 5) {
        val said = ArrayList<String>()
        val events = ArrayList<VehicleEvent>()
        val queue = SpeechQueue(
            scope = scope.backgroundScope,
            engine = object : SpeechEngine {
                override suspend fun speak(text: String, rate: SpeechRate): Boolean {
                    said += "$text@${scope.currentTime}"
                    return true
                }
            },
            settings = { SpeechSettings(gapSec = gapSec) },
            now = { scope.currentTime },
            emit = { events += it },
            dedupWindowMs = 60_000,
            queueMax = queueMax,
        )
    }

    @Test fun normalSpeechWaitsTheGapHighDoesNot() = runTest {
        val rig = Rig(this)
        rig.queue.enqueue("a", SpeechPriority.Normal, "1")
        advanceTimeBy(1)
        rig.queue.enqueue("b", SpeechPriority.Normal, "2")
        rig.queue.enqueue("c", SpeechPriority.High, "3")
        advanceTimeBy(100)
        assertEquals(listOf("a", "c"), rig.said.map { it.substringBefore("@") })
        advanceTimeBy(8_500)
        assertEquals(listOf("a", "c", "b"), rig.said.map { it.substringBefore("@") })
        assertEquals(true, rig.said.last().substringAfter("@").toLong() >= 8_000)
    }

    @Test fun highGoesBeforeQueuedNormal() = runTest {
        val rig = Rig(this)
        rig.queue.enqueue("first", SpeechPriority.Normal, "1")
        advanceTimeBy(1)
        rig.queue.enqueue("n", SpeechPriority.Normal, "2")
        rig.queue.enqueue("h", SpeechPriority.High, "3")
        advanceTimeBy(20_000)
        assertEquals(listOf("first", "h", "n"), rig.said.map { it.substringBefore("@") })
    }

    @Test fun sameTextInsideWindowIsNotReadAgainButIsAcknowledged() = runTest {
        val rig = Rig(this)
        rig.queue.enqueue("창문을 열어도 될까요?", SpeechPriority.Normal, "r1")
        advanceTimeBy(1)
        rig.queue.enqueue("창문을 열어도 될까요?", SpeechPriority.Normal, "r2")
        advanceTimeBy(30_000)
        assertEquals(1, rig.said.size)
        assertEquals(listOf<VehicleEvent>(VehicleEvent.SpeechDone("r2"), VehicleEvent.SpeechDone("r1")).toSet(), rig.events.toSet())
        advanceTimeBy(61_000)
        rig.queue.enqueue("창문을 열어도 될까요?", SpeechPriority.Normal, "r3")
        advanceTimeBy(100)
        assertEquals(2, rig.said.size)
    }

    @Test fun replayForcesRepeat() = runTest {
        val rig = Rig(this)
        rig.queue.enqueue("목적지", SpeechPriority.High, null)
        advanceTimeBy(1)
        rig.queue.enqueue("목적지", SpeechPriority.High, null, force = true)
        advanceTimeBy(10)
        assertEquals(2, rig.said.size)
    }

    @Test fun overflowDropsOldestNormalAndReportsIt() = runTest {
        val rig = Rig(this, queueMax = 2)
        rig.queue.enqueue("0", SpeechPriority.Normal, "i0")
        advanceTimeBy(1)
        listOf("1", "2", "3").forEach { rig.queue.enqueue(it, SpeechPriority.Normal, "i$it") }
        advanceTimeBy(60_000)
        assertEquals(listOf("0", "2", "3"), rig.said.map { it.substringBefore("@") })
        assertEquals(true, rig.events.contains(VehicleEvent.SpeechDropped("i1")))
    }
}
