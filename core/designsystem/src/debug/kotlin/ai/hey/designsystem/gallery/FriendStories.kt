package ai.hey.designsystem.gallery

import ai.hey.designsystem.component.FriendRow
import ai.hey.designsystem.component.MessageCard
import ai.hey.designsystem.component.MessageRow
import ai.hey.designsystem.layout.VStack
import ai.hey.designsystem.theme.HeyGap
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private const val NO_SPACE_FRIEND = "오늘퇴근길에는비가와서조용한음악을틀어드리고내일아침일정도함께알려드릴게요오늘퇴근길에는비가와서조용한음악을틀어드리고"
private const val LONG_120 = "오늘 발표 잘할 거야. 끝나고 맛있는 거 먹자. 응원할게! 그리고 저녁에는 같이 모여서 이야기하자. 집에 돌아가는 길에 비가 오니까 우산 꼭 챙기고, 조심해서 운전해야 해."

/** The friend parts: list row, inbox row and message card, in every shape the data can take. */
internal fun friendParts(): List<Story> = buildList {
    add(Story("friend-rows", StoryFrame.Part) {
        VStack(HeyGap.G16, modifier = Modifier.width(1100.dp).padding(32.dp)) {
            FriendRow("민수", {}, Modifier.fillMaxWidth(), lastMessage = "오늘 발표 잘할 거야. 응원할게!", badge = "도착 알림 켜짐")
            FriendRow("지은", {}, Modifier.fillMaxWidth(), lastMessage = "저녁에 보자")
            FriendRow("하준", {}, Modifier.fillMaxWidth())
            FriendRow(NO_SPACE_FRIEND, {}, Modifier.fillMaxWidth(), lastMessage = NO_SPACE_FRIEND, badge = "도착 알림 켜짐")
        }
    })
    add(Story("message-rows", StoryFrame.Part) {
        VStack(HeyGap.G16, modifier = Modifier.width(1100.dp).padding(32.dp)) {
            MessageRow("민수", "오늘 발표 잘할 거야. 응원할게!", "11월 15일 오전 8:40", {}, Modifier.fillMaxWidth())
            MessageRow("지은", "저녁에 보자", "11월 15일 오전 7:10", {}, Modifier.fillMaxWidth())
            MessageRow(NO_SPACE_FRIEND, LONG_120, "11월 15일 오전 7:10", {}, Modifier.fillMaxWidth())
        }
    })
    add(Story("message-card", StoryFrame.Part) {
        VStack(HeyGap.G16, modifier = Modifier.width(1100.dp).padding(32.dp)) {
            MessageCard("민수", "오늘 발표 잘할 거야. 끝나고 맛있는 거 먹자. 응원할게!", "11월 15일 오전 8:40", Modifier.fillMaxWidth())
            MessageCard("지은", LONG_120, "11월 15일 오전 7:10", Modifier.fillMaxWidth())
            MessageCard(NO_SPACE_FRIEND, NO_SPACE_FRIEND, "11월 15일 오전 7:10", Modifier.fillMaxWidth())
        }
    })
}
