package ai.hey.feature.memory

import ai.hey.designsystem.component.CarFrame
import ai.hey.designsystem.component.ConfirmDialog
import ai.hey.designsystem.component.EmptyPanel
import ai.hey.designsystem.component.ErrorKind
import ai.hey.designsystem.component.ErrorPanel
import ai.hey.designsystem.component.HeaderBar
import ai.hey.designsystem.component.HeyButton
import ai.hey.designsystem.component.HeyButtonVariant
import ai.hey.designsystem.component.HeyLayered
import ai.hey.designsystem.component.HeyNotice
import ai.hey.designsystem.component.HeyScrollColumn
import ai.hey.designsystem.component.HeyStatusTone
import ai.hey.designsystem.component.HeyText
import ai.hey.designsystem.component.PageHeading
import ai.hey.designsystem.component.SkeletonRow
import ai.hey.designsystem.icon.HeyIcons
import ai.hey.designsystem.layout.fillSize
import ai.hey.designsystem.layout.fillWidth
import ai.hey.designsystem.theme.HeyGap
import ai.hey.designsystem.theme.HeyTone
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ai.hey.designsystem.component.HeyCardList
import ai.hey.designsystem.component.HeyCardRow
import ai.hey.designsystem.component.HeyIconButton
import ai.hey.designsystem.component.HeyListCard
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class MemoryKindUi { Fact, Summary }

/** One remembered thing as the screen shows it. [date] is already formatted ("11월 13일"). */
data class MemoryRowUi(val id: String, val kind: MemoryKindUi, val text: String, val date: String?)

/** What the list zone shows. The screen never sees conversation text, only facts and summaries. */
sealed interface MemoryListUi {
    data object Loading : MemoryListUi

    /** The server could not be reached. */
    data object Failed : MemoryListUi

    /** Nothing remembered; [savingOff] adds "기억 저장이 꺼져 있어요" (the C7 switch is off). */
    data class Empty(val savingOff: Boolean) : MemoryListUi

    /** [busyIds] are rows whose delete request is in flight; [allBusy] while "모두 지우기" runs. */
    data class Items(
        val rows: List<MemoryRowUi>,
        val busyIds: Set<String> = emptySet(),
        val allBusy: Boolean = false,
        /** The server has more pages: a "더 보기" button follows the rows. */
        val hasMore: Boolean = false,
        val loadingMore: Boolean = false,
    ) : MemoryListUi
}

/**
 * C6 (PRD 4.6). Stateless except for which confirmation is open: delete one (`modal-memory-delete`) or all
 * (`modal-memory-delete-all`). The caller composes this only while parked; a failed delete arrives as [notice]
 * ("지우지 못했어요...") and the row stays in [list].
 */
@Composable
fun MemoryScreen(
    list: MemoryListUi,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onDelete: (String) -> Unit,
    onDeleteAll: () -> Unit,
    onLoadMore: () -> Unit = {},
    modifier: Modifier = Modifier,
    notice: String? = null,
) {
    var pendingId by remember { mutableStateOf<String?>(null) }
    var pendingAll by remember { mutableStateOf(false) }
    val items = list as? MemoryListUi.Items
    // A row that vanished (deleted, or reloaded away) cannot stay in the dialog.
    val pending = items?.rows?.firstOrNull { it.id == pendingId }
    if (pendingId != null && pending == null) pendingId = null
    if (pendingAll && items == null) pendingAll = false

    HeyLayered(
        modifier = modifier.fillSize(),
        overlay = {
            if (pending != null) {
                ConfirmDialog(
                    title = stringResource(R.string.memory_delete_title),
                    message = pending.text,
                    confirmText = stringResource(R.string.memory_delete_confirm),
                    dismissText = stringResource(R.string.memory_cancel),
                    onConfirm = { onDelete(pending.id) },
                    onDismiss = { pendingId = null },
                    modifier = Modifier.fillSize(),
                    danger = true,
                    busy = pending.id in (items?.busyIds ?: emptySet()),
                )
            } else if (pendingAll) {
                ConfirmDialog(
                    title = stringResource(R.string.memory_delete_all_title),
                    message = stringResource(R.string.memory_delete_all_message),
                    confirmText = stringResource(R.string.memory_delete_all_confirm),
                    dismissText = stringResource(R.string.memory_cancel),
                    onConfirm = onDeleteAll,
                    onDismiss = { pendingAll = false },
                    modifier = Modifier.fillSize(),
                    danger = true,
                    busy = items?.allBusy == true,
                )
            }
        },
    ) {
        CarFrame(
            statusStrip = {
                // Studio 4dc8ad3 car-memory: the header row sits 45.5 dp higher than the shared frame's top margin gives.
                HeaderBar(
                    title = stringResource(R.string.memory_app_name),
                    onBack = onBack,
                    backLabel = stringResource(R.string.memory_back),
                    stateLabel = stringResource(R.string.memory_parked),
                    modifier = Modifier.shifted(y = (-45.5).dp),
                )
            },
            driving = false,
        ) {
            // Studio page margin is 64 and the cards end at 1536: widen the scroll viewport 16 left and 40 right
            // (the scroll column keeps its own 24 end padding for the scrollbar).
            HeyScrollColumn(HeyGap.G32, modifier = Modifier.fillSize().widenForStudioMargin()) {
                PageHeading(stringResource(R.string.memory_title))
                // The failure line sits in the page flow: the header and the back button stay visible.
                if (notice != null) HeyNotice(notice, HeyStatusTone.Danger, persistent = true)
                when (list) {
                    MemoryListUi.Loading -> {
                        HeyText(stringResource(R.string.memory_loading), tone = HeyTone.Muted)
                        repeat(3) { SkeletonRow(Modifier.fillWidth()) }
                    }
                    MemoryListUi.Failed -> ErrorPanel(
                        kind = ErrorKind.Server,
                        onAction = onRetry,
                        title = stringResource(R.string.memory_error_title),
                        message = stringResource(R.string.memory_error_message),
                        actionLabel = stringResource(R.string.memory_retry),
                    )
                    is MemoryListUi.Empty -> {
                        EmptyPanel(
                            icon = HeyIcons.Book,
                            title = stringResource(R.string.memory_empty_title),
                            message = stringResource(R.string.memory_empty_message),
                            modifier = Modifier.fillWidth(),
                        )
                        if (list.savingOff) HeyText(stringResource(R.string.memory_off), tone = HeyTone.Muted)
                    }
                    is MemoryListUi.Items -> {
                        HeyCardList {
                            list.rows.forEach { row ->
                                val enabled = row.id !in list.busyIds && !list.allBusy
                                val deleteLabel = stringResource(R.string.memory_delete_label)
                                HeyListCard {
                                    HeyCardRow(
                                        title = row.text,
                                        line = row.date?.let { stringResource(R.string.memory_date_line, it) },
                                        trailing = {
                                            // Studio glyph is 28 dp at the card's right padding; the 76 dp touch target is centred on it.
                                            HeyIconButton(
                                                HeyIcons.Trash, deleteLabel, { pendingId = row.id },
                                                modifier = Modifier.shifted(x = 24.dp), enabled = enabled, bare = true, glyph = 28.dp,
                                            )
                                        },
                                    )
                                }
                            }
                        }
                        if (list.hasMore) {
                            HeyButton(
                                stringResource(R.string.memory_more),
                                onLoadMore,
                                variant = HeyButtonVariant.Secondary,
                                loading = list.loadingMore,
                            )
                        }
                        HeyButton(
                            stringResource(R.string.memory_delete_all),
                            { pendingAll = true },
                            variant = HeyButtonVariant.Soft,
                        )
                    }
                }
            }
        }
    }
}

private fun Modifier.widenForStudioMargin(): Modifier = layout { m, c ->
    val left = 16.dp.roundToPx()
    val up = 91.dp.roundToPx()
    val w = c.maxWidth + left + 40.dp.roundToPx()
    val h = c.maxHeight + up
    val p = m.measure(c.copy(minWidth = w, maxWidth = w, minHeight = h, maxHeight = h))
    layout(c.maxWidth, c.maxHeight) { p.place(-left, -up) }
}

/** Moves the drawing without changing the space the node takes. */
private fun Modifier.shifted(x: Dp = 0.dp, y: Dp = 0.dp): Modifier = layout { m, c ->
    val p = m.measure(c)
    layout(p.width, p.height) { p.place(x.roundToPx(), y.roundToPx()) }
}
