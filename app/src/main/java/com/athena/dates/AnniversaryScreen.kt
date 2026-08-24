package com.athena.dates

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import java.time.LocalDate

@Composable
fun AnniversaryScreen(entries: List<DateEntry>, today: LocalDate, onEdit: (DateEntry) -> Unit, onDelete: (DateEntry) -> Unit, modifier: Modifier = Modifier) {
    EntryListScreen("还没有纪念日", entries.filter { it.kind == DateKind.Anniversary }, today, onEdit, onDelete, modifier)
}
