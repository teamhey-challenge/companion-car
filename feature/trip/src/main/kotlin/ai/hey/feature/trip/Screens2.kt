package ai.hey.feature.trip

import ai.hey.designsystem.component.ConditionLine
import ai.hey.designsystem.component.DropoffCandidateRow
import ai.hey.designsystem.component.EmptyPanel
import ai.hey.designsystem.component.EtaBlock
import ai.hey.designsystem.component.HeyBanner
import ai.hey.designsystem.component.HeyBannerAction
import ai.hey.designsystem.component.HeyButton
import ai.hey.designsystem.component.HeyButtonSize
import ai.hey.designsystem.component.HeyButtonVariant
import ai.hey.designsystem.layout.fillHeight
import ai.hey.designsystem.component.HeyScrollColumn
import ai.hey.designsystem.component.HeyStatusPill
import ai.hey.designsystem.component.HeyStatusTone
import ai.hey.designsystem.component.HeyText
import ai.hey.designsystem.component.KoreanSummary
import ai.hey.designsystem.component.ProposalStatus
import ai.hey.designsystem.component.ReasonChips
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
import ai.hey.model.CarCardView
import ai.hey.model.ConditionId
import ai.hey.model.DropoffCandidate
import ai.hey.model.DropoffKind
import ai.hey.model.DropoffReasonId
import ai.hey.model.MusicPref
import ai.hey.model.LatLng
import ai.hey.model.Temperature
import ai.hey.session.CarCommands
import ai.hey.session.CarPhase
import ai.hey.session.CarUiState
import ai.hey.session.CommandKey
import ai.hey.session.CarCommand
import ai.hey.vehicle.RouteFailure
import ai.hey.vehicle.RouteStartState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

// ================================================================ D3 boarding card

@Composable
private fun conditionLines(card: CarCardView, changed: Set<ConditionId>): List<ConditionLine> = card.conditions.mapIndexed { index, id ->
    val base = card.conditionLabelsKo.getOrNull(index) ?: stringResource(
        when (id) {
            ConditionId.MeetEntrance -> R.string.trip_condition_label_meet_entrance
            ConditionId.Temperature -> R.string.trip_condition_label_temperature
            ConditionId.Quiet -> R.string.trip_condition_label_quiet
            ConditionId.Music -> R.string.trip_condition_label_music
        },
    )
    val text = when {
        id == ConditionId.MeetEntrance && !card.entranceNote.isNullOrBlank() ->
            stringResource(R.string.trip_condition_detail, base, card.entranceNote!!)
        id == ConditionId.Temperature && card.temperature != null -> {
            val temp = card.temperatureLabelKo ?: stringResource(
                if (card.temperature == Temperature.Cooler) R.string.trip_temperature_cooler else R.string.trip_temperature_warmer,
            )
            stringResource(R.string.trip_condition_detail, base, temp)
        }
        id == ConditionId.Music && card.music != null -> {
            val music = card.musicLabelKo ?: stringResource(if (card.music == MusicPref.Off) R.string.trip_music_off else R.string.trip_music_low)
            stringResource(R.string.trip_condition_detail, base, music)
        }
        else -> base
    }
    val icon = if (id == ConditionId.Temperature && card.temperature == Temperature.Warmer) HeyIcons.ThermometerWarm else id.icon()
    ConditionLine(icon, text, changed = id in changed)
}

@Composable
internal fun CardScreen(
    state: CarUiState,
    phase: CarPhase.Card,
    commands: CarCommands,
    nowMillis: Long,
    modifier: Modifier = Modifier,
) {
    val view = phase.view
    val card = view.card
    val routeStarted = view.route != null
    val replayLine = replayText(card)
    val inTrip = phase is CarPhase.InTrip
    EndTripLayer(state, commands) {
        TripFrame(
            state = state,
            nowMillis = nowMillis,
            modifier = modifier,
            rail = {
                HStack(HeyGap.G16, modifier = Modifier.fillWidth()) {
                    // The relay refuses card.recheck once the route runs, so IN_TRIP has no such button (this also frees the rail for arrive).
                    if (!inTrip) {
                        HeyButton(
                            stringResource(R.string.trip_card_recheck),
                            { state.parked?.recheckCard() },
                            variant = HeyButtonVariant.Secondary,
                            size = HeyButtonSize.Large,
                            icon = HeyIcons.Refresh,
                            enabled = CarCommand.Recheck in phase.enabled && card != null && state.online,
                            loading = state.isLoading(CommandKey.Recheck),
                        )
                    }
                    HeyButton(
                        stringResource(R.string.trip_card_propose),
                        { state.parked?.openDropoff() },
                        variant = HeyButtonVariant.Secondary,
                        size = HeyButtonSize.Large,
                        icon = HeyIcons.Flag,
                        enabled = CarCommand.Propose in phase.enabled && card != null,
                    )
                    HeyButton(
                        stringResource(R.string.trip_card_replay),
                        { commands.replay(replayLine) },
                        variant = HeyButtonVariant.Ghost,
                        size = HeyButtonSize.Large,
                        icon = HeyIcons.Speaker,
                        enabled = card != null,
                    )
                    HeyButton(
                        stringResource(if (routeStarted) R.string.trip_card_restart_route else R.string.trip_card_start_route),
                        { state.parked?.startRoute() },
                        Modifier.grow(),
                        size = HeyButtonSize.Large,
                        icon = HeyIcons.Route,
                        enabled = CarCommand.StartRoute in phase.enabled && card != null && phase.route != RouteStartState.AwaitingNaviConfirm,
                        loading = phase.route == RouteStartState.Requested || phase.route == RouteStartState.AwaitingNaviConfirm,
                    )
                    if (inTrip) {
                        HeyButton(
                            stringResource(R.string.trip_card_arrived),
                            { state.parked?.arrive() },
                            variant = HeyButtonVariant.Secondary,
                            size = HeyButtonSize.Large,
                            icon = HeyIcons.Flag,
                            enabled = CarCommand.Arrive in phase.enabled && state.online,
                            loading = state.isLoading(CommandKey.Arrive),
                        )
                    }
                }
            },
        ) {
            if (card == null) {
                VStack(HeyGap.G24, align = HeyAlign.Center, arrange = HeyArrange.Center, modifier = Modifier.fillSize()) {
                    EmptyPanel(
                        icon = HeyIcons.Clock,
                        title = stringResource(R.string.trip_card_waiting_title),
                    )
                    EndTripButton(state, phase.enabled)
                }
            } else {
                val lines = conditionLines(card, state.changedConditions)
                HStack(HeyGap.G32, align = HeyAlign.Start, modifier = Modifier.fillSize()) {
                    HeyScrollColumn(HeyGap.G16, modifier = Modifier.grow(3f)) {
                        KoreanSummary(card.destination.nameKo, card.destination.addressKo, card.destination.nameLocal, lines)
                    }
                    VStack(HeyGap.G16, modifier = Modifier.grow(2f).fillHeight()) {
                    HeyScrollColumn(HeyGap.G16, modifier = Modifier.grow()) {
                        val routeState = phase.route
                        val pill = when (routeState) {
                            RouteStartState.Requested -> R.string.trip_card_pill_requested to HeyStatusTone.Info
                            RouteStartState.AwaitingNaviConfirm -> R.string.trip_card_pill_confirm to HeyStatusTone.Warning
                            is RouteStartState.Failed -> R.string.trip_card_pill_failed to HeyStatusTone.Danger
                            is RouteStartState.Started, RouteStartState.Idle ->
                                (if (routeStarted) R.string.trip_card_route_started else R.string.trip_card_checking) to HeyStatusTone.Info
                        }
                        HeyStatusPill(stringResource(pill.first), pill.second)
                        val canRoute = CarCommand.StartRoute in phase.enabled
                        if (routeState == RouteStartState.AwaitingNaviConfirm) {
                            HeyBanner(
                                HeyStatusTone.Info,
                                stringResource(R.string.trip_card_route_confirm),
                                Modifier.fillWidth(),
                                actions = listOf(HeyBannerAction(stringResource(R.string.trip_card_route_cancel), { state.parked?.cancelRoute() }, enabled = canRoute)),
                            )
                        }
                        if (routeState is RouteStartState.Failed) {
                            val code = routeState.code
                            val title = when (code) {
                                RouteFailure.NAVI_NOT_READY -> R.string.trip_card_route_not_ready
                                RouteFailure.NAVI_CONFIRM_TIMEOUT -> R.string.trip_card_route_confirm_timeout
                                else -> R.string.trip_card_route_failed
                            }
                            val retry = HeyBannerAction(stringResource(R.string.trip_card_retry), { state.parked?.startRoute() }, enabled = canRoute)
                            HeyBanner(
                                HeyStatusTone.Danger,
                                stringResource(title),
                                Modifier.fillWidth(),
                                actions = if (code == RouteFailure.NAVI_NOT_READY) {
                                    listOf(HeyBannerAction(stringResource(R.string.trip_card_open_navi), { state.parked?.openNavi() }, primary = true, enabled = canRoute), retry)
                                } else {
                                    listOf(retry)
                                },
                            )
                        }
                        if (view.routeStale) {
                            HeyBanner(
                                HeyStatusTone.Warning,
                                stringResource(R.string.trip_card_route_stale),
                                Modifier.fillWidth(),
                            )
                        }
                        val route = view.route
                        val progress = view.progress
                        if (inTrip && progress != null) {
                            EtaBlock(durationText(progress.etaSec), distanceText(progress.distanceM), progress.stale)
                        } else if (route != null && route.durationSec > 0) {
                            EtaBlock(durationText(route.durationSec), distanceText(route.distanceM), stale = false)
                        }
                        ErrorBanner(state, Modifier.fillWidth())
                    }
                    HeyButton(
                        stringResource(R.string.trip_reply_open),
                        { state.parked?.openReply() },
                        Modifier.fillWidth(),
                        variant = HeyButtonVariant.Secondary,
                        size = HeyButtonSize.Regular,
                        icon = HeyIcons.Message,
                        enabled = CarCommand.Reply in phase.enabled,
                    )
                    // Rare and destructive: a quiet 76dp ghost action at the foot of the column, not part of the main flow.
                    EndTripButton(state, phase.enabled, quiet = true)
                    }
                }
            }
        }
    }
}

/** The sentence "다시 듣기" reads: the destination, as the relay would say it. */
@Composable
private fun replayText(card: CarCardView?): String =
    if (card == null) "" else stringResource(R.string.trip_card_replay_text, card.destination.nameKo)

// ================================================================ D4 dropoff proposal

@Composable
internal fun DropoffScreen(
    state: CarUiState,
    phase: CarPhase.DropoffProposal,
    commands: CarCommands,
    nowMillis: Long,
    modifier: Modifier = Modifier,
) {
    val view = phase.view
    val card = view.card
    val draft = state.activeDraft
    val candidates = state.candidates
    val reasons = reasonLabels()
    val default = stringResource(R.string.trip_dropoff_default_place)
    // Without a candidate list (relay failed or empty) the destination itself is the one fallback candidate.
    val items: List<DropoffCandidate> = if (candidates.items.isNotEmpty()) {
        candidates.items
    } else if (card != null && !candidates.loading) {
        listOf(
            DropoffCandidate(
                candidateId = "default",
                kind = DropoffKind.Default,
                label = default,
                labelLocal = default,
                point = LatLng(card.destination.lat, card.destination.lng),
                distanceM = 0.0,
            ),
        )
    } else {
        emptyList()
    }
    val selected = items.firstOrNull { it.candidateId == draft.candidateId }
    val proposal = view.dropoff
    TripFrame(
        state = state,
        nowMillis = nowMillis,
        modifier = modifier,
        rail = {
            HStack(HeyGap.G16, modifier = Modifier.fillWidth()) {
                HeyButton(
                    stringResource(R.string.trip_dropoff_close),
                    commands::closeDropoff,
                    variant = HeyButtonVariant.Secondary,
                    size = HeyButtonSize.Large,
                )
                HeyButton(
                    stringResource(R.string.trip_dropoff_send),
                    {
                        if (selected != null) {
                            val reason = draft.reasonIndex?.let { DropoffReasonId.entries.getOrNull(it) }
                            state.parked?.proposeDropoff(selected, reason)
                        }
                    },
                    Modifier.grow(),
                    size = HeyButtonSize.Large,
                    icon = HeyIcons.Send,
                    enabled = selected != null && state.online,
                    loading = state.proposeInFlight,
                )
            }
        },
    ) {
        VStack(HeyGap.G16, modifier = Modifier.fillSize()) {
            VStack(HeyGap.G8) {
                HeyText(stringResource(R.string.trip_dropoff_title), variant = HeyTextVariant.Title, maxLines = 1)
            }
            if (!state.online) {
                HeyBanner(HeyStatusTone.Warning, stringResource(R.string.trip_dropoff_offline), Modifier.fillWidth())
            }
            HStack(HeyGap.G32, align = HeyAlign.Start, modifier = Modifier.fillWidth().grow()) {
                HeyScrollColumn(HeyGap.G8, modifier = Modifier.grow()) {
                    if (candidates.loading) {
                        HeyText(stringResource(R.string.trip_dropoff_loading), tone = HeyTone.Muted)
                    }
                    if (candidates.items.isEmpty() && !candidates.loading) {
                        EmptyPanel(
                            icon = HeyIcons.Pin,
                            title = stringResource(R.string.trip_dropoff_none_title),
                            modifier = Modifier.fillWidth(),
                        )
                    }
                    items.forEach { candidate ->
                        DropoffCandidateRow(
                            label = candidate.label,
                            selected = candidate.candidateId == draft.candidateId,
                            onSelect = { commands.selectCandidate(candidate.candidateId) },
                            modifier = Modifier.fillWidth(),
                            distanceText = candidate.distanceM.takeIf { it > 0 }?.let { distanceText(it.toInt()) },
                        )
                    }
                }
                HeyScrollColumn(HeyGap.G16, modifier = Modifier.grow()) {
                    HeyText(stringResource(R.string.trip_dropoff_reasons_title), variant = HeyTextVariant.Heading, maxLines = 1)
                    ReasonChips(reasons, draft.reasonIndex, commands::selectReason, Modifier.fillWidth())
                    proposal?.let { ProposalStatus(it.status.toUi()) }
                    ErrorBanner(state, Modifier.fillWidth())
                }
            }
        }
    }
}
