package com.vayana.feature.settings

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vayana.core.datastore.settings.ImportedFont
import com.vayana.core.datastore.settings.Setting
import com.vayana.core.datastore.settings.SettingsRepository
import com.vayana.core.datastore.settings.SettingsRegistry
import com.vayana.core.datastore.settings.SettingsSnapshot
import com.vayana.core.filesystem.StorageRoots
import com.vayana.feature.settings.backup.BackupInspection
import com.vayana.feature.settings.backup.BackupManager
import com.vayana.feature.settings.backup.AutomaticBackupSettings
import com.vayana.feature.settings.backup.AutomaticBackupFrequency
import com.vayana.feature.settings.backup.BackupFolderFile
import com.vayana.feature.settings.backup.BackupOutcome
import com.vayana.feature.settings.backup.InspectOutcome
import com.vayana.feature.settings.backup.RestoreOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID
import com.vayana.core.database.dao.PendingCloudDeletionDao

sealed interface BackupUiState {
    data object Idle : BackupUiState
    data object Creating : BackupUiState
    data object Restoring : BackupUiState
    data object BackupComplete : BackupUiState
    data class BackupFailed(val message: String) : BackupUiState
    data class RestoreFailed(val message: String) : BackupUiState
    data class RestoreIncompatible(val message: String) : BackupUiState
}

data class BackupFolderFilesState(
    val loading: Boolean = false,
    val files: List<BackupFolderFile> = emptyList(),
    val error: String? = null,
)

sealed interface GitHubSyncSettingsTransferState {
    data object Idle : GitHubSyncSettingsTransferState
    data object Working : GitHubSyncSettingsTransferState
    data object ExportComplete : GitHubSyncSettingsTransferState
    data object ImportComplete : GitHubSyncSettingsTransferState
    data object MissingPassphrase : GitHubSyncSettingsTransferState
    data class Failed(val message: String) : GitHubSyncSettingsTransferState
}

sealed interface GitHubConnectionTestState {
    data object Idle : GitHubConnectionTestState
    data object Working : GitHubConnectionTestState
    data object Connected : GitHubConnectionTestState
    data object ReadyForInitialSync : GitHubConnectionTestState
    data object MissingConfig : GitHubConnectionTestState
    data class Failed(val message: String) : GitHubConnectionTestState
}

sealed interface RestorePreviewState {
    data object Idle : RestorePreviewState
    data object Loading : RestorePreviewState
    data class Ready(val uri: Uri, val inspection: BackupInspection) : RestorePreviewState
    data class Failed(val message: String) : RestorePreviewState
}

sealed interface ReaderFontImportState {
    data object Idle : ReaderFontImportState
    data object Working : ReaderFontImportState
    data class Imported(val displayName: String) : ReaderFontImportState
    data class Failed(val message: String) : ReaderFontImportState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val backupManager: BackupManager,
    private val automaticBackupSettings: AutomaticBackupSettings,
    private val gitHubSyncSettingsTransfer: GitHubSyncSettingsTransfer,
    private val gitHubConnectionTester: GitHubConnectionTester,
    private val storageRoots: StorageRoots,
    pendingCloudDeletionDao: PendingCloudDeletionDao,
) : ViewModel() {
    val settings: StateFlow<SettingsSnapshot> = settingsRepository.snapshot
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsSnapshot())

    private val _backupState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val backupState: StateFlow<BackupUiState> = _backupState
    val automaticBackup = automaticBackupSettings.state
    private val _backupFolderFiles = MutableStateFlow(BackupFolderFilesState())
    val backupFolderFiles: StateFlow<BackupFolderFilesState> = _backupFolderFiles
    private var backupFolderListingJob: Job? = null

    init {
        viewModelScope.launch {
            automaticBackup.map { it.folderUri to it.lastSuccessAt }.distinctUntilChanged().collectLatest {
                refreshBackupFolderFiles()
            }
        }
    }

    fun refreshBackupFolderFiles() {
        backupFolderListingJob?.cancel()
        if (automaticBackup.value.folderUri == null) {
            _backupFolderFiles.value = BackupFolderFilesState()
            return
        }
        backupFolderListingJob = viewModelScope.launch {
            _backupFolderFiles.value = BackupFolderFilesState(loading = true)
            _backupFolderFiles.value = automaticBackupSettings.listBackupFiles().fold(
                onSuccess = { BackupFolderFilesState(files = it) },
                onFailure = { BackupFolderFilesState(error = it.message ?: "Could not read the backup folder") },
            )
        }
    }

    fun chooseAutomaticBackupFolder(uri: Uri) {
        automaticBackupSettings.chooseFolder(uri).onFailure { throwable ->
            automaticBackupSettings.recordError(throwable.message ?: "Could not use the selected folder")
        }
    }

    fun setAutomaticBackupKeepCount(count: Int) = automaticBackupSettings.setKeepCount(count)

    fun setAutomaticBackupFrequency(frequency: AutomaticBackupFrequency) {
        automaticBackupSettings.setFrequency(frequency).onFailure { throwable ->
            automaticBackupSettings.recordError(throwable.message ?: "Could not update the backup schedule")
        }
    }

    fun disableAutomaticBackup() = automaticBackupSettings.disable()

    private val _restorePreview = MutableStateFlow<RestorePreviewState>(RestorePreviewState.Idle)
    val restorePreview: StateFlow<RestorePreviewState> = _restorePreview
    private var restoreInspectionJob: Job? = null

    private val _readerFontImportState = MutableStateFlow<ReaderFontImportState>(ReaderFontImportState.Idle)
    val readerFontImportState: StateFlow<ReaderFontImportState> = _readerFontImportState

    private val _githubSyncSettingsTransferState = MutableStateFlow<GitHubSyncSettingsTransferState>(GitHubSyncSettingsTransferState.Idle)
    val githubSyncSettingsTransferState: StateFlow<GitHubSyncSettingsTransferState> = _githubSyncSettingsTransferState

    private val _githubConnectionTestState = MutableStateFlow<GitHubConnectionTestState>(GitHubConnectionTestState.Idle)
    val githubConnectionTestState: StateFlow<GitHubConnectionTestState> = _githubConnectionTestState

    /** Cloud files of permanently deleted books that sync hasn't removed yet. */
    val pendingCloudDeletions: StateFlow<Int> = pendingCloudDeletionDao.observeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun <T : Any> update(setting: Setting<T>, value: T) {
        viewModelScope.launch {
            if (setting == SettingsRegistry.ReaderFontFamily) {
                settingsRepository.updateReaderCustomFontId(null)
            }
            settingsRepository.update(setting, value)
        }
    }

    fun importReaderFont(source: Uri) {
        viewModelScope.launch {
            _readerFontImportState.value = ReaderFontImportState.Working
            val result = withContext(Dispatchers.IO) { copyReaderFont(context, storageRoots, source) }
            result.fold(
                onSuccess = { font ->
                    // Read through the repository, not `settings.value`: that StateFlow is WhileSubscribed(5s) and
                    // reports the empty default whenever nothing is collecting, which would wipe the existing list.
                    val existingFonts = settingsRepository.snapshot.first().readerImportedFonts
                    // Every import mints a fresh UUID for both id and fileName, so identity can never dedupe.
                    // Re-importing the same face is a replace: match on display name and drop the superseded copy.
                    val updatedFonts = existingFonts.filterNot { it.displayName.equals(font.displayName, ignoreCase = true) } + font
                    settingsRepository.updateReaderImportedFonts(updatedFonts)
                    withContext(Dispatchers.IO) { pruneOrphanFontFiles(storageRoots, updatedFonts) }
                    settingsRepository.updateReaderCustomFontId(font.id)
                    _readerFontImportState.value = ReaderFontImportState.Imported(font.displayName)
                },
                onFailure = { throwable ->
                    _readerFontImportState.value = ReaderFontImportState.Failed(throwable.message ?: "Could not import this font")
                },
            )
        }
    }

    fun selectReaderCustomFont(fontId: String?) {
        viewModelScope.launch { settingsRepository.updateReaderCustomFontId(fontId) }
    }

    fun dismissReaderFontImportState() {
        _readerFontImportState.value = ReaderFontImportState.Idle
    }

    fun reset(setting: Setting<out Any>) {
        viewModelScope.launch { settingsRepository.reset(setting) }
    }

    fun resetAll() {
        viewModelScope.launch { settingsRepository.resetAll() }
    }

    fun createBackup(destination: Uri) {
        if (_backupState.value == BackupUiState.Creating || _backupState.value == BackupUiState.Restoring) return
        _backupState.value = BackupUiState.Creating
        viewModelScope.launch {
            _backupState.value = when (val outcome = backupManager.createBackup(destination)) {
                BackupOutcome.Success -> BackupUiState.BackupComplete
                is BackupOutcome.Failed -> BackupUiState.BackupFailed(outcome.message)
            }
            if (_backupState.value == BackupUiState.BackupComplete) refreshBackupFolderFiles()
        }
    }

    fun restoreBackup(source: Uri) {
        val preview = _restorePreview.value as? RestorePreviewState.Ready ?: return
        if (preview.uri != source || !preview.inspection.isCompatible ||
            _backupState.value == BackupUiState.Creating || _backupState.value == BackupUiState.Restoring) return
        restoreInspectionJob?.cancel()
        _restorePreview.value = RestorePreviewState.Idle
        _backupState.value = BackupUiState.Restoring
        viewModelScope.launch {
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

    fun exportGitHubSyncSettings(destination: Uri) {
        viewModelScope.launch {
            _githubSyncSettingsTransferState.value = GitHubSyncSettingsTransferState.Working
            _githubSyncSettingsTransferState.value = when (val outcome = gitHubSyncSettingsTransfer.exportTo(destination)) {
                GitHubSyncSettingsTransferOutcome.Success -> GitHubSyncSettingsTransferState.ExportComplete
                GitHubSyncSettingsTransferOutcome.MissingPassphrase -> GitHubSyncSettingsTransferState.MissingPassphrase
                is GitHubSyncSettingsTransferOutcome.Failed -> GitHubSyncSettingsTransferState.Failed(outcome.message)
            }
        }
    }

    fun importGitHubSyncSettings(source: Uri) {
        viewModelScope.launch {
            _githubSyncSettingsTransferState.value = GitHubSyncSettingsTransferState.Working
            _githubSyncSettingsTransferState.value = when (val outcome = gitHubSyncSettingsTransfer.importFrom(source)) {
                GitHubSyncSettingsTransferOutcome.Success -> GitHubSyncSettingsTransferState.ImportComplete
                GitHubSyncSettingsTransferOutcome.MissingPassphrase -> GitHubSyncSettingsTransferState.MissingPassphrase
                is GitHubSyncSettingsTransferOutcome.Failed -> GitHubSyncSettingsTransferState.Failed(outcome.message)
            }
        }
    }

    fun dismissGitHubSyncSettingsTransferState() {
        _githubSyncSettingsTransferState.value = GitHubSyncSettingsTransferState.Idle
    }

    fun testGitHubConnection() {
        viewModelScope.launch {
            _githubConnectionTestState.value = GitHubConnectionTestState.Working
            _githubConnectionTestState.value = when (val outcome = gitHubConnectionTester.test()) {
                GitHubConnectionTestOutcome.Connected -> GitHubConnectionTestState.Connected
                GitHubConnectionTestOutcome.ReadyForInitialSync -> GitHubConnectionTestState.ReadyForInitialSync
                GitHubConnectionTestOutcome.MissingConfig -> GitHubConnectionTestState.MissingConfig
                is GitHubConnectionTestOutcome.Failed -> GitHubConnectionTestState.Failed(outcome.message)
            }
        }
    }

    fun dismissGitHubConnectionTestState() {
        _githubConnectionTestState.value = GitHubConnectionTestState.Idle
    }

    fun inspectRestoreFile(uri: Uri) {
        if (_backupState.value == BackupUiState.Creating || _backupState.value == BackupUiState.Restoring) return
        restoreInspectionJob?.cancel()
        restoreInspectionJob = viewModelScope.launch {
            _restorePreview.value = RestorePreviewState.Loading
            _restorePreview.value = when (val outcome = backupManager.inspectBackup(uri)) {
                is InspectOutcome.Success -> RestorePreviewState.Ready(uri, outcome.inspection)
                is InspectOutcome.Failed -> RestorePreviewState.Failed(outcome.message)
            }
        }
    }

    fun dismissRestorePreview() {
        restoreInspectionJob?.cancel()
        restoreInspectionJob = null
        _restorePreview.value = RestorePreviewState.Idle
    }
}

private val SupportedFontExtensions = setOf("ttf", "otf", "woff", "woff2")

private fun copyReaderFont(context: Context, storageRoots: StorageRoots, source: Uri): Result<ImportedFont> = runCatching {
    val displayName = context.contentResolver.displayName(source)
    val extension = displayName.substringAfterLast('.', missingDelimiterValue = "")
        .lowercase(Locale.US)
        .takeIf { it in SupportedFontExtensions }
        ?: throw IllegalArgumentException("Choose a .ttf, .otf, .woff, or .woff2 font file")
    val id = UUID.randomUUID().toString()
    val fileName = "$id.$extension"
    val destination = storageRoots.fontsDir.resolve(fileName)
    context.contentResolver.openInputStream(source)?.use { input ->
        destination.outputStream().use { output -> input.copyTo(output) }
    } ?: throw IllegalArgumentException("Could not read the selected font file")
    ImportedFont(id = id, displayName = displayName.cleanDisplayName(), fileName = fileName)
}

/**
 * Deletes every file in the fonts directory no [fonts] entry still points at. Each import writes a new
 * `<uuid>.<ext>` file, so a replaced or dropped entry would otherwise leave its bytes on disk forever with
 * nothing left to reference them.
 */
private fun pruneOrphanFontFiles(storageRoots: StorageRoots, fonts: List<ImportedFont>) {
    val referenced = fonts.mapTo(mutableSetOf()) { it.fileName }
    storageRoots.fontsDir.listFiles()?.forEach { file ->
        if (file.isFile && file.name !in referenced) runCatching { file.delete() }
    }
}

private fun android.content.ContentResolver.displayName(uri: Uri): String {
    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) {
            val name = cursor.getString(index)
            if (!name.isNullOrBlank()) return name
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "Imported font"
}

private fun String.cleanDisplayName(): String =
    substringBeforeLast('.', missingDelimiterValue = this)
        .replace(Regex("\\s+"), " ")
        .trim()
        .ifBlank { "Imported font" }
