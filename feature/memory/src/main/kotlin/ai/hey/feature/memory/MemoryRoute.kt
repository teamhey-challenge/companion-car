package ai.hey.feature.memory

import ai.hey.model.MemoryItem
import ai.hey.model.MemoryKind
import ai.hey.session.CompanionCommands
import ai.hey.session.CompanionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import java.time.OffsetDateTime
import java.time.ZoneId

/** The list zone for [state]: pure, so the state-to-screen mapping is testable without a screen. */
fun memoryListUi(state: CompanionState): MemoryListUi {
    val m = state.memory
    return when {
        m.failed && m.items.isEmpty() -> MemoryListUi.Failed
        // "모두 지우기" runs as loading over the existing rows: keep them, block them.
        m.loading && m.items.isNotEmpty() -> MemoryListUi.Items(m.items.map(::row), m.deleting, allBusy = true, hasMore = m.hasMore)
        m.loading || !m.loaded -> MemoryListUi.Loading
        m.items.isEmpty() -> MemoryListUi.Empty(savingOff = !state.settings.memoryEnabled)
        else -> MemoryListUi.Items(m.items.map(::row), m.deleting, hasMore = m.hasMore, loadingMore = m.loadingMore)
    }
}

private fun row(item: MemoryItem) = MemoryRowUi(
    id = item.id,
    kind = if (item.kind == MemoryKind.Fact) MemoryKindUi.Fact else MemoryKindUi.Summary,
    text = item.text,
    date = memoryDate(item.createdAt),
)

/** "2026-11-13T09:00:00Z" -> "11월 13일" in the device's zone; anything unreadable shows no date. */
fun memoryDate(iso: String, zone: ZoneId = ZoneId.systemDefault()): String? =
    runCatching { OffsetDateTime.parse(iso).atZoneSameInstant(zone).let { "${it.monthValue}월 ${it.dayOfMonth}일" } }.getOrNull()

/**
 * C6 wired to the companion repository: opening it reads the first page from the server. Works with or without a
 * linked phone. The caller shows it only while parked.
 */
@Composable
fun MemoryRoute(state: CompanionState, commands: CompanionCommands, onBack: () -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(Unit) { commands.loadMemory() }
    MemoryScreen(
        list = memoryListUi(state),
        onBack = onBack,
        onRetry = commands::loadMemory,
        onDelete = commands::deleteMemory,
        onDeleteAll = commands::deleteAllMemory,
        onLoadMore = commands::loadMoreMemory,
        modifier = modifier,
        notice = if (state.memory.deleteFailed) stringResource(R.string.memory_delete_failed) else null,
    )
}
