package com.vayana.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
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

fun DateFormatStyle.format(date: LocalDate): String = formatter().format(date)

fun DateFormatStyle.format(epochMillis: Long): String = format(epochMillis.toLocalDateTime().toLocalDate())

/** The date in this style followed by the device's short time. */
fun DateFormatStyle.formatWithTime(epochMillis: Long): String {
    val dateTime = epochMillis.toLocalDateTime()
    val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()).format(dateTime)
    return "${format(dateTime.toLocalDate())} $time"
}

/** This moment as a date in the app's [LocalDateFormatStyle]. */
@Composable
@ReadOnlyComposable
fun Long.asAppDate(): String = LocalDateFormatStyle.current.format(this)

@Composable
@ReadOnlyComposable
fun Long.asAppDateTime(): String = LocalDateFormatStyle.current.formatWithTime(this)

@Composable
@ReadOnlyComposable
fun LocalDate.asAppDate(): String = LocalDateFormatStyle.current.format(this)

private fun Long.toLocalDateTime() = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault())

private fun DateFormatStyle.formatter(): DateTimeFormatter = when (this) {
    DateFormatStyle.SYSTEM -> DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
    DateFormatStyle.DAY_FIRST -> DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
    DateFormatStyle.MONTH_FIRST -> DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
    DateFormatStyle.ISO -> DateTimeFormatter.ISO_LOCAL_DATE
}
