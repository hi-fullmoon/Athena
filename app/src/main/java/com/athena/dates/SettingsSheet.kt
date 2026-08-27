package com.athena.dates

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import java.util.Locale

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
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 28.dp),
        ) {
            Text("设置", style = MaterialTheme.typography.headlineSmall)
            Text(
                "管理外观、提醒与数据",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            SettingsHeading("外观")
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                tonalElevation = 0.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.DarkMode, null)
                        Text("显示模式", Modifier.padding(start = 12.dp), fontWeight = FontWeight.Medium)
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
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
                            Text("壁纸取色", fontWeight = FontWeight.Medium)
                            Text(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "使用系统壁纸生成应用配色" else "需要 Android 12 或更高版本",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
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
                        Text("主题色", Modifier.padding(start = 12.dp), fontWeight = FontWeight.Medium)
                    }
                    if (appearance.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Text(
                            "关闭壁纸取色后可选择主题色",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AthenaPalette.entries.forEach { palette ->
                            FilterChip(
                                selected = appearance.paletteName == palette.name ||
                                    (appearance.paletteName == null && palette == AthenaPalette.Violet),
                                onClick = { onPalette(palette) },
                                enabled = !appearance.dynamicColor || Build.VERSION.SDK_INT < Build.VERSION_CODES.S,
                                leadingIcon = {
                                    Box(Modifier.size(13.dp).clip(CircleShape).background(palette.primary))
                                },
                                label = { Text(palette.label) },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            SettingsHeading("管理与工具")
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                tonalElevation = 0.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column {
                    SettingsDestination(Icons.Outlined.Notifications, "提醒状态", "$reminderCount 个日期已设置提醒 · ${if (notificationsAvailable) "通知已开启" else "通知未开启"}", onReminderSettings)
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SettingsDestination(Icons.Outlined.Archive, "已归档的倒数日", if (archivedCount == 0) "暂无" else "$archivedCount 个", onArchived)
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SettingsDestination(Icons.Outlined.DataObject, "数据管理", "备份、恢复、导入与导出", onDataManagement)
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    ListItem(
                        headlineContent = { Text("桌面小组件") },
                        supportingContent = { Text("长按桌面空白处，在小组件中添加“Athena 近期日期”") },
                        leadingContent = { Icon(Icons.Outlined.Widgets, null) },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onDismiss, Modifier.fillMaxWidth().height(52.dp)) { Text("完成") }
        }
    }
}

@Composable
private fun SettingsHeading(text: String) {
    Text(text, Modifier.padding(top = 16.dp, bottom = 10.dp), fontWeight = FontWeight.SemiBold)
}

@Composable
private fun SettingsDestination(icon: ImageVector, title: String, detail: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(detail) },
        leadingContent = { Icon(icon, null) },
        trailingContent = { Icon(Icons.Outlined.ChevronRight, "打开$title") },
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
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
            Text("已归档的倒数日", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            if (entries.isEmpty()) {
                Text("暂无归档。一次性且已过期的倒数日会自动移到这里。")
            } else {
                entries.sortedByDescending(DateEntry::date).forEach { entry ->
                    ListItem(
                        headlineContent = { Text(entry.title) },
                        supportingContent = {
                            Text(
                                buildString {
                                    append(entry.date.format(DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日", Locale.CHINA)))
                                    if (entry.note.isNotBlank()) append(" · ").append(entry.note)
                                },
                            )
                        },
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
