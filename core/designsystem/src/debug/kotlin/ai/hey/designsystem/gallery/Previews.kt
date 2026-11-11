package ai.hey.designsystem.gallery

import ai.hey.designsystem.theme.HeyTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider

/** Every story is previewable in the IDE; the same stories are what Paparazzi records. */
class PartStories : PreviewParameterProvider<Story> {
    override val values: Sequence<Story> = Stories.parts.asSequence()
}

class ScreenStories : PreviewParameterProvider<Story> {
    override val values: Sequence<Story> = Stories.screens.filter { it.frame == StoryFrame.Screen1600 }.asSequence()
}

class CompactScreenStories : PreviewParameterProvider<Story> {
    override val values: Sequence<Story> = Stories.screens.filter { it.frame == StoryFrame.Screen1280 }.asSequence()
}

private const val CAR_1600 = "spec:width=1600dp,height=900dp,dpi=160,orientation=landscape"
private const val CAR_1280 = "spec:width=1280dp,height=720dp,dpi=160,orientation=landscape"
private const val PART = "spec:width=1200dp,height=900dp,dpi=160,orientation=landscape"

@Composable
private fun Show(dark: Boolean, story: Story) = HeyTheme(dark) { story.content() }

@Preview(name = "part dark", device = PART)
@Composable
fun PartDarkPreview(@PreviewParameter(PartStories::class) story: Story) = Show(true, story)

@Preview(name = "part light", device = PART)
@Composable
fun PartLightPreview(@PreviewParameter(PartStories::class) story: Story) = Show(false, story)

@Preview(name = "screen 1600 dark", device = CAR_1600)
@Composable
fun ScreenDarkPreview(@PreviewParameter(ScreenStories::class) story: Story) = Show(true, story)

@Preview(name = "screen 1600 light", device = CAR_1600)
@Composable
fun ScreenLightPreview(@PreviewParameter(ScreenStories::class) story: Story) = Show(false, story)

@Preview(name = "screen 1280 dark", device = CAR_1280)
@Composable
fun CompactScreenDarkPreview(@PreviewParameter(CompactScreenStories::class) story: Story) = Show(true, story)

@Preview(name = "screen 1280 light", device = CAR_1280)
@Composable
fun CompactScreenLightPreview(@PreviewParameter(CompactScreenStories::class) story: Story) = Show(false, story)
