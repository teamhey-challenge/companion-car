package ai.hey.feature.settings

import ai.hey.model.Friend
import ai.hey.model.ReceivedMessage
import ai.hey.session.CompanionState
import ai.hey.session.FriendsState
import ai.hey.session.MessagesState
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FriendsUiTest {
    private val seoul = ZoneId.of("Asia/Seoul")

    @Test fun `the last message of a friend is their newest and a friend without one has none`() {
        val state = CompanionState(
            friends = FriendsState(items = listOf(Friend("f1", "민수", "2026-11-14", false), Friend("f2", "하준", "2026-11-14", true)), loaded = true),
            messages = MessagesState(
                items = listOf(
                    ReceivedMessage("m2", "f1", "민수", "새 말", "2026-11-15T08:40:00Z"),
                    ReceivedMessage("m1", "f1", "민수", "옛 말", "2026-11-14T08:40:00Z"),
                ),
                loaded = true,
            ),
        )
        val ui = friendsUi(state, seoul)
        assertEquals("새 말", ui.friends[0].lastMessage)
        assertNull(ui.friends[1].lastMessage)
        assertEquals(true, ui.friends[1].arrivalOn)
    }

    @Test fun `message times are words in the car's time zone and an unreadable stamp shows nothing`() {
        assertEquals("11월 15일 오후 5:40", messageTime("2026-11-15T08:40:00Z", seoul))
        assertEquals("", messageTime("어제", seoul))
    }
}
