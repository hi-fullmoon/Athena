package com.athena.dates

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@Composable
fun CountdownScreen(entries: List<DateEntry>, today: LocalDate, onEdit: (DateEntry) -> Unit, onDelete: (DateEntry) -> Unit, modifier: Modifier = Modifier) {
    EntryListScreen("还没有倒数日", entries.filter { it.kind == DateKind.Countdown }, today, onEdit, onDelete, modifier)
}

@Composable
fun EntryListScreen(emptyMessage: String, entries: List<DateEntry>, today: LocalDate, onEdit: (DateEntry) -> Unit, onDelete: (DateEntry) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 104.dp),
    ) {
        item {
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .48f),
                tonalElevation = 1.dp,
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (entries.isEmpty()) "从一个重要日子开始" else "都在这里了", style = MaterialTheme.typography.titleMedium)
                        Text("按下次发生时间排列，近期待办一眼可见", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(entries.size.toString(), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        if (entries.isEmpty()) {
            item { EmptyState(emptyMessage) }
        } else items(entries, key = { it.id }) { entry ->
            CountdownCard(entry, entry.nextOccurrence(today) ?: entry.date, today, { onEdit(entry) }, { onDelete(entry) })
            Spacer(Modifier.height(8.dp))
        }
    }
}
