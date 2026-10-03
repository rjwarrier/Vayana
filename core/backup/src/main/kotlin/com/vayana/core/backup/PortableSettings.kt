package com.vayana.core.backup

import com.vayana.core.datastore.settings.SettingsRegistry

object PortableSettings {
    val allowlist: Set<String> = setOf(
        SettingsRegistry.SmartShelves.key,
        SettingsRegistry.ReadingPresets.key,
        SettingsRegistry.ReadingPlans.key,
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
        SettingsRegistry.ReaderControlsTapMode.key,
        SettingsRegistry.ReaderAutoMarkSelection.key,
        SettingsRegistry.ReaderBionicReading.key,
        SettingsRegistry.ReaderTextAlign.key,
        SettingsRegistry.ReaderHyphenation.key,
        SettingsRegistry.DailyReadingGoalMinutes.key,
        SettingsRegistry.YearlyBooksGoal.key,
        SettingsRegistry.DefaultCoverSource.key,
        SettingsRegistry.FinishedPercent.key,
        SettingsRegistry.RecentlyDeletedRetention.key,
        SettingsRegistry.WeekStart.key,
        SettingsRegistry.ReadAloudRate.key,
        // SettingsRegistry.DateFormat's key; its value type lives in :core:designsystem, which this module can't see.
        "appearance.date_format",
    )
}
