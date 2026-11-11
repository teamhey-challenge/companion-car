package ai.hey.feature.settings

import ai.hey.designsystem.component.EmptyPanel
import ai.hey.designsystem.icon.HeyIcons
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    EmptyPanel(
        icon = HeyIcons.Menu,
        title = stringResource(R.string.settings_empty_title),
        modifier = modifier,
    )
}
