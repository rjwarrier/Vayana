package com.vayana.feature.settings

import android.content.Context
import android.net.Uri
import com.vayana.core.common.DispatcherProvider
import com.vayana.core.common.runCatchingCancellable
import com.vayana.core.datastore.settings.SettingsRegistry
import com.vayana.core.datastore.settings.SettingsRepository
import com.vayana.core.sync.asset.CloudAssetCipher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import org.json.JSONObject

sealed interface GitHubSyncSettingsTransferOutcome {
    data object Success : GitHubSyncSettingsTransferOutcome
    data object MissingPassphrase : GitHubSyncSettingsTransferOutcome
    data class Failed(val message: String) : GitHubSyncSettingsTransferOutcome
}

@Singleton
class GitHubSyncSettingsTransfer @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val dispatchers: DispatcherProvider,
) {
    private val cipher = CloudAssetCipher()

    suspend fun exportTo(destination: Uri): GitHubSyncSettingsTransferOutcome = withContext(dispatchers.io) {
        val settings = settingsRepository.exportToMap()
        val passphrase = settings[SettingsRegistry.GithubSyncPassphrase.key].orEmpty().toCharArray()
        if (passphrase.isEmpty()) return@withContext GitHubSyncSettingsTransferOutcome.MissingPassphrase
        try {
            val plaintext = buildPlaintext(settings).toByteArray(Charsets.UTF_8)
            val encrypted = cipher.encrypt(plaintext, passphrase, TransferAad)
            check(encrypted.size <= MaxEncryptedTransferBytes) { "GitHub sync settings export is too large" }
            val output = context.contentResolver.openOutputStream(destination)
                ?: return@withContext GitHubSyncSettingsTransferOutcome.Failed("Could not open the selected location")
            output.use { it.write(encrypted) }
            GitHubSyncSettingsTransferOutcome.Success
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            runCatchingCancellable { context.contentResolver.delete(destination, null, null) }
            GitHubSyncSettingsTransferOutcome.Failed(throwable.message ?: "GitHub sync settings export failed")
        } finally {
            passphrase.fill('\u0000')
        }
    }

    suspend fun importFrom(source: Uri): GitHubSyncSettingsTransferOutcome = withContext(dispatchers.io) {
        val currentPassphrase = settingsRepository.exportToMap()[SettingsRegistry.GithubSyncPassphrase.key].orEmpty().toCharArray()
        if (currentPassphrase.isEmpty()) return@withContext GitHubSyncSettingsTransferOutcome.MissingPassphrase
        try {
            val input = context.contentResolver.openInputStream(source)
                ?: return@withContext GitHubSyncSettingsTransferOutcome.Failed("Could not open the selected file")
            val encrypted = input.use { it.readBytesLimited(MaxEncryptedTransferBytes) }
            val plaintext = cipher.decrypt(encrypted, currentPassphrase, TransferAad)
            check(plaintext.size <= MaxPlaintextTransferBytes) { "GitHub sync settings import is too large" }
            val values = parsePlaintext(plaintext.toString(Charsets.UTF_8))
            settingsRepository.importFromMap(values)
            GitHubSyncSettingsTransferOutcome.Success
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            GitHubSyncSettingsTransferOutcome.Failed(throwable.message ?: "GitHub sync settings import failed")
        } finally {
            currentPassphrase.fill('\u0000')
        }
    }

    private fun buildPlaintext(settings: Map<String, String>): String {
        val syncSettings = JSONObject()
        GitHubSyncSettingKeys.forEach { key ->
            settings[key]?.let { syncSettings.put(key, it) }
        }
        return JSONObject()
            .put("format", "vayana.github-sync-settings")
            .put("formatVersion", TransferVersion)
            .put("exportedAt", System.currentTimeMillis())
            .put("settings", syncSettings)
            .toString()
    }

    private fun parsePlaintext(jsonText: String): Map<String, String> {
        require(jsonText.length <= MaxPlaintextTransferBytes) { "GitHub sync settings import is too large" }
        val root = JSONObject(jsonText)
        require(root.optString("format") == "vayana.github-sync-settings") { "This is not a Vayana GitHub sync settings file" }
        require(root.optInt("formatVersion") == TransferVersion) { "Unsupported GitHub sync settings version" }
        val settings = root.getJSONObject("settings")
        return buildMap {
            GitHubSyncSettingKeys.forEach { key ->
                if (settings.has(key) && !settings.isNull(key)) {
                    val value = settings.optString(key)
                    require(value.length <= MaxSettingValueChars) { "GitHub sync settings value is too large" }
                    put(key, value)
                }
            }
        }
    }
}

private fun java.io.InputStream.readBytesLimited(maxBytes: Int): ByteArray {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    val output = java.io.ByteArrayOutputStream()
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read == -1) break
        total += read
        check(total <= maxBytes) { "GitHub sync settings file is too large" }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

private val GitHubSyncSettingKeys = listOf(
    SettingsRegistry.GithubSyncEnabled.key,
    SettingsRegistry.KindleDeviceName.key,
    SettingsRegistry.GithubOwner.key,
    SettingsRegistry.GithubRepository.key,
    SettingsRegistry.GithubBranch.key,
    SettingsRegistry.GithubToken.key,
    SettingsRegistry.GithubSyncPassphrase.key,
)

private val TransferAad = "vayana.github-sync-settings.v1".toByteArray(Charsets.UTF_8)
private const val TransferVersion = 1
private const val MaxPlaintextTransferBytes = 64 * 1024
private const val MaxEncryptedTransferBytes = 128 * 1024
private const val MaxSettingValueChars = 1024
