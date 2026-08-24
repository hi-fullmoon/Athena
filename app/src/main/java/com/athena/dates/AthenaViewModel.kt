package com.athena.dates

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AthenaViewModel(
    private val repository: DateEntryRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val entries: StateFlow<List<DateEntry>> = repository.entries.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )
    val paletteName = MutableStateFlow(settingsRepository.loadPaletteName())

    fun save(entry: DateEntry) = viewModelScope.launch { repository.upsert(entry) }
    fun delete(id: String) = viewModelScope.launch { repository.delete(id) }

    fun selectPalette(name: String) {
        settingsRepository.savePaletteName(name)
        paletteName.value = name
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AthenaViewModel(
                repository = RoomDateEntryRepository(context.applicationContext),
                settingsRepository = SettingsRepository(context.applicationContext),
            ) as T
        }
    }
}
