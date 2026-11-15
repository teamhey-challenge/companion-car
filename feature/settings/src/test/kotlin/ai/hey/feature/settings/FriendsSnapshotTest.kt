package ai.hey.feature.settings

import ai.hey.designsystem.theme.HeyTheme
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import org.junit.Rule
import org.junit.Test

private fun friendsDevice(width: Int, height: Int) = DeviceConfig.PIXEL_C.copy(
    screenWidth = width,
    screenHeight = height,
    xdpi = 160,
    ydpi = 160,
    density = Density.MEDIUM,
    orientation = ScreenOrientation.LANDSCAPE,
)

private const val LONG_120 = "오늘 발표 잘할 거야. 끝나고 맛있는 거 먹자. 응원할게! 그리고 저녁에는 같이 모여서 이야기하자. 집에 돌아가는 길에 비가 오니까 우산 꼭 챙기고 조심해."

private val friends = listOf(
    FriendRowUi("f1", "민수", arrivalOn = false, lastMessage = "오늘 발표 잘할 거야. 응원할게!"),
    FriendRowUi("f2", "지은", arrivalOn = true, lastMessage = "저녁에 보자"),
    FriendRowUi("f3", "하준", arrivalOn = true),
)

private val messages = listOf(
    MessageUi("m1", "f1", "민수", "오늘 발표 잘할 거야. 끝나고 맛있는 거 먹자. 응원할게!", "11월 15일 오전 8:40"),
    MessageUi("m2", "f2", "지은", "저녁에 보자", "11월 15일 오전 7:10"),
    MessageUi("m3", "f2", "지은", LONG_120, "11월 14일 오후 9:02"),
)

private val ready = FriendsUi(friends = friends, friendsLoaded = true, messages = messages, messagesLoaded = true)

private class FriendShot(val ui: FriendsUi, val view: FriendsView)

/** Every state of the parked friends area. Driving has no friends screen (see the home snapshot "c5-friend-reading"). */
private val friendShots: List<Pair<String, FriendShot>> = listOf(
    "friends-list" to FriendShot(ready, FriendsView.List),
    "friends-empty" to FriendShot(FriendsUi(friendsLoaded = true, messagesLoaded = true), FriendsView.List),
    "friends-failed" to FriendShot(FriendsUi(friendsFailed = true), FriendsView.List),
    "friends-code" to FriendShot(ready.copy(code = FriendCodeUi.Shown("K7M2QX", "https://hey.lastmile.ltd/f/K7M2QX")), FriendsView.Code),
    "friends-code-creating" to FriendShot(ready.copy(code = FriendCodeUi.Creating), FriendsView.Code),
    "friends-code-failed" to FriendShot(ready.copy(code = FriendCodeUi.Failed), FriendsView.Code),
    "friends-inbox" to FriendShot(ready, FriendsView.Inbox()),
    "friends-inbox-friend" to FriendShot(ready, FriendsView.Inbox("f2")),
    "friends-inbox-friend-empty" to FriendShot(ready, FriendsView.Inbox("f3")),
    "friends-inbox-empty" to FriendShot(FriendsUi(friends = friends, friendsLoaded = true, messagesLoaded = true), FriendsView.Inbox()),
    "friends-inbox-failed" to FriendShot(FriendsUi(friends = friends, friendsLoaded = true, messagesFailed = true), FriendsView.Inbox()),
    "friends-message" to FriendShot(ready, FriendsView.Message("m1")),
    "friends-message-long" to FriendShot(ready, FriendsView.Message("m3")),
    "friends-message-report" to FriendShot(ready, FriendsView.Message("m1", confirmReport = true)),
    "friends-message-reporting" to FriendShot(ready.copy(reporting = true), FriendsView.Message("m1", confirmReport = true)),
    "friends-message-report-failed" to FriendShot(ready.copy(reportFailed = true), FriendsView.Message("m1")),
    "friends-message-gone" to FriendShot(ready, FriendsView.Message("gone")),
)

private fun Paparazzi.recordFriends() {
    friendShots.forEach { (name, shot) ->
        listOf(true, false).forEach { dark ->
            snapshot("$name-${if (dark) "dark" else "light"}") {
                HeyTheme(dark) {
                    FriendsScreen(
                        ui = shot.ui,
                        view = shot.view,
                        actions = FriendsActions({}, {}, {}, {}, {}, {}, {}),
                    )
                }
            }
        }
    }
}

class Friends1600SnapshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = friendsDevice(1600, 900), renderingMode = RenderingMode.NORMAL)

    @Test fun screens() = paparazzi.recordFriends()
}

class Friends840SnapshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = friendsDevice(840, 900), renderingMode = RenderingMode.NORMAL)

    @Test fun screens() = paparazzi.recordFriends()
}

class Friends1280SnapshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = friendsDevice(1280, 720), renderingMode = RenderingMode.NORMAL)

    @Test fun screens() = paparazzi.recordFriends()
}
