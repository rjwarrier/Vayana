package com.vayana.core.sync.github

import com.vayana.core.common.runCatchingCancellable
import com.vayana.core.datastore.settings.SettingsSnapshot
import com.vayana.core.sync.asset.GitHubContentsAssetStore
import com.vayana.core.sync.asset.GitHubRepository

data class GitHubSyncConfig(
    val owner: String,
    val repository: String,
    val branch: String,
    val token: String,
    val passphrase: String,
    val committerName: String,
) {
    val deviceSnapshotPath: String = "vayana/snapshots/${committerName.syncPathSegment()}.json"
}

fun SettingsSnapshot.isGitHubSyncReady(): Boolean =
    githubSyncEnabled && gitHubSyncConfig()?.canBuildRepository() == true

fun SettingsSnapshot.deviceLabelForSync(): String =
    kindleDeviceName.trim().ifBlank { "Vayana Sync" }

fun SettingsSnapshot.gitHubSyncConfig(): GitHubSyncConfig? {
    val owner = githubOwner.trim()
    val repository = githubRepository.trim()
    val branch = githubBranch.trim()
    val token = githubToken.trim()
    val passphrase = githubSyncPassphrase
    if (owner.isBlank() || repository.isBlank() || branch.isBlank() || token.isBlank() || passphrase.isBlank()) {
        return null
    }
    return GitHubSyncConfig(
        owner = owner,
        repository = repository,
        branch = branch,
        token = token,
        passphrase = passphrase,
        committerName = deviceLabelForSync(),
    )
}

fun GitHubSyncConfig.assetStore(): GitHubContentsAssetStore =
    GitHubContentsAssetStore(
        repository = GitHubRepository(
            owner = owner,
            name = repository,
            branch = branch,
        ),
        token = token,
        committerName = committerName,
        committerEmail = "$owner@users.noreply.github.com",
    )

fun GitHubSyncConfig.canBuildRepository(): Boolean =
    runCatchingCancellable {
        GitHubRepository(
            owner = owner,
            name = repository,
            branch = branch,
        )
    }.isSuccess

private fun String.syncPathSegment(): String =
    trim()
        .lowercase()
        .replace(Regex("[^a-z0-9._-]+"), "-")
        .trim('-')
        .take(80)
        .ifBlank { "vayana-sync" }
