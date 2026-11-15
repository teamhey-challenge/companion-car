package ai.hey.feature.settings

import ai.hey.designsystem.component.CarFrame
import ai.hey.designsystem.component.ConfirmDialog
import ai.hey.designsystem.component.EmptyPanel
import ai.hey.designsystem.component.FriendRow
import ai.hey.designsystem.component.HeaderBar
import ai.hey.designsystem.component.HeyButton
import ai.hey.designsystem.component.HeyButtonSize
import ai.hey.designsystem.component.HeyButtonVariant
import ai.hey.designsystem.component.HeyLayered
import ai.hey.designsystem.component.HeyNotice
import ai.hey.designsystem.component.HeyScrollColumn
import ai.hey.designsystem.component.HeyStatusTone
import ai.hey.designsystem.component.HeyText
import ai.hey.designsystem.component.MessageCard
import ai.hey.designsystem.component.MessageRow
import ai.hey.designsystem.component.PageHeading
import ai.hey.designsystem.component.QrPanel
import ai.hey.designsystem.icon.HeyIcons
import ai.hey.designsystem.layout.HStack
import ai.hey.designsystem.layout.HeyAlign
import ai.hey.designsystem.layout.fillSize
import ai.hey.designsystem.layout.fillWidth
import ai.hey.designsystem.theme.HeyGap
import ai.hey.designsystem.theme.HeyTone
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/** One friend on the read-only car list (D-47): the last thing they sent, and whether I tell them when I arrive. */
data class FriendRowUi(val id: String, val name: String, val arrivalOn: Boolean, val lastMessage: String? = null)

/** One received message; [time] is already words ("오늘 오전 8:40"). */
data class MessageUi(val id: String, val friendId: String, val sender: String, val text: String, val time: String)

/** "내 친구 코드" panel: the QR carries [Shown.url], the text is the 6-character [Shown.code]. */
sealed interface FriendCodeUi {
    data object None : FriendCodeUi
    data object Creating : FriendCodeUi
    data class Shown(val code: String, val url: String) : FriendCodeUi
    data object Failed : FriendCodeUi
}

/** Where the parked-only friends area is. Driving has no such screen at all (the gate leaves it, PRD 3.5). */
sealed interface FriendsView {
    data object List : FriendsView
    data object Code : FriendsView

    /** [friendId] null = every received message, else only that friend's. */
    data class Inbox(val friendId: String? = null) : FriendsView

    /** [confirmReport] = the report question is open on top. */
    data class Message(val id: String, val confirmReport: Boolean = false) : FriendsView
}

data class FriendsUi(
    val friends: List<FriendRowUi> = emptyList(),
    val friendsLoaded: Boolean = false,
    val friendsFailed: Boolean = false,
    val code: FriendCodeUi = FriendCodeUi.None,
    val messages: List<MessageUi> = emptyList(),
    val messagesLoaded: Boolean = false,
    val messagesFailed: Boolean = false,
    val reporting: Boolean = false,
    val reportFailed: Boolean = false,
)

class FriendsActions(
    val onNavigate: (FriendsView) -> Unit,
    val onBack: () -> Unit,
    val onCreateCode: () -> Unit,
    val onRetryFriends: () -> Unit,
    val onRetryMessages: () -> Unit,
    val onReadAloud: (MessageUi) -> Unit,
    val onReport: (String) -> Unit,
)

/**
 * 친구 (parked only): friend list (read-only), 내 친구 코드, 받은 말 and one message with 신고하기 (D-47, D-53). Adding,
 * removing and 도착 알리기 are phone-only. While driving this screen does not exist: the friend's line is only read aloud.
 */
@Composable
fun FriendsScreen(ui: FriendsUi, view: FriendsView, actions: FriendsActions, modifier: Modifier = Modifier) {
    val open = (view as? FriendsView.Message)?.let { m -> ui.messages.firstOrNull { it.id == m.id } }
    HeyLayered(
        modifier = modifier.fillSize(),
        overlay = {
            if (view is FriendsView.Message && view.confirmReport && open != null) {
                ConfirmDialog(
                    title = stringResource(R.string.friends_report_title),
                    message = stringResource(R.string.friends_report_message),
                    confirmText = stringResource(R.string.friends_report_confirm),
                    dismissText = stringResource(R.string.friends_report_cancel),
                    onConfirm = { actions.onReport(open.id) },
                    onDismiss = { actions.onNavigate(FriendsView.Message(open.id)) },
                    busy = ui.reporting,
                    danger = true,
                    modifier = Modifier.fillSize(),
                )
            }
        },
    ) {
        CarFrame(
            statusStrip = {
                HeaderBar(
                    title = stringResource(R.string.friends_app_name),
                    onBack = actions.onBack,
                    backLabel = stringResource(R.string.friends_back),
                    stateLabel = stringResource(R.string.friends_parked),
                )
            },
            driving = false,
        ) {
            HeyScrollColumn(HeyGap.G24, modifier = Modifier.fillSize()) {
                when (view) {
                    FriendsView.List -> FriendList(ui, actions)
                    FriendsView.Code -> FriendCode(ui.code, actions.onCreateCode)
                    is FriendsView.Inbox -> Inbox(ui, view.friendId, actions)
                    is FriendsView.Message -> MessageDetail(ui, open, actions)
                }
            }
        }
    }
}

@Composable
private fun ai.hey.designsystem.layout.HeyStackScope.FriendList(ui: FriendsUi, actions: FriendsActions) {
    PageHeading(stringResource(R.string.friends_title), subtitle = stringResource(R.string.friends_phone_only))
    HStack(HeyGap.G16, align = HeyAlign.Center) {
        HeyButton(stringResource(R.string.friends_open_code), { actions.onNavigate(FriendsView.Code) }, variant = HeyButtonVariant.Soft, icon = HeyIcons.Qr)
        HeyButton(stringResource(R.string.friends_open_inbox), { actions.onNavigate(FriendsView.Inbox()) }, variant = HeyButtonVariant.Soft, icon = HeyIcons.Message)
    }
    when {
        ui.friendsFailed -> LoadFailed(actions.onRetryFriends)
        ui.friendsLoaded && ui.friends.isEmpty() -> EmptyPanel(
            HeyIcons.Users,
            stringResource(R.string.friends_none_title),
            Modifier.fillWidth(),
            message = stringResource(R.string.friends_none_message),
        )
        else -> ui.friends.forEach { f ->
            FriendRow(
                name = f.name,
                onClick = { actions.onNavigate(FriendsView.Inbox(f.id)) },
                modifier = Modifier.fillWidth(),
                lastMessage = f.lastMessage,
                badge = if (f.arrivalOn) stringResource(R.string.friends_arrival_on) else null,
            )
        }
    }
}

@Composable
private fun ai.hey.designsystem.layout.HeyStackScope.FriendCode(code: FriendCodeUi, onRetry: () -> Unit) {
    PageHeading(stringResource(R.string.friends_code_title))
    when (code) {
        FriendCodeUi.None, FriendCodeUi.Creating -> HeyText(stringResource(R.string.friends_code_creating), tone = HeyTone.Muted)
        FriendCodeUi.Failed -> {
            HeyNotice(stringResource(R.string.friends_code_failed), HeyStatusTone.Danger, persistent = true)
            HeyButton(stringResource(R.string.friends_retry), onRetry, variant = HeyButtonVariant.Secondary, icon = HeyIcons.Refresh)
        }
        is FriendCodeUi.Shown -> QrPanel(
            content = code.url,
            code = code.code,
            status = stringResource(R.string.friends_code_status),
            modifier = Modifier.fillWidth(),
        )
    }
}

@Composable
private fun ai.hey.designsystem.layout.HeyStackScope.Inbox(ui: FriendsUi, friendId: String?, actions: FriendsActions) {
    val shown = if (friendId == null) ui.messages else ui.messages.filter { it.friendId == friendId }
    val who = ui.friends.firstOrNull { it.id == friendId }?.name
    PageHeading(if (who != null) stringResource(R.string.friends_inbox_of_title, who) else stringResource(R.string.friends_inbox_title))
    when {
        ui.messagesFailed -> LoadFailed(actions.onRetryMessages)
        ui.messagesLoaded && shown.isEmpty() -> EmptyPanel(
            HeyIcons.Message,
            stringResource(R.string.friends_no_message_title),
            Modifier.fillWidth(),
            message = stringResource(if (who != null) R.string.friends_none_message_friend else R.string.friends_no_message_message),
        )
        else -> shown.forEach { m ->
            MessageRow(m.sender, m.text, m.time, { actions.onNavigate(FriendsView.Message(m.id)) }, Modifier.fillWidth())
        }
    }
    if (who != null) HeyText(stringResource(R.string.friends_say_hint, who), tone = HeyTone.Muted)
}

@Composable
private fun ai.hey.designsystem.layout.HeyStackScope.MessageDetail(ui: FriendsUi, message: MessageUi?, actions: FriendsActions) {
    if (message == null) {
        // Reported (hidden) or gone: nothing to show, the way back is the list.
        EmptyPanel(HeyIcons.Check, stringResource(R.string.friends_no_message_title), Modifier.fillWidth())
        HeyButton(stringResource(R.string.friends_to_inbox), { actions.onNavigate(FriendsView.Inbox()) }, variant = HeyButtonVariant.Primary, size = HeyButtonSize.Large)
        return
    }
    PageHeading(stringResource(R.string.friends_message_title, message.sender))
    if (ui.reportFailed) HeyNotice(stringResource(R.string.friends_report_failed), HeyStatusTone.Danger, persistent = true)
    MessageCard(message.sender, message.text, message.time, Modifier.fillWidth())
    HStack(HeyGap.G16, align = HeyAlign.Center) {
        HeyButton(stringResource(R.string.friends_read_aloud), { actions.onReadAloud(message) }, size = HeyButtonSize.Large, icon = HeyIcons.Speaker)
        HeyButton(stringResource(R.string.friends_report), { actions.onNavigate(FriendsView.Message(message.id, confirmReport = true)) }, variant = HeyButtonVariant.Soft, icon = HeyIcons.Flag)
    }
}

@Composable
private fun ai.hey.designsystem.layout.HeyStackScope.LoadFailed(onRetry: () -> Unit) {
    EmptyPanel(HeyIcons.Message, stringResource(R.string.friends_failed_title), Modifier.fillWidth(), message = stringResource(R.string.friends_failed_message))
    HeyButton(stringResource(R.string.friends_retry), onRetry, variant = HeyButtonVariant.Secondary, icon = HeyIcons.Refresh)
}
