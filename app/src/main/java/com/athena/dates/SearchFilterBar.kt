package com.athena.dates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EntrySearchControls(
    query: EntryQuery,
    onQueryChange: ((EntryQuery) -> EntryQuery) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
    availableTags: List<DateTag> = emptyList(),
) {
    var filtersOpen by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(
                Modifier.weight(1f).height(48.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(18.dp),
                tonalElevation = 2.dp,
                shadowElevation = 1.dp,
            ) {
                Row(Modifier.padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Search, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        value = query.search,
                        onValueChange = { value -> onQueryChange { it.copy(search = value) } },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (query.search.isEmpty()) {
                                    Text("搜索日子、备注", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                }
                                innerTextField()
                            }
                        },
                    )
                    if (query.search.isNotEmpty()) {
                        IconButton({ onQueryChange { it.copy(search = "") } }, Modifier.size(44.dp)) {
                            Icon(Icons.Outlined.Close, "清除搜索", Modifier.size(18.dp))
                        }
                    } else Spacer(Modifier.width(12.dp))
                }
            }
            ToolbarAction(
                description = if (query.activeFilterCount == 0) "筛选" else "筛选，已启用 ${query.activeFilterCount} 项",
                onClick = { filtersOpen = true },
            ) {
                Icon(Icons.Outlined.Tune, null, Modifier.size(21.dp))
                if (query.activeFilterCount > 0) {
                    Box(
                        Modifier.align(Alignment.TopEnd).size(17.dp).clip(CircleShape)
                            .semantics { contentDescription = "${query.activeFilterCount} 个筛选条件" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(color = MaterialTheme.colorScheme.primary, shape = CircleShape) {
                            Text(
                                query.activeFilterCount.toString(),
                                Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
            Box {
                ToolbarAction("排序：${query.sort.label}", { sortOpen = true }) {
                    Icon(Icons.AutoMirrored.Outlined.Sort, null, Modifier.size(21.dp))
                }
                DropdownMenu(sortOpen, onDismissRequest = { sortOpen = false }) {
                    EntrySort.entries.forEach { sort ->
                        DropdownMenuItem(
                            text = { Text(sort.label, fontWeight = if (query.sort == sort) FontWeight.Bold else FontWeight.Normal) },
                            onClick = {
                                onQueryChange { it.copy(sort = sort) }
                                sortOpen = false
                            },
                        )
                    }
                }
            }
        }

        if (query.activeFilterCount > 0) {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (query.kinds != DateKind.entries.toSet()) ActivePill(query.kinds.joinToString("、") { it.label })
                if (query.status != EntryStatusFilter.All) ActivePill(query.status.label)
                if (query.yearlyRepeat != BooleanEntryFilter.All) ActivePill("年度重复 · ${query.yearlyRepeat.label}")
                if (query.reminder != BooleanEntryFilter.All) ActivePill("提醒 · ${query.reminder.label}")
                if (query.tagIds.isNotEmpty()) ActivePill("${query.tagIds.size} 个标签")
                TextButton(onReset, Modifier.height(36.dp)) { Text("全部清除") }
            }
        }
    }

    if (filtersOpen) {
        ModalBottomSheet(onDismissRequest = { filtersOpen = false }) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding()
                    .padding(horizontal = 20.dp).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("筛选", style = MaterialTheme.typography.headlineSmall)
                        Text("只看此刻真正关心的日期", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onReset) { Text("重置") }
                }
                FilterGroup("日期类型") {
                    DateKind.entries.forEach { kind ->
                        FilterChip(
                            selected = kind in query.kinds,
                            onClick = {
                                onQueryChange { current ->
                                    current.copy(kinds = if (kind in current.kinds) current.kinds - kind else current.kinds + kind)
                                }
                            },
                            label = { Text(kind.label) },
                        )
                    }
                }
                FilterGroup("有效状态") {
                    SingleChoiceChips(EntryStatusFilter.entries, query.status, { it.label }) { chosen ->
                        onQueryChange { it.copy(status = chosen) }
                    }
                }
                FilterGroup("年度重复") {
                    SingleChoiceChips(BooleanEntryFilter.entries, query.yearlyRepeat, { it.label }) { chosen ->
                        onQueryChange { it.copy(yearlyRepeat = chosen) }
                    }
                }
                FilterGroup("提醒") {
                    SingleChoiceChips(BooleanEntryFilter.entries, query.reminder, { filter ->
                        when (filter) {
                            BooleanEntryFilter.All -> "全部"
                            BooleanEntryFilter.Yes -> "已开启"
                            BooleanEntryFilter.No -> "未开启"
                        }
                    }) { chosen -> onQueryChange { it.copy(reminder = chosen) } }
                }
                if (availableTags.isNotEmpty()) {
                    FilterGroup("标签 · 匹配任一") {
                        availableTags.forEach { tag ->
                            FilterChip(
                                selected = tag.id in query.tagIds,
                                onClick = {
                                    onQueryChange { current ->
                                        current.copy(tagIds = if (tag.id in current.tagIds) current.tagIds - tag.id else current.tagIds + tag.id)
                                    }
                                },
                                label = { Text(tag.name) },
                            )
                        }
                    }
                }
                Button({ filtersOpen = false }, Modifier.fillMaxWidth().height(52.dp)) { Text("应用筛选") }
            }
        }
    }
}

@Composable
private fun ToolbarAction(description: String, onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Surface(
        Modifier.size(48.dp).semantics { contentDescription = description }.clickable(role = Role.Button, onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 2.dp,
        shadowElevation = 1.dp,
    ) {
        Box(Modifier.padding(12.dp), contentAlignment = Alignment.Center, content = content)
    }
}

@Composable
private fun ActivePill(text: String) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(50)) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun FilterGroup(title: String, content: @Composable FlowRowScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
    }
}

@Composable
private fun <T> SingleChoiceChips(values: List<T>, selected: T, label: (T) -> String, onSelected: (T) -> Unit) {
    values.forEach { value ->
        FilterChip(selected = value == selected, onClick = { onSelected(value) }, label = { Text(label(value)) })
    }
}
