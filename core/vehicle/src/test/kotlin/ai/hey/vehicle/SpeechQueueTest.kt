package ai.hey.vehicle

import ai.hey.model.SpeechPriority
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SpeechQueueTest {
    /** [speakMillis] is how long one reading takes; [works] false makes the speaker fail. */
    private class Rig(val scope: TestScope, gapSec: Int = 8, queueMax: Int = 5, val speakMillis: Long = 0) {
        val said = ArrayList<String>()
        val events = ArrayList<VehicleEvent>()
        var works = true
        var gate: CompletableDeferred<Unit>? = null
        val queue = SpeechQueue(
            scope = scope.backgroundScope,
            engine = object : SpeechEngine {
                override suspend fun speak(text: String): Boolean {
                    if (!works) return false
                    said += "$text@${scope.currentTime}"
                    gate?.await()
                    if (speakMillis > 0) kotlinx.coroutines.delay(speakMillis)
                    return true
                }
            },
            settings = { SpeechSettings(gapSec = gapSec) },
            now = { scope.currentTime },
            emit = { events += it },
            dedupWindowMs = 60_000,
            queueMax = queueMax,
        )

        fun texts() = said.map { it.substringBefore("@") }
    }

    @Test fun normalSpeechWaitsTheGapHighDoesNot() = runTest {
        val rig = Rig(this)
        rig.queue.enqueue("a", SpeechPriority.Normal, "1")
        advanceTimeBy(1)
        rig.queue.enqueue("b", SpeechPriority.Normal, "2")
        rig.queue.enqueue("c", SpeechPriority.High, "3")
        advanceTimeBy(100)
        assertEquals(listOf("a", "c"), rig.texts())
        advanceTimeBy(8_500)
        assertEquals(listOf("a", "c", "b"), rig.texts())
        assertTrue(rig.said.last().substringAfter("@").toLong() >= 8_000)
    }

    @Test fun highGoesBeforeQueuedNormal() = runTest {
        val rig = Rig(this)
        rig.queue.enqueue("first", SpeechPriority.Normal, "1")
        advanceTimeBy(1)
        rig.queue.enqueue("n", SpeechPriority.Normal, "2")
        rig.queue.enqueue("h", SpeechPriority.High, "3")
        advanceTimeBy(20_000)
        assertEquals(listOf("first", "h", "n"), rig.texts())
    }

    @Test fun aCopyOfAWaitingRequestIsNotDoneUntilTheTextIsHeard() = runTest {
        val rig = Rig(this)
        rig.queue.enqueue("first", SpeechPriority.Normal, "r0")
        advanceTimeBy(1)
        rig.queue.enqueue("창문을 열어도 될까요?", SpeechPriority.Normal, "r1")
        rig.queue.enqueue("창문을 열어도 될까요?", SpeechPriority.Normal, "r2")
        // r1 and r2 wait behind the gap: nothing may be reported for them yet
        assertFalse(rig.events.any { it == VehicleEvent.SpeechDone("r1") || it == VehicleEvent.SpeechDone("r2") })
        advanceTimeBy(30_000)
        assertEquals(listOf("first", "창문을 열어도 될까요?"), rig.texts())
        assertTrue(rig.events.contains(VehicleEvent.SpeechDone("r1")))
        assertTrue(rig.events.contains(VehicleEvent.SpeechDone("r2")))
    }

    @Test fun aRepeatAfterAReadingInsideTheWindowIsAcknowledgedWithoutReading() = runTest {
        val rig = Rig(this)
        rig.queue.enqueue("카드로 결제할 수 있나요?", SpeechPriority.Normal, "r1")
        advanceTimeBy(1_000)
        rig.queue.enqueue("카드로 결제할 수 있나요?", SpeechPriority.Normal, "r2")
        advanceTimeBy(100)
        assertEquals(1, rig.said.size)
        assertTrue(rig.events.contains(VehicleEvent.SpeechDone("r2")))
        advanceTimeBy(61_000)
        rig.queue.enqueue("카드로 결제할 수 있나요?", SpeechPriority.Normal, "r3")
        advanceTimeBy(100)
        assertEquals(2, rig.said.size)
    }

    @Test fun highPriorityIsNeverDeduplicated() = runTest {
        val rig = Rig(this)
        rig.queue.enqueue("여기서 내려 주세요.", SpeechPriority.High, "r1")
        advanceTimeBy(1)
        rig.queue.enqueue("여기서 내려 주세요.", SpeechPriority.High, "r2")
        advanceTimeBy(10)
        rig.queue.enqueue("승객이 내릴 곳을 지도에서 골랐어요.", SpeechPriority.High, null)
        rig.queue.enqueue("승객이 내릴 곳을 지도에서 골랐어요.", SpeechPriority.High, null)
        advanceTimeBy(10)
        assertEquals(4, rig.said.size)
    }

    @Test fun aDroppedTextIsNotRememberedAsHeard() = runTest {
        val rig = Rig(this, queueMax = 1)
        rig.queue.enqueue("0", SpeechPriority.Normal, "i0")
        advanceTimeBy(1)
        rig.queue.enqueue("느린 요청", SpeechPriority.Normal, "iA")
        rig.queue.enqueue("다음 요청", SpeechPriority.Normal, "iB") // pushes iA out of the queue
        assertTrue(rig.events.contains(VehicleEvent.SpeechDropped("iA")))
        advanceTimeBy(60_000)
        rig.queue.enqueue("느린 요청", SpeechPriority.Normal, "iC")
        advanceTimeBy(60_000)
        assertTrue("the dropped text must be read when it comes again", rig.texts().contains("느린 요청"))
        assertTrue(rig.events.contains(VehicleEvent.SpeechDone("iC")))
    }

    @Test fun aBrokenSpeakerDropsInsteadOfReportingDone() = runTest {
        val rig = Rig(this)
        rig.works = false
        rig.queue.enqueue("창문", SpeechPriority.Normal, "r1")
        advanceTimeBy(100)
        assertTrue(rig.events.contains(VehicleEvent.SpeechDropped("r1")))
        assertFalse(rig.events.any { it is VehicleEvent.SpeechDone })
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
        assertEquals(listOf("0", "2", "3"), rig.texts())
        assertTrue(rig.events.contains(VehicleEvent.SpeechDropped("i1")))
    }
}
