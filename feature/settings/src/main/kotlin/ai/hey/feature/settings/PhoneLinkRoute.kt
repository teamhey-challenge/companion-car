package ai.hey.feature.settings

import ai.hey.designsystem.component.PhoneLinkUi
import ai.hey.session.AccountFailure
import ai.hey.session.CompanionCommands
import ai.hey.session.CompanionState
import ai.hey.session.PairingUi
import ai.hey.session.SettingsAction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

/** How long "연결됐어요!" stays before the screen settles on the linked account. */
const val LINK_FLASH_MS = 2_500L

/**
 * Session state to what the phone-connection panel draws. A QR is drawn only while [parked] (§6.6: the QR is hidden while
 * moving); [flashing] = "연결됐어요!" is still on (set by the owner for [LINK_FLASH_MS] after a fresh link).
 */
fun phoneLinkUi(pairing: PairingUi, parked: Boolean, flashing: Boolean): PhoneLinkUi = when (pairing) {
    PairingUi.None, PairingUi.Creating -> PhoneLinkUi.Loading
    is PairingUi.Pending -> if (parked) PhoneLinkUi.Code(pairing.pairCode) else PhoneLinkUi.Loading
    is PairingUi.Claimed -> if (pairing.fresh && flashing) PhoneLinkUi.Flash(pairing.googleName) else PhoneLinkUi.Linked(pairing.googleName)
    PairingUi.Failed -> PhoneLinkUi.Offline
}

/**
 * `car/settings/phone` wired to the companion repository. The code is asked for while the screen is open and the car is
 * parked; when the car moves the code is dropped (QR hidden) and a new one is made when it is parked again.
 */
@Composable
fun PhoneLinkRoute(
    state: CompanionState,
    commands: CompanionCommands,
    parked: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(parked) {
        if (parked) commands.openPairing()
        onDispose { commands.closePairing() }
    }
    val pairing = state.pairing
    var flashing by remember { mutableStateOf(false) }
    val fresh = pairing is PairingUi.Claimed && pairing.fresh
    LaunchedEffect(fresh) {
        if (fresh) {
            flashing = true
            delay(LINK_FLASH_MS)
            flashing = false
        }
    }
    PhoneLinkScreen(
        ui = phoneLinkUi(pairing, parked, flashing),
        onBack = onBack,
        onUnpair = commands::unpair,
        onRetry = commands::openPairing,
        modifier = modifier,
        unpairing = state.settingsAction == SettingsAction.Unpairing,
        unpairFailed = state.accountFailure == AccountFailure.Unpair,
    )
}
