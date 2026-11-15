package ai.hey.car

import ai.hey.model.ServerFrame
import ai.hey.model.parseServerFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameNotifierTest {
    private val posted = mutableListOf<CenterNotice>()
    private val notifier = FrameNotifier({ posted += it })

    @Test fun `friend message and trip done are posted, with a name particle`() {
        notifier.onFrame(ServerFrame.FriendMessage("m1", "지아", "곧 도착해"))
        notifier.onFrame(ServerFrame.FriendMessage("m2", "민준", "안녕"))
        notifier.onFrame(ServerFrame.TripDone("t1", "오늘도 같이 달렸어. 수고했어!", "2026-11-15", "2026-11-15T05:00:00Z"))
        assertEquals(
            listOf(
                CenterNotice.Friend("m1", "지아", "곧 도착해", subjectIsI = false),
                CenterNotice.Friend("m2", "민준", "안녕", subjectIsI = true),
                CenterNotice.TripDiary("t1", "오늘도 같이 달렸어. 수고했어!"),
            ),
            posted,
        )
    }

    @Test fun `the same frame id is posted once, also after other frames`() {
        val f = ServerFrame.FriendMessage("m1", "지아", "hi")
        notifier.onFrame(f)
        notifier.onFrame(ServerFrame.TripDone("t1", "x", "d", "a"))
        notifier.onFrame(f)
        notifier.onFrame(ServerFrame.TripDone("t1", "x", "d", "a"))
        assertEquals(listOf("m1", "t1"), posted.map { it.frameId })
    }

    @Test fun `an id falls out of memory after the window, so an old replay can show again`() {
        val small = FrameNotifier({ posted += it }, remembered = 2)
        for (id in listOf("a", "b", "c")) small.onFrame(ServerFrame.TripDone(id, "x", "d", "a"))
        small.onFrame(ServerFrame.TripDone("c", "x", "d", "a"))
        assertEquals(listOf("a", "b", "c"), posted.map { it.frameId })
        small.onFrame(ServerFrame.TripDone("a", "x", "d", "a"))
        assertEquals(4, posted.size)
    }

    @Test fun `other frames never post a notification (two kinds only)`() {
        notifier.onFrame(ServerFrame.FriendArrived("a1", "지아"))
        notifier.onFrame(ServerFrame.Proactive("p1", "greet"))
        notifier.onFrame(ServerFrame.Error("x", null, null))
        assertTrue(posted.isEmpty())
    }

    @Test fun `the trip_done wire frame parses and a frame without text or id is dropped`() {
        val f = parseServerFrame("""{"type":"trip_done","id":"t9","at":"2026-11-15T05:00:00.000Z","text":"오늘도 같이 달렸어. 수고했어!","date":"2026-11-15"}""")
        assertEquals(ServerFrame.TripDone("t9", "오늘도 같이 달렸어. 수고했어!", "2026-11-15", "2026-11-15T05:00:00.000Z"), f)
        assertEquals(null, parseServerFrame("""{"type":"trip_done","id":"t9"}"""))
        assertEquals(null, parseServerFrame("""{"type":"trip_done","text":"x"}"""))
    }

    @Test fun `trip_done carries the car line the server chose`() {
        val f = parseServerFrame("""{"type":"trip_done","id":"t9","at":"a","text":"x","date":"d","carLine":"폰을 연결하면 일기를 볼 수 있어"}""")
        assertEquals("폰을 연결하면 일기를 볼 수 있어", (f as ServerFrame.TripDone).carLine)
    }

    @Test fun `final consonant detection`() {
        assertTrue(FrameNotifier.endsWithFinalConsonant("민준"))
        assertFalse(FrameNotifier.endsWithFinalConsonant("지아"))
        assertFalse(FrameNotifier.endsWithFinalConsonant("Amy"))
        assertFalse(FrameNotifier.endsWithFinalConsonant(""))
    }
}
