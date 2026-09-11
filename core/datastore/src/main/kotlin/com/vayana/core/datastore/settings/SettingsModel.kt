package com.vayana.core.datastore.settings

import androidx.annotation.StringRes
import com.vayana.core.designsystem.theme.DarkVariant
import com.vayana.core.designsystem.theme.DisplayProfile
import com.vayana.core.designsystem.theme.MotionSetting
import com.vayana.core.designsystem.theme.ThemeMode
import com.vayana.core.resources.R

enum class SettingsGroup(
    @param:StringRes val titleRes: Int,
    @param:StringRes val subtitleRes: Int,
) {
    APPEARANCE(R.string.settings_group_appearance, R.string.settings_group_appearance_subtitle),
    READER_TYPOGRAPHY(R.string.settings_group_reader_typography, R.string.settings_group_reader_typography_subtitle),
    READER_LAYOUT(R.string.settings_group_reader_layout, R.string.settings_group_reader_layout_subtitle),
    READER_BEHAVIOR(R.string.settings_group_reader_behavior, R.string.settings_group_reader_behavior_subtitle),
    GOALS(R.string.settings_group_goals, R.string.settings_group_goals_subtitle),
    SYNC(R.string.settings_group_sync, R.string.settings_group_sync_subtitle),
    MAINTENANCE(R.string.settings_group_maintenance, R.string.settings_group_maintenance_subtitle),
}

sealed class Setting<T : Any>(
    val key: String,
    val defaultValue: T,
    @param:StringRes val titleRes: Int,
    @param:StringRes val subtitleRes: Int?,
    val group: SettingsGroup,
)

class BooleanSetting(
    key: String,
    defaultValue: Boolean,
    @StringRes titleRes: Int,
    @StringRes subtitleRes: Int?,
    group: SettingsGroup,
) : Setting<Boolean>(key, defaultValue, titleRes, subtitleRes, group)

class IntSetting(
    key: String,
    defaultValue: Int,
    @StringRes titleRes: Int,
    @StringRes subtitleRes: Int?,
    group: SettingsGroup,
    val range: IntRange,
    val step: Int,
) : Setting<Int>(key, defaultValue, titleRes, subtitleRes, group)

class FloatSetting(
    key: String,
    defaultValue: Float,
    @StringRes titleRes: Int,
    @StringRes subtitleRes: Int?,
    group: SettingsGroup,
    val range: ClosedFloatingPointRange<Float>,
    val step: Float,
) : Setting<Float>(key, defaultValue, titleRes, subtitleRes, group)

class StringSetting(
    key: String,
    defaultValue: String,
    @StringRes titleRes: Int,
    @StringRes subtitleRes: Int?,
    group: SettingsGroup,
    val maxLength: Int = 160,
    val secure: Boolean = false,
    val exportable: Boolean = true,
) : Setting<String>(key, defaultValue, titleRes, subtitleRes, group)

class ChoiceSetting<T>(
    key: String,
    defaultValue: T,
    @StringRes titleRes: Int,
    @StringRes subtitleRes: Int?,
    group: SettingsGroup,
    val options: List<ChoiceOption<T>>,
) : Setting<T>(key, defaultValue, titleRes, subtitleRes, group) where T : Enum<T>

data class ChoiceOption<T : Any>(val value: T, @param:StringRes val labelRes: Int)

data class ImportedFont(
    val id: String,
    val displayName: String,
    val fileName: String,
)

data class SettingsSnapshot(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val displayProfile: DisplayProfile = DisplayProfile.STANDARD,
    val darkVariant: DarkVariant = DarkVariant.STANDARD,
    val motionSetting: MotionSetting = MotionSetting.FULL,
    val readerFontSizePercent: Int = 100,
    val readerLineHeight: Float = 1.5f,
    val readerFontFamily: ReaderFontFamily = ReaderFontFamily.SERIF,
    val readerImportedFonts: List<ImportedFont> = emptyList(),
    val readerCustomFontId: String? = null,
    val readerTheme: ReaderTheme = ReaderTheme.SYSTEM,
    val readerSideMarginPercent: Int = 10,
    val readerHeaderGapDp: Int = 60,
    val readerFooterGapDp: Int = 8,
    val readerUsePublisherStyles: Boolean = true,
    val readerTapZoneMode: TapZoneMode = TapZoneMode.THREE_ZONE,
    val readerVolumeKeys: Boolean = false,
    val readerKeepAwake: Boolean = false,
    val readerShowHeaders: Boolean = true,
    val readerShowFooter: Boolean = true,
    val readerAutoMarkSelection: Boolean = false,
    val readerBionicReading: Boolean = false,
    val dailyReadingGoalMinutes: Int = 20,
    val yearlyBooksGoal: Int = 12,
    val defaultCoverSource: DefaultCoverSource = DefaultCoverSource.YOURS,
    val landscapeTwoColumnLayout: Boolean = true,
    val kindleDeviceName: String = "My Vayana",
    val githubSyncEnabled: Boolean = false,
    val githubOwner: String = "",
    val githubRepository: String = "",
    val githubBranch: String = "main",
    val githubToken: String = "",
    val githubSyncPassphrase: String = "",
)

enum class ReaderFontFamily { SERIF, SANS, MONO }

enum class ReaderTheme { SYSTEM, LIGHT, PAPER, SEPIA, MINT, SKY, ROSE, DARK, OLED }

enum class TapZoneMode { THREE_ZONE }

enum class DefaultCoverSource { YOURS, GOODREADS }

object SettingsRegistry {
    val ThemeMode: ChoiceSetting<com.vayana.core.designsystem.theme.ThemeMode> =
        ChoiceSetting<com.vayana.core.designsystem.theme.ThemeMode>(
        key = "appearance.theme_mode",
        defaultValue = com.vayana.core.designsystem.theme.ThemeMode.SYSTEM,
        titleRes = R.string.settings_theme_mode_title,
        subtitleRes = R.string.settings_theme_mode_subtitle,
        group = SettingsGroup.APPEARANCE,
        options = listOf<ChoiceOption<com.vayana.core.designsystem.theme.ThemeMode>>(
            ChoiceOption(com.vayana.core.designsystem.theme.ThemeMode.SYSTEM, R.string.settings_theme_mode_system),
            ChoiceOption(com.vayana.core.designsystem.theme.ThemeMode.LIGHT, R.string.settings_theme_mode_light),
            ChoiceOption(com.vayana.core.designsystem.theme.ThemeMode.DARK, R.string.settings_theme_mode_dark),
        ),
    )
    val DisplayProfile: ChoiceSetting<com.vayana.core.designsystem.theme.DisplayProfile> =
        ChoiceSetting<com.vayana.core.designsystem.theme.DisplayProfile>(
        key = "appearance.display_profile",
        defaultValue = com.vayana.core.designsystem.theme.DisplayProfile.STANDARD,
        titleRes = R.string.settings_display_profile_title,
        subtitleRes = R.string.settings_display_profile_subtitle,
        group = SettingsGroup.APPEARANCE,
        options = listOf<ChoiceOption<com.vayana.core.designsystem.theme.DisplayProfile>>(
            ChoiceOption(com.vayana.core.designsystem.theme.DisplayProfile.STANDARD, R.string.settings_display_profile_standard),
            ChoiceOption(com.vayana.core.designsystem.theme.DisplayProfile.E_INK, R.string.settings_display_profile_eink),
        ),
    )
    val DarkVariant: ChoiceSetting<com.vayana.core.designsystem.theme.DarkVariant> =
        ChoiceSetting<com.vayana.core.designsystem.theme.DarkVariant>(
        key = "appearance.dark_variant",
        defaultValue = com.vayana.core.designsystem.theme.DarkVariant.STANDARD,
        titleRes = R.string.settings_dark_variant_title,
        subtitleRes = R.string.settings_dark_variant_subtitle,
        group = SettingsGroup.APPEARANCE,
        options = listOf<ChoiceOption<com.vayana.core.designsystem.theme.DarkVariant>>(
            ChoiceOption(com.vayana.core.designsystem.theme.DarkVariant.STANDARD, R.string.settings_dark_variant_standard),
            ChoiceOption(com.vayana.core.designsystem.theme.DarkVariant.SOFTER, R.string.settings_dark_variant_softer),
            ChoiceOption(com.vayana.core.designsystem.theme.DarkVariant.TRUE_BLACK, R.string.settings_dark_variant_true_black),
        ),
    )
    val Motion: ChoiceSetting<com.vayana.core.designsystem.theme.MotionSetting> =
        ChoiceSetting<com.vayana.core.designsystem.theme.MotionSetting>(
        key = "appearance.motion",
        defaultValue = com.vayana.core.designsystem.theme.MotionSetting.FULL,
        titleRes = R.string.settings_motion_title,
        subtitleRes = R.string.settings_motion_subtitle,
        group = SettingsGroup.APPEARANCE,
        options = listOf<ChoiceOption<com.vayana.core.designsystem.theme.MotionSetting>>(
            ChoiceOption(com.vayana.core.designsystem.theme.MotionSetting.FULL, R.string.settings_motion_full),
            ChoiceOption(com.vayana.core.designsystem.theme.MotionSetting.REDUCED, R.string.settings_motion_reduced),
            ChoiceOption(com.vayana.core.designsystem.theme.MotionSetting.OFF, R.string.settings_motion_off),
        ),
    )
    val ReaderFontSize: IntSetting = IntSetting(
        key = "reader.font_size_percent",
        defaultValue = 100,
        titleRes = R.string.settings_reader_font_size_title,
        subtitleRes = R.string.settings_reader_font_size_subtitle,
        group = SettingsGroup.READER_TYPOGRAPHY,
        range = 80..250,
        step = 5,
    )
    val ReaderLineHeight: FloatSetting = FloatSetting(
        key = "reader.line_height",
        defaultValue = 1.5f,
        titleRes = R.string.settings_reader_line_height_title,
        subtitleRes = R.string.settings_reader_line_height_subtitle,
        group = SettingsGroup.READER_TYPOGRAPHY,
        range = 1.2f..4.0f,
        step = 0.1f,
    )
    val ReaderFontFamily: ChoiceSetting<com.vayana.core.datastore.settings.ReaderFontFamily> =
        ChoiceSetting<com.vayana.core.datastore.settings.ReaderFontFamily>(
        key = "reader.font_family",
        defaultValue = com.vayana.core.datastore.settings.ReaderFontFamily.SERIF,
        titleRes = R.string.settings_reader_font_family_title,
        subtitleRes = R.string.settings_reader_font_family_subtitle,
        group = SettingsGroup.READER_TYPOGRAPHY,
        options = listOf<ChoiceOption<com.vayana.core.datastore.settings.ReaderFontFamily>>(
            ChoiceOption(com.vayana.core.datastore.settings.ReaderFontFamily.SERIF, R.string.settings_reader_font_family_serif),
            ChoiceOption(com.vayana.core.datastore.settings.ReaderFontFamily.SANS, R.string.settings_reader_font_family_sans),
            ChoiceOption(com.vayana.core.datastore.settings.ReaderFontFamily.MONO, R.string.settings_reader_font_family_mono),
        ),
    )
    val ReaderTheme: ChoiceSetting<com.vayana.core.datastore.settings.ReaderTheme> =
        ChoiceSetting<com.vayana.core.datastore.settings.ReaderTheme>(
        key = "reader.theme",
        defaultValue = com.vayana.core.datastore.settings.ReaderTheme.SYSTEM,
        titleRes = R.string.settings_reader_theme_title,
        subtitleRes = R.string.settings_reader_theme_subtitle,
        group = SettingsGroup.READER_TYPOGRAPHY,
        options = listOf<ChoiceOption<com.vayana.core.datastore.settings.ReaderTheme>>(
            ChoiceOption(com.vayana.core.datastore.settings.ReaderTheme.SYSTEM, R.string.settings_reader_theme_system),
            ChoiceOption(com.vayana.core.datastore.settings.ReaderTheme.LIGHT, R.string.settings_reader_theme_light),
            ChoiceOption(com.vayana.core.datastore.settings.ReaderTheme.PAPER, R.string.settings_reader_theme_paper),
            ChoiceOption(com.vayana.core.datastore.settings.ReaderTheme.SEPIA, R.string.settings_reader_theme_sepia),
            ChoiceOption(com.vayana.core.datastore.settings.ReaderTheme.MINT, R.string.settings_reader_theme_mint),
            ChoiceOption(com.vayana.core.datastore.settings.ReaderTheme.SKY, R.string.settings_reader_theme_sky),
            ChoiceOption(com.vayana.core.datastore.settings.ReaderTheme.ROSE, R.string.settings_reader_theme_rose),
            ChoiceOption(com.vayana.core.datastore.settings.ReaderTheme.DARK, R.string.settings_reader_theme_dark),
            ChoiceOption(com.vayana.core.datastore.settings.ReaderTheme.OLED, R.string.settings_reader_theme_oled),
        ),
    )
    val ReaderSideMargin: IntSetting = IntSetting(
        key = "reader.side_margin_percent",
        defaultValue = 10,
        titleRes = R.string.settings_reader_side_margin_title,
        subtitleRes = R.string.settings_reader_side_margin_subtitle,
        group = SettingsGroup.READER_LAYOUT,
        range = 0..24,
        step = 2,
    )
    val ReaderHeaderGap: IntSetting = IntSetting(
        key = "reader.header_gap_dp",
        defaultValue = 60,
        titleRes = R.string.settings_reader_header_gap_title,
        subtitleRes = R.string.settings_reader_header_gap_subtitle,
        group = SettingsGroup.READER_LAYOUT,
        range = 0..120,
        step = 4,
    )
    val ReaderFooterGap: IntSetting = IntSetting(
        key = "reader.footer_gap_dp",
        defaultValue = 8,
        titleRes = R.string.settings_reader_footer_gap_title,
        subtitleRes = R.string.settings_reader_footer_gap_subtitle,
        group = SettingsGroup.READER_LAYOUT,
        range = 0..80,
        step = 4,
    )
    val ReaderPublisherStyles: BooleanSetting = BooleanSetting(
        key = "reader.publisher_styles",
        defaultValue = true,
        titleRes = R.string.settings_reader_publisher_styles_title,
        subtitleRes = R.string.settings_reader_publisher_styles_subtitle,
        group = SettingsGroup.READER_LAYOUT,
    )
    val ReaderTapZoneMode: ChoiceSetting<TapZoneMode> = ChoiceSetting<TapZoneMode>(
        key = "reader.tap_zone_mode",
        defaultValue = TapZoneMode.THREE_ZONE,
        titleRes = R.string.settings_reader_tap_zone_title,
        subtitleRes = R.string.settings_reader_tap_zone_subtitle,
        group = SettingsGroup.READER_BEHAVIOR,
        options = listOf<ChoiceOption<TapZoneMode>>(ChoiceOption(TapZoneMode.THREE_ZONE, R.string.settings_reader_tap_zone_three_zone)),
    )
    val ReaderVolumeKeys: BooleanSetting = BooleanSetting(
        key = "reader.volume_keys",
        defaultValue = false,
        titleRes = R.string.settings_reader_volume_keys_title,
        subtitleRes = R.string.settings_reader_volume_keys_subtitle,
        group = SettingsGroup.READER_BEHAVIOR,
    )
    val ReaderKeepAwake: BooleanSetting = BooleanSetting(
        key = "reader.keep_awake",
        defaultValue = false,
        titleRes = R.string.settings_reader_keep_awake_title,
        subtitleRes = R.string.settings_reader_keep_awake_subtitle,
        group = SettingsGroup.READER_BEHAVIOR,
    )
    val ReaderShowHeaders: BooleanSetting = BooleanSetting(
        key = "reader.show_headers",
        defaultValue = true,
        titleRes = R.string.settings_reader_show_headers_title,
        subtitleRes = R.string.settings_reader_show_headers_subtitle,
        group = SettingsGroup.READER_LAYOUT,
    )
    val ReaderShowFooter: BooleanSetting = BooleanSetting(
        key = "reader.show_footer",
        defaultValue = true,
        titleRes = R.string.settings_reader_show_footer_title,
        subtitleRes = R.string.settings_reader_show_footer_subtitle,
        group = SettingsGroup.READER_LAYOUT,
    )
    val ReaderAutoMarkSelection: BooleanSetting = BooleanSetting(
        key = "reader.auto_mark_selection",
        defaultValue = false,
        titleRes = R.string.settings_reader_auto_mark_selection_title,
        subtitleRes = R.string.settings_reader_auto_mark_selection_subtitle,
        group = SettingsGroup.READER_BEHAVIOR,
    )
    val ReaderBionicReading: BooleanSetting = BooleanSetting(
        key = "reader.bionic_reading",
        defaultValue = false,
        titleRes = R.string.settings_reader_bionic_reading_title,
        subtitleRes = R.string.settings_reader_bionic_reading_subtitle,
        group = SettingsGroup.READER_TYPOGRAPHY,
    )

    val DailyReadingGoalMinutes: IntSetting = IntSetting(
        key = "goals.daily_reading_minutes",
        defaultValue = 20,
        titleRes = R.string.settings_daily_reading_goal_title,
        subtitleRes = R.string.settings_daily_reading_goal_subtitle,
        group = SettingsGroup.GOALS,
        range = 0..180,
        step = 5,
    )
    val YearlyBooksGoal: IntSetting = IntSetting(
        key = "goals.yearly_books",
        defaultValue = 12,
        titleRes = R.string.settings_yearly_books_goal_title,
        subtitleRes = R.string.settings_yearly_books_goal_subtitle,
        group = SettingsGroup.GOALS,
        range = 0..100,
        step = 1,
    )
    val DefaultCoverSource: ChoiceSetting<com.vayana.core.datastore.settings.DefaultCoverSource> =
        ChoiceSetting<com.vayana.core.datastore.settings.DefaultCoverSource>(
        key = "library.default_cover_source",
        defaultValue = com.vayana.core.datastore.settings.DefaultCoverSource.YOURS,
        titleRes = R.string.settings_default_cover_source_title,
        subtitleRes = R.string.settings_default_cover_source_subtitle,
        group = SettingsGroup.APPEARANCE,
        options = listOf<ChoiceOption<com.vayana.core.datastore.settings.DefaultCoverSource>>(
            ChoiceOption(com.vayana.core.datastore.settings.DefaultCoverSource.YOURS, R.string.settings_default_cover_source_yours),
            ChoiceOption(com.vayana.core.datastore.settings.DefaultCoverSource.GOODREADS, R.string.settings_default_cover_source_goodreads),
        ),
    )
    val LandscapeTwoColumnLayout: BooleanSetting = BooleanSetting(
        key = "appearance.landscape_two_column_layout",
        defaultValue = true,
        titleRes = R.string.settings_landscape_two_column_title,
        subtitleRes = R.string.settings_landscape_two_column_subtitle,
        group = SettingsGroup.APPEARANCE,
    )
    val KindleDeviceName: StringSetting = StringSetting(
        key = "sync.kindle_device_name",
        defaultValue = "My Vayana",
        titleRes = R.string.settings_kindle_device_name_title,
        subtitleRes = R.string.settings_kindle_device_name_subtitle,
        group = SettingsGroup.SYNC,
    )
    val GithubSyncEnabled: BooleanSetting = BooleanSetting(
        key = "sync.github_enabled",
        defaultValue = false,
        titleRes = R.string.settings_github_sync_enabled_title,
        subtitleRes = R.string.settings_github_sync_enabled_subtitle,
        group = SettingsGroup.SYNC,
    )
    val GithubOwner: StringSetting = StringSetting(
        key = "sync.github_owner",
        defaultValue = "",
        titleRes = R.string.settings_github_owner_title,
        subtitleRes = R.string.settings_github_owner_subtitle,
        group = SettingsGroup.SYNC,
        maxLength = 100,
    )
    val GithubRepository: StringSetting = StringSetting(
        key = "sync.github_repository",
        defaultValue = "",
        titleRes = R.string.settings_github_repository_title,
        subtitleRes = R.string.settings_github_repository_subtitle,
        group = SettingsGroup.SYNC,
        maxLength = 100,
    )
    val GithubBranch: StringSetting = StringSetting(
        key = "sync.github_branch",
        defaultValue = "main",
        titleRes = R.string.settings_github_branch_title,
        subtitleRes = R.string.settings_github_branch_subtitle,
        group = SettingsGroup.SYNC,
        maxLength = 255,
    )
    val GithubToken: StringSetting = StringSetting(
        key = "sync.github_token",
        defaultValue = "",
        titleRes = R.string.settings_github_token_title,
        subtitleRes = R.string.settings_github_token_subtitle,
        group = SettingsGroup.SYNC,
        maxLength = 512,
        secure = true,
        exportable = false,
    )
    val GithubSyncPassphrase: StringSetting = StringSetting(
        key = "sync.github_passphrase",
        defaultValue = "",
        titleRes = R.string.settings_github_passphrase_title,
        subtitleRes = R.string.settings_github_passphrase_subtitle,
        group = SettingsGroup.SYNC,
        maxLength = 256,
        secure = true,
        exportable = false,
    )

    val all: List<Setting<out Any>> = listOf(
        ThemeMode,
        DisplayProfile,
        DarkVariant,
        Motion,
        ReaderFontSize,
        ReaderLineHeight,
        ReaderFontFamily,
        ReaderTheme,
        ReaderSideMargin,
        ReaderHeaderGap,
        ReaderFooterGap,
        ReaderPublisherStyles,
        ReaderShowHeaders,
        ReaderShowFooter,
        ReaderTapZoneMode,
        ReaderVolumeKeys,
        ReaderKeepAwake,
        ReaderAutoMarkSelection,
        ReaderBionicReading,
        DailyReadingGoalMinutes,
        YearlyBooksGoal,
        DefaultCoverSource,
        LandscapeTwoColumnLayout,
        GithubSyncEnabled,
        KindleDeviceName,
        GithubOwner,
        GithubRepository,
        GithubBranch,
        GithubToken,
        GithubSyncPassphrase,
    )
}
