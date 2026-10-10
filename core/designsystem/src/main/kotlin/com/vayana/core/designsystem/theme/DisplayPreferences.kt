package com.vayana.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalLocale
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** How dates are written across the app. [SYSTEM] follows the device language. */
enum class DateFormatStyle { SYSTEM, DAY_FIRST, MONTH_FIRST, ISO }

val LocalDateFormatStyle = staticCompositionLocalOf { DateFormatStyle.SYSTEM }

/** Whether the color scheme comes from the wallpaper (Material You). */
val LocalDynamicColor = staticCompositionLocalOf { false }

fun DateFormatStyle.format(date: LocalDate, locale: Locale = Locale.getDefault()): String = formatter(locale).format(date)

fun DateFormatStyle.format(epochMillis: Long, locale: Locale = Locale.getDefault()): String = format(epochMillis.toLocalDateTime().toLocalDate(), locale)

/** The date in this style followed by the device's short time. */
fun DateFormatStyle.formatWithTime(epochMillis: Long, locale: Locale = Locale.getDefault()): String {
    val dateTime = epochMillis.toLocalDateTime()
    val time = timeFormatterCache.getOrPut(locale) { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale) }
        .format(dateTime)
    return "${format(dateTime.toLocalDate(), locale)} $time"
}

/** This moment as a date in the app's [LocalDateFormatStyle]. */
@Composable
@ReadOnlyComposable
fun Long.asAppDate(): String = LocalDateFormatStyle.current.format(this, LocalLocale.current.platformLocale)

@Composable
@ReadOnlyComposable
fun Long.asAppDateTime(): String = LocalDateFormatStyle.current.formatWithTime(this, LocalLocale.current.platformLocale)

@Composable
@ReadOnlyComposable
fun LocalDate.asAppDate(): String = LocalDateFormatStyle.current.format(this, LocalLocale.current.platformLocale)

private fun Long.toLocalDateTime() = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault())

/**
 * Formatters are immutable and thread-safe but costly to build (patterns are parsed), and dates are formatted in list
 * rows on every recomposition, so each style keeps one per locale.
 */
private fun DateFormatStyle.formatter(locale: Locale): DateTimeFormatter {
    return formatterCache.getOrPut(this to locale) {
        when (this) {
            DateFormatStyle.SYSTEM -> DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
            DateFormatStyle.DAY_FIRST -> DateTimeFormatter.ofPattern("d MMM yyyy", locale)
            DateFormatStyle.MONTH_FIRST -> DateTimeFormatter.ofPattern("MMM d, yyyy", locale)
            DateFormatStyle.ISO -> DateTimeFormatter.ISO_LOCAL_DATE
        }
    }
}

private val formatterCache = java.util.concurrent.ConcurrentHashMap<Pair<DateFormatStyle, Locale>, DateTimeFormatter>()
private val timeFormatterCache = java.util.concurrent.ConcurrentHashMap<Locale, DateTimeFormatter>()
