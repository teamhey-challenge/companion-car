package ai.hey.feature.trip

import ai.hey.designsystem.component.HeyConnection
import ai.hey.designsystem.component.ProposalState
import ai.hey.designsystem.component.RequestKind as UiRequestKind
import ai.hey.designsystem.component.RequestState as UiRequestState
import ai.hey.designsystem.component.QuickReplyUi
import ai.hey.designsystem.icon.HeyIcons
import ai.hey.model.ConditionId
import ai.hey.model.DropoffReasonId
import ai.hey.model.ErrorCode
import ai.hey.model.ProposalStatus
import ai.hey.model.QuickReplyId
import ai.hey.model.RequestItem
import ai.hey.model.RequestKind
import ai.hey.model.RequestState
import ai.hey.session.ConnectionStatus
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource

fun ConnectionStatus.toStrip(): HeyConnection = when (this) {
    ConnectionStatus.Online -> HeyConnection.Online
    ConnectionStatus.Connecting, ConnectionStatus.Reconnecting -> HeyConnection.Reconnecting
    ConnectionStatus.Registering, ConnectionStatus.RegistrationFailed -> HeyConnection.Offline
}

internal fun ConditionId.icon(): ImageVector = when (this) {
    ConditionId.Luggage -> HeyIcons.Luggage
    ConditionId.LessWalking -> HeyIcons.Walk
    ConditionId.MeetEntrance -> HeyIcons.Door
    ConditionId.Temperature -> HeyIcons.ThermometerCool
}

internal fun ProposalStatus.toUi(): ProposalState = when (this) {
    ProposalStatus.Pending -> ProposalState.Pending
    ProposalStatus.Ok, ProposalStatus.Accepted -> ProposalState.Ok
    ProposalStatus.Elsewhere, ProposalStatus.Declined -> ProposalState.Elsewhere
    ProposalStatus.Unsure -> ProposalState.Unsure
    ProposalStatus.Superseded -> ProposalState.Superseded
}

internal fun RequestItem.uiKind(): UiRequestKind = when {
    kind == RequestKind.Free -> UiRequestKind.Free
    replyExpected -> UiRequestKind.Question
    else -> UiRequestKind.Notice
}

internal fun RequestItem.uiState(): UiRequestState = when (state) {
    RequestState.Received, RequestState.Failed -> UiRequestState.Received
    RequestState.Spoken -> UiRequestState.Spoken
    RequestState.Answered -> UiRequestState.Answered
}

@Composable
internal fun reasonLabels(): List<String> = DropoffReasonId.entries.map {
    stringResource(
        when (it) {
            DropoffReasonId.FrontNoStop -> R.string.trip_reason_front_no_stop
            DropoffReasonId.SideCloser -> R.string.trip_reason_side_closer
            DropoffReasonId.LuggageEasier -> R.string.trip_reason_luggage_easier
            DropoffReasonId.ParkingEntrance -> R.string.trip_reason_parking_entrance
            DropoffReasonId.AcrossSafer -> R.string.trip_reason_across_safer
            DropoffReasonId.Construction -> R.string.trip_reason_construction
        },
    )
}

@Composable
internal fun quickReplies(): List<QuickReplyUi> = QuickReplyId.entries.map {
    QuickReplyUi(
        it.name,
        stringResource(
            when (it) {
                QuickReplyId.Yes -> R.string.trip_reply_yes
                QuickReplyId.Moment -> R.string.trip_reply_moment
                QuickReplyId.FiveMin -> R.string.trip_reply_five_min
                QuickReplyId.StopHere -> R.string.trip_reply_stop_here
                QuickReplyId.CardOk -> R.string.trip_reply_card_ok
                QuickReplyId.Cannot -> R.string.trip_reply_cannot
            },
        ),
    )
}

@Composable
internal fun errorText(code: ErrorCode): String = stringResource(
    when (code) {
        ErrorCode.STALE_VERSION -> R.string.trip_error_stale
        ErrorCode.TRIP_ENDED -> R.string.trip_error_ended
        else -> R.string.trip_error_generic
    },
)
