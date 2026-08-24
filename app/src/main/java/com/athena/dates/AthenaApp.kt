package com.athena.dates

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.YearMonth

private val MainSectionSaver = Saver<MainSection, String>({ it.name }, { MainSection.valueOf(it) })
private val YearMonthSaver = Saver<YearMonth, String>({ it.toString() }, YearMonth::parse)
private val LocalDateSaver = Saver<LocalDate, String>({ it.toString() }, LocalDate::parse)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AthenaApp(viewModel: AthenaViewModel) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val paletteName by viewModel.paletteName.collectAsStateWithLifecycle()
    val today = rememberCurrentDate()
    val palette = AthenaPalette.entries.firstOrNull { it.name == paletteName } ?: AthenaPalette.Violet
    var section by rememberSaveable(stateSaver = MainSectionSaver) { mutableStateOf(MainSection.Calendar) }
    var month by rememberSaveable(stateSaver = YearMonthSaver) { mutableStateOf(YearMonth.from(today)) }
    var selectedDate by rememberSaveable(stateSaver = LocalDateSaver) { mutableStateOf(today) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    var paletteMenuOpen by remember { mutableStateOf(false) }
    val editing = editingId?.let { id -> entries.firstOrNull { it.id == id } }
    val deleting = deletingId?.let { id -> entries.firstOrNull { it.id == id } }

    fun openEditor(entry: DateEntry? = null) { editingId = entry?.id; editorOpen = true; paletteMenuOpen = false }

    AthenaTheme(palette) {
        Surface(Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = { Text(section.label, fontWeight = FontWeight.SemiBold) },
                        actions = {
                            IconButton({ openEditor() }) { Icon(Icons.Outlined.Add, "添加重要日子") }
                            Box {
                                IconButton({ paletteMenuOpen = true }) { Icon(Icons.Outlined.MoreVert, "更多设置") }
                                DropdownMenu(paletteMenuOpen, onDismissRequest = { paletteMenuOpen = false }) {
                                    AthenaPalette.entries.forEach { choice ->
                                        DropdownMenuItem(
                                            text = { Text(choice.label) },
                                            leadingIcon = { Box(Modifier.size(18.dp).clip(CircleShape).background(choice.primary)) },
                                            trailingIcon = { if (choice == palette) Icon(Icons.Outlined.Check, "当前主题") },
                                            onClick = { viewModel.selectPalette(choice.name); paletteMenuOpen = false },
                                        )
                                    }
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                    )
                },
                bottomBar = { AthenaBottomBar(section) { section = it } },
            ) { padding ->
                when (section) {
                    MainSection.Calendar -> CalendarScreen(entries, today, month, selectedDate, { newMonth -> month = newMonth; selectedDate = newMonth.atDay(minOf(selectedDate.dayOfMonth, newMonth.lengthOfMonth())) }, { selectedDate = it }, ::openEditor, { deletingId = it.id }, Modifier.padding(padding))
                    MainSection.Anniversary -> AnniversaryScreen(entries, today, ::openEditor, { deletingId = it.id }, Modifier.padding(padding))
                    MainSection.Countdown -> CountdownScreen(entries, today, ::openEditor, { deletingId = it.id }, Modifier.padding(padding))
                }
            }
            if (editorOpen) EditorSheet(editing, today, { editorOpen = false; editingId = null }) { entry ->
                viewModel.save(entry)
                val focusDate = entry.nextOccurrence(today) ?: entry.date
                month = YearMonth.from(focusDate); selectedDate = focusDate
                section = when (entry.kind) { DateKind.Anniversary -> MainSection.Anniversary; DateKind.Countdown -> MainSection.Countdown; DateKind.Schedule -> MainSection.Calendar }
                editorOpen = false; editingId = null
            }
            deleting?.let { entry ->
                AlertDialog(
                    onDismissRequest = { deletingId = null },
                    icon = { Icon(Icons.Outlined.DeleteOutline, null) },
                    title = { Text("删除“${entry.title}”？") },
                    text = { Text("删除后无法恢复，这个日期及其重复记录都会消失。") },
                    confirmButton = { Button({ viewModel.delete(entry.id); deletingId = null }) { Text("删除") } },
                    dismissButton = { OutlinedButton({ deletingId = null }) { Text("取消") } },
                )
            }
        }
    }
}
