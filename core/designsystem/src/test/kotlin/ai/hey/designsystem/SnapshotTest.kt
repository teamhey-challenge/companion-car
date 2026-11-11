package ai.hey.designsystem

import ai.hey.designsystem.gallery.Stories
import ai.hey.designsystem.gallery.Story
import ai.hey.designsystem.gallery.StoryFrame
import ai.hey.designsystem.theme.HeyTheme
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import org.junit.Rule
import org.junit.Test

private fun device(frame: StoryFrame) = DeviceConfig.PIXEL_C.copy(
    screenWidth = frame.widthDp,
    screenHeight = frame.heightDp,
    xdpi = 160,
    ydpi = 160,
    density = Density.MEDIUM,
    orientation = ScreenOrientation.LANDSCAPE,
)

private fun Paparazzi.record(stories: List<Story>) {
    stories.forEach { story ->
        listOf(true, false).forEach { dark ->
            snapshot("${story.id}-${if (dark) "dark" else "light"}") {
                HeyTheme(dark) { story.content() }
            }
        }
    }
}

/** 1600x900dp at 160dpi: the reference car display. */
class Screen1600SnapshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = device(StoryFrame.Screen1600), renderingMode = RenderingMode.NORMAL)

    @Test fun screens() = paparazzi.record(Stories.screens.filter { it.frame == StoryFrame.Screen1600 })
}

/** 1280x720dp must not break either. */
class Screen1280SnapshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = device(StoryFrame.Screen1280), renderingMode = RenderingMode.NORMAL)

    @Test fun screens() = paparazzi.record(Stories.screens.filter { it.frame == StoryFrame.Screen1280 })
}

/** Parts are measured to their own height. */
class PartSnapshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = device(StoryFrame.Part), renderingMode = RenderingMode.SHRINK)

    @Test fun parts() = paparazzi.record(Stories.parts)
}
