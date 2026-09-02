package com.vayana.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vayana.core.datastore.settings.SettingsRepository
import com.vayana.core.datastore.settings.SettingsSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class AppSettingsViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {
    val settings: StateFlow<SettingsSnapshot> = settingsRepository.snapshot
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsSnapshot())
}
