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
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class AppSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val bookRepository: BookRepository,
) : ViewModel() {
    /** Null until the first DataStore read and the one-off existing-library check are done. */
    val settings: StateFlow<SettingsSnapshot?> = flow {
        // Installs from before onboarding existed already have a library; don't walk them through setup.
        if (!settingsRepository.snapshot.first().onboardingCompleted && bookRepository.hasAnyBooks()) {
            settingsRepository.updateOnboardingCompleted(true)
        }
        emitAll(settingsRepository.snapshot)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
