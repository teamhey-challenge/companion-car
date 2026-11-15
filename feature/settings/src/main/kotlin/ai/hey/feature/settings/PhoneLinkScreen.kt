package ai.hey.feature.settings

import ai.hey.designsystem.component.CarFrame
import ai.hey.designsystem.component.ConfirmDialog
import ai.hey.designsystem.component.HeaderBar
import ai.hey.designsystem.component.HeyButton
import ai.hey.designsystem.component.HeyButtonVariant
import ai.hey.designsystem.component.HeyLayered
import ai.hey.designsystem.component.HeyNotice
import ai.hey.designsystem.component.HeyScrollColumn
import ai.hey.designsystem.component.HeyStatusTone
import ai.hey.designsystem.component.PageHeading
import ai.hey.designsystem.component.PhoneLinkPanel
import ai.hey.designsystem.component.PhoneLinkCodeCard
import ai.hey.designsystem.component.PhoneLinkUi
import ai.hey.designsystem.layout.HeyArrange
import ai.hey.designsystem.layout.fillWidth
import ai.hey.designsystem.icon.HeyIcons
import ai.hey.designsystem.layout.HStack
import ai.hey.designsystem.layout.HeyAlign
import ai.hey.designsystem.layout.HeyWindow
import ai.hey.designsystem.layout.VStack
import ai.hey.designsystem.layout.fillSize
import ai.hey.designsystem.layout.isCompactWindow
import ai.hey.designsystem.theme.HeyGap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/**
 * `car/settings/phone`: QR + the same code as text, the linked Google account and "연결 해제". Parked only; the owner passes a
 * [PhoneLinkUi.Code] only while the car is parked, so no QR is ever drawn while moving (§6.6).
 * [unpairing] = the unlink request is running (its dialog shows work); [unpairFailed] = the last one failed.
 */
@Composable
fun PhoneLinkScreen(
    ui: PhoneLinkUi,
    onBack: () -> Unit,
    onUnpair: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    unpairing: Boolean = false,
    unpairFailed: Boolean = false,
) {
    var confirm by remember { mutableStateOf(false) }
    // The dialog closes when the link is gone (the screen is then on a fresh code) or the request failed (the notice says so).
    if (confirm && !unpairing && (ui !is PhoneLinkUi.Linked || unpairFailed)) confirm = false
    HeyLayered(
        modifier = modifier.fillSize(),
        overlay = {
            if (confirm) {
                ConfirmDialog(
                    title = stringResource(R.string.settings_unpair_title),
                    message = stringResource(R.string.settings_unpair_message),
                    confirmText = stringResource(R.string.settings_unpair_confirm),
                    dismissText = stringResource(R.string.settings_cancel),
                    onConfirm = onUnpair,
                    onDismiss = { confirm = false },
                    busy = unpairing,
                    modifier = Modifier.fillSize(),
                )
            }
        },
    ) {
        CarFrame(
            statusStrip = {
                HeaderBar(
                    title = stringResource(R.string.settings_app_name),
                    onBack = onBack,
                    backLabel = stringResource(R.string.settings_back),
                    stateLabel = stringResource(R.string.settings_parked),
                )
            },
            driving = false,
        ) {
            HeyWindow(Modifier.fillSize()) { width, height ->
                val compact = isCompactWindow(width, height)
                val heading: @Composable () -> Unit = {
                    PageHeading(stringResource(R.string.phone_title), subtitle = stringResource(R.string.phone_subtitle))
                }
                val actions: @Composable () -> Unit = { PhoneLinkActions(ui, unpairFailed, onRetry) { confirm = true } }
                if (compact) {
                    HeyScrollColumn(HeyGap.G24, modifier = Modifier.fillSize()) {
                        heading()
                        PhoneLinkPanel(ui, compact = true)
                        actions()
                    }
                } else if (ui is PhoneLinkUi.Code) {
                    // Studio `car/settings/phone`: the title alone, then the QR card centred in the rest of the page.
                    VStack(HeyGap.G24, modifier = Modifier.fillSize()) {
                        PageHeading(stringResource(R.string.phone_title))
                        VStack(HeyGap.G24, modifier = Modifier.grow(1f).fillWidth(), arrange = HeyArrange.Center) { PhoneLinkCodeCard(ui.code, onRetry) }
                    }
                } else {
                    HStack(HeyGap.G32, modifier = Modifier.fillSize(), align = HeyAlign.Center) {
                        VStack(HeyGap.G24, modifier = Modifier.grow(1f)) {
                            heading()
                            actions()
                        }
                        PhoneLinkPanel(ui, modifier = Modifier.grow(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneLinkActions(ui: PhoneLinkUi, unpairFailed: Boolean, onRetry: () -> Unit, onAskUnpair: () -> Unit) {
    VStack(HeyGap.G16) {
        if (unpairFailed) HeyNotice(stringResource(R.string.settings_unpair_failed), HeyStatusTone.Danger, persistent = true)
        when (ui) {
            is PhoneLinkUi.Linked -> HeyButton(
                stringResource(R.string.settings_unpair), onAskUnpair,
                variant = HeyButtonVariant.Soft, icon = HeyIcons.Link,
            )
            PhoneLinkUi.Offline -> HeyButton(
                stringResource(R.string.settings_retry), onRetry,
                variant = HeyButtonVariant.Secondary, icon = HeyIcons.Refresh,
            )
            else -> Unit
        }
    }
}
