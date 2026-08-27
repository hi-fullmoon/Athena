package com.athena.dates

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@Composable
fun CountdownScreen(
    entries: List<DateEntry>,
    today: LocalDate,
    onEdit: (DateEntry) -> Unit,
    onDelete: (DateEntry) -> Unit,
    modifier: Modifier = Modifier,
    isFiltering: Boolean = false,
) {
    EntryListScreen(
        kind = DateKind.Countdown,
        emptyMessage = "还没有倒数日",
        entries = entries.filter { it.kind == DateKind.Countdown },
        today = today,
        onEdit = onEdit,
        onDelete = onDelete,
        isFiltering = isFiltering,
        modifier = modifier,
    )
}

@Composable
fun EntryListScreen(
    kind: DateKind,
    emptyMessage: String,
    entries: List<DateEntry>,
    today: LocalDate,
    onEdit: (DateEntry) -> Unit,
    onDelete: (DateEntry) -> Unit,
    modifier: Modifier = Modifier,
    isFiltering: Boolean = false,
) {
    val title = if (kind == DateKind.Anniversary) "全部纪念日" else "进行中的倒数日"
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 104.dp),
    ) {
        if (entries.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Text(
                        "共 ${entries.size} 个",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Spacer(Modifier.height(14.dp))
            }
        }
        if (entries.isEmpty()) {
            item {
                EmptyState(
                    message = if (isFiltering) "没有匹配的结果" else emptyMessage,
                    detail = when {
                        isFiltering -> "试试清除搜索或调整筛选条件"
                        kind == DateKind.Anniversary -> "记录生日、相识日，或任何值得纪念的日子"
                        else -> "为期待的日子设一个倒计时"
                    },
                )
            }
        } else items(entries, key = { it.id }) { entry ->
            CountdownCard(entry, entry.nextOccurrence(today) ?: entry.date, today, { onEdit(entry) }, { onDelete(entry) })
            Spacer(Modifier.height(8.dp))
        }
    }
}
