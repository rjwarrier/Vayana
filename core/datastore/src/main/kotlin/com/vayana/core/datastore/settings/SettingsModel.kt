package com.vayana.core.datastore.settings

import androidx.annotation.StringRes
import com.vayana.core.designsystem.theme.DarkVariant
import com.vayana.core.designsystem.theme.DateFormatStyle
import com.vayana.core.designsystem.theme.DisplayProfile
import com.vayana.core.designsystem.theme.EinkPalette
import com.vayana.core.designsystem.theme.MotionSetting
import com.vayana.core.designsystem.theme.ThemeMode
import com.vayana.core.resources.R
import java.time.DayOfWeek

enum class SettingsGroup(
    @param:StringRes val titleRes: Int,
    @param:StringRes val subtitleRes: Int,
) {
    APPEARANCE(R.string.settings_group_appearance, R.string.settings_group_appearance_subtitle),
    LIBRARY(R.string.settings_group_library, R.string.settings_group_library_subtitle),
    READER_TEXT(R.string.settings_group_reader_text, R.string.settings_group_reader_text_subtitle),
    READER_PAGE(R.string.settings_group_reader_page, R.string.settings_group_reader_page_subtitle),
    READER_CONTROLS(R.string.settings_group_reader_controls, R.string.settings_group_reader_controls_subtitle),
    GOALS(R.string.settings_group_goals, R.string.settings_group_goals_subtitle),
    SYNC(R.string.settings_group_sync, R.string.settings_group_sync_subtitle),

    /** Holds no registry settings; its page is the backup & restore card. */
    BACKUP(R.string.settings_group_backup, R.string.settings_group_backup_subtitle),
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
    val onboardingCompleted: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val displayProfile: DisplayProfile = DisplayProfile.STANDARD,
    val einkPalette: EinkPalette = EinkPalette.MONOCHROME,
    val darkVariant: DarkVariant = DarkVariant.STANDARD,
    val motionSetting: MotionSetting = MotionSetting.FULL,
    val navigationMode: NavigationMode = NavigationMode.FLOATING_BAR,
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
    val readerControlsTapMode: ReaderControlsTapMode = ReaderControlsTapMode.DOUBLE,
    val readerVolumeKeys: Boolean = false,
    val readerKeepAwake: Boolean = false,
    /** Scale "time left" by how fast this reader has actually been reading. */
    val readerPersonalPace: Boolean = true,
    /** What the pace estimate has measured: seconds actually spent, and the seconds the fixed estimate gave the same text. */
    val readerPaceActualSeconds: Float = 0f,
    val readerPaceEstimatedSeconds: Float = 0f,
    val readerShowHeaders: Boolean = true,
    val readerShowFooter: Boolean = true,
    val readerAutoMarkSelection: Boolean = false,
    val readerBionicReading: Boolean = false,
    val readerBolderText: Boolean = false,
    val readerPdfCropMargins: Boolean = false,
    val readerPdfFitWidth: Boolean = false,
    val readerTextAlign: ReaderTextAlign = ReaderTextAlign.BOOK,
    val readerHyphenation: ReaderHyphenation = ReaderHyphenation.BOOK,
    val readerFullScreen: Boolean = false,
    val readerPageTurnAnimation: Boolean = false,
    val einkRefreshEveryPages: Int = 6,
    val einkAudioFeaturesEnabled: Boolean = true,
    val dailyReadingGoalMinutes: Int = 20,
    /** A notification on days the daily goal isn't met yet, at [readingReminderHour]. */
    val readingReminderEnabled: Boolean = false,
    val readingReminderHour: Int = 20,
    /** Notifications before a borrowed physical book is due back. */
    val borrowRemindersEnabled: Boolean = true,
    /** Home-screen widgets' corner radius in dp, or [WidgetCornerRadiusMatchLauncher]. */
    val widgetCornerRadius: Int = WidgetCornerRadiusMatchLauncher,
    val widgetProgressStyle: WidgetProgressStyle = WidgetProgressStyle.FLAT,
    val yearlyBooksGoal: Int = 12,
    val defaultCoverSource: DefaultCoverSource = DefaultCoverSource.YOURS,
    /** Mirrors Home Library's catalog into Offline books while it is installed on this phone. */
    val homeLibrarySyncEnabled: Boolean = true,
    val finishedPercent: Int = 98,
    val landscapeTwoColumnLayout: Boolean = true,
    val kindleDeviceName: String = "My Vayana",
    val githubSyncEnabled: Boolean = false,
    val githubOwner: String = "",
    val githubRepository: String = "",
    val githubBranch: String = "main",
    val githubToken: String = "",
    val githubSyncPassphrase: String = "",
    val readingAutoSyncEveryPages: Int = 3,
    val dynamicColor: Boolean = false,
    val dateFormatStyle: DateFormatStyle = DateFormatStyle.SYSTEM,
    val startScreen: StartScreen = StartScreen.LIBRARY,
    val recentlyDeletedRetention: RecentlyDeletedRetention = RecentlyDeletedRetention.FOREVER,
    val weekStart: WeekStart = WeekStart.SUNDAY,
    val readAloudRate: Float = 1f,
    val readAloudPitch: Float = 1f,
    val readAloudVoiceName: String = "",
    /** Package of the text-to-speech engine to read with; blank uses the phone's default engine. */
    val readAloudEngine: String = "",
    /** 0 follows the system brightness. */
    val readerBrightnessPercent: Int = 0,
    val readerWarmLightPercent: Int = 0,
    val readerEdgeSwipeLight: Boolean = true,
) {
    /** Reading progress (0..1) from which a book counts as finished. */
    val finishedFraction: Float get() = finishedPercent / 100f

    /** Standard devices always expose audio; e-ink users can opt out for hardware without speakers. */
    val readerAudioFeaturesEnabled: Boolean
        get() = displayProfile != DisplayProfile.E_INK || einkAudioFeaturesEnabled
}

enum class ReaderFontFamily { SERIF, SANS, MONO }

/** How paragraphs are aligned; [BOOK] leaves it to the book. */
enum class ReaderTextAlign { BOOK, JUSTIFIED, LEFT, RIGHT, CENTER }

/** Whether words break at line ends with a hyphen; [BOOK] leaves it to the book. */
enum class ReaderHyphenation { BOOK, ON, OFF }

enum class ReaderTheme { SYSTEM, LIGHT, PAPER, SEPIA, MINT, SKY, ROSE, DARK, OLED }

/** Whether taps at the page edges turn pages. Horizontal swiping remains available in both modes. */
enum class TapZoneMode { THREE_ZONE, SWIPE_ONLY }

/** Number of quick taps in the middle of the page required to show reader controls. */
enum class ReaderControlsTapMode(val tapCount: Int) { SINGLE(1), DOUBLE(2), TRIPLE(3) }

enum class DefaultCoverSource { YOURS, GOODREADS }

enum class NavigationMode { BOTTOM_BAR, FLOATING_BAR }

/** Where the app opens. [LAST_BOOK] opens the library with the most recently read book on top of it. */
enum class StartScreen { LIBRARY, NOTES, STATISTICS, LAST_BOOK }

/** How long books stay in Recently deleted before they're deleted permanently; [FOREVER] is 0 days. */
enum class RecentlyDeletedRetention(val days: Int) { FOREVER(0), DAYS_7(7), DAYS_30(30), DAYS_90(90) }

enum class WeekStart(val day: DayOfWeek) { SUNDAY(DayOfWeek.SUNDAY), MONDAY(DayOfWeek.MONDAY) }

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
    val EinkPalette = ChoiceSetting(
        key = "appearance.eink_palette",
        defaultValue = com.vayana.core.designsystem.theme.EinkPalette.MONOCHROME,
        titleRes = R.string.settings_eink_palette_title,
        subtitleRes = R.string.settings_eink_palette_subtitle,
        group = SettingsGroup.APPEARANCE,
        options = listOf(
            ChoiceOption(com.vayana.core.designsystem.theme.EinkPalette.MONOCHROME, R.string.settings_eink_palette_monochrome),
            ChoiceOption(com.vayana.core.designsystem.theme.EinkPalette.COLOR, R.string.settings_eink_palette_color),
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
    val EinkAudioFeatures: BooleanSetting = BooleanSetting(
        key = "appearance.eink_audio_features",
        defaultValue = true,
        titleRes = R.string.settings_eink_audio_features_title,
        subtitleRes = R.string.settings_eink_audio_features_subtitle,
        group = SettingsGroup.APPEARANCE,
    )
    val NavigationMode: ChoiceSetting<com.vayana.core.datastore.settings.NavigationMode> =
        ChoiceSetting<com.vayana.core.datastore.settings.NavigationMode>(
        key = "appearance.navigation_mode",
        defaultValue = com.vayana.core.datastore.settings.NavigationMode.FLOATING_BAR,
        titleRes = R.string.settings_navigation_mode_title,
        subtitleRes = R.string.settings_navigation_mode_subtitle,
        group = SettingsGroup.APPEARANCE,
        options = listOf<ChoiceOption<com.vayana.core.datastore.settings.NavigationMode>>(
            ChoiceOption(com.vayana.core.datastore.settings.NavigationMode.BOTTOM_BAR, R.string.settings_navigation_mode_bottom_bar),
            ChoiceOption(com.vayana.core.datastore.settings.NavigationMode.FLOATING_BAR, R.string.settings_navigation_mode_floating_bar),
        ),
    )
    val ReaderFontSize: IntSetting = IntSetting(
        key = "reader.font_size_percent",
        defaultValue = 100,
        titleRes = R.string.settings_reader_font_size_title,
        subtitleRes = R.string.settings_reader_font_size_subtitle,
        group = SettingsGroup.READER_TEXT,
        range = 80..250,
        step = 5,
    )
    val ReaderLineHeight: FloatSetting = FloatSetting(
        key = "reader.line_height",
        defaultValue = 1.5f,
        titleRes = R.string.settings_reader_line_height_title,
        subtitleRes = R.string.settings_reader_line_height_subtitle,
        group = SettingsGroup.READER_TEXT,
        range = 1.2f..4.0f,
        step = 0.1f,
    )
    val ReaderFontFamily: ChoiceSetting<com.vayana.core.datastore.settings.ReaderFontFamily> =
        ChoiceSetting<com.vayana.core.datastore.settings.ReaderFontFamily>(
        key = "reader.font_family",
        defaultValue = com.vayana.core.datastore.settings.ReaderFontFamily.SERIF,
        titleRes = R.string.settings_reader_font_family_title,
        subtitleRes = R.string.settings_reader_font_family_subtitle,
        group = SettingsGroup.READER_TEXT,
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
        group = SettingsGroup.READER_PAGE,
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
        group = SettingsGroup.READER_PAGE,
        range = 0..24,
        step = 2,
    )
    val ReaderHeaderGap: IntSetting = IntSetting(
        key = "reader.header_gap_dp",
        defaultValue = 60,
        titleRes = R.string.settings_reader_header_gap_title,
        subtitleRes = R.string.settings_reader_header_gap_subtitle,
        group = SettingsGroup.READER_PAGE,
        range = 0..120,
        step = 4,
    )
    val ReaderFooterGap: IntSetting = IntSetting(
        key = "reader.footer_gap_dp",
        defaultValue = 8,
        titleRes = R.string.settings_reader_footer_gap_title,
        subtitleRes = R.string.settings_reader_footer_gap_subtitle,
        group = SettingsGroup.READER_PAGE,
        range = 0..80,
        step = 4,
    )
    val ReaderPublisherStyles: BooleanSetting = BooleanSetting(
        key = "reader.publisher_styles",
        defaultValue = true,
        titleRes = R.string.settings_reader_publisher_styles_title,
        subtitleRes = R.string.settings_reader_publisher_styles_subtitle,
        group = SettingsGroup.READER_TEXT,
    )
    val ReaderTapZoneMode: ChoiceSetting<TapZoneMode> = ChoiceSetting<TapZoneMode>(
        key = "reader.tap_zone_mode",
        defaultValue = TapZoneMode.THREE_ZONE,
        titleRes = R.string.settings_reader_tap_zone_title,
        subtitleRes = R.string.settings_reader_tap_zone_subtitle,
        group = SettingsGroup.READER_CONTROLS,
        options = listOf<ChoiceOption<TapZoneMode>>(
            ChoiceOption(TapZoneMode.THREE_ZONE, R.string.settings_reader_tap_zone_three_zone),
            ChoiceOption(TapZoneMode.SWIPE_ONLY, R.string.settings_reader_tap_zone_swipe_only),
        ),
    )
    val ReaderControlsTapMode: ChoiceSetting<com.vayana.core.datastore.settings.ReaderControlsTapMode> = ChoiceSetting(
        key = "reader.controls_tap_mode",
        defaultValue = com.vayana.core.datastore.settings.ReaderControlsTapMode.DOUBLE,
        titleRes = R.string.settings_reader_controls_tap_title,
        subtitleRes = R.string.settings_reader_controls_tap_subtitle,
        group = SettingsGroup.READER_CONTROLS,
        options = listOf(
            ChoiceOption(com.vayana.core.datastore.settings.ReaderControlsTapMode.SINGLE, R.string.settings_reader_controls_tap_single),
            ChoiceOption(com.vayana.core.datastore.settings.ReaderControlsTapMode.DOUBLE, R.string.settings_reader_controls_tap_double),
            ChoiceOption(com.vayana.core.datastore.settings.ReaderControlsTapMode.TRIPLE, R.string.settings_reader_controls_tap_triple),
        ),
    )
    val ReaderVolumeKeys: BooleanSetting = BooleanSetting(
        key = "reader.volume_keys",
        defaultValue = false,
        titleRes = R.string.settings_reader_volume_keys_title,
        subtitleRes = R.string.settings_reader_volume_keys_subtitle,
        group = SettingsGroup.READER_CONTROLS,
    )
    val ReaderKeepAwake: BooleanSetting = BooleanSetting(
        key = "reader.keep_awake",
        defaultValue = false,
        titleRes = R.string.settings_reader_keep_awake_title,
        subtitleRes = R.string.settings_reader_keep_awake_subtitle,
        group = SettingsGroup.READER_CONTROLS,
    )
    val ReaderPersonalPace: BooleanSetting = BooleanSetting(
        key = "reader.personal_pace",
        defaultValue = true,
        titleRes = R.string.settings_reader_personal_pace_title,
        subtitleRes = R.string.settings_reader_personal_pace_subtitle,
        group = SettingsGroup.READER_CONTROLS,
    )
    val ReaderPaceActualSeconds: FloatSetting = FloatSetting(
        key = "reader.pace_actual_seconds",
        defaultValue = 0f,
        titleRes = R.string.settings_reader_personal_pace_title,
        subtitleRes = null,
        group = SettingsGroup.READER_CONTROLS,
        range = 0f..PaceSecondsMax,
        step = 1f,
    )
    val ReaderPaceEstimatedSeconds: FloatSetting = FloatSetting(
        key = "reader.pace_estimated_seconds",
        defaultValue = 0f,
        titleRes = R.string.settings_reader_personal_pace_title,
        subtitleRes = null,
        group = SettingsGroup.READER_CONTROLS,
        range = 0f..PaceSecondsMax,
        step = 1f,
    )
    val ReaderShowHeaders: BooleanSetting = BooleanSetting(
        key = "reader.show_headers",
        defaultValue = true,
        titleRes = R.string.settings_reader_show_headers_title,
        subtitleRes = R.string.settings_reader_show_headers_subtitle,
        group = SettingsGroup.READER_PAGE,
    )
    val ReaderShowFooter: BooleanSetting = BooleanSetting(
        key = "reader.show_footer",
        defaultValue = true,
        titleRes = R.string.settings_reader_show_footer_title,
        subtitleRes = R.string.settings_reader_show_footer_subtitle,
        group = SettingsGroup.READER_PAGE,
    )
    val ReaderAutoMarkSelection: BooleanSetting = BooleanSetting(
        key = "reader.auto_mark_selection",
        defaultValue = false,
        titleRes = R.string.settings_reader_auto_mark_selection_title,
        subtitleRes = R.string.settings_reader_auto_mark_selection_subtitle,
        group = SettingsGroup.READER_CONTROLS,
    )
    val ReaderBionicReading: BooleanSetting = BooleanSetting(
        key = "reader.bionic_reading",
        defaultValue = false,
        titleRes = R.string.settings_reader_bionic_reading_title,
        subtitleRes = R.string.settings_reader_bionic_reading_subtitle,
        group = SettingsGroup.READER_TEXT,
    )
    val ReaderBolderText: BooleanSetting = BooleanSetting(
        key = "reader.bolder_text",
        defaultValue = false,
        titleRes = R.string.settings_reader_bolder_text_title,
        subtitleRes = R.string.settings_reader_bolder_text_subtitle,
        group = SettingsGroup.READER_TEXT,
    )
    val ReaderPdfCropMargins: BooleanSetting = BooleanSetting(
        key = "reader.pdf_crop_margins",
        defaultValue = false,
        titleRes = R.string.reader_pdf_crop_margins_title,
        subtitleRes = R.string.reader_pdf_crop_margins_subtitle,
        group = SettingsGroup.READER_PAGE,
    )
    val ReaderPdfFitWidth: BooleanSetting = BooleanSetting(
        key = "reader.pdf_fit_width",
        defaultValue = false,
        titleRes = R.string.reader_pdf_fit_width_title,
        subtitleRes = R.string.reader_pdf_fit_width_subtitle,
        group = SettingsGroup.READER_PAGE,
    )
    val ReaderTextAlign: ChoiceSetting<com.vayana.core.datastore.settings.ReaderTextAlign> =
        ChoiceSetting<com.vayana.core.datastore.settings.ReaderTextAlign>(
        key = "reader.text_align",
        defaultValue = com.vayana.core.datastore.settings.ReaderTextAlign.BOOK,
        titleRes = R.string.settings_reader_text_align_title,
        subtitleRes = R.string.settings_reader_text_align_subtitle,
        group = SettingsGroup.READER_TEXT,
        options = listOf<ChoiceOption<com.vayana.core.datastore.settings.ReaderTextAlign>>(
            ChoiceOption(com.vayana.core.datastore.settings.ReaderTextAlign.BOOK, R.string.settings_reader_text_align_book),
            ChoiceOption(com.vayana.core.datastore.settings.ReaderTextAlign.JUSTIFIED, R.string.settings_reader_text_align_justified),
            ChoiceOption(com.vayana.core.datastore.settings.ReaderTextAlign.LEFT, R.string.settings_reader_text_align_left),
            ChoiceOption(com.vayana.core.datastore.settings.ReaderTextAlign.CENTER, R.string.settings_reader_text_align_center),
            ChoiceOption(com.vayana.core.datastore.settings.ReaderTextAlign.RIGHT, R.string.settings_reader_text_align_right),
        ),
    )
    val ReaderHyphenation: ChoiceSetting<com.vayana.core.datastore.settings.ReaderHyphenation> =
        ChoiceSetting<com.vayana.core.datastore.settings.ReaderHyphenation>(
        key = "reader.hyphenation",
        defaultValue = com.vayana.core.datastore.settings.ReaderHyphenation.BOOK,
        titleRes = R.string.settings_reader_hyphenation_title,
        subtitleRes = R.string.settings_reader_hyphenation_subtitle,
        group = SettingsGroup.READER_TEXT,
        options = listOf<ChoiceOption<com.vayana.core.datastore.settings.ReaderHyphenation>>(
            ChoiceOption(com.vayana.core.datastore.settings.ReaderHyphenation.BOOK, R.string.settings_reader_hyphenation_book),
            ChoiceOption(com.vayana.core.datastore.settings.ReaderHyphenation.ON, R.string.settings_reader_hyphenation_on),
            ChoiceOption(com.vayana.core.datastore.settings.ReaderHyphenation.OFF, R.string.settings_reader_hyphenation_off),
        ),
    )

    val ReaderFullScreen: BooleanSetting = BooleanSetting(
        key = "reader.full_screen",
        defaultValue = false,
        titleRes = R.string.settings_reader_full_screen_title,
        subtitleRes = R.string.settings_reader_full_screen_subtitle,
        group = SettingsGroup.READER_PAGE,
    )
    val ReaderPageTurnAnimation: BooleanSetting = BooleanSetting(
        key = "reader.page_turn_animation",
        defaultValue = false,
        titleRes = R.string.settings_reader_page_turn_animation_title,
        subtitleRes = R.string.settings_reader_page_turn_animation_subtitle,
        group = SettingsGroup.READER_CONTROLS,
    )
    val EinkRefreshEveryPages: IntSetting = IntSetting(
        key = "appearance.eink_refresh_pages",
        defaultValue = 6,
        titleRes = R.string.settings_eink_refresh_pages_title,
        subtitleRes = R.string.settings_eink_refresh_pages_subtitle,
        group = SettingsGroup.APPEARANCE,
        range = 0..20,
        step = 1,
    )
    val FinishedPercent: IntSetting = IntSetting(
        key = "library.finished_percent",
        defaultValue = 98,
        titleRes = R.string.settings_finished_percent_title,
        subtitleRes = R.string.settings_finished_percent_subtitle,
        group = SettingsGroup.LIBRARY,
        range = 90..100,
        step = 1,
    )
    val ReadingAutoSyncEveryPages: IntSetting = IntSetting(
        key = "sync.reading_auto_sync_pages",
        defaultValue = 3,
        titleRes = R.string.settings_reading_auto_sync_pages_title,
        subtitleRes = R.string.settings_reading_auto_sync_pages_subtitle,
        group = SettingsGroup.SYNC,
        range = 0..30,
        step = 1,
    )

    val ReadAloudRate: FloatSetting = FloatSetting(
        key = "reader.read_aloud_rate",
        defaultValue = 1f,
        titleRes = R.string.settings_read_aloud_rate_title,
        subtitleRes = R.string.settings_read_aloud_rate_subtitle,
        group = SettingsGroup.READER_CONTROLS,
        range = 0.5f..2f,
        step = 0.25f,
    )
    val ReadAloudPitch: FloatSetting = FloatSetting(
        key = "reader.read_aloud_pitch",
        defaultValue = 1f,
        titleRes = R.string.settings_read_aloud_pitch_title,
        subtitleRes = R.string.settings_read_aloud_pitch_subtitle,
        group = SettingsGroup.READER_CONTROLS,
        range = 0.5f..2f,
        step = 0.1f,
    )
    val ReadAloudVoiceName: StringSetting = StringSetting(
        key = "reader.read_aloud_voice_name",
        defaultValue = "",
        titleRes = R.string.settings_read_aloud_voice_title,
        subtitleRes = R.string.settings_read_aloud_voice_subtitle,
        group = SettingsGroup.READER_CONTROLS,
        maxLength = 255,
    )
    val ReadAloudEngine: StringSetting = StringSetting(
        key = "reader.read_aloud_engine",
        defaultValue = "",
        titleRes = R.string.settings_read_aloud_engine_title,
        subtitleRes = R.string.settings_read_aloud_engine_subtitle,
        group = SettingsGroup.READER_CONTROLS,
        maxLength = 255,
    )
    val LibraryViewMode: StringSetting = StringSetting(
        key = "library.view_mode",
        defaultValue = "THUMBNAILS",
        titleRes = R.string.settings_start_screen_title,
        subtitleRes = null,
        group = SettingsGroup.LIBRARY,
        maxLength = 20,
        exportable = false,
    )
    val ReaderBrightness: IntSetting = IntSetting(
        key = "reader.brightness_percent",
        defaultValue = 0,
        titleRes = R.string.settings_reader_brightness_title,
        subtitleRes = R.string.settings_reader_brightness_subtitle,
        group = SettingsGroup.READER_PAGE,
        range = 0..100,
        step = 5,
    )
    val ReaderWarmLight: IntSetting = IntSetting(
        key = "reader.warm_light_percent",
        defaultValue = 0,
        titleRes = R.string.settings_reader_warm_light_title,
        subtitleRes = R.string.settings_reader_warm_light_subtitle,
        group = SettingsGroup.READER_PAGE,
        range = 0..60,
        step = 5,
    )
    val ReaderEdgeSwipeLight: BooleanSetting = BooleanSetting(
        key = "reader.edge_swipe_light",
        defaultValue = true,
        titleRes = R.string.settings_reader_edge_swipe_light_title,
        subtitleRes = R.string.settings_reader_edge_swipe_light_subtitle,
        group = SettingsGroup.READER_CONTROLS,
    )
    val DynamicColor: BooleanSetting = BooleanSetting(
        key = "appearance.dynamic_color",
        defaultValue = false,
        titleRes = R.string.settings_dynamic_color_title,
        subtitleRes = R.string.settings_dynamic_color_subtitle,
        group = SettingsGroup.APPEARANCE,
    )
    val DateFormat: ChoiceSetting<DateFormatStyle> = ChoiceSetting<DateFormatStyle>(
        key = "appearance.date_format",
        defaultValue = DateFormatStyle.SYSTEM,
        titleRes = R.string.settings_date_format_title,
        subtitleRes = R.string.settings_date_format_subtitle,
        group = SettingsGroup.APPEARANCE,
        options = listOf<ChoiceOption<DateFormatStyle>>(
            ChoiceOption(DateFormatStyle.SYSTEM, R.string.settings_date_format_system),
            ChoiceOption(DateFormatStyle.DAY_FIRST, R.string.settings_date_format_day_first),
            ChoiceOption(DateFormatStyle.MONTH_FIRST, R.string.settings_date_format_month_first),
            ChoiceOption(DateFormatStyle.ISO, R.string.settings_date_format_iso),
        ),
    )
    val StartScreen: ChoiceSetting<com.vayana.core.datastore.settings.StartScreen> =
        ChoiceSetting<com.vayana.core.datastore.settings.StartScreen>(
        key = "app.start_screen",
        defaultValue = com.vayana.core.datastore.settings.StartScreen.LIBRARY,
        titleRes = R.string.settings_start_screen_title,
        subtitleRes = R.string.settings_start_screen_subtitle,
        group = SettingsGroup.LIBRARY,
        options = listOf<ChoiceOption<com.vayana.core.datastore.settings.StartScreen>>(
            ChoiceOption(com.vayana.core.datastore.settings.StartScreen.LIBRARY, R.string.settings_start_screen_library),
            ChoiceOption(com.vayana.core.datastore.settings.StartScreen.LAST_BOOK, R.string.settings_start_screen_last_book),
            ChoiceOption(com.vayana.core.datastore.settings.StartScreen.NOTES, R.string.settings_start_screen_notes),
            ChoiceOption(com.vayana.core.datastore.settings.StartScreen.STATISTICS, R.string.settings_start_screen_statistics),
        ),
    )
    val RecentlyDeletedRetention: ChoiceSetting<com.vayana.core.datastore.settings.RecentlyDeletedRetention> =
        ChoiceSetting<com.vayana.core.datastore.settings.RecentlyDeletedRetention>(
        key = "library.recently_deleted_retention",
        defaultValue = com.vayana.core.datastore.settings.RecentlyDeletedRetention.FOREVER,
        titleRes = R.string.settings_recently_deleted_retention_title,
        subtitleRes = R.string.settings_recently_deleted_retention_subtitle,
        group = SettingsGroup.LIBRARY,
        options = listOf<ChoiceOption<com.vayana.core.datastore.settings.RecentlyDeletedRetention>>(
            ChoiceOption(com.vayana.core.datastore.settings.RecentlyDeletedRetention.FOREVER, R.string.settings_recently_deleted_retention_forever),
            ChoiceOption(com.vayana.core.datastore.settings.RecentlyDeletedRetention.DAYS_7, R.string.settings_recently_deleted_retention_7),
            ChoiceOption(com.vayana.core.datastore.settings.RecentlyDeletedRetention.DAYS_30, R.string.settings_recently_deleted_retention_30),
            ChoiceOption(com.vayana.core.datastore.settings.RecentlyDeletedRetention.DAYS_90, R.string.settings_recently_deleted_retention_90),
        ),
    )
    val WeekStart: ChoiceSetting<com.vayana.core.datastore.settings.WeekStart> =
        ChoiceSetting<com.vayana.core.datastore.settings.WeekStart>(
        key = "goals.week_start",
        defaultValue = com.vayana.core.datastore.settings.WeekStart.SUNDAY,
        titleRes = R.string.settings_week_start_title,
        subtitleRes = R.string.settings_week_start_subtitle,
        group = SettingsGroup.GOALS,
        options = listOf<ChoiceOption<com.vayana.core.datastore.settings.WeekStart>>(
            ChoiceOption(com.vayana.core.datastore.settings.WeekStart.SUNDAY, R.string.settings_week_start_sunday),
            ChoiceOption(com.vayana.core.datastore.settings.WeekStart.MONDAY, R.string.settings_week_start_monday),
        ),
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
    val ReadingReminderEnabled: BooleanSetting = BooleanSetting(
        key = "reminders.reading_enabled",
        defaultValue = false,
        titleRes = R.string.settings_reading_reminder_title,
        subtitleRes = R.string.settings_reading_reminder_subtitle,
        group = SettingsGroup.GOALS,
    )
    val ReadingReminderHour: IntSetting = IntSetting(
        key = "reminders.reading_hour",
        defaultValue = 20,
        titleRes = R.string.settings_reading_reminder_hour_title,
        subtitleRes = R.string.settings_reading_reminder_hour_subtitle,
        group = SettingsGroup.GOALS,
        range = 6..23,
        step = 1,
    )
    /**
     * Every Vayana home-screen widget's corner radius in dp, set on the widget configure screen; [MatchLauncher] (the
     * default) uses the home screen's own widget corners.
     */
    val WidgetCornerRadius: IntSetting = IntSetting(
        key = "widgets.corner_radius",
        defaultValue = WidgetCornerRadiusMatchLauncher,
        titleRes = R.string.widget_corners,
        subtitleRes = null,
        group = SettingsGroup.APPEARANCE,
        range = WidgetCornerRadiusMatchLauncher..WidgetCornerRadiusMax,
        step = WidgetCornerRadiusStep,
    )
    /** Every widget's progress bar: flat, or M3 Expressive's squiggle. Set on the widget configure screen. */
    val WidgetProgressBar: ChoiceSetting<WidgetProgressStyle> = ChoiceSetting(
        key = "widgets.progress_style",
        defaultValue = WidgetProgressStyle.FLAT,
        titleRes = R.string.widget_progress_style,
        subtitleRes = null,
        group = SettingsGroup.APPEARANCE,
        options = listOf(
            ChoiceOption(WidgetProgressStyle.FLAT, R.string.widget_progress_flat),
            ChoiceOption(WidgetProgressStyle.SQUIGGLY, R.string.widget_progress_squiggly),
        ),
    )
    val BorrowReminders: BooleanSetting = BooleanSetting(
        key = "reminders.borrowed_enabled",
        defaultValue = true,
        titleRes = R.string.settings_borrow_reminders_title,
        subtitleRes = R.string.settings_borrow_reminders_subtitle,
        group = SettingsGroup.GOALS,
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
    val HomeLibrarySync: BooleanSetting = BooleanSetting(
        key = "library.home_library_sync",
        defaultValue = true,
        titleRes = R.string.settings_home_library_sync_title,
        subtitleRes = R.string.settings_home_library_sync_subtitle,
        group = SettingsGroup.LIBRARY,
    )
    val DefaultCoverSource: ChoiceSetting<com.vayana.core.datastore.settings.DefaultCoverSource> =
        ChoiceSetting<com.vayana.core.datastore.settings.DefaultCoverSource>(
        key = "library.default_cover_source",
        defaultValue = com.vayana.core.datastore.settings.DefaultCoverSource.YOURS,
        titleRes = R.string.settings_default_cover_source_title,
        subtitleRes = R.string.settings_default_cover_source_subtitle,
        group = SettingsGroup.LIBRARY,
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
        group = SettingsGroup.LIBRARY,
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

    /** Also the on-screen order within each [SettingsGroup]. */
    val TodayCardEnabled = BooleanSetting("library.today_card", true, R.string.today_show, null, SettingsGroup.LIBRARY)

    val all: List<Setting<out Any>> = listOf(
        ThemeMode,
        DarkVariant,
        DynamicColor,
        DisplayProfile,
        EinkPalette,
        EinkAudioFeatures,
        NavigationMode,
        Motion,
        EinkRefreshEveryPages,
        DateFormat,
        StartScreen,
        DefaultCoverSource,
        HomeLibrarySync,
        LandscapeTwoColumnLayout,
        FinishedPercent,
        RecentlyDeletedRetention,
        ReaderFontFamily,
        ReaderFontSize,
        ReaderLineHeight,
        ReaderPublisherStyles,
        ReaderBionicReading,
        ReaderBolderText,
        ReaderTextAlign,
        ReaderHyphenation,
        ReaderTheme,
        ReaderBrightness,
        ReaderWarmLight,
        ReaderFullScreen,
        ReaderSideMargin,
        ReaderShowHeaders,
        ReaderHeaderGap,
        ReaderShowFooter,
        ReaderFooterGap,
        ReaderTapZoneMode,
        ReaderControlsTapMode,
        ReaderPageTurnAnimation,
        ReaderVolumeKeys,
        ReaderEdgeSwipeLight,
        ReadAloudRate,
        ReadAloudPitch,
        ReaderAutoMarkSelection,
        ReaderKeepAwake,
        ReaderPersonalPace,
        TodayCardEnabled,
        DailyReadingGoalMinutes,
        ReadingReminderEnabled,
        ReadingReminderHour,
        BorrowReminders,
        YearlyBooksGoal,
        WeekStart,
        GithubSyncEnabled,
        KindleDeviceName,
        GithubOwner,
        GithubRepository,
        GithubBranch,
        GithubToken,
        GithubSyncPassphrase,
        ReadingAutoSyncEveryPages,
    )

    /** Persisted and backed up, but edited only by purpose-built feature UI. */
    val SmartShelves = StringSetting("library.smart_shelves", "[]", R.string.tools_smart_shelves, null, SettingsGroup.LIBRARY, maxLength = 262_144)
    val ReadingPresets = StringSetting("reader.named_presets", "[]", R.string.tools_presets, null, SettingsGroup.READER_TEXT, maxLength = 262_144)
    val ReadingPlans = StringSetting("goals.finish_by", "{}", R.string.tools_finish_by, null, SettingsGroup.GOALS, maxLength = 262_144)

    val ReadNextCapacity = IntSetting("library.read_next_capacity", 10, R.string.queue_capacity, null, SettingsGroup.LIBRARY, 2..50, 1)
    val NotebookExportFolder = StringSetting("notes.auto_export_folder", "", R.string.notebook_auto_export, null, SettingsGroup.LIBRARY, maxLength = 4096, exportable = false)

    val internal: List<Setting<out Any>> = listOf(
        ReadNextCapacity,
        NotebookExportFolder,
        SmartShelves,
        ReadingPresets,
        ReadingPlans,
        ReadAloudVoiceName,
        ReadAloudEngine,
        LibraryViewMode,
        ReaderPdfCropMargins,
        ReaderPdfFitWidth,
        WidgetCornerRadius,
        WidgetProgressBar,
        ReaderPaceActualSeconds,
        ReaderPaceEstimatedSeconds,
    )
    val persisted: List<Setting<out Any>> = all + internal
}

/** Far above any real reader's lifetime of reading, so the pace totals never reach the cap. */
const val PaceSecondsMax = 1_000_000_000f

/** Widgets follow the home screen's own corner radius. */
const val WidgetCornerRadiusMatchLauncher = -1
const val WidgetCornerRadiusMax = 32
const val WidgetCornerRadiusStep = 4

/** How home-screen widgets draw progress. */
enum class WidgetProgressStyle { FLAT, SQUIGGLY }
