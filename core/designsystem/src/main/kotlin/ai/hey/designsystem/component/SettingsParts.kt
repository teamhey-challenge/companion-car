package ai.hey.designsystem.component

import ai.hey.designsystem.icon.HeyIcons
import ai.hey.designsystem.layout.HStack
import ai.hey.designsystem.theme.HeyGap
import ai.hey.designsystem.theme.HeyTheme
import ai.hey.designsystem.token.HeyTokens
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * The switch of a studio list card (`.mk-toggle`, 58x34, 5 padding): it sits in the trailing slot of a [HeyCardRow] and carries
 * the row's [label] for accessibility. While [saving] it shows a spinner and takes no input.
 */
@Composable
fun HeyCardSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    saving: Boolean = false,
) {
    val colors = HeyTheme.colors
    val track = when {
        !enabled -> colors.bgSunken
        checked -> colors.accent
        else -> colors.borderStrong
    }
    HStack(HeyGap.G16, modifier = modifier) {
        if (saving) HeySpinner(colors.fgMuted)
        Box(
            Modifier
                .size(width = 58.dp, height = 34.dp)
                .clip(RoundedCornerShape(HeyTokens.Radius.full))
                .background(track)
                .semantics { contentDescription = label }
                .toggleable(
                    value = checked,
                    enabled = enabled && !saving,
                    role = Role.Switch,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onValueChange = onCheckedChange,
                )
                .padding(5.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(Modifier.size(24.dp).clip(RoundedCornerShape(HeyTokens.Radius.full)).background(colors.bgPage))
        }
    }
}

/** The 76dp round face of a list card (`.mk-face`): the character still of [look] on the info container. */
@Composable
fun HeyFaceBadge(look: CharacterLook, modifier: Modifier = Modifier) {
    Box(
        modifier.size(76.dp).clip(RoundedCornerShape(HeyTokens.Radius.full)).background(HeyTheme.colors.infoContainer),
        contentAlignment = Alignment.Center,
    ) {
        Image(characterStill(look), null, Modifier.fillMaxSize().padding(6.dp), contentScale = ContentScale.Fit)
    }
}

/** The 28dp chevron at the end of a list card that opens something. */
@Composable
fun HeyCardChevron(modifier: Modifier = Modifier) {
    InkIcon(HeyIcons.ChevronRight, null, HeyTheme.colors.fgMuted, modifier)
}
