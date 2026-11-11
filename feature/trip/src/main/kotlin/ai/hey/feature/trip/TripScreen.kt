package ai.hey.feature.trip

import ai.hey.designsystem.component.EmptyPanel
import ai.hey.designsystem.icon.HeyIcons
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

@Composable
fun TripScreen(modifier: Modifier = Modifier) {
    EmptyPanel(
        icon = HeyIcons.Car,
        title = stringResource(R.string.trip_empty_title),
        modifier = modifier,
    )
}
