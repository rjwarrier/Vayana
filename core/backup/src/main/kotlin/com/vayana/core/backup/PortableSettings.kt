package com.vayana.core.backup

import com.vayana.core.datastore.settings.SettingsRegistry

object PortableSettings {
    val allowlist: Set<String> = setOf(
        SettingsRegistry.ReaderFontSize.key,
        SettingsRegistry.ReaderLineHeight.key,
        SettingsRegistry.ReaderFontFamily.key,
        SettingsRegistry.ReaderTheme.key,
        SettingsRegistry.ReaderSideMargin.key,
        SettingsRegistry.ReaderHeaderGap.key,
        SettingsRegistry.ReaderFooterGap.key,
        SettingsRegistry.ReaderPublisherStyles.key,
        SettingsRegistry.ReaderShowHeaders.key,
        SettingsRegistry.ReaderShowFooter.key,
        SettingsRegistry.ReaderTapZoneMode.key,
        SettingsRegistry.ReaderAutoMarkSelection.key,
        SettingsRegistry.ReaderBionicReading.key,
        SettingsRegistry.DailyReadingGoalMinutes.key,
        SettingsRegistry.YearlyBooksGoal.key,
    )
}
