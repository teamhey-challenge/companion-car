package ai.hey.feature.trip

import ai.hey.designsystem.component.ConditionLine
import ai.hey.designsystem.component.DropoffCandidateRow
import ai.hey.designsystem.component.EmptyPanel
import ai.hey.designsystem.component.EtaBlock
import ai.hey.designsystem.component.HeyBanner
import ai.hey.designsystem.component.HeyButton
import ai.hey.designsystem.component.HeyButtonSize
import ai.hey.designsystem.component.HeyButtonVariant
import ai.hey.designsystem.component.HeyDivider
import ai.hey.designsystem.component.HeyScrollColumn
import ai.hey.designsystem.component.HeyStatusPill
import ai.hey.designsystem.component.HeyStatusTone
import ai.hey.designsystem.component.HeyText
import ai.hey.designsystem.component.KoreanSummary
import ai.hey.designsystem.component.PassengerPinCard
import ai.hey.designsystem.component.ProposalStatus
import ai.hey.designsystem.component.ReasonChips
import ai.hey.designsystem.icon.HeyIcons
import ai.hey.designsystem.layout.HStack
import ai.hey.designsystem.layout.HeyAlign
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
import ai.hey.model.LatLng
import ai.hey.model.Temperature
import ai.hey.model.distanceTo
import ai.hey.session.CarCommands
import ai.hey.session.CarPhase
import ai.hey.session.CarUiState
import ai.hey.session.CommandKey
import ai.hey.session.ParkedCommands
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

// ================================================================ D3 boarding card

@Composable
private fun conditionLines(card: CarCardView, changed: Set<ConditionId>): List<ConditionLine> = card.conditions.mapIndexed { index, id ->
    val base = card.conditionLabelsKo.getOrNull(index) ?: stringResource(
        when (id) {
            ConditionId.Luggage -> R.string.trip_condition_label_luggage
            ConditionId.LessWalking -> R.string.trip_condition_label_less_walking
            ConditionId.MeetEntrance -> R.string.trip_condition_label_meet_entrance
            ConditionId.Temperature -> R.string.trip_condition_label_temperature
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
        else -> base
    }
    val icon = if (id == ConditionId.Temperature && card.temperature == Temperature.Warmer) HeyIcons.ThermometerWarm else id.icon()
    ConditionLine(icon, text, changed = id in changed)
}

@Composable
internal fun CardScreen(
    state: CarUiState,
    phase: CarPhase.BoardingCard,
    parked: ParkedCommands,
    commands: CarCommands,
    nowMillis: Long,
    modifier: Modifier = Modifier,
) {
    val view = phase.view
    val card = view.card
    val routeStarted = view.route != null
    val replayLine = replayText(card)
    TripFrame(
        state = state,
        nowMillis = nowMillis,
        modifier = modifier,
        rail = {
            HStack(HeyGap.G16, modifier = Modifier.fillWidth()) {
                HeyButton(
                    stringResource(R.string.trip_card_recheck),
                    parked::recheckCard,
                    variant = HeyButtonVariant.Secondary,
                    size = HeyButtonSize.Large,
                    icon = HeyIcons.Refresh,
                    enabled = card != null && state.online,
                    loading = state.isLoading(CommandKey.Recheck),
                )
                HeyButton(
                    stringResource(R.string.trip_card_propose),
                    parked::openDropoff,
                    variant = HeyButtonVariant.Secondary,
                    size = HeyButtonSize.Large,
                    icon = HeyIcons.Flag,
                    enabled = card != null,
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
                    parked::startRoute,
                    Modifier.grow(),
                    size = HeyButtonSize.Large,
                    icon = HeyIcons.Route,
                    enabled = card != null,
                    loading = state.routeStarting,
                )
                if (phase.inTrip) {
                    HeyButton(
                        stringResource(R.string.trip_card_arrived),
                        parked::arrive,
                        variant = HeyButtonVariant.Secondary,
                        size = HeyButtonSize.Large,
                        icon = HeyIcons.Flag,
                        enabled = state.online,
                        loading = state.isLoading(CommandKey.Arrive),
                    )
                }
            }
        },
    ) {
        if (card == null) {
            EmptyPanel(
                icon = HeyIcons.Clock,
                title = stringResource(R.string.trip_card_waiting_title),
                message = stringResource(R.string.trip_card_waiting_message),
                modifier = Modifier.fillSize(),
            )
        } else {
            val lines = conditionLines(card, state.changedConditions)
            HStack(HeyGap.G32, align = HeyAlign.Start, modifier = Modifier.fillSize()) {
                HeyScrollColumn(HeyGap.G16, modifier = Modifier.grow(2f)) {
                    KoreanSummary(card.destination.nameKo, card.destination.addressKo, card.destination.nameLocal, lines)
                }
                VStack(HeyGap.G16, modifier = Modifier.grow(1f)) {
                    HeyStatusPill(
                        stringResource(if (routeStarted) R.string.trip_card_route_started else R.string.trip_card_checking),
                        HeyStatusTone.Info,
                    )
                    if (state.routeFailed) {
                        HeyBanner(HeyStatusTone.Danger, stringResource(R.string.trip_card_route_failed), Modifier.fillWidth())
                        HeyButton(
                            stringResource(R.string.trip_card_retry),
                            parked::startRoute,
                            variant = HeyButtonVariant.Secondary,
                        )
                    }
                    if (view.routeStale) {
                        HeyBanner(
                            HeyStatusTone.Warning,
                            stringResource(R.string.trip_card_route_stale),
                            Modifier.fillWidth(),
                            message = stringResource(R.string.trip_card_route_stale_message),
                        )
                    }
                    val route = view.route
                    val progress = view.progress
                    if (phase.inTrip && progress != null) {
                        EtaBlock(durationText(progress.etaSec), distanceText(progress.distanceM), progress.stale)
                    } else if (route != null && route.durationSec > 0) {
                        EtaBlock(durationText(route.durationSec), distanceText(route.distanceM), stale = false)
                    }
                    HeyDivider()
                    HeyText(stringResource(R.string.trip_card_replay_hint), variant = HeyTextVariant.Caption, tone = HeyTone.Muted, maxLines = 2)
                    ErrorBanner(state, Modifier.fillWidth())
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
    parked: ParkedCommands,
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
    val pin = view.pendingPins.firstOrNull()
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
                            parked.proposeDropoff(selected, reason)
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
                HeyText(stringResource(R.string.trip_dropoff_subtitle), tone = HeyTone.Muted, maxLines = 1)
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
                            message = stringResource(R.string.trip_dropoff_none_message),
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
                    HeyText(stringResource(R.string.trip_dropoff_reasons_title), variant = HeyTextVariant.Title, maxLines = 1)
                    ReasonChips(reasons, draft.reasonIndex, commands::selectReason, Modifier.fillWidth())
                    if (proposal != null && proposal.by == ai.hey.model.DropoffBy.Driver) {
                        ProposalStatus(proposal.status.toUi())
                    }
                    if (pin != null) {
                        val from = card?.let { LatLng(it.destination.lat, it.destination.lng) }
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
