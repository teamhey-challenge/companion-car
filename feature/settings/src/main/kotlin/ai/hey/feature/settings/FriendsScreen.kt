package ai.hey.feature.settings

import ai.hey.designsystem.component.CarFrame
import ai.hey.designsystem.component.CompactCarPage
import ai.hey.designsystem.component.FriendBigAvatar
import ai.hey.designsystem.component.FriendCodePanel
import ai.hey.designsystem.component.FriendInviteCopy
import ai.hey.designsystem.component.FriendNote
import ai.hey.designsystem.component.FriendShortcut
import ai.hey.designsystem.component.FriendsTitle
import ai.hey.designsystem.component.FriendInviteButton
import ai.hey.designsystem.component.FriendGap
import ai.hey.designsystem.component.FriendSpacer
import ai.hey.designsystem.component.FriendSlotFill
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
import ai.hey.designsystem.component.HeyTextField
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
import ai.hey.model.FriendRequest
import ai.hey.session.AcceptUi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/** One friend on the read-only car list: the last thing they sent, and whether I tell them when I arrive. */
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

    /** "친구 초대 수락": type the 6-character code a friend shows on their own "내 친구 코드". */
    data object Invite : FriendsView
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
    val accept: AcceptUi = AcceptUi.None,
    val incoming: List<FriendRequest> = emptyList(),
)

class FriendsActions(
    val onNavigate: (FriendsView) -> Unit,
    val onBack: () -> Unit,
    val onCreateCode: () -> Unit,
    val onRetryFriends: () -> Unit,
    val onRetryMessages: () -> Unit,
    val onReadAloud: (MessageUi) -> Unit,
    val onReport: (String) -> Unit,
    val onSendRequest: (String) -> Unit = {},
    val onClearAccept: () -> Unit = {},
    val onAcceptRequest: (String) -> Unit = {},
    val onDeclineRequest: (String) -> Unit = {},
)

/**
 * 친구 (parked only): friend list (read-only), 내 친구 코드, 받은 말 and one message with 신고하기. Adding,
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
        val request = ui.incoming.firstOrNull()
        if (view == FriendsView.Invite && request != null && ui.accept !is AcceptUi.Done) {
            // Studio `car-friends-invite`: the compact page with the asker's card.
            CompactCarPage(
                title = stringResource(R.string.friends_app_name),
                stateLabel = stringResource(R.string.friends_parked),
                backLabel = stringResource(R.string.friends_back),
                onBack = actions.onBack,
                heading = stringResource(R.string.friends_invite_heading),
            ) {
                FriendBigAvatar()
                FriendSpacer(FriendGap.Avatar)
                FriendInviteCopy(
                    stringResource(R.string.friends_request_title, request.fromName),
                    stringResource(R.string.friends_request_message),
                )
                if (ui.accept is AcceptUi.Failed) {
                    FriendSpacer(FriendGap.Notice)
                    HeyNotice(stringResource(R.string.friends_invite_offline), HeyStatusTone.Danger, persistent = true)
                }
                FriendSpacer(FriendGap.Block)
                FriendInviteButton(stringResource(R.string.friends_invite_accept), { actions.onAcceptRequest(request.id) }, primary = true)
                FriendSpacer(FriendGap.Block)
                FriendInviteButton(stringResource(R.string.friends_invite_later), { actions.onDeclineRequest(request.id) }, primary = false)
            }
        } else CarFrame(
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
                    FriendsView.Invite -> InviteAccept(ui, actions)
                }
            }
        }
    }
}

@Composable
private fun ai.hey.designsystem.layout.HeyStackScope.FriendList(ui: FriendsUi, actions: FriendsActions) {
    FriendsTitle(stringResource(R.string.friends_title))
    HStack(HeyGap.G24, modifier = Modifier.fillWidth()) {
        FriendShortcut(stringResource(R.string.friends_open_code), HeyIcons.Qr, { actions.onNavigate(FriendsView.Code) }, Modifier.grow())
        FriendShortcut(stringResource(R.string.friends_open_inbox), HeyIcons.Message, { actions.onNavigate(FriendsView.Inbox()) }, Modifier.grow())
        FriendShortcut(
            stringResource(R.string.friends_open_invite), HeyIcons.User, { actions.onNavigate(FriendsView.Invite) }, Modifier.grow(),
            count = ui.incoming.size,
        )
    }
    when {
        ui.friendsFailed -> LoadFailed(actions.onRetryFriends)
        ui.friendsLoaded && ui.friends.isEmpty() -> EmptyPanel(
            HeyIcons.Users,
            stringResource(R.string.friends_none_title),
            Modifier.fillWidth(),
            message = stringResource(R.string.friends_none_message),
        )
        else -> ui.friends.chunked(2).forEach { pair ->
            HStack(HeyGap.G24, align = HeyAlign.Start, modifier = Modifier.fillWidth()) {
                pair.forEach { f ->
                    FriendRow(
                        name = f.name,
                        onClick = { actions.onNavigate(FriendsView.Inbox(f.id)) },
                        modifier = Modifier.grow(),
                        lastMessage = f.lastMessage,
                        badge = if (f.arrivalOn) stringResource(R.string.friends_arrival_on) else null,
                    )
                }
                if (pair.size == 1) FriendSlotFill()
            }
        }
    }
    FriendNote(stringResource(R.string.friends_phone_only))
}

@Composable
private fun ai.hey.designsystem.layout.HeyStackScope.FriendCode(code: FriendCodeUi, onRetry: () -> Unit) {
    FriendsTitle(stringResource(R.string.friends_code_title))
    when (code) {
        FriendCodeUi.None, FriendCodeUi.Creating -> HeyText(stringResource(R.string.friends_code_creating), tone = HeyTone.Muted)
        FriendCodeUi.Failed -> {
            HeyNotice(stringResource(R.string.friends_code_failed), HeyStatusTone.Danger, persistent = true)
            HeyButton(stringResource(R.string.friends_retry), onRetry, variant = HeyButtonVariant.Secondary, icon = HeyIcons.Refresh)
        }
        is FriendCodeUi.Shown -> FriendCodePanel(
            content = code.url,
            code = code.code,
            lead = stringResource(R.string.friends_code_lead),
            note = stringResource(R.string.friends_code_status),
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

/** 6 characters from the same alphabet the server uses; spaces and dashes are ignored. */
internal fun cleanInviteCode(raw: String): String = raw.filter { it.isLetterOrDigit() }.uppercase().take(INVITE_CODE_LENGTH)

internal const val INVITE_CODE_LENGTH = 6

@Composable
private fun ai.hey.designsystem.layout.HeyStackScope.InviteAccept(ui: FriendsUi, actions: FriendsActions) {
    val accept = ui.accept
    var code by remember { mutableStateOf("") }
    val request = ui.incoming.firstOrNull()
    PageHeading(stringResource(R.string.friends_invite_title))
    if (accept is AcceptUi.Done) {
        EmptyPanel(
            HeyIcons.Check,
            stringResource(R.string.friends_invite_done_title, accept.name),
            Modifier.fillWidth(),
            message = stringResource(R.string.friends_invite_done_message),
        )
        HeyButton(stringResource(R.string.friends_invite_to_list), { actions.onClearAccept(); actions.onNavigate(FriendsView.List) }, variant = HeyButtonVariant.Primary, size = HeyButtonSize.Large)
        return
    }
    if (request != null) {
        // 받은 초대: the person asking, answered here (parked only like everything in this area).
        EmptyPanel(
            HeyIcons.User,
            stringResource(R.string.friends_request_title, request.fromName),
            Modifier.fillWidth(),
            message = stringResource(R.string.friends_request_message),
        )
        if (accept is AcceptUi.Failed) HeyNotice(stringResource(R.string.friends_invite_offline), HeyStatusTone.Danger, persistent = true)
        HStack(HeyGap.G16, align = HeyAlign.Center) {
            HeyButton(stringResource(R.string.friends_invite_accept), { actions.onAcceptRequest(request.id) }, variant = HeyButtonVariant.Primary, size = HeyButtonSize.Large)
            HeyButton(stringResource(R.string.friends_invite_later), { actions.onDeclineRequest(request.id) }, variant = HeyButtonVariant.Secondary)
        }
        return
    }
    if (accept is AcceptUi.Sent) {
        EmptyPanel(
            HeyIcons.Check,
            stringResource(R.string.friends_invite_sent_title),
            Modifier.fillWidth(),
            message = stringResource(R.string.friends_invite_sent_message, accept.name),
        )
        HeyButton(stringResource(R.string.friends_invite_to_list), { actions.onClearAccept(); actions.onNavigate(FriendsView.List) }, variant = HeyButtonVariant.Primary, size = HeyButtonSize.Large)
        return
    }
    HeyText(stringResource(R.string.friends_invite_prompt))
    HeyTextField(
        label = stringResource(R.string.friends_invite_label),
        value = code,
        onValueChange = { code = cleanInviteCode(it); if (accept is AcceptUi.Failed) actions.onClearAccept() },
        modifier = Modifier.fillWidth(),
        enabled = accept != AcceptUi.Working,
        placeholder = stringResource(R.string.friends_invite_placeholder),
    )
    if (accept is AcceptUi.Failed) {
        HeyNotice(
            stringResource(
                when (accept.reason) {
                    AcceptUi.Reason.Unknown -> R.string.friends_invite_unknown
                    AcceptUi.Reason.Expired -> R.string.friends_invite_expired
                    AcceptUi.Reason.Own -> R.string.friends_invite_own
                    AcceptUi.Reason.Already -> R.string.friends_invite_already
                    AcceptUi.Reason.Offline -> R.string.friends_invite_offline
                },
            ),
            HeyStatusTone.Danger,
            persistent = true,
        )
    }
    HStack(HeyGap.G16, align = HeyAlign.Center) {
        HeyButton(
            stringResource(R.string.friends_invite_send),
            { actions.onSendRequest(code) },
            variant = HeyButtonVariant.Primary,
            size = HeyButtonSize.Large,
            enabled = code.length == INVITE_CODE_LENGTH && accept != AcceptUi.Working,
            loading = accept == AcceptUi.Working,
        )
        HeyButton(stringResource(R.string.friends_invite_later), { actions.onClearAccept(); actions.onNavigate(FriendsView.List) }, variant = HeyButtonVariant.Secondary)
    }
}
