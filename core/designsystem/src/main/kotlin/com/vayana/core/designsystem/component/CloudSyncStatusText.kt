package com.vayana.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.vayana.core.resources.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** The device a synced snapshot came from, or a generic label when it didn't say. */
@Composable
fun syncDeviceLabel(deviceLabel: String?): String =
    deviceLabel?.trim()?.takeIf { it.isNotEmpty() } ?: stringResource(R.string.sync_status_unknown_device)

/** A consistent, human-friendly description of the cloud snapshot behind a sync conflict. */
@Composable
fun cloudSyncStatusText(syncedAt: Long?, deviceLabel: String?): String {
    val device = syncDeviceLabel(deviceLabel)
    if (syncedAt == null) {
        return stringResource(R.string.sync_status_from_device, device)
    }

    val now = remember(syncedAt) { System.currentTimeMillis() }
    return when (val presentation = cloudSyncTimePresentation(syncedAt, now)) {
        is CloudSyncTimePresentation.MinutesAgo -> pluralStringResource(
            R.plurals.sync_status_minutes_ago,
            presentation.value,
            presentation.value,
            device,
        )
        is CloudSyncTimePresentation.HoursAgo -> pluralStringResource(
            R.plurals.sync_status_hours_ago,
            presentation.value,
            presentation.value,
            device,
        )
        CloudSyncTimePresentation.OnAnotherDay -> {
            val locale = LocalLocale.current.platformLocale
            // The pattern is a resource so each language can order the day, month and time its own way.
            val pattern = stringResource(R.string.sync_status_date_time_pattern)
            val dateTime = Instant.ofEpochMilli(syncedAt)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern(pattern, locale))
            stringResource(R.string.sync_status_on_date, dateTime, device)
        }
    }
}

internal sealed interface CloudSyncTimePresentation {
    data class MinutesAgo(val value: Int) : CloudSyncTimePresentation
    data class HoursAgo(val value: Int) : CloudSyncTimePresentation
    data object OnAnotherDay : CloudSyncTimePresentation
}

internal fun cloudSyncTimePresentation(
    syncedAt: Long,
    now: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): CloudSyncTimePresentation {
    val syncedDate = Instant.ofEpochMilli(syncedAt).atZone(zoneId).toLocalDate()
    val currentDate = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()
    if (syncedDate != currentDate) return CloudSyncTimePresentation.OnAnotherDay

    val elapsedMillis = (now - syncedAt).coerceAtLeast(0L)
    val elapsedMinutes = TimeUnit.MILLISECONDS.toMinutes(elapsedMillis).toInt()
    return if (elapsedMinutes < 60) {
        CloudSyncTimePresentation.MinutesAgo(elapsedMinutes.coerceAtLeast(1))
    } else {
        CloudSyncTimePresentation.HoursAgo(TimeUnit.MILLISECONDS.toHours(elapsedMillis).toInt().coerceAtLeast(1))
    }
}
