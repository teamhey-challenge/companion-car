package ai.hey.feature.home.temp

import ai.hey.designsystem.component.HeyBanner
import ai.hey.designsystem.component.HeyBannerAction
import ai.hey.designsystem.component.HeyConnection
import ai.hey.designsystem.component.HeyStatusPill
import ai.hey.designsystem.component.HeyStatusTone
import ai.hey.designsystem.component.HeyText
import ai.hey.designsystem.component.TalkPhase
import ai.hey.designsystem.theme.HeyTextVariant
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// TEMPORARY until CarDS PR2a (StatusBadge, ErrorPanel, Subtitle). Same names and signatures; deleted when it merges.

enum class ErrorKind { Offline, Connecting, Microphone, Server, Recognition, Audio }

@Composable
fun StatusBadge(
    phase: TalkPhase,
    modifier: Modifier = Modifier,
    driving: Boolean = false,
    connection: HeyConnection = HeyConnection.Online,
    animated: Boolean = true,
) {
    val text = when {
        connection == HeyConnection.Reconnecting -> "연결 중…"
        connection == HeyConnection.Offline -> "오프라인"
        phase == TalkPhase.Listening -> "듣는 중"
        phase == TalkPhase.Thinking -> "생각 중"
        phase == TalkPhase.Speaking -> "말하는 중"
        else -> return
    }
    HeyStatusPill(text, HeyStatusTone.Info, modifier)
}

@Composable
fun ErrorPanel(
    kind: ErrorKind,
    onAction: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    title: String? = null,
    message: String? = null,
) {
    val (t, m, a) = when (kind) {
        ErrorKind.Offline -> Triple("잠시 연결이 끊겼어요", "연결을 확인한 뒤 다시 이야기해요.", "다시 연결")
        ErrorKind.Connecting -> Triple("다시 연결하고 있어요", "잠시만 기다려 주세요. 연결되면 이야기를 이어가요.", "연결 중")
        ErrorKind.Microphone -> Triple("마이크 권한이 있어야 대화할 수 있어요", "권한을 열면 바로 이야기할 수 있어요.", "권한 열기")
        ErrorKind.Server -> Triple("지금은 대답하지 못해요", "잠시 뒤에 다시 이야기해요.", "다시 시도")
        ErrorKind.Recognition -> Triple("잘 못 들었어요", "다시 말해 줄래요?", "다시 말하기")
        ErrorKind.Audio -> Triple("소리를 낼 수 없어요", "스피커를 확인한 뒤 다시 해 봐요.", "다시 시도")
    }
    HeyBanner(
        tone = HeyStatusTone.Danger,
        title = title ?: t,
        message = message ?: m,
        modifier = modifier,
        actions = if (onAction == null) emptyList() else listOf(HeyBannerAction(actionLabel ?: a, onAction, primary = true)),
    )
}

@Composable
fun Subtitle(text: String, modifier: Modifier = Modifier, driving: Boolean = false, large: Boolean = true) {
    if (driving) return
    HeyText(text, modifier, variant = if (large) HeyTextVariant.Heading else HeyTextVariant.Body, maxLines = 3)
}
