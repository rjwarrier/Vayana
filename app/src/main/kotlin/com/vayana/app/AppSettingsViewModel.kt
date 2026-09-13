package com.vayana.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vayana.core.database.repository.BookRepository
import com.vayana.core.datastore.settings.SettingsRepository
import com.vayana.core.datastore.settings.SettingsSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class AppSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val bookRepository: BookRepository,
) : ViewModel() {
    /** Null until the first DataStore read. */
    val settings: StateFlow<SettingsSnapshot?> = settingsRepository.snapshot
        .map { snapshot ->
            // Installs from before onboarding existed already have a library; don't walk them through setup.
            if (!snapshot.onboardingCompleted && bookRepository.hasAnyBooks()) {
                settingsRepository.updateOnboardingCompleted(true)
                snapshot.copy(onboardingCompleted = true)
            } else {
                snapshot
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
