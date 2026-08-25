package com.athena.dates

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.core.content.edit
import kotlinx.coroutines.launch

enum class WidgetStyle { System, Transparent }

data class WidgetConfiguration(
    val kinds: Set<DateKind> = DateKind.entries.toSet(),
    val tagIds: Set<String> = emptySet(),
    val displayCount: Int = 3,
    val style: WidgetStyle = WidgetStyle.System,
) {
    init {
        require(displayCount in 1..6)
    }
}

internal class WidgetConfigurationStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(widgetId: Int): WidgetConfiguration {
        val kinds = preferences.getString(key(widgetId, "kinds"), null)
            ?.split(',')?.mapNotNull(DateKind::fromStored)?.toSet()
            ?: DateKind.entries.toSet()
        val tags = preferences.getString(key(widgetId, "tags"), null)
            ?.split(',')?.filter(String::isNotBlank)?.toSet().orEmpty()
        val count = preferences.getInt(key(widgetId, "count"), 3).coerceIn(1, 6)
        val style = preferences.getString(key(widgetId, "style"), null)
            ?.let { stored -> WidgetStyle.entries.firstOrNull { it.name == stored } }
            ?: WidgetStyle.System
        return WidgetConfiguration(kinds, tags, count, style)
    }

    fun save(widgetId: Int, configuration: WidgetConfiguration) {
        preferences.edit {
            putString(key(widgetId, "kinds"), configuration.kinds.joinToString(",", transform = DateKind::storageKey))
            putString(key(widgetId, "tags"), configuration.tagIds.joinToString(","))
            putInt(key(widgetId, "count"), configuration.displayCount)
            putString(key(widgetId, "style"), configuration.style.name)
        }
    }

    fun delete(widgetId: Int) {
        preferences.edit {
            listOf("kinds", "tags", "count", "style").forEach { remove(key(widgetId, it)) }
        }
    }

    private fun key(widgetId: Int, field: String) = "widget_${widgetId}_$field"

    private companion object {
        const val PREFS_NAME = "athena_widget_configuration"
    }
}

class WidgetConfigurationActivity : ComponentActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        widgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setContent {
            AthenaTheme(AthenaPalette.Violet, AppearanceSettings(), darkTheme = androidx.compose.foundation.isSystemInDarkTheme()) {
                var tags by remember { mutableStateOf<List<DateTag>>(emptyList()) }
                LaunchedEffect(Unit) { tags = RoomDateEntryRepository(applicationContext).snapshotData().tags }
                WidgetConfigurationScreen(
                    initial = WidgetConfigurationStore(applicationContext).load(widgetId),
                    tags = tags,
                    onSave = ::saveAndFinish,
                )
            }
        }
    }

    private fun saveAndFinish(configuration: WidgetConfiguration) {
        WidgetConfigurationStore(applicationContext).save(widgetId, configuration)
        lifecycleScope.launch {
            refreshUpcomingDatesWidget(applicationContext, intArrayOf(widgetId))
            setResult(
                Activity.RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
            )
            finish()
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun WidgetConfigurationScreen(
    initial: WidgetConfiguration,
    tags: List<DateTag>,
    onSave: (WidgetConfiguration) -> Unit,
) {
    var configuration by remember(initial) { mutableStateOf(initial) }
    Scaffold(topBar = { TopAppBar(title = { Text("配置即将到来") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("日期类型", style = MaterialTheme.typography.titleMedium)
            DateKind.entries.forEach { kind ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = kind in configuration.kinds,
                        onCheckedChange = { checked ->
                            configuration = configuration.copy(
                                kinds = if (checked) configuration.kinds + kind else configuration.kinds - kind,
                            )
                        },
                    )
                    Text(kind.label)
                }
            }
            Text("标签（不选表示全部）", style = MaterialTheme.typography.titleMedium)
            if (tags.isEmpty()) Text("尚未创建标签", color = MaterialTheme.colorScheme.onSurfaceVariant)
            tags.forEach { tag ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = tag.id in configuration.tagIds,
                        onCheckedChange = { checked ->
                            configuration = configuration.copy(
                                tagIds = if (checked) configuration.tagIds + tag.id else configuration.tagIds - tag.id,
                            )
                        },
                    )
                    Text(tag.name)
                }
            }
            Text("显示数量", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..6).forEach { count ->
                    FilterChip(
                        selected = configuration.displayCount == count,
                        onClick = { configuration = configuration.copy(displayCount = count) },
                        label = { Text(count.toString()) },
                    )
                }
            }
            Text("样式", style = MaterialTheme.typography.titleMedium)
            WidgetStyle.entries.forEach { style ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = configuration.style == style,
                        onClick = { configuration = configuration.copy(style = style) },
                    )
                    Text(if (style == WidgetStyle.System) "跟随系统" else "透明")
                }
            }
            Button(
                onClick = { onSave(configuration) },
                enabled = configuration.kinds.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("保存配置") }
        }
    }
}
