package ai.hey.feature.settings

import ai.hey.designsystem.component.CarFrame
import ai.hey.designsystem.component.CharacterLook
import ai.hey.designsystem.component.CharacterSize
import ai.hey.designsystem.component.CharacterSlot
import ai.hey.designsystem.component.ChoiceRow
import ai.hey.designsystem.component.HeyText
import ai.hey.designsystem.layout.VStack
import ai.hey.designsystem.theme.HeyTextVariant
import ai.hey.designsystem.theme.HeyTheme
import ai.hey.designsystem.component.ConfirmDialog
import ai.hey.designsystem.component.HeaderBar
import ai.hey.designsystem.component.HeyButton
import ai.hey.designsystem.component.HeyButtonVariant
import ai.hey.designsystem.component.HeyKeyboard
import ai.hey.designsystem.component.HeyLayered
import ai.hey.designsystem.component.HeyNotice
import ai.hey.designsystem.component.HeyScrollColumn
import ai.hey.designsystem.component.HeySegmentOption
import ai.hey.designsystem.component.HeyStatusTone
import ai.hey.designsystem.component.HeyTextField
import ai.hey.designsystem.component.PageHeading
import ai.hey.designsystem.component.ToggleRow
import ai.hey.designsystem.component.ToggleSurface
import ai.hey.designsystem.icon.HeyIcons
import ai.hey.designsystem.layout.HStack
import ai.hey.designsystem.layout.HeyAlign
import ai.hey.designsystem.layout.fillSize
import ai.hey.designsystem.layout.fillWidth
import ai.hey.designsystem.theme.HeyGap
import ai.hey.designsystem.theme.HeyTone
import ai.hey.designsystem.component.QrPanel
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay

private const val PENDING_GRACE_MS = 250L

/** 먼저 말 걸기 단계. Off hides the frequency chips; the minutes are the contract values (20 / 10 / 5). */
enum class ProactiveUi { Off, Low, Normal, High }

enum class SpeechUi { Banmal, Jondaetmal }

/** A privacy action (withdraw) whose request failed: the screen says so and offers the same action again. */
enum class AccountFailureUi { None, Withdraw }

/** The C7 account request that is in flight. */
enum class SettingsPending { None, Withdraw }

/** The settings fields that can have a save in flight (each saves on its own, PRD 4.7.5 Partial = n/a). */
enum class SettingField { Proactive, Memory, Speech, Look }

/** One friend on C7: read-only except the 도착 알리기 switch. */
data class FriendRowUi(val id: String, val name: String, val shareArrival: Boolean, val saving: Boolean = false)

/** "내 친구 코드" panel: the QR carries [Shown.url], the text is the 6-character [Shown.code]. */
sealed interface FriendCodeUi {
    data object None : FriendCodeUi
    data object Creating : FriendCodeUi
    data class Shown(val code: String, val url: String) : FriendCodeUi
    data object Failed : FriendCodeUi
}

data class SettingsUi(
    val proactive: ProactiveUi = ProactiveUi.Normal,
    val memoryEnabled: Boolean = true,
    val speech: SpeechUi = SpeechUi.Banmal,
    /** A phone is linked: the 폰 연결 row says so (the unlink itself lives on the phone-connection screen). */
    val phonePaired: Boolean = false,
    /** Fields whose save request has been running; the toggle shows work after 2 s, the screen blocks that field. */
    val saving: Set<SettingField> = emptySet(),
    val accountFailure: AccountFailureUi = AccountFailureUi.None,
    /** The confirmed unpair/withdraw request that is still running (its dialog shows work; the retry button waits). */
    val pending: SettingsPending = SettingsPending.None,
    /** What the character wears (D-41); the 꾸미기 section previews and edits it. */
    val look: CharacterLook = CharacterLook(),
    val friends: List<FriendRowUi> = emptyList(),
    val friendsFailed: Boolean = false,
    val friendCode: FriendCodeUi = FriendCodeUi.None,
)

/** The debug-only server address field; null in release builds, which never show it. */
class ServerAddress(val saved: String, val onApply: (String) -> Unit)

/** The confirmation that is open; the screen owns it, the caller learns only the final answer. */
private enum class Confirm { None, Withdraw }

/**
 * C7 (PRD 4.7). Every change goes to [onChange] at once; a failed save is reported by the caller as [notice] and as the
 * old value coming back in [settings]. Dialogs: unpair (memory stays) and withdraw (memory is deleted at once).
 * The theme is not a setting: it follows the system.
 */
@Composable
fun SettingsScreen(
    settings: SettingsUi,
    onBack: () -> Unit,
    onProactive: (ProactiveUi) -> Unit,
    onMemoryEnabled: (Boolean) -> Unit,
    onSpeech: (SpeechUi) -> Unit,
    onLook: (CharacterLook) -> Unit,
    onOpenMemory: () -> Unit,
    /** 폰 연결 opens the phone-connection screen (D-33), linked or not. */
    onOpenPair: () -> Unit,
    onWithdraw: () -> Unit,
    modifier: Modifier = Modifier,
    notice: String? = null,
    onFriendCode: () -> Unit = {},
    onCloseFriendCode: () -> Unit = {},
    onShareArrival: (id: String, share: Boolean) -> Unit = { _, _ -> },
    server: ServerAddress? = null,
) {
    var confirm by remember { mutableStateOf(Confirm.None) }
    // A confirmed request keeps its dialog open (spinner, buttons blocked) until it ends. A fast request can start and end
    // between two frames, so the dialog also closes after a short wait when "pending" was never seen.
    var submitted by remember { mutableStateOf(false) }
    LaunchedEffect(submitted, settings.pending) {
        if (submitted && settings.pending == SettingsPending.None) {
            delay(PENDING_GRACE_MS)
            submitted = false
            confirm = Confirm.None
        }
    }
    HeyLayered(
        modifier = modifier.fillSize(),
        overlay = {
            when (confirm) {
                Confirm.Withdraw -> ConfirmDialog(
                    title = stringResource(R.string.settings_withdraw_title),
                    message = stringResource(R.string.settings_withdraw_message),
                    confirmText = stringResource(R.string.settings_withdraw_confirm),
                    dismissText = stringResource(R.string.settings_cancel),
                    onConfirm = { submitted = true; onWithdraw() },
                    busy = settings.pending == SettingsPending.Withdraw,
                    onDismiss = { confirm = Confirm.None },
                    modifier = Modifier.fillSize(),
                    danger = true,
                )
                Confirm.None -> Unit
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
            HeyScrollColumn(HeyGap.G32, modifier = Modifier.fillSize()) {
                PageHeading(stringResource(R.string.settings_title), subtitle = stringResource(R.string.settings_subtitle))
                // The save-failure line sits in the page flow: the header and the back button stay visible.
                if (notice != null) HeyNotice(notice, HeyStatusTone.Danger, persistent = true)
                if (settings.accountFailure != AccountFailureUi.None) {
                    HStack(HeyGap.G16, align = HeyAlign.Center) {
                        HeyNotice(stringResource(R.string.settings_withdraw_failed), HeyStatusTone.Danger, persistent = true)
                        HeyButton(
                            stringResource(R.string.settings_retry),
                            onWithdraw,
                            variant = HeyButtonVariant.Secondary,
                            icon = HeyIcons.Refresh,
                            enabled = settings.pending == SettingsPending.None,
                        )
                    }
                }
                LookSection(settings.look, onLook, enabled = SettingField.Look !in settings.saving)
                FriendsSection(settings, onFriendCode, onCloseFriendCode, onShareArrival)
                val proactiveSaving = SettingField.Proactive in settings.saving
                ToggleRow(
                    label = stringResource(R.string.settings_proactive),
                    description = stringResource(R.string.settings_proactive_desc),
                    checked = settings.proactive != ProactiveUi.Off,
                    // Turning it back on returns to the usual step, not to a step the driver never picked.
                    onCheckedChange = { on -> onProactive(if (on) ProactiveUi.Normal else ProactiveUi.Off) },
                    saving = proactiveSaving,
                    surface = ToggleSurface.Info,
                    modifier = Modifier.fillWidth(),
                )
                if (settings.proactive != ProactiveUi.Off) {
                    ChoiceRow(
                        options = listOf(
                            HeySegmentOption(ProactiveUi.Low, stringResource(R.string.settings_freq_low)),
                            HeySegmentOption(ProactiveUi.Normal, stringResource(R.string.settings_freq_normal)),
                            HeySegmentOption(ProactiveUi.High, stringResource(R.string.settings_freq_high)),
                        ),
                        selected = settings.proactive,
                        onSelect = onProactive,
                        label = stringResource(R.string.settings_freq),
                        enabled = !proactiveSaving,
                        modifier = Modifier.fillWidth(),
                    )
                }
                ToggleRow(
                    label = stringResource(R.string.settings_memory),
                    description = stringResource(R.string.settings_memory_desc),
                    checked = settings.memoryEnabled,
                    onCheckedChange = onMemoryEnabled,
                    saving = SettingField.Memory in settings.saving,
                    surface = ToggleSurface.Plain,
                    modifier = Modifier.fillWidth(),
                )
                ChoiceRow(
                    options = listOf(
                        HeySegmentOption(SpeechUi.Banmal, stringResource(R.string.settings_speech_banmal)),
                        HeySegmentOption(SpeechUi.Jondaetmal, stringResource(R.string.settings_speech_jondaetmal)),
                    ),
                    selected = settings.speech,
                    onSelect = onSpeech,
                    label = stringResource(R.string.settings_speech),
                    enabled = SettingField.Speech !in settings.saving,
                    modifier = Modifier.fillWidth(),
                )
                // 폰 연결 is the last settings item (§2): the row opens `car/settings/phone` (QR, or the linked account).
                HStack(HeyGap.G16, align = HeyAlign.Center) {
                    HeyButton(
                        stringResource(R.string.settings_open_memory),
                        onOpenMemory,
                        variant = HeyButtonVariant.Soft,
                        icon = HeyIcons.Book,
                    )
                    HeyButton(
                        stringResource(if (settings.phonePaired) R.string.settings_phone_linked else R.string.settings_pair),
                        onOpenPair,
                        variant = HeyButtonVariant.Soft,
                        icon = if (settings.phonePaired) HeyIcons.Link else HeyIcons.Qr,
                    )
                }
                if (server != null) ServerSetting(server.saved, server.onApply)
                HeyButton(
                    stringResource(R.string.settings_withdraw),
                    { confirm = Confirm.Withdraw },
                    variant = HeyButtonVariant.Danger,
                    icon = HeyIcons.Trash,
                )
            }
        }
    }
}

/** 친구 (parked only like all of C7): own invite code with QR, read-only list, per-friend 도착 알리기. */
@Composable
internal fun FriendsSection(
    settings: SettingsUi,
    onFriendCode: () -> Unit,
    onCloseFriendCode: () -> Unit,
    onShareArrival: (String, Boolean) -> Unit,
) {
    VStack(HeyGap.G16, modifier = Modifier.fillWidth()) {
        HeyText(stringResource(R.string.settings_friends), variant = HeyTextVariant.Heading)
        when (val code = settings.friendCode) {
            FriendCodeUi.None -> HeyButton(
                stringResource(R.string.settings_friend_code),
                onFriendCode,
                variant = HeyButtonVariant.Soft,
                icon = HeyIcons.Qr,
            )
            FriendCodeUi.Creating -> HeyText(stringResource(R.string.settings_friend_code_creating), tone = HeyTone.Muted)
            FriendCodeUi.Failed -> {
                HeyNotice(stringResource(R.string.settings_friend_code_failed), HeyStatusTone.Danger, persistent = true)
                HStack(HeyGap.G16, align = HeyAlign.Center) {
                    HeyButton(stringResource(R.string.settings_retry), onFriendCode, variant = HeyButtonVariant.Secondary, icon = HeyIcons.Refresh)
                    HeyButton(stringResource(R.string.settings_friend_code_close), onCloseFriendCode, variant = HeyButtonVariant.Soft)
                }
            }
            is FriendCodeUi.Shown -> {
                QrPanel(
                    content = code.url,
                    code = code.code,
                    status = stringResource(R.string.settings_friend_code_status),
                    modifier = Modifier.fillWidth(),
                )
                HeyButton(stringResource(R.string.settings_friend_code_close), onCloseFriendCode, variant = HeyButtonVariant.Soft)
            }
        }
        if (settings.friendsFailed) HeyNotice(stringResource(R.string.settings_friends_failed), HeyStatusTone.Danger, persistent = true)
        if (settings.friends.isEmpty() && !settings.friendsFailed) {
            HeyText(stringResource(R.string.settings_friend_none), tone = HeyTone.Muted)
        }
        settings.friends.forEach { f ->
            ToggleRow(
                label = f.name,
                description = stringResource(R.string.settings_friend_arrival_desc, f.name),
                checked = f.shareArrival,
                onCheckedChange = { onShareArrival(f.id, it) },
                saving = f.saving,
                surface = ToggleSurface.Secondary,
                modifier = Modifier.fillWidth(),
            )
        }
    }
}

/**
 * 꾸미기 (D-41, parked only like all of C7): the Rive file's two bodies, two hats and the scarf, picked with the same choice
 * pills as the other settings and previewed on the still of exactly that combination. No shop, no payment.
 */
@Composable
internal fun LookSection(look: CharacterLook, onLook: (CharacterLook) -> Unit, enabled: Boolean) {
    val dark = HeyTheme.dark
    VStack(HeyGap.G16, modifier = Modifier.fillWidth()) {
        HeyText(stringResource(R.string.settings_look), variant = HeyTextVariant.Heading)
        HStack(HeyGap.G32, align = HeyAlign.Center, modifier = Modifier.fillWidth()) {
            CharacterSlot(
                size = CharacterSize.Small,
                look = look,
                contentDescription = stringResource(R.string.settings_look_preview),
            )
            VStack(HeyGap.G16, modifier = Modifier.grow()) {
                ChoiceRow(
                    options = listOf(
                        HeySegmentOption(0, stringResource(R.string.settings_look_body_aqua)),
                        HeySegmentOption(1, stringResource(R.string.settings_look_body_peach)),
                    ),
                    selected = look.body(dark),
                    onSelect = { onLook(look.copy(bodyColor = it)) },
                    label = stringResource(R.string.settings_look_body),
                    enabled = enabled,
                    modifier = Modifier.fillWidth(),
                )
                ChoiceRow(
                    options = listOf(
                        HeySegmentOption(0, stringResource(R.string.settings_look_none)),
                        HeySegmentOption(1, stringResource(R.string.settings_look_hat_beanie)),
                        HeySegmentOption(2, stringResource(R.string.settings_look_hat_bucket)),
                    ),
                    selected = look.hat,
                    onSelect = { onLook(look.copy(hat = it)) },
                    label = stringResource(R.string.settings_look_hat),
                    enabled = enabled,
                    modifier = Modifier.fillWidth(),
                )
                ChoiceRow(
                    options = listOf(
                        HeySegmentOption(0, stringResource(R.string.settings_look_none)),
                        HeySegmentOption(1, stringResource(R.string.settings_look_neck_scarf)),
                    ),
                    selected = look.neck,
                    onSelect = { onLook(look.copy(neck = it)) },
                    label = stringResource(R.string.settings_look_neck),
                    enabled = enabled,
                    modifier = Modifier.fillWidth(),
                )
            }
        }
    }
}

/** http(s) with a host; anything else is rejected before it can trigger a connection. */
internal fun validServer(value: String): Boolean {
    val rest = when {
        value.startsWith("http://") -> value.removePrefix("http://")
        value.startsWith("https://") -> value.removePrefix("https://")
        else -> return false
    }
    val host = rest.substringBefore('/').substringBefore(':')
    return host.length >= 3 && host.all { it.isLetterOrDigit() || it == '.' || it == '-' }
}

@Composable
private fun ServerSetting(saved: String, onApply: (String) -> Unit) {
    var draft by remember(saved) { mutableStateOf(saved) }
    val value = draft.trim().trimEnd('/')
    HStack(HeyGap.G16, align = HeyAlign.End, modifier = Modifier.fillWidth()) {
        HeyTextField(
            label = stringResource(R.string.settings_server),
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.grow(),
            keyboard = HeyKeyboard.Uri,
            placeholder = stringResource(R.string.settings_server_hint),
            error = if (validServer(value)) null else stringResource(R.string.settings_server_error),
        )
        HeyButton(
            stringResource(R.string.settings_server_apply),
            { onApply(value) },
            variant = HeyButtonVariant.Secondary,
            enabled = validServer(value) && value != saved,
        )
    }
}
