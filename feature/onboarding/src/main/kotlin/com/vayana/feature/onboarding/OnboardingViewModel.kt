package com.vayana.feature.onboarding

import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vayana.core.datastore.settings.SettingsRegistry
import com.vayana.core.datastore.settings.SettingsRepository
import com.vayana.core.designsystem.theme.DisplayProfile
import com.vayana.core.designsystem.theme.EinkPalette
import com.vayana.core.resources.UiText
import com.vayana.core.sync.setup.GitHubConnectionTestOutcome
import com.vayana.core.sync.setup.GitHubConnectionTester
import com.vayana.core.sync.setup.GitHubSyncSettingsTransfer
import com.vayana.core.sync.setup.GitHubSyncSettingsTransferOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** How the reader chose to set up sync during onboarding. */
enum class SyncSetupMode { LATER, FRESH, IMPORT }

/** A new GitHub sync, as typed in onboarding. */
data class FreshSyncConfig(
    val owner: String,
    val repository: String,
    val branch: String,
    val token: String,
    val passphrase: String,
) {
    val isComplete: Boolean
        get() = owner.isNotBlank() && repository.isNotBlank() && branch.isNotBlank() && token.isNotBlank() && passphrase.isNotEmpty()
}

sealed interface SyncSetupStatus {
    data object Idle : SyncSetupStatus
    data object Working : SyncSetupStatus
    /** The repository answered; [hasSnapshot] when another device has synced to it already. */
    data class Connected(val hasSnapshot: Boolean) : SyncSetupStatus
    data object MissingConfig : SyncSetupStatus
    data object Imported : SyncSetupStatus
    data class Failed(val message: UiText) : SyncSetupStatus
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val syncSettingsTransfer: GitHubSyncSettingsTransfer,
    private val connectionTester: GitHubConnectionTester,
) : ViewModel() {
    /** The phone's own model name ("Pixel 9"), a sensible start the reader can change. */
    val suggestedDeviceName: String = Build.MODEL.orEmpty().trim().ifBlank { SettingsRegistry.KindleDeviceName.defaultValue }

    private val _syncStatus = MutableStateFlow<SyncSetupStatus>(SyncSetupStatus.Idle)
    val syncStatus: StateFlow<SyncSetupStatus> = _syncStatus
    private var syncJob: Job? = null

    fun chooseDisplayProfile(profile: DisplayProfile) {
        viewModelScope.launch {
            settingsRepository.update(SettingsRegistry.DisplayProfile, profile)
        }
    }

    fun chooseEinkPalette(palette: EinkPalette) {
        viewModelScope.launch { settingsRepository.update(SettingsRegistry.EinkPalette, palette) }
    }

    /** A status from an earlier choice no longer applies once the reader switches between setting up and importing. */
    fun resetSyncStatus() {
        syncJob?.cancel()
        _syncStatus.value = SyncSetupStatus.Idle
    }

    /** Imports another device's exported setup with the passphrase typed here, keeping [deviceName] for this device. */
    fun importSetup(file: Uri, passphrase: String, deviceName: String) {
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            _syncStatus.value = SyncSetupStatus.Working
            val secret = passphrase.toCharArray()
            val outcome = try {
                syncSettingsTransfer.importFrom(file, secret, keepDeviceName = deviceName)
            } finally {
                secret.fill('\u0000')
            }
            _syncStatus.value = when (outcome) {
                GitHubSyncSettingsTransferOutcome.Success -> SyncSetupStatus.Imported
                GitHubSyncSettingsTransferOutcome.MissingPassphrase -> SyncSetupStatus.MissingConfig
                is GitHubSyncSettingsTransferOutcome.Failed -> SyncSetupStatus.Failed(outcome.message)
            }
        }
    }

    /** Saves [config] (the tester reads the saved settings) and checks the token can reach the repository. */
    fun testFreshSync(config: FreshSyncConfig) {
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            _syncStatus.value = SyncSetupStatus.Working
            saveFreshSync(config)
            _syncStatus.value = when (val outcome = connectionTester.test()) {
                GitHubConnectionTestOutcome.Connected -> SyncSetupStatus.Connected(hasSnapshot = true)
                GitHubConnectionTestOutcome.ReadyForInitialSync -> SyncSetupStatus.Connected(hasSnapshot = false)
                GitHubConnectionTestOutcome.MissingConfig -> SyncSetupStatus.MissingConfig
                is GitHubConnectionTestOutcome.Failed -> SyncSetupStatus.Failed(outcome.message)
            }
        }
    }

    /**
     * Finishes onboarding: saves this device's name and, for a new sync, its settings (turned on once complete).
     * An imported setup is already saved.
     */
    fun complete(deviceName: String, freshSync: FreshSyncConfig?) {
        viewModelScope.launch {
            syncJob?.join()
            deviceName.trim().takeIf { it.isNotEmpty() }?.let { settingsRepository.update(SettingsRegistry.KindleDeviceName, it) }
            freshSync?.let { saveFreshSync(it) }
            settingsRepository.updateOnboardingCompleted(true)
        }
    }

    private suspend fun saveFreshSync(config: FreshSyncConfig) {
        settingsRepository.update(SettingsRegistry.GithubOwner, config.owner.trim())
        settingsRepository.update(SettingsRegistry.GithubRepository, config.repository.trim())
        settingsRepository.update(SettingsRegistry.GithubBranch, config.branch.trim().ifBlank { DefaultBranch })
        settingsRepository.update(SettingsRegistry.GithubToken, config.token.trim())
        settingsRepository.update(SettingsRegistry.GithubSyncPassphrase, config.passphrase)
        settingsRepository.update(SettingsRegistry.GithubSyncEnabled, config.isComplete)
    }

    private companion object {
        const val DefaultBranch = "main"
    }
}
