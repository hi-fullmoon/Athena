package com.athena.dates

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSheet(
    appearance: AppearanceSettings,
    reminderCount: Int,
    notificationsAvailable: Boolean,
    archivedCount: Int,
    onDismiss: () -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onPalette: (AthenaPalette) -> Unit,
    onReminderSettings: () -> Unit,
    onArchived: () -> Unit,
    onDataManagement: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 28.dp),
        ) {
            Text("设置", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), fontWeight = FontWeight.SemiBold)
            SettingsHeading("外观")
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.DarkMode, null)
                    Text("显示模式", Modifier.padding(start = 12.dp), fontWeight = FontWeight.Medium)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = appearance.themeMode == mode,
                            onClick = { onThemeMode(mode) },
                            label = { Text(mode.label) },
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("动态配色", fontWeight = FontWeight.Medium)
                        Text(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "使用系统壁纸颜色" else "需要 Android 12 或更高版本",
                        )
                    }
                    Switch(
                        checked = appearance.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                        onCheckedChange = onDynamicColor,
                        enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Palette, null)
                    Text("关闭动态配色时使用", Modifier.padding(start = 12.dp), fontWeight = FontWeight.Medium)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AthenaPalette.entries.forEach { palette ->
                        FilterChip(
                            selected = appearance.paletteName == palette.name ||
                                (appearance.paletteName == null && palette == AthenaPalette.Violet),
                            onClick = { onPalette(palette) },
                            label = { Text(palette.label) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            SettingsHeading("提醒与数据")
            SettingsDestination(
                icon = Icons.Outlined.Notifications,
                title = "提醒状态",
                detail = "$reminderCount 条日期已开启提醒 · ${if (notificationsAvailable) "通知可用" else "需要检查通知权限"}",
                onClick = onReminderSettings,
            )
            SettingsDestination(
                icon = Icons.Outlined.Archive,
                title = "过期倒数日归档",
                detail = if (archivedCount == 0) "暂无归档" else "$archivedCount 条已归档",
                onClick = onArchived,
            )
            SettingsDestination(
                icon = Icons.Outlined.DataObject,
                title = "数据管理",
                detail = "完整备份、恢复与 ICS 交换",
                onClick = onDataManagement,
            )
            ListItem(
                headlineContent = { Text("桌面小组件") },
                supportingContent = { Text("长按桌面空白处，在小组件列表中选择 Athena") },
                leadingContent = { Icon(Icons.Outlined.Widgets, null) },
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onDismiss, Modifier.fillMaxWidth().padding(horizontal = 20.dp)) { Text("完成") }
        }
    }
}

@Composable
private fun SettingsHeading(text: String) {
    Text(text, Modifier.padding(horizontal = 20.dp, vertical = 12.dp), fontWeight = FontWeight.SemiBold)
}

@Composable
private fun SettingsDestination(icon: ImageVector, title: String, detail: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(detail) },
        leadingContent = { Icon(icon, null) },
        trailingContent = { Icon(Icons.Outlined.ChevronRight, "打开$title") },
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArchivedEntriesSheet(
    entries: List<DateEntry>,
    onDismiss: () -> Unit,
    onRestore: (DateEntry) -> Unit,
    onDelete: (DateEntry) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 28.dp),
        ) {
            Text("过期倒数日归档", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            if (entries.isEmpty()) {
                Text("暂无归档。一次性且已过期的倒数日会自动移到这里。")
            } else {
                entries.sortedByDescending(DateEntry::date).forEach { entry ->
                    ListItem(
                        headlineContent = { Text(entry.title) },
                        supportingContent = { Text("${entry.date} · ${entry.note.ifBlank { "无备注" }}") },
                        trailingContent = {
                            Row {
                                TextButton({ onRestore(entry) }) { Text("恢复") }
                                TextButton({ onDelete(entry) }) { Text("删除") }
                            }
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onDismiss, Modifier.fillMaxWidth()) { Text("关闭") }
        }
    }
}
