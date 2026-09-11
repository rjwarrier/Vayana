package com.vayana.core.backup

import com.vayana.core.datastore.settings.SettingsRegistry
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PortableSettingsTest {
    @Test
    fun allowlistContainsOnlyPortablePreferences() {
        assertTrue(SettingsRegistry.ReaderFontSize.key in PortableSettings.allowlist)
        assertTrue(SettingsRegistry.ReaderTheme.key in PortableSettings.allowlist)
        assertTrue(SettingsRegistry.DailyReadingGoalMinutes.key in PortableSettings.allowlist)
        assertTrue(SettingsRegistry.DefaultCoverSource.key in PortableSettings.allowlist)

        assertFalse(SettingsRegistry.KindleDeviceName.key in PortableSettings.allowlist)
        assertFalse(SettingsRegistry.GithubSyncEnabled.key in PortableSettings.allowlist)
        assertFalse(SettingsRegistry.GithubOwner.key in PortableSettings.allowlist)
        assertFalse(SettingsRegistry.GithubRepository.key in PortableSettings.allowlist)
        assertFalse(SettingsRegistry.GithubBranch.key in PortableSettings.allowlist)
        assertFalse(SettingsRegistry.GithubToken.key in PortableSettings.allowlist)
        assertFalse(SettingsRegistry.GithubSyncPassphrase.key in PortableSettings.allowlist)
        assertFalse("appearance.display_profile" in PortableSettings.allowlist)
        assertFalse("appearance.motion" in PortableSettings.allowlist)
        assertFalse(SettingsRegistry.ReaderVolumeKeys.key in PortableSettings.allowlist)
        assertFalse(SettingsRegistry.ReaderKeepAwake.key in PortableSettings.allowlist)
        assertFalse(SettingsRegistry.LandscapeTwoColumnLayout.key in PortableSettings.allowlist)
    }
}
