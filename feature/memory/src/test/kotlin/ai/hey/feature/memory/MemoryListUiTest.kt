package ai.hey.feature.memory

import ai.hey.model.CompanionSettings
import ai.hey.model.MemoryItem
import ai.hey.model.MemoryKind
import ai.hey.session.CompanionState
import ai.hey.session.MemoryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class MemoryListUiTest {
    private val items = listOf(
        MemoryItem("a", MemoryKind.Fact, "비 오는 날 음악", "2026-11-13T09:00:00Z"),
        MemoryItem("b", MemoryKind.Summary, "퇴근길 대화", "2026-11-12T09:00:00Z"),
    )

    private fun state(m: MemoryState, memoryEnabled: Boolean = true) =
        CompanionState(memory = m, settings = CompanionSettings(memoryEnabled = memoryEnabled))

    @Test fun `not read yet is loading, never empty`() {
        assertEquals(MemoryListUi.Loading, memoryListUi(state(MemoryState())))
        assertEquals(MemoryListUi.Loading, memoryListUi(state(MemoryState(loading = true))))
    }

    @Test fun `read and empty is empty, with the saving-off hint only when saving is off`() {
        assertEquals(MemoryListUi.Empty(false), memoryListUi(state(MemoryState(loaded = true))))
        assertEquals(MemoryListUi.Empty(true), memoryListUi(state(MemoryState(loaded = true), memoryEnabled = false)))
    }

    @Test fun `failed first load is the error state`() {
        assertEquals(MemoryListUi.Failed, memoryListUi(state(MemoryState(failed = true))))
    }

    @Test fun `failed next page keeps the rows already read`() {
        val ui = memoryListUi(state(MemoryState(items = items, failed = true, loaded = true)))
        assertTrue(ui is MemoryListUi.Items)
        assertEquals(2, (ui as MemoryListUi.Items).rows.size)
    }

    @Test fun `delete all keeps the rows and blocks them`() {
        val ui = memoryListUi(state(MemoryState(items = items, loading = true, loaded = true))) as MemoryListUi.Items
        assertTrue(ui.allBusy)
        assertEquals(listOf("a", "b"), ui.rows.map { it.id })
    }

    @Test fun `in-flight deletes mark only their rows`() {
        val ui = memoryListUi(state(MemoryState(items = items, deleting = setOf("b"), loaded = true))) as MemoryListUi.Items
        assertEquals(setOf("b"), ui.busyIds)
    }

    @Test fun `kinds and dates map for display`() {
        val ui = memoryListUi(state(MemoryState(items = items, loaded = true))) as MemoryListUi.Items
        assertEquals(MemoryKindUi.Fact, ui.rows[0].kind)
        assertEquals(MemoryKindUi.Summary, ui.rows[1].kind)
        assertEquals("11월 13일", memoryDate("2026-11-13T09:00:00Z", ZoneId.of("Asia/Seoul")))
        assertNull(memoryDate("yesterday"))
    }
}
