package com.vayana.feature.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.Animatable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.vayana.core.designsystem.theme.VayanaLinearProgressIndicator
import com.vayana.core.designsystem.theme.isMotionEnabled
import com.vayana.core.designsystem.theme.vayanaTween
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R
import java.time.Month
import java.time.format.TextStyle

@Composable
internal fun YearInBooksCard(year: YearInBooks) {
    val maxMonth = year.monthlyFinishes.maxOrNull()?.coerceAtLeast(1) ?: 1
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.medium),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(Paddings.card), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(R.string.statistics_year_books_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.statistics_year_books_finished, year.finishedCount, year.year),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (monthIndex in 0 until 12) {
                    MonthFinishBar(
                        monthIndex = monthIndex,
                        count = year.monthlyFinishes[monthIndex],
                        maxCount = maxMonth,
                        current = monthIndex + 1 == year.currentMonth,
                        future = monthIndex + 1 > year.currentMonth,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Text(
                stringResource(R.string.statistics_year_books_source),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            year.goalPace?.let { pace ->
                Text(
                    stringResource(R.string.statistics_goals_yearly_value, year.finishedCount, year.goalTarget),
                    style = MaterialTheme.typography.bodyMedium,
                )
                VayanaLinearProgressIndicator(
                    progress = { (year.finishedCount.toFloat() / year.goalTarget).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                val message = when {
                    pace.booksRemaining == 0 -> stringResource(R.string.statistics_year_books_goal_reached)
                    pace.daysPerBook == 1 -> stringResource(R.string.statistics_year_books_goal_daily, pace.booksRemaining)
                    pace.daysPerBook != null -> stringResource(
                        R.string.statistics_year_books_goal_pace,
                        pace.booksRemaining,
                        pace.daysPerBook,
                    )
                    pace.daysRemaining == 1 -> stringResource(R.string.statistics_year_books_goal_today, pace.booksRemaining)
                    else -> stringResource(
                        R.string.statistics_year_books_goal_urgent,
                        pace.booksRemaining,
                        pace.daysRemaining,
                    )
                }
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MonthFinishBar(monthIndex: Int, count: Int, maxCount: Int, current: Boolean, future: Boolean, modifier: Modifier) {
    val barHeight = remember { Animatable(0f) }
    val motionEnabled = isMotionEnabled()
    val animationSpec = vayanaTween<Float>(durationMillis = 300, delayMillis = monthIndex * 12)
    LaunchedEffect(count, maxCount, motionEnabled) {
        val target = (count.toFloat() / maxCount).coerceIn(0f, 1f)
        if (motionEnabled) barHeight.animateTo(target, animationSpec) else barHeight.snapTo(target)
    }
    val locale = LocalLocale.current.platformLocale
    val month = Month.of(monthIndex + 1).getDisplayName(TextStyle.FULL, locale)
    val shortMonth = Month.of(monthIndex + 1).getDisplayName(TextStyle.NARROW, locale)
    val description = if (current) {
        stringResource(R.string.statistics_year_books_current_month_accessibility, month, count)
    } else {
        stringResource(R.string.statistics_year_books_month_accessibility, month, count)
    }
    Column(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (count > 0) count.toString() else " ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(modifier = Modifier.fillMaxWidth().height(36.dp), contentAlignment = Alignment.BottomCenter) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(Radii.full)),
            )
            if (count > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .fillMaxHeight(barHeight.value)
                        .background(if (future) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(topStart = Radii.extraSmall, topEnd = Radii.extraSmall)),
                )
            }
        }
        Text(
            shortMonth,
            style = MaterialTheme.typography.labelSmall,
            color = when {
                current -> MaterialTheme.colorScheme.primary
                future -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(top = Spacing.xs),
        )
        if (current) {
            Box(modifier = Modifier.padding(top = 2.dp).fillMaxWidth(0.55f).height(2.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(Radii.full)))
        }
    }
}
