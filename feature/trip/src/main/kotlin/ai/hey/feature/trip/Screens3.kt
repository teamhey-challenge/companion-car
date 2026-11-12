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
import ai.hey.designsystem.component.HeyBannerAction
import ai.hey.designsystem.component.QuickReplyGrid
import ai.hey.designsystem.component.VoiceConfirmDialog
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
import ai.hey.model.ErrorCode
import ai.hey.model.QuickReplyId
import ai.hey.model.QuickReplySet
import ai.hey.model.HeyConfig
import ai.hey.model.pendingRequestCount
import ai.hey.model.replyTarget
import ai.hey.session.CarCommands
import ai.hey.session.CarPhase
import ai.hey.session.CarUiState
import ai.hey.session.CommandKey
import ai.hey.session.VoiceState
import ai.hey.session.CarCommand
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

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
    commands: CarCommands,
    nowMillis: Long,
    modifier: Modifier = Modifier,
) {
    val view = phase.view
    val requests = view.requests.reversed()
    val target = view.replyTarget
    val canReply = state.online && CarCommand.Reply in phase.enabled
    val busyReply = state.replyInFlight?.replyId?.name
    val voice = state.voice
    EndTripLayer(state, commands) {
        HeyLayered(
            modifier = Modifier.fillSize(),
            overlay = (voice as? VoiceState.Confirm)?.let { confirm ->
                {
                    VoiceConfirmDialog(
                        title = stringResource(R.string.trip_voice_confirm),
                        textKo = confirm.textKo,
                        send = HeyDialogAction(stringResource(R.string.trip_voice_send)) { state.parked?.sendVoiceReply() },
                        retry = HeyDialogAction(stringResource(R.string.trip_voice_retry)) { state.parked?.startVoiceReply() },
                        cancel = HeyDialogAction(stringResource(R.string.trip_voice_cancel), commands::cancelVoice),
                        modifier = Modifier.fillSize(),
                        sending = state.voiceInFlight,
                        enabled = canReply,
                        notice = confirm.failure?.let { errorText(it) },
                    )
                }
            },
        ) {
            TripFrame(
                state = state,
                nowMillis = nowMillis,
                modifier = modifier,
                rail = {
                    HStack(HeyGap.G16, arrange = HeyArrange.End, modifier = Modifier.fillWidth()) {
                        if (CarCommand.Arrive in phase.enabled) {
                            HeyButton(
                                stringResource(R.string.trip_requests_arrive),
                                { state.parked?.arrive() },
                                variant = HeyButtonVariant.Secondary,
                                size = HeyButtonSize.Large,
                                icon = HeyIcons.Flag,
                                loading = state.isLoading(CommandKey.Arrive),
                                enabled = state.online,
                            )
                        }
                        EndTripButton(state, phase.enabled)
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
                    HeyText(
                        stringResource(if (target != null) R.string.trip_requests_title else R.string.trip_requests_title_free),
                        variant = HeyTextVariant.Title,
                        maxLines = 1,
                    )
                    if (!state.online) HeyBanner(HeyStatusTone.Warning, stringResource(R.string.trip_requests_offline), Modifier.fillWidth())
                    HStack(HeyGap.G32, align = HeyAlign.Start, modifier = Modifier.fillWidth().grow()) {
                        HeyScrollColumn(HeyGap.G8, modifier = Modifier.grow()) {
                            requests.forEach { request ->
                                RequestRow(
                                    textKo = request.text,
                                    kind = request.uiKind(),
                                    state = request.uiState(),
                                    time = clockText(request.ts, clockZone()),
                                    onReplay = { commands.replayRequest(request.requestId) },
                                    modifier = Modifier.fillWidth(),
                                )
                            }
                        }
                        HeyScrollColumn(HeyGap.G16, modifier = Modifier.grow()) {
                            // Group A: only while a passenger question is open.
                            if (target != null) {
                                QuickReplyGrid(
                                    replies = quickReplies(QuickReplySet.Answer),
                                    onReply = { reply -> state.parked?.sendReply(target.requestId, QuickReplyId.valueOf(reply.id)) },
                                    modifier = Modifier.fillWidth(),
                                    enabled = canReply && !state.voiceInFlight,
                                    loadingId = busyReply,
                                )
                            }
                            // Voice reply first (the primary way to speak): push-to-talk, parked only (this screen exists only while parked).
                            // While listening the banner IS the state (with its cancel), so the start button is not drawn next to it.
                            if (voice is VoiceState.Listening) {
                                VoiceStatus(voice, commands)
                            } else if (voice is VoiceState.Failed && voice.reason == VoiceState.Reason.PermissionDenied) {
                                // Without the microphone the start button cannot work: the warning is the one element, and its action asks again.
                                HeyBanner(
                                    HeyStatusTone.Warning,
                                    stringResource(R.string.trip_voice_permission),
                                    Modifier.fillWidth(),
                                    message = stringResource(R.string.trip_voice_permission_hint),
                                    actions = listOf(HeyBannerAction(stringResource(R.string.trip_voice_permission_retry), { state.parked?.startVoiceReply() }, enabled = canReply)),
                                )
                            } else {
                                HeyButton(
                                    stringResource(R.string.trip_voice_start),
                                    { state.parked?.startVoiceReply() },
                                    Modifier.fillWidth(),
                                    size = HeyButtonSize.Large,
                                    icon = HeyIcons.Mic,
                                    enabled = canReply && !state.voiceInFlight,
                                )
                                VoiceStatus(voice, commands)
                            }
                            // The dialog tells a refused voice send itself; the same sentence behind the scrim is noise.
                            if ((voice as? VoiceState.Confirm)?.failure == null) ErrorBanner(state, Modifier.fillWidth())
                            // Group B: the driver speaks first, any time while parked, behind a toggle (the toggle names the group).
                            HeyButton(
                                stringResource(R.string.trip_reply_driver_first_open),
                                commands::toggleDriverFirst,
                                Modifier.fillWidth(),
                                variant = HeyButtonVariant.Secondary,
                                size = HeyButtonSize.Regular,
                                icon = if (state.driverFirstOpen) HeyIcons.ChevronDown else HeyIcons.ChevronRight,
                            )
                            if (state.driverFirstOpen) {
                                QuickReplyGrid(
                                    replies = quickReplies(QuickReplySet.DriverFirst),
                                    onReply = { reply -> state.parked?.sendReply(null, QuickReplyId.valueOf(reply.id)) },
                                    modifier = Modifier.fillWidth(),
                                    enabled = canReply && !state.voiceInFlight,
                                    loadingId = busyReply,
                                    columns = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceStatus(voice: VoiceState, commands: CarCommands) {
    when (voice) {
        VoiceState.Listening -> HeyBanner(
            HeyStatusTone.Info,
            stringResource(R.string.trip_voice_listening),
            Modifier.fillWidth(),
            actions = listOf(HeyBannerAction(stringResource(R.string.trip_voice_cancel), commands::cancelVoice)),
        )
        VoiceState.NeedsPermission -> HeyBanner(HeyStatusTone.Info, stringResource(R.string.trip_voice_permission), Modifier.fillWidth())
        VoiceState.Sent -> HeyBanner(HeyStatusTone.Success, stringResource(R.string.trip_voice_sent), Modifier.fillWidth())
        is VoiceState.Failed -> HeyBanner(
            HeyStatusTone.Warning,
            stringResource(
                when (voice.reason) {
                    VoiceState.Reason.NoSpeech -> R.string.trip_voice_no_speech
                    VoiceState.Reason.PermissionDenied -> R.string.trip_voice_permission
                    VoiceState.Reason.Unavailable -> R.string.trip_voice_unavailable
                },
            ),
            Modifier.fillWidth(),
        )
        VoiceState.Idle, is VoiceState.Confirm -> Unit
    }
}

// ================================================================ D7 arrived

@Composable
internal fun ArrivedScreen(
    state: CarUiState,
    phase: CarPhase.Arrived,
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
    EndTripLayer(state, commands) {
        TripFrame(
            state = state,
            nowMillis = nowMillis,
            modifier = modifier,
            rail = {
                HStack(HeyGap.G16, arrange = HeyArrange.End, modifier = Modifier.fillWidth()) {
                    EndTripButton(state, phase.enabled, label = R.string.trip_arrived_end, primary = true)
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
