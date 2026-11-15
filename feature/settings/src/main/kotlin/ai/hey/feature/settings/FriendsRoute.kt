package ai.hey.feature.settings

import ai.hey.model.ReceivedMessage
import ai.hey.session.CompanionCommands
import ai.hey.session.CompanionState
import ai.hey.session.InviteUi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일 a h:mm", Locale.KOREA)

/** "11월 15일 오전 8:40" in the car's time zone; an unreadable stamp shows as nothing rather than as a guess. */
internal fun messageTime(iso: String, zone: ZoneId = ZoneId.systemDefault()): String =
    runCatching { TimeFormat.format(Instant.parse(iso).atZone(zone)) }.getOrDefault("")

/** [state] to what the friends area draws. [messages] arrive newest first, so the first one of a friend is their last. */
fun friendsUi(state: CompanionState, zone: ZoneId = ZoneId.systemDefault()): FriendsUi {
    val received: List<ReceivedMessage> = state.messages.items
    return FriendsUi(
        friends = state.friends.items.map { f ->
            FriendRowUi(f.id, f.name, f.shareArrival, lastMessage = received.firstOrNull { it.friendId == f.id }?.text)
        },
        friendsLoaded = state.friends.loaded,
        friendsFailed = state.friends.failed,
        code = when (val i = state.friends.invite) {
            InviteUi.None -> FriendCodeUi.None
            InviteUi.Creating -> FriendCodeUi.Creating
            InviteUi.Failed -> FriendCodeUi.Failed
            is InviteUi.Shown -> FriendCodeUi.Shown(i.code, i.url)
        },
        messages = received.map { MessageUi(it.id, it.friendId, it.friendName, it.text, messageTime(it.at, zone)) },
        messagesLoaded = state.messages.loaded,
        messagesFailed = state.messages.failed,
        reporting = state.messages.reporting,
        reportFailed = state.messages.reportFailed,
        accept = state.friends.accept,
        incoming = state.friends.incoming,
    )
}

/**
 * The friends area wired to the companion repository (parked only: the NavHost leaves it when the car moves). Opening it
 * reads the friends and the received messages; the invite code is made when its view opens and dropped when it closes.
 * [onReadAloud] speaks a message again (the app decides the audio focus).
 */
@Composable
fun FriendsRoute(
    state: CompanionState,
    commands: CompanionCommands,
    onBack: () -> Unit,
    onReadAloud: (text: String, sender: String) -> Unit,
    modifier: Modifier = Modifier,
    initial: FriendsView = FriendsView.List,
) {
    var view by remember { mutableStateOf(initial) }
    LaunchedEffect(Unit) { commands.loadFriends(); commands.loadMessages(); commands.loadRequests() }
    LaunchedEffect(view is FriendsView.Code) { if (view is FriendsView.Code) commands.createInvite() }
    DisposableEffect(Unit) { onDispose { commands.closeInvite(); commands.clearAccept() } }
    // A reported message leaves the list: the open card then closes by itself.
    val open = (view as? FriendsView.Message)?.id
    LaunchedEffect(open, state.messages.items) {
        if (open != null && state.messages.items.none { it.id == open } && state.messages.loaded) view = FriendsView.Inbox()
    }
    FriendsScreen(
        ui = friendsUi(state),
        view = view,
        actions = FriendsActions(
            onNavigate = { next -> commands.clearReportFailure(); view = next },
            onBack = {
                when (val v = view) {
                    FriendsView.List -> onBack()
                    FriendsView.Code, is FriendsView.Inbox, FriendsView.Invite -> view = FriendsView.List
                    is FriendsView.Message -> view = if (v.confirmReport) FriendsView.Message(v.id) else FriendsView.Inbox()
                }
            },
            onCreateCode = commands::createInvite,
            onSendRequest = commands::sendFriendRequest,
            onClearAccept = commands::clearAccept,
            onAcceptRequest = commands::acceptRequest,
            onDeclineRequest = commands::declineRequest,
            onRetryFriends = commands::loadFriends,
            onRetryMessages = commands::loadMessages,
            onReadAloud = { onReadAloud(it.text, it.sender) },
            onReport = { id ->
                commands.reportMessage(id)
                view = FriendsView.Message(id)
            },
        ),
        modifier = modifier,
    )
}
