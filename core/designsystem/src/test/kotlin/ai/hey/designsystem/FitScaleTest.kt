package ai.hey.designsystem

import ai.hey.designsystem.layout.fitScale
import org.junit.Assert.assertEquals
import org.junit.Test

/** The 1600x900 canvas of [ai.hey.designsystem.layout.WideCanvas] fits the window by the smaller axis ratio. */
class FitScaleTest {
    @Test fun sixteenByNineFillsTheWindow() {
        assertEquals(1f, fitScale(1600, 900), 1e-6f)
        assertEquals(1.6f, fitScale(2560, 1440), 1e-6f)
        assertEquals(0.8f, fitScale(1280, 720), 1e-6f)
    }

    @Test fun realCarWindowIsLetterboxedTopAndBottom() {
        // 1682x1210 px (1.39:1): width limits, canvas is 1682 x 946 px, so 264 px of bars in total.
        val s = fitScale(1682, 1210)
        assertEquals(1682f / 1600f, s, 1e-6f)
        assertEquals(1210f - 900f * s, 264f, 0.5f)
    }

    @Test fun tallWindowIsLimitedByWidthAndNeverClipped() {
        val s = fitScale(840, 1212)
        assertEquals(0.525f, s, 1e-6f)
        assert(1600 * s <= 840.001f && 900 * s <= 1212f)
    }
}
