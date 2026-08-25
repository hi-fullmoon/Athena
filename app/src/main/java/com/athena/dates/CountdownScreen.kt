package com.athena.dates

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@Composable
fun CountdownScreen(entries: List<DateEntry>, today: LocalDate, onEdit: (DateEntry) -> Unit, onDelete: (DateEntry) -> Unit, modifier: Modifier = Modifier) {
    EntryListScreen("还没有倒数日", entries.filter { it.kind == DateKind.Countdown }, today, onEdit, onDelete, modifier)
}

@Composable
fun EntryListScreen(emptyMessage: String, entries: List<DateEntry>, today: LocalDate, onEdit: (DateEntry) -> Unit, onDelete: (DateEntry) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
        if (entries.isEmpty()) item { EmptyState(emptyMessage) }
        else items(entries, key = { it.id }) { entry ->
            CountdownCard(entry, entry.nextOccurrence(today) ?: entry.date, today, { onEdit(entry) }, { onDelete(entry) })
            Spacer(Modifier.height(10.dp))
        }
    }
}
