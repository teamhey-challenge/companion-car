package ai.hey.feature.memory

import ai.hey.designsystem.theme.HeyTheme
import androidx.compose.runtime.Composable
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import org.junit.Rule
import org.junit.Test

private fun device(width: Int, height: Int) = DeviceConfig.PIXEL_C.copy(
    screenWidth = width,
    screenHeight = height,
    xdpi = 160,
    ydpi = 160,
    density = Density.MEDIUM,
    orientation = ScreenOrientation.LANDSCAPE,
)

private val rows = listOf(
    MemoryRowUi("1", MemoryKindUi.Fact, "비 오는 날, 잔잔한 음악을 좋아해요.", "11월 13일"),
    MemoryRowUi("2", MemoryKindUi.Summary, "퇴근길에는 조용한 대화를 좋아해요.", "11월 12일"),
    MemoryRowUi("3", MemoryKindUi.Fact, "금요일에 중요한 발표가 있어요.", "11월 11일"),
)

private class Shot(val list: MemoryListUi, val notice: String? = null)

private val shots: List<Pair<String, Shot>> = listOf(
    "c6-memory" to Shot(MemoryListUi.Items(rows)),
    "c6-empty" to Shot(MemoryListUi.Empty(savingOff = false)),
    "c6-empty-off" to Shot(MemoryListUi.Empty(savingOff = true)),
    "c6-loading" to Shot(MemoryListUi.Loading),
    "c6-error" to Shot(MemoryListUi.Failed),
    "c6-delete-failed" to Shot(MemoryListUi.Items(rows), notice = "지우지 못했어요. 다시 시도해 주세요"),
)

@Composable
private fun Screen(shot: Shot) {
    MemoryScreen(list = shot.list, onBack = {}, onRetry = {}, onDelete = {}, onDeleteAll = {}, notice = shot.notice)
}

private fun Paparazzi.recordAll() {
    shots.forEach { (name, shot) ->
        listOf(true, false).forEach { dark ->
            snapshot("$name-${if (dark) "dark" else "light"}") { HeyTheme(dark) { Screen(shot) } }
        }
    }
}

class Memory1600SnapshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = device(1600, 900), renderingMode = RenderingMode.NORMAL)

    @Test fun screens() = paparazzi.recordAll()
}

class Memory1280SnapshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = device(1280, 720), renderingMode = RenderingMode.NORMAL)

    @Test fun screens() = paparazzi.recordAll()
}
