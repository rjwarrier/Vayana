package com.vayana.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vayana.core.common.runCatchingCancellable
import com.vayana.core.database.repository.BookRepository
import com.vayana.core.datastore.settings.SettingsRepository
import com.vayana.core.datastore.settings.NavigationMode
import com.vayana.core.datastore.settings.SettingsRegistry
import com.vayana.core.datastore.settings.SettingsSnapshot
import com.vayana.feature.library.IncomingBookFiles
import com.vayana.feature.library.RecentlyDeletedAutoPurge
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AppSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val bookRepository: BookRepository,
    private val recentlyDeletedAutoPurge: RecentlyDeletedAutoPurge,
    incomingBookFiles: IncomingBookFiles,
) : ViewModel() {
    /** True while book files from another app wait to be imported, so the app can bring the library forward. */
    val hasIncomingBooks: StateFlow<Boolean> = incomingBookFiles.pending
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Null until the first DataStore read and the one-off existing-library check are done. */
    val settings: StateFlow<SettingsSnapshot?> = flow {
        // Installs from before onboarding existed already have a library; don't walk them through setup.
        if (!settingsRepository.snapshot.first().onboardingCompleted && bookRepository.hasAnyBooks()) {
            settingsRepository.updateOnboardingCompleted(true)
        }
        emitAll(settingsRepository.snapshot)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // At launch, and again whenever the user shortens how long Recently deleted keeps books.
        viewModelScope.launch {
            settingsRepository.snapshot
                .map { it.recentlyDeletedRetention.days }
                .distinctUntilChanged()
                .collect { days -> runCatchingCancellable { recentlyDeletedAutoPurge.purgeExpired(days) } }
        }
    }

    /** The most recently read book that can open in the reader right now, for "Open on: Continue last book". */
    suspend fun lastReadBookId(): Long? = bookRepository.lastReadOpenableBookId()

    fun toggleNavigationMode() {
        val currentMode = settings.value?.navigationMode ?: return
        val nextMode = when (currentMode) {
            NavigationMode.BOTTOM_BAR -> NavigationMode.FLOATING_BAR
            NavigationMode.FLOATING_BAR -> NavigationMode.BOTTOM_BAR
        }
        viewModelScope.launch {
            settingsRepository.update(SettingsRegistry.NavigationMode, nextMode)
        }
    }
}
