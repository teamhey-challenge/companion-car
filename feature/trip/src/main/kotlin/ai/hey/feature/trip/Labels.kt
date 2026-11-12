package ai.hey.feature.trip

import ai.hey.designsystem.component.HeyConnection
import ai.hey.designsystem.component.ProposalState
import ai.hey.designsystem.component.QuickReplyUi
import ai.hey.designsystem.icon.HeyIcons
import ai.hey.model.ConditionId
import ai.hey.model.DropoffReasonId
import ai.hey.model.ErrorCode
import ai.hey.model.Lang
import ai.hey.model.DriverProposalStatus
import ai.hey.model.QuickReplyId
import ai.hey.model.QuickReplySet
import ai.hey.model.quickRepliesOf
import ai.hey.model.RequestItem
import ai.hey.model.RequestKind
import ai.hey.model.RequestState
import ai.hey.session.ConnectionStatus
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import ai.hey.designsystem.component.RequestKind as UiRequestKind
import ai.hey.designsystem.component.RequestState as UiRequestState

fun ConnectionStatus.toStrip(): HeyConnection = when (this) {
    ConnectionStatus.Online -> HeyConnection.Online
    ConnectionStatus.Connecting, ConnectionStatus.Reconnecting -> HeyConnection.Reconnecting
    ConnectionStatus.Registering, ConnectionStatus.RegistrationFailed, ConnectionStatus.Rejected, ConnectionStatus.ProtocolMismatch -> HeyConnection.Offline
}

fun Lang.labelRes(): Int = when (this) {
    Lang.En -> R.string.trip_lang_en
    Lang.Ja -> R.string.trip_lang_ja
    Lang.ZhHans -> R.string.trip_lang_zh
    Lang.ZhHant -> R.string.trip_lang_zh_hant
    Lang.Vi -> R.string.trip_lang_vi
    Lang.Th -> R.string.trip_lang_th
    Lang.Ko -> R.string.trip_lang_ko
}

internal fun ConditionId.icon(): ImageVector = when (this) {
    ConditionId.MeetEntrance -> HeyIcons.Door
    ConditionId.Temperature -> HeyIcons.ThermometerCool
    ConditionId.Quiet -> HeyIcons.VolumeOff
    ConditionId.Music -> HeyIcons.Music
}

internal fun DriverProposalStatus.toUi(): ProposalState = when (this) {
    DriverProposalStatus.Pending -> ProposalState.Pending
    DriverProposalStatus.Ok -> ProposalState.Ok
    DriverProposalStatus.Elsewhere -> ProposalState.Elsewhere
    DriverProposalStatus.Unsure -> ProposalState.Unsure
    DriverProposalStatus.Superseded -> ProposalState.Superseded
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
internal fun quickReplies(set: QuickReplySet): List<QuickReplyUi> = quickRepliesOf(set).map {
    QuickReplyUi(
        it.name,
        stringResource(
            when (it) {
                QuickReplyId.Yes -> R.string.trip_reply_yes
                QuickReplyId.Cannot -> R.string.trip_reply_cannot
                QuickReplyId.Moment -> R.string.trip_reply_moment
                QuickReplyId.StopHere -> R.string.trip_reply_stop_here
                QuickReplyId.FiveMin -> R.string.trip_reply_five_min
                QuickReplyId.CardOk -> R.string.trip_reply_card_ok
            },
        ),
    )
}

@Composable
internal fun errorText(code: ErrorCode): String = stringResource(
    when (code) {
        ErrorCode.PROTOCOL_MISMATCH -> R.string.trip_error_protocol
        ErrorCode.STALE_VERSION -> R.string.trip_error_stale
        ErrorCode.TRIP_ENDED -> R.string.trip_error_ended
        else -> R.string.trip_error_generic
    },
)
