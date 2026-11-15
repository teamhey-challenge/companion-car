package ai.hey.feature.memory

import ai.hey.designsystem.theme.HeyTheme
import androidx.compose.runtime.Composable
import ai.hey.designsystem.testing.CarPaparazzi
import ai.hey.designsystem.testing.CarWindow
import org.junit.Rule
import org.junit.Test


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

private fun CarPaparazzi.recordAll() {
    shots.forEach { (name, shot) ->
        listOf(true, false).forEach { dark ->
            snapshot("$name-${if (dark) "dark" else "light"}") { HeyTheme(dark) { Screen(shot) } }
        }
    }
}

class Memory1600SnapshotTest {
    @get:Rule val paparazzi = CarPaparazzi(CarWindow.Full1600)

    @Test fun screens() = paparazzi.recordAll()
}

/** Pleos multi-window, 1/2 width (840 x 1212 dp) and 1/3 width (560 x 1212 dp); drawn at the 900 dp design height. */
class Memory840SnapshotTest {
    @get:Rule val paparazzi = CarPaparazzi(CarWindow.Half840)

    @Test fun screens() = paparazzi.recordAll()
}

class Memory560SnapshotTest {
    @get:Rule val paparazzi = CarPaparazzi(CarWindow.Third560)

    @Test fun screens() = paparazzi.recordAll()
}

class Memory1280SnapshotTest {
    @get:Rule val paparazzi = CarPaparazzi(CarWindow.Small1280)

    @Test fun screens() = paparazzi.recordAll()
}

/** The real Pleos app window (1682 x 1210 px = 1251 x 900 design dp). */
class Memory1251SnapshotTest {
    @get:Rule val paparazzi = CarPaparazzi(CarWindow.Car1251)

    @Test fun screens() = paparazzi.recordAll()
}
