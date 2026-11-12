package ai.hey.feature.trip

import ai.hey.designsystem.component.EmptyPanel
import ai.hey.designsystem.component.HeyBanner
import ai.hey.designsystem.component.HeyButton
import ai.hey.designsystem.component.HeyBannerAction
import ai.hey.designsystem.component.HeyCard
import ai.hey.designsystem.component.HeyIconButton
import ai.hey.designsystem.component.HeyStatusPill
import ai.hey.designsystem.component.HeyStatusTone
import ai.hey.designsystem.component.HeyText
import ai.hey.designsystem.component.JoinRequestCard
import ai.hey.designsystem.component.LanguageGreetingStrip
import ai.hey.designsystem.component.QrBlock
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
import ai.hey.session.CarCommands
import ai.hey.session.CarPhase
import ai.hey.session.CarUiState
import ai.hey.session.ConnectionStatus
import ai.hey.model.serverNow
import ai.hey.session.VehicleIssue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

// ================================================================ D1 waiting

@Composable
internal fun WaitingScreen(state: CarUiState, commands: CarCommands, nowMillis: Long, modifier: Modifier = Modifier) {
    TripFrame(
        state = state,
        nowMillis = nowMillis,
        modifier = modifier,
        rail = {
            HStack(HeyGap.G16, arrange = HeyArrange.End, modifier = Modifier.fillWidth()) {
                HeyIconButton(HeyIcons.Menu, stringResource(R.string.trip_settings), { state.parked?.openSettings() })
            }
        },
    ) {
        val qr = state.qrUrl
        when {
            state.connection == ConnectionStatus.ProtocolMismatch -> VStack(HeyGap.G24, align = HeyAlign.Center, arrange = HeyArrange.Center, modifier = Modifier.fillSize()) {
                EmptyPanel(
                    icon = HeyIcons.Warning,
                    title = stringResource(R.string.trip_waiting_protocol_title),
                    message = stringResource(R.string.trip_waiting_protocol_message),
                )
                VehicleIssueBanners(state, commands)
            }
            state.connection == ConnectionStatus.Rejected -> VStack(HeyGap.G24, align = HeyAlign.Center, arrange = HeyArrange.Center, modifier = Modifier.fillSize()) {
                EmptyPanel(
                    icon = HeyIcons.Warning,
                    title = stringResource(R.string.trip_waiting_rejected_title),
                    message = stringResource(R.string.trip_waiting_rejected_message),
                )
                HeyButton(stringResource(R.string.trip_waiting_reregister), { state.parked?.reregister() })
                VehicleIssueBanners(state, commands)
            }
            qr == null -> {
                val failed = state.connection == ConnectionStatus.RegistrationFailed
                VStack(HeyGap.G24, align = HeyAlign.Center, arrange = HeyArrange.Center, modifier = Modifier.fillSize()) {
                    EmptyPanel(
                        icon = if (failed) HeyIcons.Warning else HeyIcons.Qr,
                        title = stringResource(if (failed) R.string.trip_waiting_register_failed_title else R.string.trip_waiting_registering),
                        message = if (failed) stringResource(R.string.trip_waiting_register_failed_message) else null,
                        modifier = Modifier.fillWidth(),
                    )
                    VehicleIssueBanners(state, commands)
                }
            }
            else -> WaitingContent(state, qr, commands)
        }
    }
}

@Composable
private fun VehicleIssueBanners(state: CarUiState, commands: CarCommands) {
    state.issues.forEach { issue ->
        when (issue) {
            is VehicleIssue.Navi -> HeyBanner(
                HeyStatusTone.Danger,
                stringResource(R.string.trip_waiting_navi_unavailable),
                Modifier.fillWidth(),
                actions = listOf(HeyBannerAction(stringResource(R.string.trip_card_retry), { commands.retryVehicleIssue(issue) })),
            )
            is VehicleIssue.Speech -> HeyBanner(
                HeyStatusTone.Warning,
                stringResource(R.string.trip_waiting_speech_unavailable),
                Modifier.fillWidth(),
                actions = listOf(HeyBannerAction(stringResource(R.string.trip_card_retry), { commands.retryVehicleIssue(issue) })),
            )
        }
    }
}

@Composable
private fun WaitingContent(state: CarUiState, qrUrl: String, commands: CarCommands) {
    // The Korean line repeats the screen's own "QR을 찍어 주세요" heading, so only the six passenger languages are listed.
    // The sentences come from the shared copy catalog (`car.scanQr`), see feature/trip/build.gradle.kts.
    val greetings = listOf(
        stringResource(R.string.trip_scan_qr_en),
        stringResource(R.string.trip_scan_qr_ja),
        stringResource(R.string.trip_scan_qr_zh_hans),
        stringResource(R.string.trip_scan_qr_zh_hant),
        stringResource(R.string.trip_scan_qr_vi),
        stringResource(R.string.trip_scan_qr_th),
    )
    HStack(HeyGap.G32, align = HeyAlign.Start, modifier = Modifier.fillSize()) {
        VStack(HeyGap.G16, modifier = Modifier.grow()) {
            HeyText(stringResource(R.string.trip_waiting_title), variant = HeyTextVariant.Title, maxLines = 1)
            QrBlock(qrUrl)
        }
        VStack(HeyGap.G16, modifier = Modifier.grow()) {
            val online = state.online
            HeyCard(Modifier.fillWidth()) {
                // One compact row (label + plate left, status right) so the six-language grid below fits at 1280x720.
                HStack(HeyGap.G16, align = HeyAlign.Center, modifier = Modifier.fillWidth()) {
                    VStack(HeyGap.G4, modifier = Modifier.grow()) {
                        HeyText(stringResource(R.string.trip_waiting_plate_label), tone = HeyTone.Muted, maxLines = 1)
                        HeyText(state.view?.plateSuffix ?: state.settings.plateSuffix, variant = HeyTextVariant.Hero, maxLines = 1)
                    }
                    HeyStatusPill(
                        stringResource(if (online) R.string.trip_waiting_connected else R.string.trip_waiting_offline),
                        if (online) HeyStatusTone.Success else HeyStatusTone.Warning,
                    )
                }
            }
            if (state.connection == ConnectionStatus.Reconnecting || state.connection == ConnectionStatus.Connecting) {
                HeyBanner(HeyStatusTone.Warning, stringResource(R.string.trip_waiting_server_error), Modifier.fillWidth())
            }
            ErrorBanner(state, Modifier.fillWidth())
            // One column, one message: a vehicle problem the driver can act on replaces the decorative greeting strip
            // (both do not fit at 1280x720, and clipping a banner is worse than not showing a greeting).
            if (state.issues.isEmpty()) {
                LanguageGreetingStrip(greetings, Modifier.fillWidth())
            } else {
                VehicleIssueBanners(state, commands)
            }
        }
    }
}

// ================================================================ D2 join request

@Composable
internal fun JoinScreen(
    state: CarUiState,
    phase: CarPhase.JoinRequest,
    commands: CarCommands,
    nowMillis: Long,
    modifier: Modifier = Modifier,
) {
    val join = phase.join
    TripFrame(state = state, nowMillis = nowMillis, modifier = modifier) {
        VStack(HeyGap.G24, arrange = HeyArrange.Center, modifier = Modifier.fillSize()) {
            VStack(HeyGap.G8) {
                HeyText(stringResource(R.string.trip_join_title), variant = HeyTextVariant.Hero, maxLines = 2)
            }
            val left = ((join.expiresAt - serverNow(nowMillis, state.clockOffsetMs)) / 1000).toInt().coerceAtLeast(0)
            val locked = state.joinInFlight
            JoinRequestCard(
                langLabel = stringResource(join.lang.labelRes()),
                partySize = join.partySize,
                expiresInSec = left,
                checkCode = join.checkCode,
                onApprove = { state.parked?.approveJoin(join.joinRequestId) },
                onDecline = { state.parked?.declineJoin(join.joinRequestId) },
                modifier = Modifier.fillWidth(),
                enabled = !locked && state.online,
            )
            ErrorBanner(state, Modifier.fillWidth())
        }
    }
}
