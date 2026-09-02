package com.vayana.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vayana.core.datastore.settings.Setting
import com.vayana.core.datastore.settings.SettingsRepository
import com.vayana.core.datastore.settings.SettingsSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val settings: StateFlow<SettingsSnapshot> = settingsRepository.snapshot
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsSnapshot())

    fun <T : Any> update(setting: Setting<T>, value: T) {
        viewModelScope.launch { settingsRepository.update(setting, value) }
    }

    fun reset(setting: Setting<out Any>) {
        viewModelScope.launch { settingsRepository.reset(setting) }
    }

    fun resetAll() {
        viewModelScope.launch { settingsRepository.resetAll() }
    }
}
