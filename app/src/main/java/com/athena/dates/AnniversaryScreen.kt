package com.athena.dates

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import java.time.LocalDate

@Composable
fun AnniversaryScreen(
    entries: List<DateEntry>,
    today: LocalDate,
    onEdit: (DateEntry) -> Unit,
    onDelete: (DateEntry) -> Unit,
    modifier: Modifier = Modifier,
    isFiltering: Boolean = false,
) {
    EntryListScreen(
        kind = DateKind.Anniversary,
        emptyMessage = "还没有纪念日",
        entries = entries.filter { it.kind == DateKind.Anniversary },
        today = today,
        onEdit = onEdit,
        onDelete = onDelete,
        isFiltering = isFiltering,
        modifier = modifier,
    )
}
