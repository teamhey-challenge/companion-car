package ai.hey.designsystem.component

import ai.hey.designsystem.icon.HeyIcons
import ai.hey.designsystem.layout.HStack
import ai.hey.designsystem.layout.VStack
import ai.hey.designsystem.theme.HeyGap
import ai.hey.designsystem.theme.HeyTextVariant
import ai.hey.designsystem.theme.HeyTheme
import ai.hey.designsystem.token.HeyTokens
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role

/** Round person mark of a friend; a fixed 76dp so every row in a list lines up. */
@Composable
private fun FriendAvatar() {
    Box(
        Modifier.size(HeyTokens.Size.touchCar).clip(RoundedCornerShape(HeyTokens.Radius.full)).background(HeyTheme.colors.accentContainer),
        contentAlignment = Alignment.Center,
    ) { InkIcon(HeyIcons.User, null, HeyTheme.colors.fgDefault) }
}

/**
 * One friend of the read-only car list (D-47): avatar, name, the last thing they sent ([lastMessage], one line) and a
 * status pill ([badge], e.g. 도착 알림 켜짐). Pressing it ([onClick]) opens that friend's past messages. Parent owns the margin.
 */
@Composable
fun FriendRow(
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    lastMessage: String? = null,
    badge: String? = null,
) {
    val colors = HeyTheme.colors
    val shape = RoundedCornerShape(HeyTokens.Radius.md)
    HStack(
        gap = HeyGap.G16,
        modifier = modifier
            .clip(shape)
            .background(colors.bgRaised, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = HeyTokens.Size.touchCarPrimary)
            .padding(HeyTokens.Space.s24),
    ) {
        FriendAvatar()
        VStack(HeyGap.G4, modifier = Modifier.grow()) {
            InkText(name, colors.fgDefault, variant = HeyTextVariant.Heading, maxLines = 1)
            if (lastMessage != null) InkText(lastMessage, colors.fgMuted, maxLines = 1)
        }
        if (badge != null) HeyStatusPill(badge, HeyStatusTone.Success, icon = HeyIcons.Check)
    }
}

/**
 * One received message in the inbox list: who, the first line, when. The whole row is the press target ([onClick]) and
 * opens the message card. Shown while parked only.
 */
@Composable
fun MessageRow(
    sender: String,
    preview: String,
    time: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HeyTheme.colors
    val shape = RoundedCornerShape(HeyTokens.Radius.md)
    HStack(
        gap = HeyGap.G16,
        modifier = modifier
            .clip(shape)
            .background(colors.bgRaised, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = HeyTokens.Size.touchCarPrimary)
            .padding(HeyTokens.Space.s24),
    ) {
        FriendAvatar()
        VStack(HeyGap.G4, modifier = Modifier.grow()) {
            HStack(HeyGap.G16, modifier = Modifier.fillMaxWidth()) {
                InkText(sender, colors.fgDefault, Modifier.grow(), variant = HeyTextVariant.Heading, maxLines = 1)
                InkText(time, colors.fgMuted, variant = HeyTextVariant.Caption, maxLines = 1)
            }
            InkText(preview, colors.fgMuted, maxLines = 1)
        }
        InkIcon(HeyIcons.ChevronRight, null, colors.fgMuted)
    }
}

/** The full text of one received message (D-53: at most 120 characters), with the sender above and the time below. */
@Composable
fun MessageCard(
    sender: String,
    text: String,
    time: String,
    modifier: Modifier = Modifier,
) {
    val colors = HeyTheme.colors
    val shape = RoundedCornerShape(HeyTokens.Radius.lg)
    VStack(
        gap = HeyGap.G16,
        modifier = modifier.clip(shape).background(colors.bgRaised, shape).padding(HeyTokens.Space.s32),
    ) {
        InkText(sender, colors.fgMuted, variant = HeyTextVariant.Heading, maxLines = 1)
        InkText(text, colors.fgDefault, variant = HeyTextVariant.Title, maxLines = 6)
        InkText(time, colors.fgMuted, variant = HeyTextVariant.Caption, maxLines = 1)
    }
}
