package ai.hey.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

class FrameThrottleTest {
    private fun drawnPerSecond(vsyncHz: Int, jitterNanos: (Int) -> Long = { 0 }): Int {
        val throttle = FrameThrottle()
        var drawn = 0
        for (i in 0 until vsyncHz * 10) {
            val t = i * 1_000_000_000L / vsyncHz + jitterNanos(i)
            if (throttle.shouldDraw(t)) drawn++
        }
        return drawn / 10
    }

    @Test fun `60 Hz draws 30 per second`() = assertEquals(30, drawnPerSecond(60))

    @Test fun `60 Hz with half a millisecond of jitter still draws 30 per second`() =
        assertEquals(30, drawnPerSecond(60) { if (it % 2 == 0) 400_000L else -400_000L })

    @Test fun `120 Hz draws 30 per second`() = assertEquals(30, drawnPerSecond(120))

    @Test fun `30 Hz and below draw every frame`() {
        assertEquals(30, drawnPerSecond(30))
    }

    @Test fun `a dropped vsync does not make the next one wait a whole extra frame`() {
        val throttle = FrameThrottle()
        assertEquals(true, throttle.shouldDraw(0))
        assertEquals(false, throttle.shouldDraw(16_700_000))
        // the vsync at 33.4 ms was dropped by the system: the one at 50.0 ms is drawn, not skipped as "odd"
        assertEquals(true, throttle.shouldDraw(50_100_000))
        assertEquals(false, throttle.shouldDraw(66_800_000))
        assertEquals(true, throttle.shouldDraw(83_500_000))
    }
}
