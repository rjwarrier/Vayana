package com.vayana.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.vayana.core.designsystem.dialog.ConfirmActionDialog
import com.vayana.core.designsystem.dialog.ExpressiveDialogHeader
import com.vayana.core.designsystem.dialog.ExpressiveDialogSurface
import com.vayana.core.designsystem.tokens.Elevations
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R
import com.vayana.reader.api.Footnote
import java.text.DecimalFormat
import java.util.concurrent.TimeUnit

/** Which page edge a vertical light swipe started on: brightness on the left, warm light on the right. */
internal enum class ReaderEdge { LEFT, RIGHT }

/** Play/pause, speed, sleep timer and stop for read-aloud, floating above the page. */
@Composable
internal fun ReadAloudBar(
    state: ReadAloudState,
    onTogglePlayback: () -> Unit,
    onCycleRate: () -> Unit,
    onCycleSleepTimer: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.padding(Spacing.md),
        shape = RoundedCornerShape(Radii.full),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = Elevations.shadowSmall,
        shadowElevation = Elevations.shadowSmall,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            IconButton(onClick = onTogglePlayback) {
                Icon(
                    imageVector = if (state.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    contentDescription = stringResource(
                        if (state.playing) R.string.reader_read_aloud_pause else R.string.reader_read_aloud_play,
                    ),
                )
            }
            TextButton(onClick = onCycleRate) {
                Text(stringResource(R.string.reader_read_aloud_rate, RateFormat.format(state.rate)))
            }
            TextButton(onClick = onCycleSleepTimer) {
                Icon(imageVector = Icons.Outlined.Bedtime, contentDescription = null, modifier = Modifier.size(Sizes.iconSmall))
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text(
                    text = if (state.sleepTimerMinutes > 0) {
                        stringResource(R.string.reader_read_aloud_sleep_minutes, state.sleepTimerMinutes)
                    } else {
                        stringResource(R.string.reader_read_aloud_sleep_off)
                    },
                )
            }
            IconButton(onClick = onStop) {
                Icon(imageVector = Icons.Outlined.Close, contentDescription = stringResource(R.string.reader_read_aloud_stop))
            }
        }
    }
}

@Composable
internal fun ReadAloudVoiceMissingDialog(onDismiss: () -> Unit, onOpenSettings: () -> Unit) {
    ConfirmActionDialog(
        onDismissRequest = onDismiss,
        icon = Icons.Outlined.RecordVoiceOver,
        title = stringResource(R.string.reader_read_aloud_voice_missing_title),
        body = stringResource(R.string.reader_read_aloud_voice_missing_body),
        confirmLabel = stringResource(R.string.reader_read_aloud_voice_missing_open),
        dismissLabel = stringResource(R.string.settings_reset_all_cancel),
        onConfirm = onOpenSettings,
    )
}

/** A footnote shown over the page, so reading it doesn't lose the place. */
@Composable
internal fun FootnoteDialog(footnote: Footnote, onDismiss: () -> Unit, onGoToNote: () -> Unit) {
    ExpressiveDialogSurface(onDismissRequest = onDismiss, scrollable = true) {
        ExpressiveDialogHeader(icon = Icons.Outlined.Description, title = stringResource(R.string.reader_footnote_title))
        Text(text = footnote.text, style = MaterialTheme.typography.bodyLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (footnote.href.isNotBlank()) {
                TextButton(onClick = onGoToNote) {
                    Text(stringResource(R.string.reader_footnote_go_to))
                }
            }
            FilledTonalButton(onClick = onDismiss, shape = Radii.buttonShape) {
                Text(stringResource(R.string.reader_footnote_close))
            }
        }
    }
}

/** "Welcome back" when a book is reopened after a while: how long it's been, where the reader was, their last highlight. */
@Composable
internal fun ReturnRecapCard(
    recap: ReaderRecap,
    chapterTitle: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Paddings.screenHorizontal)
            .clickable(onClick = onDismiss),
        shape = RoundedCornerShape(Radii.large),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = Elevations.shadowSmall,
        shadowElevation = Elevations.shadowSmall,
    ) {
        Column(modifier = Modifier.padding(Paddings.card), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(text = stringResource(R.string.reader_return_recap_title), style = MaterialTheme.typography.titleSmall)
            val days = TimeUnit.MILLISECONDS.toDays(recap.awayMillis).toInt()
            Text(
                text = if (days >= 1) {
                    stringResource(R.string.reader_return_recap_days, days)
                } else {
                    stringResource(R.string.reader_return_recap_hours, TimeUnit.MILLISECONDS.toHours(recap.awayMillis).toInt())
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            chapterTitle?.takeIf { it.isNotBlank() }?.let { chapter ->
                Text(text = stringResource(R.string.reader_return_recap_chapter, chapter), style = MaterialTheme.typography.bodyMedium)
            }
            recap.highlight?.let { highlight ->
                Text(
                    text = stringResource(R.string.reader_return_recap_highlight, highlight),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The live level while swiping an edge for brightness or warm light. */
@Composable
internal fun LightLevelIndicator(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(Radii.full),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

private val RateFormat = DecimalFormat("0.##")
