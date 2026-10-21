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
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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
)

@HiltViewModel
internal class PrivacyViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val _state = MutableStateFlow<PrivacyState?>(null)
    val state: StateFlow<PrivacyState?> = _state

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
        }
    }

    /** Stops any recognition and deletes the text recognised from every scanned book. */
    fun removeRecognizedText() {
        viewModelScope.launch {
            BookRecognition.stopAll(context)
            _state.value = withContext(dispatchers.io) {
                BookRecognition.store(context).deleteAll()
                _state.value?.withRecognizedText()
            }
        }
    }

    private fun PrivacyState.withRecognizedText(): PrivacyState {
        val (books, bytes) = BookRecognition.store(context).usage()
        return copy(recognizedTextBooks = books, recognizedTextBytes = bytes)
    }
}

private fun Context.deviceEncryption(): DeviceEncryption =
    when (getSystemService(DevicePolicyManager::class.java)?.storageEncryptionStatus) {
        DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE, DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER -> DeviceEncryption.ENCRYPTED
        DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY -> DeviceEncryption.ENCRYPTED_WITHOUT_LOCK
        DevicePolicyManager.ENCRYPTION_STATUS_INACTIVE, DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED -> DeviceEncryption.NOT_ENCRYPTED
        else -> DeviceEncryption.UNKNOWN
    }
