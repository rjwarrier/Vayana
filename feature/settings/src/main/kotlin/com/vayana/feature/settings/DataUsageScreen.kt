package com.vayana.feature.settings

import android.text.format.Formatter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vayana.core.designsystem.component.VayanaLoadingIndicator
import com.vayana.core.designsystem.theme.LocalFloatingNavigationInset
import com.vayana.core.designsystem.theme.PagedLazyColumn
import com.vayana.core.designsystem.theme.asAppDateTime
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R
import com.vayana.core.sync.usage.DataNetwork
import com.vayana.core.sync.usage.DayUsage
import com.vayana.core.sync.usage.NetworkSplit
import com.vayana.core.sync.usage.SyncDataUsageEntry
import com.vayana.core.sync.usage.SyncKind
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun DataUsageRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: DataUsageViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    DataUsageScreen(state = state, onBack = onBack, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DataUsageScreen(state: DataUsageState?, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.data_usage_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.notes_back_content_description))
                    }
                },
                // The app shell already pads for the status bar, as on Settings and Diagnostics.
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
    ) { innerPadding ->
        if (state == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                VayanaLoadingIndicator(Modifier.size(Sizes.fab))
            }
            return@Scaffold
        }
        PagedLazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Paddings.screenHorizontal,
                end = Paddings.screenHorizontal,
                top = innerPadding.calculateTopPadding() + Spacing.sm,
                bottom = innerPadding.calculateBottomPadding() + Spacing.xl + LocalFloatingNavigationInset.current,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item { SettingsContentContainer { TodayHero(state.app.firstOrNull { it.period == UsagePeriod.TODAY }) } }
            item { SettingsContentContainer { PeriodTiles(state.app.filter { it.period != UsagePeriod.TODAY }) } }
            item { SettingsContentContainer { AppHistoryCard(state.history) } }
            item { SettingsContentContainer { SyncSummaryCard(state.sync) } }
            item { SettingsContentContainer { RecentSyncsCard(state.sync.recent) } }
        }
    }
}

/** Today's total, large, on the primary container: the one number most visits are for. */
@Composable
private fun TodayHero(today: AppPeriodUsage?) {
    val notAvailable = stringResource(R.string.data_usage_not_available)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.extraLargeIncreased),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(Modifier.padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(R.string.data_usage_app_section) + " · " + stringResource(R.string.data_usage_period_today), style = MaterialTheme.typography.labelLarge)
            Text(
                today?.total()?.let { bytes(it) } ?: notAvailable,
                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                NetworkPill(Icons.Outlined.Wifi, stringResource(R.string.data_usage_wifi), today?.wifiBytes?.let { bytes(it) } ?: notAvailable)
                NetworkPill(Icons.Outlined.SignalCellularAlt, stringResource(R.string.data_usage_mobile), today?.mobileBytes?.let { bytes(it) } ?: notAvailable)
            }
            Text(stringResource(R.string.data_usage_app_note), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun NetworkPill(icon: ImageVector, label: String, value: String) {
    // A tint of the hero's own text colour reads on any container colour, light or dark theme.
    val onHero = MaterialTheme.colorScheme.onPrimaryContainer
    Surface(shape = Radii.chipShape, color = onHero.copy(alpha = 0.12f), contentColor = onHero) {
        Row(
            Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(Sizes.iconSmall))
            Text("$label $value", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** 7 and 30 days side by side, each on its own tonal container. */
@Composable
private fun PeriodTiles(periods: List<AppPeriodUsage>) {
    val notAvailable = stringResource(R.string.data_usage_not_available)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            periods.forEachIndexed { index, period ->
                val (container, content) = if (index == 0) {
                    MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
                }
                Surface(Modifier.weight(1f), shape = RoundedCornerShape(Radii.largeIncreased), color = container, contentColor = content) {
                    Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(stringResource(period.period.label()), style = MaterialTheme.typography.labelLarge)
                        Text(period.total()?.let { bytes(it) } ?: notAvailable, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
                        Text("${stringResource(R.string.data_usage_wifi)} ${period.wifiBytes?.let { bytes(it) } ?: notAvailable}", style = MaterialTheme.typography.bodySmall)
                        Text("${stringResource(R.string.data_usage_mobile)} ${period.mobileBytes?.let { bytes(it) } ?: notAvailable}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (periods.any { it.mobileBytes == null }) {
            Text(stringResource(R.string.data_usage_not_available_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun UsagePeriod.label(): Int = when (this) {
    UsagePeriod.TODAY -> R.string.data_usage_period_today
    UsagePeriod.WEEK -> R.string.data_usage_period_week
    UsagePeriod.MONTH -> R.string.data_usage_period_month
}

private fun AppPeriodUsage.total(): Long? = listOfNotNull(wifiBytes, mobileBytes).takeIf { it.isNotEmpty() }?.sum()

@Composable
private fun UsageCard(title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.extraLargeIncreased),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            content()
        }
    }
}

@Composable
private fun AppHistoryCard(history: List<DayUsage>) {
    UsageCard(stringResource(R.string.data_usage_history_section)) {
        val days = history.lastDays(30, LocalDate.now())
        Text(stringResource(R.string.data_usage_history_chart), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        UsageChart(days, stringResource(R.string.data_usage_history_chart_description, bytes(days.sumOf { it.split.totalBytes }), bytes(days.sumOf { it.split.mobileBytes })))
        Legend()
        val locale = LocalLocale.current.platformLocale
        val monthFormat = remember(locale) { DateTimeFormatter.ofPattern("MMMM yyyy", locale) }
        history.monthlyTotals().forEach { (month, split) -> MonthRow(month.format(monthFormat), split) }
        Text(stringResource(R.string.data_usage_history_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A month's total with a bar split into its Wi-Fi and mobile shares. */
@Composable
private fun MonthRow(label: String, split: NetworkSplit) {
    val wifiColor = MaterialTheme.colorScheme.primary
    val mobileColor = MaterialTheme.colorScheme.tertiary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(bytes(split.totalBytes), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
        }
        Canvas(Modifier.fillMaxWidth().height(10.dp)) {
            val radius = CornerRadius(size.height / 2)
            drawRoundRect(track, cornerRadius = radius)
            val total = split.totalBytes.takeIf { it > 0 } ?: return@Canvas
            val wifiWidth = size.width * split.wifiBytes / total
            drawRoundRect(mobileColor, cornerRadius = radius)
            if (wifiWidth > 0f) drawRoundRect(wifiColor, size = Size(wifiWidth, size.height), cornerRadius = radius)
        }
        Text(
            "${stringResource(R.string.data_usage_wifi)} ${bytes(split.wifiBytes)} · ${stringResource(R.string.data_usage_mobile)} ${bytes(split.mobileBytes)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SyncSummaryCard(sync: SyncUsageSummary) {
    UsageCard(stringResource(R.string.data_usage_sync_section)) {
        Text(
            pluralStringResource(R.plurals.data_usage_sync_today, sync.todayCount, sync.todayCount, bytes(sync.today.wifiBytes), bytes(sync.today.mobileBytes)),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            stringResource(R.string.data_usage_sync_month, bytes(sync.lastMonth.totalBytes), bytes(sync.lastMonth.mobileBytes)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(stringResource(R.string.data_usage_sync_chart_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        UsageChart(
            sync.days,
            stringResource(R.string.data_usage_sync_chart_description, bytes(sync.days.sumOf { it.split.totalBytes }), bytes(sync.days.sumOf { it.split.mobileBytes })),
        )
        Legend()
    }
}

/** One rounded bar per day: Wi-Fi at the bottom, mobile stacked on top, scaled to the busiest day. */
@Composable
private fun UsageChart(days: List<DayUsage>, description: String) {
    val wifiColor = MaterialTheme.colorScheme.primary
    val mobileColor = MaterialTheme.colorScheme.tertiary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val max = days.maxOfOrNull { it.split.totalBytes }?.takeIf { it > 0 } ?: 1L
    Column {
        Canvas(Modifier.fillMaxWidth().height(120.dp).semantics { contentDescription = description }) {
            val slot = size.width / days.size
            val barWidth = slot * 0.62f
            val radius = CornerRadius(barWidth / 2)
            days.forEachIndexed { index, day ->
                val left = index * slot + (slot - barWidth) / 2
                // A faint full-height track keeps quiet days visible as slots, not gaps.
                drawRoundRect(track, Offset(left, 0f), Size(barWidth, size.height), radius)
                val totalHeight = size.height * day.split.totalBytes / max
                if (totalHeight <= 0f) return@forEachIndexed
                val wifiHeight = size.height * day.split.wifiBytes / max
                drawRoundRect(mobileColor, Offset(left, size.height - totalHeight), Size(barWidth, totalHeight), radius)
                if (wifiHeight > 0f) drawRoundRect(wifiColor, Offset(left, size.height - wifiHeight), Size(barWidth, wifiHeight), radius)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = Spacing.xs)) {
            // About seven labels whatever the span, so two-digit dates never collide on a phone.
            val every = (days.size + 6) / 7
            days.forEachIndexed { index, day ->
                Text(
                    if ((days.lastIndex - index) % every == 0) "${day.date.dayOfMonth}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
        LegendDot(MaterialTheme.colorScheme.primary, stringResource(R.string.data_usage_wifi))
        LegendDot(MaterialTheme.colorScheme.tertiary, stringResource(R.string.data_usage_mobile))
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Surface(Modifier.size(12.dp), shape = CircleShape, color = color) {}
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun RecentSyncsCard(recent: List<SyncDataUsageEntry>) {
    UsageCard(stringResource(R.string.data_usage_sync_recent)) {
        if (recent.isEmpty()) Text(stringResource(R.string.data_usage_sync_empty), style = MaterialTheme.typography.bodyMedium)
        recent.forEach { SyncRow(it) }
    }
}

@Composable
private fun SyncRow(entry: SyncDataUsageEntry) {
    val (kindLabel, kindIcon) = when (entry.kind) {
        SyncKind.FULL -> R.string.data_usage_kind_full to Icons.Outlined.CloudSync
        SyncKind.BACKGROUND -> R.string.data_usage_kind_background to Icons.Outlined.Sync
        SyncKind.LAUNCH_CHECK -> R.string.data_usage_kind_launch_check to Icons.Outlined.RocketLaunch
        SyncKind.HISTORY -> R.string.data_usage_kind_history to Icons.Outlined.History
    }
    val network = stringResource(
        when (entry.network) {
            DataNetwork.WIFI -> R.string.data_usage_wifi
            DataNetwork.MOBILE -> R.string.data_usage_mobile
            DataNetwork.OTHER -> R.string.data_usage_network_other
            DataNetwork.NONE -> R.string.data_usage_network_none
        },
    )
    val mobile = entry.network == DataNetwork.MOBILE
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            Modifier.size(Sizes.touchTarget - Spacing.sm),
            shape = CircleShape,
            color = if (mobile) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
            contentColor = if (mobile) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(kindIcon, contentDescription = null, modifier = Modifier.size(Sizes.iconSmall)) }
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(kindLabel), style = MaterialTheme.typography.titleSmall)
            Text("${entry.at.asAppDateTime()} · $network", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("↓ ${bytes(entry.downBytes)}", style = MaterialTheme.typography.labelLarge)
            Text("↑ ${bytes(entry.upBytes)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun bytes(value: Long): String = Formatter.formatShortFileSize(LocalContext.current, value)
