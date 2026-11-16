package ai.hey.designsystem.component

import ai.hey.designsystem.icon.HeyIcons
import androidx.compose.foundation.layout.height
import ai.hey.designsystem.theme.HeyTextVariant
import ai.hey.designsystem.theme.HeyGap
import ai.hey.designsystem.layout.HStack
import ai.hey.designsystem.layout.VStack
import ai.hey.designsystem.theme.HeyTheme
import ai.hey.designsystem.token.HeyTokens
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Round person mark of a friend (studio `.friends-avatar`: 88 on cards, 76 on message rows). */
@Composable
private fun FriendAvatar(size: androidx.compose.ui.unit.Dp = HeyTokens.Size.touchCar, iconSize: androidx.compose.ui.unit.Dp = 28.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(HeyTokens.Radius.full)).background(HeyTheme.colors.accentContainer),
        contentAlignment = Alignment.Center,
    ) { InkIcon(HeyIcons.User, null, HeyTheme.colors.fgDefault, Modifier.size(iconSize)) }
}

private val CardShape = RoundedCornerShape(32.dp)

/** Studio `.friends-screen h1`: 32/600, -0.04em, line 41.6. */
@Composable
fun FriendsTitle(text: String, modifier: Modifier = Modifier) {
    InkText(
        text, HeyTheme.colors.fgDefault, modifier, maxLines = 1,
        weight = FontWeight.SemiBold, trackingEm = -0.04f, metrics = HeyTextMetrics(32.sp, 41.6.sp),
    )
}

/** One white shortcut pill of the friends page (icon, label, optional [count] bubble, arrow); 28 side padding. */
@Composable
fun FriendShortcut(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, count: Int = 0) {
    val colors = HeyTheme.colors
    Row(
        modifier.clip(RoundedCornerShape(HeyTokens.Radius.full)).background(colors.bgRaised)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 28.dp, vertical = 27.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InkIcon(icon, null, colors.fgDefault, Modifier.size(28.dp))
        InkText(label, colors.fgDefault, maxLines = 1, metrics = HeyTextMetrics(28.sp, 42.sp))
        if (count > 0) {
            Box(
                Modifier.clip(RoundedCornerShape(HeyTokens.Radius.full)).background(colors.accentContainer).padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { InkText(count.toString(), colors.fgDefault, maxLines = 1, metrics = HeyTextMetrics(20.sp, 30.sp)) }
        }
        Box(Modifier.weight(1f))
        InkIcon(HeyIcons.ArrowRight, null, colors.fgDefault, Modifier.size(20.dp, 28.dp))
    }
}

/** The quiet one-line note under the friends list (22, muted). */
@Composable
fun FriendNote(text: String, modifier: Modifier = Modifier) {
    InkText(text, HeyTheme.colors.fgMuted, modifier, metrics = HeyTextMetrics(22.sp, 36.3.sp))
}

/**
 * One friend card of the studio list (two per row): 88 avatar, name 32/600, last message 24, optional [badge] line
 * (e.g. 도착 알리기 켬) and an arrow. Pressing it ([onClick]) opens that friend's past messages.
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
    Row(
        modifier.clip(CardShape).background(colors.bgRaised, CardShape)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 203.dp).padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FriendAvatar(88.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InkText(
                name, colors.fgDefault, maxLines = 1,
                weight = FontWeight.SemiBold, metrics = HeyTextMetrics(32.sp, 44.8.sp),
            )
            if (lastMessage != null) InkText(lastMessage, colors.fgDefault, maxLines = 2, metrics = HeyTextMetrics(24.sp, 36.sp))
            if (badge != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    InkIcon(HeyIcons.Check, null, colors.accent, Modifier.size(20.dp))
                    InkText(badge, colors.accent, maxLines = 1, metrics = HeyTextMetrics(20.sp, 30.sp))
                }
            }
        }
        Box(Modifier.size(48.dp, 88.dp), contentAlignment = Alignment.Center) {
            InkIcon(HeyIcons.ArrowRight, null, colors.fgDefault, Modifier.size(28.dp))
        }
    }
}

/** "내 친구 코드" body: the QR on a 340 white card and, to its right, the lead line, the 60/700 code and the validity note. */
@Composable
fun FriendCodePanel(content: String, code: String, lead: String, note: String, modifier: Modifier = Modifier) {
    val colors = HeyTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(100.dp, Alignment.CenterHorizontally)) {
        Box(Modifier.size(340.dp).clip(CardShape).background(colors.bgRaised, CardShape), contentAlignment = Alignment.Center) {
            HeyQrCode(content, 280.dp)
        }
        Column(Modifier.widthIn(max = 380.dp).padding(top = 52.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            InkText(lead, colors.fgDefault, maxLines = 2, metrics = HeyTextMetrics(28.sp, 46.2.sp))
            InkText(code, colors.fgDefault, maxLines = 1, weight = FontWeight.Bold, trackingEm = 0.15f, metrics = HeyTextMetrics(60.sp, 90.sp))
            InkText(note, colors.fgMuted, maxLines = 2, metrics = HeyTextMetrics(22.sp, 36.3.sp))
        }
    }
}

/**
 * The compact car page of the invite mockup (`car-friends-invite`): small header (back, "Hey", hairline, 22 state label),
 * a 52/600 [heading] at x64 and the [content] centred from y259. Drawn at fixed offsets like the mockup.
 */
@Composable
fun CompactCarPage(
    title: String,
    stateLabel: String,
    backLabel: String,
    onBack: () -> Unit,
    heading: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = HeyTheme.colors
    Box(modifier.fillMaxSize().background(colors.bgPage)) {
        Box(Modifier.offset(28.dp, 21.dp)) { HeyBackButton(backLabel, onBack, ghost = true) }
        Row(Modifier.offset(106.dp, 30.dp), verticalAlignment = Alignment.CenterVertically) {
            InkText(title, colors.fgDefault, maxLines = 1, weight = FontWeight.SemiBold, metrics = HeyTextMetrics(42.sp, 63.sp))
            Box(Modifier.padding(start = 11.dp, end = 10.dp).size(HeyTokens.Border.hairline, 40.dp).background(colors.borderDefault))
            InkText(stateLabel, colors.fgMuted, maxLines = 1, weight = FontWeight.Normal, metrics = HeyTextMetrics(22.sp, 33.sp))
        }
        InkText(
            heading, colors.fgDefault, Modifier.offset(64.dp, 145.dp), maxLines = 1,
            weight = FontWeight.SemiBold, trackingEm = -0.04f, metrics = HeyTextMetrics(52.sp, 67.6.sp),
        )
        Column(Modifier.fillMaxWidth().offset(y = 259.dp), horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

/** Compact pill of the invite mockup: 80 high, 24/500 label; [primary] is the filled accept button, else a white one. */
@Composable
fun FriendInviteButton(text: String, onClick: () -> Unit, primary: Boolean, modifier: Modifier = Modifier) {
    val colors = HeyTheme.colors
    val bg = if (primary) colors.primary else colors.bgRaised
    val ink = if (primary) colors.fgOnPrimary else colors.fgDefault
    Box(
        modifier.heightIn(min = 80.dp).clip(RoundedCornerShape(HeyTokens.Radius.full)).background(bg)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        InkText(text, ink, maxLines = 1, weight = FontWeight.Medium, metrics = HeyTextMetrics(24.sp, 33.6.sp))
    }
}

/** Empty half of a two-column friend row when the count is odd. */
@Composable
fun ai.hey.designsystem.layout.HeyStackScope.FriendSlotFill() = Box(Modifier.grow())

/** Vertical gap steps between the parts of [CompactCarPage]'s centred body (studio `.mk-copy` rhythm: 28 / 16 / 24), built from space tokens. */
enum class FriendGap(internal val size: androidx.compose.ui.unit.Dp) {
    Avatar(HeyTokens.Space.s24 + HeyTokens.Space.s4),
    Notice(HeyTokens.Space.s16),
    Block(HeyTokens.Space.s24),
}

/** Vertical gap between the parts of [CompactCarPage]'s centred body. */
@Composable
fun FriendSpacer(gap: FriendGap) = androidx.compose.foundation.layout.Spacer(Modifier.height(gap.size))

/** The big round person mark of the invite mockup (100, icon 32). */
@Composable
fun FriendBigAvatar(modifier: Modifier = Modifier) = Box(modifier) { FriendAvatar(100.dp, 32.dp) }

/** Centred 30/600 line and 26 line under [FriendBigAvatar] (studio `.mk-copy`). */
@Composable
fun FriendInviteCopy(title: String, message: String, modifier: Modifier = Modifier) {
    val colors = HeyTheme.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.dp)) {
        InkText(
            title, colors.fgDefault, maxLines = 2, align = HeyTextAlign.Center,
            weight = FontWeight.SemiBold, trackingEm = -0.025f, metrics = HeyTextMetrics(30.sp, 43.5.sp),
        )
        InkText(message, colors.fgDefault, maxLines = 3, align = HeyTextAlign.Center, weight = FontWeight.Normal, metrics = HeyTextMetrics(26.sp, 44.2.sp))
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

/** The full text of one received message (at most 120 characters), with the sender above and the time below. */
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
