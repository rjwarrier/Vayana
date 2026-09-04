package com.vayana.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vayana.core.datastore.settings.Setting
import com.vayana.core.datastore.settings.SettingsRepository
import com.vayana.core.datastore.settings.SettingsSnapshot
import com.vayana.feature.settings.backup.BackupInspection
import com.vayana.feature.settings.backup.BackupManager
import com.vayana.feature.settings.backup.BackupOutcome
import com.vayana.feature.settings.backup.InspectOutcome
import com.vayana.feature.settings.backup.RestoreOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface BackupUiState {
    data object Idle : BackupUiState
    data object Working : BackupUiState
    data object BackupComplete : BackupUiState
    data class BackupFailed(val message: String) : BackupUiState
    data class RestoreFailed(val message: String) : BackupUiState
    data class RestoreIncompatible(val message: String) : BackupUiState
}

sealed interface RestorePreviewState {
    data object Idle : RestorePreviewState
    data object Loading : RestorePreviewState
    data class Ready(val uri: Uri, val inspection: BackupInspection) : RestorePreviewState
    data class Failed(val message: String) : RestorePreviewState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val backupManager: BackupManager,
) : ViewModel() {
    val settings: StateFlow<SettingsSnapshot> = settingsRepository.snapshot
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsSnapshot())

    private val _backupState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val backupState: StateFlow<BackupUiState> = _backupState

    private val _restorePreview = MutableStateFlow<RestorePreviewState>(RestorePreviewState.Idle)
    val restorePreview: StateFlow<RestorePreviewState> = _restorePreview

    fun <T : Any> update(setting: Setting<T>, value: T) {
        viewModelScope.launch { settingsRepository.update(setting, value) }
    }

    fun reset(setting: Setting<out Any>) {
        viewModelScope.launch { settingsRepository.reset(setting) }
    }

    fun resetAll() {
        viewModelScope.launch { settingsRepository.resetAll() }
    }

    fun createBackup(destination: Uri) {
        viewModelScope.launch {
            _backupState.value = BackupUiState.Working
            _backupState.value = when (val outcome = backupManager.createBackup(destination)) {
                BackupOutcome.Success -> BackupUiState.BackupComplete
                is BackupOutcome.Failed -> BackupUiState.BackupFailed(outcome.message)
            }
        }
    }

    fun restoreBackup(source: Uri) {
        _restorePreview.value = RestorePreviewState.Idle
        viewModelScope.launch {
            _backupState.value = BackupUiState.Working
            when (val outcome = backupManager.restoreBackup(source)) {
                RestoreOutcome.Success -> Unit // process restarts on success; nothing left to update
                is RestoreOutcome.Incompatible -> _backupState.value = BackupUiState.RestoreIncompatible(outcome.message)
                is RestoreOutcome.Failed -> _backupState.value = BackupUiState.RestoreFailed(outcome.message)
            }
        }
    }

    fun dismissBackupState() {
        _backupState.value = BackupUiState.Idle
    }

    fun inspectRestoreFile(uri: Uri) {
        viewModelScope.launch {
            _restorePreview.value = RestorePreviewState.Loading
            _restorePreview.value = when (val outcome = backupManager.inspectBackup(uri)) {
                is InspectOutcome.Success -> RestorePreviewState.Ready(uri, outcome.inspection)
                is InspectOutcome.Failed -> RestorePreviewState.Failed(outcome.message)
            }
        }
    }

    fun dismissRestorePreview() {
        _restorePreview.value = RestorePreviewState.Idle
    }
}
