package com.vayana.core.datastore.settings

import androidx.annotation.StringRes
import com.vayana.core.designsystem.theme.DarkVariant
import com.vayana.core.designsystem.theme.DisplayProfile
import com.vayana.core.designsystem.theme.MotionSetting
import com.vayana.core.designsystem.theme.ThemeMode
import com.vayana.core.resources.R

enum class SettingsGroup(@param:StringRes val titleRes: Int) {
    APPEARANCE(R.string.settings_group_appearance),
    READER_TYPOGRAPHY(R.string.settings_group_reader_typography),
    READER_LAYOUT(R.string.settings_group_reader_layout),
    READER_BEHAVIOR(R.string.settings_group_reader_behavior),
    MAINTENANCE(R.string.settings_group_maintenance),
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

class ChoiceSetting<T>(
    key: String,
    defaultValue: T,
    @StringRes titleRes: Int,
    @StringRes subtitleRes: Int?,
    group: SettingsGroup,
    val options: List<ChoiceOption<T>>,
) : Setting<T>(key, defaultValue, titleRes, subtitleRes, group) where T : Enum<T>

data class ChoiceOption<T : Any>(val value: T, @param:StringRes val labelRes: Int)

data class SettingsSnapshot(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val displayProfile: DisplayProfile = DisplayProfile.STANDARD,
    val darkVariant: DarkVariant = DarkVariant.STANDARD,
    val motionSetting: MotionSetting = MotionSetting.FULL,
    val readerFontSizePercent: Int = 100,
    val readerLineHeight: Float = 1.5f,
    val readerFontFamily: ReaderFontFamily = ReaderFontFamily.SERIF,
    val readerTheme: ReaderTheme = ReaderTheme.SYSTEM,
    val readerSideMarginPercent: Int = 10,
    val readerUsePublisherStyles: Boolean = true,
    val readerTapZoneMode: TapZoneMode = TapZoneMode.THREE_ZONE,
    val readerVolumeKeys: Boolean = false,
    val readerKeepAwake: Boolean = false,
)

enum class ReaderFontFamily { SERIF, SANS, MONO }

enum class ReaderTheme { SYSTEM, LIGHT, SEPIA, DARK }

enum class TapZoneMode { THREE_ZONE }

object SettingsRegistry {
    val ThemeMode = ChoiceSetting(
        key = "appearance.theme_mode",
        defaultValue = com.vayana.core.designsystem.theme.ThemeMode.SYSTEM,
        titleRes = R.string.settings_theme_mode_title,
        subtitleRes = R.string.settings_theme_mode_subtitle,
        group = SettingsGroup.APPEARANCE,
        options = listOf(
            ChoiceOption(com.vayana.core.designsystem.theme.ThemeMode.SYSTEM, R.string.settings_theme_mode_system),
            ChoiceOption(com.vayana.core.designsystem.theme.ThemeMode.LIGHT, R.string.settings_theme_mode_light),
            ChoiceOption(com.vayana.core.designsystem.theme.ThemeMode.DARK, R.string.settings_theme_mode_dark),
        ),
    )
    val DisplayProfile = ChoiceSetting(
        key = "appearance.display_profile",
        defaultValue = com.vayana.core.designsystem.theme.DisplayProfile.STANDARD,
        titleRes = R.string.settings_display_profile_title,
        subtitleRes = R.string.settings_display_profile_subtitle,
        group = SettingsGroup.APPEARANCE,
        options = listOf(
            ChoiceOption(com.vayana.core.designsystem.theme.DisplayProfile.STANDARD, R.string.settings_display_profile_standard),
            ChoiceOption(com.vayana.core.designsystem.theme.DisplayProfile.E_INK, R.string.settings_display_profile_eink),
        ),
    )
    val DarkVariant = ChoiceSetting(
        key = "appearance.dark_variant",
        defaultValue = com.vayana.core.designsystem.theme.DarkVariant.STANDARD,
        titleRes = R.string.settings_dark_variant_title,
        subtitleRes = R.string.settings_dark_variant_subtitle,
        group = SettingsGroup.APPEARANCE,
        options = listOf(
            ChoiceOption(com.vayana.core.designsystem.theme.DarkVariant.STANDARD, R.string.settings_dark_variant_standard),
            ChoiceOption(com.vayana.core.designsystem.theme.DarkVariant.SOFTER, R.string.settings_dark_variant_softer),
            ChoiceOption(com.vayana.core.designsystem.theme.DarkVariant.TRUE_BLACK, R.string.settings_dark_variant_true_black),
        ),
    )
    val Motion = ChoiceSetting(
        key = "appearance.motion",
        defaultValue = com.vayana.core.designsystem.theme.MotionSetting.FULL,
        titleRes = R.string.settings_motion_title,
        subtitleRes = R.string.settings_motion_subtitle,
        group = SettingsGroup.APPEARANCE,
        options = listOf(
            ChoiceOption(com.vayana.core.designsystem.theme.MotionSetting.FULL, R.string.settings_motion_full),
            ChoiceOption(com.vayana.core.designsystem.theme.MotionSetting.REDUCED, R.string.settings_motion_reduced),
            ChoiceOption(com.vayana.core.designsystem.theme.MotionSetting.OFF, R.string.settings_motion_off),
        ),
    )
    val ReaderFontSize = IntSetting(
        key = "reader.font_size_percent",
        defaultValue = 100,
        titleRes = R.string.settings_reader_font_size_title,
        subtitleRes = R.string.settings_reader_font_size_subtitle,
        group = SettingsGroup.READER_TYPOGRAPHY,
        range = 80..160,
        step = 5,
    )
    val ReaderLineHeight = FloatSetting(
        key = "reader.line_height",
        defaultValue = 1.5f,
        titleRes = R.string.settings_reader_line_height_title,
        subtitleRes = R.string.settings_reader_line_height_subtitle,
        group = SettingsGroup.READER_TYPOGRAPHY,
        range = 1.2f..2.0f,
        step = 0.1f,
    )
    val ReaderFontFamily = ChoiceSetting(
        key = "reader.font_family",
        defaultValue = com.vayana.core.datastore.settings.ReaderFontFamily.SERIF,
        titleRes = R.string.settings_reader_font_family_title,
        subtitleRes = R.string.settings_reader_font_family_subtitle,
        group = SettingsGroup.READER_TYPOGRAPHY,
        options = listOf(
            ChoiceOption(com.vayana.core.datastore.settings.ReaderFontFamily.SERIF, R.string.settings_reader_font_family_serif),
            ChoiceOption(com.vayana.core.datastore.settings.ReaderFontFamily.SANS, R.string.settings_reader_font_family_sans),
            ChoiceOption(com.vayana.core.datastore.settings.ReaderFontFamily.MONO, R.string.settings_reader_font_family_mono),
        ),
    )
    val ReaderTheme = ChoiceSetting(
        key = "reader.theme",
        defaultValue = com.vayana.core.datastore.settings.ReaderTheme.SYSTEM,
        titleRes = R.string.settings_reader_theme_title,
        subtitleRes = R.string.settings_reader_theme_subtitle,
        group = SettingsGroup.READER_TYPOGRAPHY,
        options = listOf(
            ChoiceOption(com.vayana.core.datastore.settings.ReaderTheme.SYSTEM, R.string.settings_reader_theme_system),
            ChoiceOption(com.vayana.core.datastore.settings.ReaderTheme.LIGHT, R.string.settings_reader_theme_light),
            ChoiceOption(com.vayana.core.datastore.settings.ReaderTheme.SEPIA, R.string.settings_reader_theme_sepia),
            ChoiceOption(com.vayana.core.datastore.settings.ReaderTheme.DARK, R.string.settings_reader_theme_dark),
        ),
    )
    val ReaderSideMargin = IntSetting(
        key = "reader.side_margin_percent",
        defaultValue = 10,
        titleRes = R.string.settings_reader_side_margin_title,
        subtitleRes = R.string.settings_reader_side_margin_subtitle,
        group = SettingsGroup.READER_LAYOUT,
        range = 0..24,
        step = 2,
    )
    val ReaderPublisherStyles = BooleanSetting(
        key = "reader.publisher_styles",
        defaultValue = true,
        titleRes = R.string.settings_reader_publisher_styles_title,
        subtitleRes = R.string.settings_reader_publisher_styles_subtitle,
        group = SettingsGroup.READER_LAYOUT,
    )
    val ReaderTapZoneMode = ChoiceSetting(
        key = "reader.tap_zone_mode",
        defaultValue = TapZoneMode.THREE_ZONE,
        titleRes = R.string.settings_reader_tap_zone_title,
        subtitleRes = R.string.settings_reader_tap_zone_subtitle,
        group = SettingsGroup.READER_BEHAVIOR,
        options = listOf(ChoiceOption(TapZoneMode.THREE_ZONE, R.string.settings_reader_tap_zone_three_zone)),
    )
    val ReaderVolumeKeys = BooleanSetting(
        key = "reader.volume_keys",
        defaultValue = false,
        titleRes = R.string.settings_reader_volume_keys_title,
        subtitleRes = R.string.settings_reader_volume_keys_subtitle,
        group = SettingsGroup.READER_BEHAVIOR,
    )
    val ReaderKeepAwake = BooleanSetting(
        key = "reader.keep_awake",
        defaultValue = false,
        titleRes = R.string.settings_reader_keep_awake_title,
        subtitleRes = R.string.settings_reader_keep_awake_subtitle,
        group = SettingsGroup.READER_BEHAVIOR,
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
        ReaderPublisherStyles,
        ReaderTapZoneMode,
        ReaderVolumeKeys,
        ReaderKeepAwake,
    )
}
