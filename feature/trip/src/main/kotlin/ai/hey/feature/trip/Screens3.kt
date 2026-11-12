package ai.hey.feature.trip

import ai.hey.designsystem.component.DrivingPanel
import ai.hey.designsystem.component.HeyBanner
import ai.hey.designsystem.component.HeyButton
import ai.hey.designsystem.component.HeyButtonSize
import ai.hey.designsystem.component.HeyButtonVariant
import ai.hey.designsystem.component.HeyDialog
import ai.hey.designsystem.component.HeyDialogAction
import ai.hey.designsystem.component.HeyLayered
import ai.hey.designsystem.component.HeyScrollColumn
import ai.hey.designsystem.component.HeyStatusTone
import ai.hey.designsystem.component.HeyText
import ai.hey.designsystem.component.PassengerPinCard
import ai.hey.designsystem.component.QuickReplyGrid
import ai.hey.designsystem.component.RequestRow
import ai.hey.designsystem.component.TripSummaryCard
import ai.hey.designsystem.icon.HeyIcons
import ai.hey.designsystem.layout.HStack
import ai.hey.designsystem.layout.HeyAlign
import ai.hey.designsystem.layout.HeyArrange
import ai.hey.designsystem.layout.VStack
import ai.hey.designsystem.layout.fillSize
import ai.hey.designsystem.layout.fillWidth
import ai.hey.designsystem.theme.HeyGap
import ai.hey.designsystem.theme.HeyTextVariant
import ai.hey.designsystem.theme.HeyTone
import ai.hey.model.HeyConfig
import ai.hey.model.LatLng
import ai.hey.model.QuickReplyId
import ai.hey.model.distanceTo
import ai.hey.model.pendingRequestCount
import ai.hey.model.replyTarget
import ai.hey.session.CarCommands
import ai.hey.session.CarPhase
import ai.hey.session.CarUiState
import ai.hey.session.CommandKey
import ai.hey.session.ParkedCommands
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import java.time.ZoneId

private val KoreaZone: ZoneId = ZoneId.of("Asia/Seoul")

// ================================================================ D5 driving

/** Nothing here can be pressed and nothing here scrolls: the frame hides the rail, the content has no inputs. */
@Composable
internal fun DrivingScreen(state: CarUiState, nowMillis: Long, modifier: Modifier = Modifier) {
    val view = state.view
    val destination = view?.card?.destination?.nameKo ?: stringResource(R.string.trip_driving_empty_title)
    val progress = view?.progress
    val eta = when {
        progress != null -> durationText(progress.etaSec)
        view?.route != null && view.route!!.durationSec > 0 -> durationText(view.route!!.durationSec)
        else -> stringResource(R.string.trip_driving_empty_message)
    }
    val atStart = progress != null && state.progressAt != null && (nowMillis - state.progressAt!!) / 1000 > HeyConfig.PROGRESS_STALE_AFTER_SEC
    TripFrame(state = state, nowMillis = nowMillis, modifier = modifier) {
        VStack(HeyGap.G24, arrange = HeyArrange.Center, modifier = Modifier.fillSize()) {
            DrivingPanel(
                destinationKo = destination,
                etaText = if (atStart) stringResource(R.string.trip_driving_eta_at_start, eta) else eta,
                pendingCount = view?.pendingRequestCount ?: 0,
                modifier = Modifier.fillWidth(),
                lastSpokenKo = state.lastSpokenKo,
            )
        }
    }
}

// ================================================================ D6 requests while parked

@Composable
internal fun RequestsScreen(
    state: CarUiState,
    phase: CarPhase.Requests,
    parked: ParkedCommands,
    commands: CarCommands,
    nowMillis: Long,
    modifier: Modifier = Modifier,
) {
    val view = phase.view
    val requests = view.requests.reversed()
    val target = view.replyTarget
    val pin = view.pendingPins.firstOrNull()
    val replies = quickReplies()
    val busyReply = state.replyInFlight?.replyId?.name
    TripFrame(
        state = state,
        nowMillis = nowMillis,
        modifier = modifier,
        rail = {
            HStack(HeyGap.G16, arrange = HeyArrange.End, modifier = Modifier.fillWidth()) {
                HeyButton(
                    stringResource(R.string.trip_requests_arrive),
                    parked::arrive,
                    variant = HeyButtonVariant.Secondary,
                    size = HeyButtonSize.Large,
                    icon = HeyIcons.Flag,
                    loading = state.isLoading(CommandKey.Arrive),
                    enabled = state.online,
                )
                HeyButton(
                    stringResource(R.string.trip_requests_close),
                    commands::dismissRequests,
                    variant = HeyButtonVariant.Secondary,
                    size = HeyButtonSize.Large,
                )
            }
        },
    ) {
        VStack(HeyGap.G16, modifier = Modifier.fillSize()) {
            VStack(HeyGap.G8) {
                HeyText(stringResource(R.string.trip_requests_title), variant = HeyTextVariant.Title, maxLines = 1)
                HeyText(stringResource(R.string.trip_requests_subtitle), tone = HeyTone.Muted, maxLines = 1)
            }
            if (!state.online) HeyBanner(HeyStatusTone.Warning, stringResource(R.string.trip_requests_offline), Modifier.fillWidth())
            HStack(HeyGap.G32, align = HeyAlign.Start, modifier = Modifier.fillWidth().grow()) {
                HeyScrollColumn(HeyGap.G8, modifier = Modifier.grow(1f)) {
                    requests.forEach { request ->
                        RequestRow(
                            textKo = request.text,
                            kind = request.uiKind(),
                            state = request.uiState(),
                            time = clockText(request.ts, KoreaZone),
                            onReplay = { commands.replayRequest(request.requestId) },
                            modifier = Modifier.fillWidth(),
                        )
                    }
                }
                VStack(HeyGap.G16, modifier = Modifier.grow(1.3f)) {
                    QuickReplyGrid(
                        replies = replies,
                        onReply = { reply -> parked.sendReply(target?.requestId, QuickReplyId.valueOf(reply.id)) },
                        modifier = Modifier.fillWidth(),
                        enabled = state.online,
                        loadingId = busyReply,
                    )
                    if (pin != null) {
                        val from = view.card?.destination?.let { LatLng(it.lat, it.lng) }
                        PassengerPinCard(
                            label = pin.label,
                            distanceText = from?.let { distanceText(it.distanceTo(pin.point)) }.orEmpty(),
                            onAccept = { parked.decidePin(pin.version, true) },
                            onDecline = { parked.decidePin(pin.version, false) },
                            modifier = Modifier.fillWidth(),
                        )
                    }
                    ErrorBanner(state, Modifier.fillWidth())
                }
            }
        }
    }
}

// ================================================================ D7 arrived

@Composable
internal fun ArrivedScreen(
    state: CarUiState,
    phase: CarPhase.Arrived,
    parked: ParkedCommands,
    commands: CarCommands,
    nowMillis: Long,
    modifier: Modifier = Modifier,
) {
    val view = phase.view
    val summary = view.summary
    val agreed = view.agreedDropoff
    val none = stringResource(R.string.trip_arrived_none)
    val rows = buildList {
        val destination = summary?.destination ?: view.card?.destination
        add(stringResource(R.string.trip_arrived_row_destination) to (destination?.nameKo ?: none))
        if (agreed != null) add(stringResource(R.string.trip_arrived_row_dropoff) to agreed.label)
        summary?.elapsedSec?.let { add(stringResource(R.string.trip_arrived_row_duration) to durationText(it)) }
        summary?.routeDistanceM?.let { add(stringResource(R.string.trip_arrived_row_distance) to distanceText(it)) }
        add(stringResource(R.string.trip_arrived_row_requests) to stringResource(R.string.trip_format_count, view.requests.size))
        add(stringResource(R.string.trip_arrived_row_replies) to stringResource(R.string.trip_format_count, view.replies.size))
    }
    val navDiffers = agreed != null && view.card != null && view.routeStale
    HeyLayered(
        modifier = Modifier.fillSize(),
        overlay = if (state.endDialogOpen) {
            {
                HeyDialog(
                    title = stringResource(R.string.trip_end_dialog_title),
                    message = stringResource(R.string.trip_end_dialog_message),
                    confirm = HeyDialogAction(stringResource(R.string.trip_end_dialog_confirm), parked::endTrip),
                    dismiss = HeyDialogAction(stringResource(R.string.trip_end_dialog_cancel), commands::closeEndDialog),
                    modifier = Modifier.fillSize(),
                    danger = true,
                )
            }
        } else {
            null
        },
    ) {
        TripFrame(
            state = state,
            nowMillis = nowMillis,
            modifier = modifier,
            rail = {
                HStack(HeyGap.G16, arrange = HeyArrange.End, modifier = Modifier.fillWidth()) {
                    HeyButton(
                        stringResource(R.string.trip_arrived_end),
                        parked::openEndDialog,
                        size = HeyButtonSize.Large,
                        icon = HeyIcons.Check,
                        enabled = state.online,
                        loading = state.isLoading(CommandKey.End),
                    )
                }
            },
        ) {
            HeyScrollColumn(HeyGap.G24, modifier = Modifier.fillSize()) {
                HeyText(stringResource(R.string.trip_arrived_title), variant = HeyTextVariant.Hero, maxLines = 1)
                if (agreed != null) HeyBanner(HeyStatusTone.Success, stringResource(R.string.trip_arrived_agreed), Modifier.fillWidth())
                TripSummaryCard(rows, Modifier.fillWidth())
                if (navDiffers) HeyBanner(HeyStatusTone.Info, stringResource(R.string.trip_arrived_navi_differs), Modifier.fillWidth())
                if (!state.online) HeyBanner(HeyStatusTone.Warning, stringResource(R.string.trip_arrived_offline), Modifier.fillWidth())
                ErrorBanner(state, Modifier.fillWidth())
            }
        }
    }
}
