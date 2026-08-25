package com.athena.dates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query.search,
            onValueChange = { value -> onQueryChange { it.copy(search = value) } },
            modifier = Modifier.weight(1f),
            singleLine = true,
            label = { Text("搜索名称或备注") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = {
                if (query.search.isNotEmpty()) {
                    IconButton({ onQueryChange { it.copy(search = "") } }) {
                        Icon(Icons.Outlined.Close, "清除搜索")
                    }
                }
            },
        )
        Spacer(Modifier.width(4.dp))
        IconButton(
            onClick = { filtersOpen = true },
            modifier = Modifier.semantics {
                contentDescription = if (query.activeFilterCount == 0) "筛选" else "筛选，已启用 ${query.activeFilterCount} 项"
            },
        ) { Icon(Icons.Outlined.Tune, null) }
        Column {
            IconButton(
                onClick = { sortOpen = true },
                modifier = Modifier.semantics { contentDescription = "排序：${query.sort.label}" },
            ) { Icon(Icons.AutoMirrored.Outlined.Sort, null) }
            DropdownMenu(sortOpen, onDismissRequest = { sortOpen = false }) {
                EntrySort.entries.forEach { sort ->
                    DropdownMenuItem(
                        text = { Text(sort.label) },
                        onClick = {
                            onQueryChange { it.copy(sort = sort) }
                            sortOpen = false
                        },
                    )
                }
            }
        }
    }

    if (filtersOpen) {
        ModalBottomSheet(onDismissRequest = { filtersOpen = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("筛选日期", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    TextButton(onReset) { Text("重置") }
                }
                FilterHeading("类型")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateKind.entries.forEach { kind ->
                        FilterChip(
                            selected = kind in query.kinds,
                            onClick = {
                                onQueryChange { current ->
                                    current.copy(
                                        kinds = if (kind in current.kinds) current.kinds - kind else current.kinds + kind,
                                    )
                                }
                            },
                            label = { Text(kind.label) },
                        )
                    }
                }
                FilterHeading("有效状态")
                SingleChoiceChips(EntryStatusFilter.entries, query.status, { it.label }) { chosen ->
                    onQueryChange { it.copy(status = chosen) }
                }
                FilterHeading("年度重复")
                SingleChoiceChips(BooleanEntryFilter.entries, query.yearlyRepeat, { it.label }) { chosen ->
                    onQueryChange { it.copy(yearlyRepeat = chosen) }
                }
                FilterHeading("提醒状态")
                SingleChoiceChips(BooleanEntryFilter.entries, query.reminder, { filter ->
                    when (filter) {
                        BooleanEntryFilter.All -> "全部"
                        BooleanEntryFilter.Yes -> "已开启"
                        BooleanEntryFilter.No -> "未开启"
                    }
                }) { chosen -> onQueryChange { it.copy(reminder = chosen) } }
                if (availableTags.isNotEmpty()) {
                    FilterHeading("标签（匹配任一）")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        availableTags.forEach { tag ->
                            FilterChip(
                                selected = tag.id in query.tagIds,
                                onClick = {
                                    onQueryChange { current ->
                                        current.copy(
                                            tagIds = if (tag.id in current.tagIds) {
                                                current.tagIds - tag.id
                                            } else {
                                                current.tagIds + tag.id
                                            },
                                        )
                                    }
                                },
                                label = { Text(tag.name) },
                            )
                        }
                    }
                }
                Button({ filtersOpen = false }, Modifier.fillMaxWidth()) { Text("查看结果") }
            }
        }
    }
}

@Composable
private fun FilterHeading(text: String) {
    Text(text, fontWeight = FontWeight.Medium)
}

@Composable
private fun <T> SingleChoiceChips(
    values: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelected(value) },
                label = { Text(label(value)) },
            )
        }
    }
}
