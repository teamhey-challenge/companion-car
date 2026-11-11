package ai.hey.designsystem.component

import ai.hey.designsystem.R
import ai.hey.designsystem.icon.HeyIcons
import ai.hey.designsystem.layout.HStack
import ai.hey.designsystem.layout.HeyAlign
import ai.hey.designsystem.layout.HeyArrange
import ai.hey.designsystem.layout.VStack
import ai.hey.designsystem.theme.HeyGap
import ai.hey.designsystem.theme.HeyTextVariant
import ai.hey.designsystem.theme.HeyTheme
import ai.hey.designsystem.theme.HeyTone
import ai.hey.designsystem.token.HeyTokens
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement

// ---------------------------------------------------------------- join request

@Composable
fun JoinRequestCard(
    langLabel: String,
    partySize: Int,
    expiresInSec: Int,
    onApprove: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val expires = remember(expiresInSec) { "%d:%02d".format(expiresInSec / 60, expiresInSec % 60) }
    HeyCard(modifier = modifier, tone = HeyCardTone.Raised) {
        HStack(gap = HeyGap.G16, arrange = HeyArrange.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            HeyText(stringResource(R.string.hey_join_title), variant = HeyTextVariant.Title)
            HeyStatusPill(stringResource(R.string.hey_join_expires, expires), HeyStatusTone.Warning, icon = HeyIcons.Clock)
        }
        HStack(gap = HeyGap.G16) {
            HeyIcon(HeyIcons.Globe, null)
            HeyText(langLabel, variant = HeyTextVariant.Heading, maxLines = 1)
            HeyIcon(HeyIcons.Users, null)
            HeyText(stringResource(R.string.hey_join_party, partySize), variant = HeyTextVariant.Heading, maxLines = 1)
        }
        if (!enabled) HeyBanner(HeyStatusTone.Info, stringResource(R.string.hey_join_parked_only))
        HStack(gap = HeyGap.G16, modifier = Modifier.fillMaxWidth()) {
            HeyButton(
                stringResource(R.string.hey_join_decline),
                onDecline,
                Modifier.grow(),
                variant = HeyButtonVariant.Secondary,
                size = HeyButtonSize.Large,
                enabled = enabled,
            )
            HeyButton(
                stringResource(R.string.hey_join_approve),
                onApprove,
                Modifier.grow(),
                variant = HeyButtonVariant.Primary,
                size = HeyButtonSize.Large,
                enabled = enabled,
            )
        }
    }
}

// ---------------------------------------------------------------- boarding card summary

data class ConditionLine(val icon: ImageVector, val text: String, val changed: Boolean = false)

@Composable
fun ConditionLineRow(line: ConditionLine, modifier: Modifier = Modifier) {
    val colors = HeyTheme.colors
    val shape = RoundedCornerShape(HeyTokens.Radius.md)
    HStack(
        gap = HeyGap.G16,
        modifier = modifier
            .clip(shape)
            .background(if (line.changed) colors.accentContainer else colors.bgSunken, shape)
            .let { if (line.changed) it.border(HeyTokens.Border.strong, colors.accent, shape) else it }
            .heightIn(min = HeyTokens.Size.touchCar)
            .padding(horizontal = HeyTokens.Space.s24, vertical = HeyTokens.Space.s8),
    ) {
        HeyIcon(line.icon, null)
        HeyText(line.text, Modifier.grow(), variant = HeyTextVariant.Body, maxLines = 2)
        if (line.changed) HeyStatusPill(stringResource(R.string.hey_condition_changed), HeyStatusTone.Accent)
    }
}

@Composable
fun KoreanSummary(
    destinationKo: String,
    addressKo: String,
    nameLocal: String,
    conditions: List<ConditionLine>,
    modifier: Modifier = Modifier,
) {
    VStack(gap = HeyGap.G24, modifier = modifier) {
        VStack(gap = HeyGap.G8) {
            HeyText(destinationKo, variant = HeyTextVariant.Hero, maxLines = 2)
            HeyText(addressKo, variant = HeyTextVariant.Heading, tone = HeyTone.Muted, maxLines = 2)
            HeyText(nameLocal, variant = HeyTextVariant.Caption, tone = HeyTone.Subtle, maxLines = 1)
        }
        if (conditions.isNotEmpty()) {
            VStack(gap = HeyGap.G8) { conditions.forEach { ConditionLineRow(it, Modifier.fillMaxWidth()) } }
        }
    }
}

// ---------------------------------------------------------------- dropoff

@Composable
fun DropoffCandidateRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    distanceText: String? = null,
) {
    val colors = HeyTheme.colors
    val shape = RoundedCornerShape(HeyTokens.Radius.md)
    HStack(
        gap = HeyGap.G16,
        modifier = modifier
            .clip(shape)
            .background(if (selected) colors.accentContainer else colors.bgSurface, shape)
            .border(if (selected) HeyTokens.Border.focus else HeyTokens.Border.hairline, if (selected) colors.accent else colors.borderDefault, shape)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, role = Role.RadioButton, onClick = onSelect)
            .heightIn(min = HeyTokens.Size.touchCar)
            .padding(horizontal = HeyTokens.Space.s24, vertical = HeyTokens.Space.s8),
    ) {
        HeyIcon(if (selected) HeyIcons.Check else HeyIcons.Pin, null, tone = if (selected) HeyTone.Accent else HeyTone.Muted)
        HeyText(label, Modifier.grow(), maxLines = 2)
        if (distanceText != null) HeyText(distanceText, variant = HeyTextVariant.Label, tone = HeyTone.Muted, maxLines = 1)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReasonChips(
    reasons: List<String>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HeyTheme.colors
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(HeyGap.G8.value),
        verticalArrangement = Arrangement.spacedBy(HeyGap.G8.value),
    ) {
        reasons.forEachIndexed { index, reason ->
            val on = index == selected
            val shape = RoundedCornerShape(HeyTokens.Radius.full)
            Box(
                Modifier
                    .heightIn(min = HeyTokens.Size.touchCar)
                    .clip(shape)
                    .background(if (on) colors.primary else colors.bgRaised, shape)
                    .border(HeyTokens.Border.strong, if (on) colors.primary else colors.borderStrong, shape)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, role = Role.RadioButton) { onSelect(index) }
                    .padding(horizontal = HeyTokens.Space.s24, vertical = HeyTokens.Space.s8),
                contentAlignment = Alignment.Center,
            ) {
                InkText(reason, if (on) colors.fgOnPrimary else colors.fgDefault, variant = HeyTextVariant.Label, maxLines = 2)
            }
        }
    }
}

enum class ProposalState { Pending, Ok, Elsewhere, Unsure, Superseded }

@Composable
fun ProposalStatus(state: ProposalState, modifier: Modifier = Modifier) {
    val (tone, icon, text) = when (state) {
        ProposalState.Pending -> Triple(HeyStatusTone.Neutral, HeyIcons.Clock, stringResource(R.string.hey_proposal_pending))
        ProposalState.Ok -> Triple(HeyStatusTone.Success, HeyIcons.Check, stringResource(R.string.hey_proposal_ok))
        ProposalState.Elsewhere -> Triple(HeyStatusTone.Warning, HeyIcons.Pin, stringResource(R.string.hey_proposal_elsewhere))
        ProposalState.Unsure -> Triple(HeyStatusTone.Info, HeyIcons.Info, stringResource(R.string.hey_proposal_unsure))
        ProposalState.Superseded -> Triple(HeyStatusTone.Neutral, HeyIcons.Refresh, stringResource(R.string.hey_proposal_superseded))
    }
    HeyStatusPill(text, tone, modifier, icon)
}

// ---------------------------------------------------------------- requests and replies

enum class RequestKind { Notice, Question, Free }
enum class RequestState { Received, Spoken, Answered }

@Composable
fun RequestRow(
    textKo: String,
    kind: RequestKind,
    state: RequestState,
    time: String,
    onReplay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HeyTheme.colors
    val shape = RoundedCornerShape(HeyTokens.Radius.md)
    val kindLabel = when (kind) {
        RequestKind.Notice -> stringResource(R.string.hey_request_notice)
        RequestKind.Question -> stringResource(R.string.hey_request_question)
        RequestKind.Free -> stringResource(R.string.hey_request_free)
    }
    val (stateTone, stateLabel) = when (state) {
        RequestState.Received -> HeyStatusTone.Info to stringResource(R.string.hey_request_received)
        RequestState.Spoken -> HeyStatusTone.Neutral to stringResource(R.string.hey_request_spoken)
        RequestState.Answered -> HeyStatusTone.Success to stringResource(R.string.hey_request_answered)
    }
    HStack(
        gap = HeyGap.G16,
        modifier = modifier
            .clip(shape)
            .background(colors.bgSurface, shape)
            .border(HeyTokens.Border.hairline, colors.borderDefault, shape)
            .padding(start = HeyTokens.Space.s24, top = HeyTokens.Space.s8, bottom = HeyTokens.Space.s8, end = HeyTokens.Space.s8),
    ) {
        VStack(gap = HeyGap.G8, modifier = Modifier.grow().padding(vertical = HeyTokens.Space.s8)) {
            HStack(gap = HeyGap.G8) {
                HeyStatusPill(kindLabel, if (kind == RequestKind.Question) HeyStatusTone.Accent else HeyStatusTone.Neutral)
                HeyStatusPill(stateLabel, stateTone)
                HeyText(time, variant = HeyTextVariant.Caption, tone = HeyTone.Subtle, maxLines = 1)
            }
            HeyText(textKo, maxLines = 2)
        }
        HeyIconButton(HeyIcons.Speaker, stringResource(R.string.hey_request_replay), onReplay)
    }
}

data class QuickReplyUi(val id: String, val text: String)

@Composable
fun QuickReplyGrid(
    replies: List<QuickReplyUi>,
    onReply: (QuickReplyUi) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    VStack(gap = HeyGap.G16, modifier = modifier) {
        replies.chunked(3).forEach { row ->
            HStack(gap = HeyGap.G16, modifier = Modifier.fillMaxWidth()) {
                row.forEach { reply ->
                    HeyButton(
                        reply.text,
                        { onReply(reply) },
                        Modifier.grow(),
                        variant = HeyButtonVariant.Secondary,
                        size = HeyButtonSize.Large,
                        enabled = enabled,
                    )
                }
                repeat(3 - row.size) { Box(Modifier.grow()) }
            }
        }
    }
}

@Composable
fun PassengerPinCard(
    label: String,
    distanceText: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HeyCard(modifier = modifier, tone = HeyCardTone.Accent) {
        HStack(gap = HeyGap.G16, modifier = Modifier.fillMaxWidth()) {
            HeyIcon(HeyIcons.Pin, null, tone = HeyTone.Accent)
            VStack(gap = HeyGap.G4, modifier = Modifier.grow()) {
                HeyText(stringResource(R.string.hey_pin_title), variant = HeyTextVariant.Caption, tone = HeyTone.Muted, maxLines = 1)
                HeyText(label, variant = HeyTextVariant.Heading, maxLines = 2)
            }
            HeyText(distanceText, variant = HeyTextVariant.Label, tone = HeyTone.Muted, maxLines = 1)
        }
        HStack(gap = HeyGap.G16, modifier = Modifier.fillMaxWidth()) {
            HeyButton(stringResource(R.string.hey_pin_decline), onDecline, Modifier.grow(), variant = HeyButtonVariant.Secondary, size = HeyButtonSize.Large)
            HeyButton(stringResource(R.string.hey_pin_accept), onAccept, Modifier.grow(), variant = HeyButtonVariant.Primary, size = HeyButtonSize.Large)
        }
    }
}

// ---------------------------------------------------------------- trip glance

@Composable
fun EtaBlock(etaText: String, distanceText: String, stale: Boolean, modifier: Modifier = Modifier) {
    VStack(gap = HeyGap.G8, modifier = modifier) {
        HStack(gap = HeyGap.G16) {
            HeyText(etaText, variant = HeyTextVariant.Hero, tone = if (stale) HeyTone.Subtle else HeyTone.Default, maxLines = 1)
            HeyText(distanceText, variant = HeyTextVariant.Heading, tone = HeyTone.Muted, maxLines = 1)
        }
        if (stale) HeyStatusPill(stringResource(R.string.hey_eta_stale), HeyStatusTone.Warning, icon = HeyIcons.Clock)
    }
}

@Composable
fun DrivingPanel(
    destinationKo: String,
    etaText: String,
    pendingCount: Int,
    modifier: Modifier = Modifier,
    lastSpokenKo: String? = null,
) {
    VStack(gap = HeyGap.G24, modifier = modifier) {
        HeyCard(Modifier.fillMaxWidth(), tone = HeyCardTone.Sunken) {
            HeyText(stringResource(R.string.hey_driving_last_spoken), variant = HeyTextVariant.Label, tone = HeyTone.Muted, maxLines = 1)
            if (lastSpokenKo != null) {
                HStack(gap = HeyGap.G16) {
                    HeyIcon(HeyIcons.Speaker, null, tone = HeyTone.Accent)
                    HeyText(lastSpokenKo, Modifier.grow(), variant = HeyTextVariant.Title, maxLines = 2)
                }
            } else {
                HeyText(stringResource(R.string.hey_driving_none), variant = HeyTextVariant.Heading, tone = HeyTone.Subtle, maxLines = 1)
            }
        }
        HStack(gap = HeyGap.G24, arrange = HeyArrange.SpaceBetween, align = HeyAlign.Center, modifier = Modifier.fillMaxWidth()) {
            VStack(gap = HeyGap.G4, modifier = Modifier.grow()) {
                HeyText(stringResource(R.string.hey_driving_destination), variant = HeyTextVariant.Label, tone = HeyTone.Muted, maxLines = 1)
                HeyText(destinationKo, variant = HeyTextVariant.Hero, maxLines = 2)
            }
            HeyText(etaText, variant = HeyTextVariant.Hero, tone = HeyTone.Accent, maxLines = 1)
        }
        if (pendingCount > 0) {
            HeyStatusPill(stringResource(R.string.hey_driving_pending, pendingCount), HeyStatusTone.Accent, icon = HeyIcons.Message)
        }
    }
}

@Composable
fun TripSummaryCard(rows: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    HeyCard(modifier) {
        rows.forEachIndexed { index, (label, value) ->
            if (index > 0) HeyDivider()
            HStack(gap = HeyGap.G24, arrange = HeyArrange.SpaceBetween, align = HeyAlign.Start, modifier = Modifier.fillMaxWidth()) {
                HeyText(label, Modifier.grow(), tone = HeyTone.Muted, maxLines = 2)
                HeyText(value, Modifier.grow(), variant = HeyTextVariant.Heading, maxLines = 3, align = HeyTextAlign.End)
            }
        }
    }
}
