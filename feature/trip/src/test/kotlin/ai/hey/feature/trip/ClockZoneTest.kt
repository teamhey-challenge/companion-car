package ai.hey.feature.trip

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.util.TimeZone

/** The emulator showed 오후 2:03 in the app next to 09:03 오후 in the system bar: the app used a fixed zone. */
class ClockZoneTest {
    private val original = TimeZone.getDefault()

    @After fun restore() = TimeZone.setDefault(original)

    @Test fun theClockFollowsTheSystemZone() {
        val instant = TripSamples.NOW // 2026-11-12 23:42 KST = 06:42 in Los Angeles (PST)
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
        assertEquals("오전 6:42", clockLabel(instant))
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"))
        assertEquals("오후 11:42", clockLabel(instant))
    }

    @Test fun anExplicitZoneWins() {
        assertEquals("오후 11:42", clockLabel(TripSamples.NOW, ZoneId.of("Asia/Seoul")))
        assertEquals("오전 6:42", clockLabel(TripSamples.NOW, ZoneId.of("America/Los_Angeles")))
    }
}
