package com.vayana.feature.settings

import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vayana.core.common.DispatcherProvider
import com.vayana.core.datastore.settings.SettingsRepository
import com.vayana.core.sync.github.assetStore
import com.vayana.core.sync.github.gitHubSyncConfig
import com.vayana.format.ocr.BookRecognition
import com.vayana.format.ocr.OcrModel
import com.vayana.format.ocr.OcrStore
import com.vayana.core.database.repository.BookRepository
import com.vayana.core.resources.UiText
import com.vayana.core.resources.R
import com.vayana.core.common.runCatchingCancellable
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class DeviceEncryption { ENCRYPTED, ENCRYPTED_WITHOUT_LOCK, NOT_ENCRYPTED, UNKNOWN }

/** What this device and these settings actually do with the reader's data, checked when the screen opens. */
internal data class PrivacyState(
    val deviceEncryption: DeviceEncryption,
    val screenLock: Boolean,
    /** Android 11+ keeps the app-specific folder holding book files away from other apps. */
    val bookFilesPrivate: Boolean,
    /** "owner/repository" when GitHub sync is on and set up; null when off. */
    val syncRepository: String?,
    /** As GitHub reports it; null when sync is off or GitHub couldn't be asked. */
    val repositoryPrivate: Boolean?,
    /** Scanned books with recognised text kept on this phone, and its size. */
    val recognizedTextBooks: Int = 0,
    val recognizedTextBytes: Long = 0,
    val recognizedBooks: List<RecognizedBookStorage> = emptyList(),
    val recognitionModels: List<RecognitionModelStorage> = emptyList(),
    val storageBusy: Boolean = false,
    val storageError: UiText? = null,
)

internal data class RecognizedBookStorage(val hash: String, val title: String?, val bytes: Long)
internal data class RecognitionModelStorage(val model: OcrModel, val bytes: Long, val busy: Boolean)

@HiltViewModel
internal class PrivacyViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val dispatchers: DispatcherProvider,
    private val books: BookRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<PrivacyState?>(null)
    val state: StateFlow<PrivacyState?> = _state
    private val storageRefresh = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            _state.value = withContext(dispatchers.io) {
                val settings = settingsRepository.snapshot.first()
                val config = settings.gitHubSyncConfig()?.takeIf { settings.githubSyncEnabled }
                PrivacyState(
                    deviceEncryption = context.deviceEncryption(),
                    screenLock = context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true,
                    bookFilesPrivate = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
                    syncRepository = config?.let { "${it.owner}/${it.repository}" },
                    repositoryPrivate = config?.let { runCatching { it.assetStore().repositoryIsPrivate() }.getOrNull() },
                ).withRecognizedText()
            }
            val recognition = BookRecognition.jobs(context).map { jobs -> jobs.filterNot { it.state.isFinished }.flatMap { it.tags }.toSet() }.distinctUntilChanged()
            val preparation = WorkManager.getInstance(context).getWorkInfosByTagFlow(BookRecognition.PreparationTag)
                .map { jobs -> jobs.filterNot { it.state.isFinished }.flatMap { it.tags }.toSet() }.distinctUntilChanged()
            combine(OcrStore.changes, recognition, preparation, storageRefresh) { _, running, queued, _ -> running + queued }
                .collect { tags ->
                    val updated = withContext(dispatchers.io) { _state.value?.withRecognizedText(tags) }
                    _state.update { current -> updated?.copy(storageBusy = current?.storageBusy ?: false, storageError = current?.storageError) }
                }
        }
    }

    /** Stops any recognition and deletes the text recognised from every scanned book. */
    fun removeRecognizedText() {
        storageAction { BookRecognition.removeAll(context); BookRecognition.store(context).bookUsage().isEmpty() }
    }

    fun removeBookText(hash: String) = storageAction {
        BookRecognition.remove(context, hash)
        hash !in BookRecognition.store(context).bookUsage()
    }

    fun removeModel(model: OcrModel) = storageAction { BookRecognition.removeModel(context, model) }

    private fun storageAction(action: suspend () -> Boolean) {
        if (_state.value?.storageBusy == true) return
        _state.update { it?.copy(storageBusy = true, storageError = null) }
        viewModelScope.launch {
            val removed = runCatchingCancellable { withContext(dispatchers.io) { action() } }.getOrDefault(false)
            _state.update { it?.copy(storageBusy = false, storageError = if (removed) null else UiText.Res(R.string.ocr_storage_error)) }
            storageRefresh.update { it + 1 }
        }
    }

    private suspend fun PrivacyState.withRecognizedText(activeTags: Set<String> = emptySet()): PrivacyState {
        val titles = books.observeAll().first().associateBy { it.fileHash }
        val usage = BookRecognition.store(context).bookUsage()
        val models = BookRecognition.models(context)
        return copy(recognizedTextBooks = usage.size, recognizedTextBytes = usage.values.sum(),
            recognizedBooks = usage.map { (hash, bytes) -> RecognizedBookStorage(hash, titles[hash]?.title, bytes) }.sortedBy { it.title },
            recognitionModels = OcrModel.entries.map { model -> RecognitionModelStorage(model, models.installedBytes(model),
                BookRecognition.modelTag(model) in activeTags || (activeTags.isNotEmpty() && activeTags.none { it.startsWith("ocr-model:") })) }
                .filter { it.bytes > 0 })
    }
}

private fun Context.deviceEncryption(): DeviceEncryption =
    when (getSystemService(DevicePolicyManager::class.java)?.storageEncryptionStatus) {
        DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE, DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER -> DeviceEncryption.ENCRYPTED
        DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY -> DeviceEncryption.ENCRYPTED_WITHOUT_LOCK
        DevicePolicyManager.ENCRYPTION_STATUS_INACTIVE, DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED -> DeviceEncryption.NOT_ENCRYPTED
        else -> DeviceEncryption.UNKNOWN
    }
