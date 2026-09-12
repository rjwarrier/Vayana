package com.vayana.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vayana.core.datastore.settings.SettingsRegistry
import com.vayana.core.datastore.settings.SettingsRepository
import com.vayana.core.designsystem.theme.DisplayProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    fun chooseDisplayProfile(profile: DisplayProfile) {
        viewModelScope.launch {
            settingsRepository.update(SettingsRegistry.DisplayProfile, profile)
        }
    }

    fun complete() {
        viewModelScope.launch {
            settingsRepository.updateOnboardingCompleted(true)
        }
    }
}
