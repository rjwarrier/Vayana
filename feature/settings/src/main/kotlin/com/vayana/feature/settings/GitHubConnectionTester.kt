package com.vayana.feature.settings

import com.vayana.core.resources.uiText
import com.vayana.core.resources.LocalizedException
import com.vayana.core.resources.UiText
import com.vayana.core.resources.R
import com.vayana.core.common.DispatcherProvider
import com.vayana.core.datastore.settings.SettingsRegistry
import com.vayana.core.datastore.settings.SettingsRepository
import com.vayana.core.sync.asset.GitHubAssetStoreException
import com.vayana.core.sync.asset.GitHubConnectionTestResult
import com.vayana.core.sync.asset.GitHubContentsAssetStore
import com.vayana.core.sync.asset.GitHubRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

sealed interface GitHubConnectionTestOutcome {
    data object Connected : GitHubConnectionTestOutcome
    data object ReadyForInitialSync : GitHubConnectionTestOutcome
    data object MissingConfig : GitHubConnectionTestOutcome
    data class Failed(val message: UiText) : GitHubConnectionTestOutcome
}

class GitHubConnectionTester @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val dispatchers: DispatcherProvider,
) {
    suspend fun test(): GitHubConnectionTestOutcome = withContext(dispatchers.io) {
        val settings = settingsRepository.exportToMap(includeNonExportable = true)
        val owner = settings[SettingsRegistry.GithubOwner.key].orEmpty().trim()
        val repo = settings[SettingsRegistry.GithubRepository.key].orEmpty().trim()
        val branch = settings[SettingsRegistry.GithubBranch.key].orEmpty().trim().ifBlank { "main" }
        val token = settings[SettingsRegistry.GithubToken.key].orEmpty().trim()
        if (owner.isBlank() || repo.isBlank() || branch.isBlank() || token.isBlank()) {
            return@withContext GitHubConnectionTestOutcome.MissingConfig
        }

        try {
            val store = GitHubContentsAssetStore(
                repository = GitHubRepository(owner = owner, name = repo, branch = branch),
                token = token,
                committerName = "Vayana Sync",
                committerEmail = "sync@vayana.local",
                dispatcher = dispatchers.io,
            )
            when (store.testConnection()) {
                GitHubConnectionTestResult.Connected -> GitHubConnectionTestOutcome.Connected
                GitHubConnectionTestResult.ReadyForInitialSync -> GitHubConnectionTestOutcome.ReadyForInitialSync
            }
        } catch (exception: GitHubAssetStoreException) {
            // GitHub's own words: not translatable.
            GitHubConnectionTestOutcome.Failed(UiText.Raw("${exception.statusCode}: ${exception.responseBody}"))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            GitHubConnectionTestOutcome.Failed(throwable.uiText(R.string.settings_error_github_connection_test_failed))
        }
    }
}
