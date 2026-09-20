package com.vayana.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Style
import androidx.compose.runtime.LaunchedEffect
import com.vayana.core.designsystem.theme.VayanaCircularProgressIndicator
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.vayana.core.datastore.settings.FloatSetting
import com.vayana.core.datastore.settings.SettingsRegistry
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
import kotlin.math.roundToInt

/** Which page edge a vertical light swipe started on: brightness on the left, warm light on the right. */
internal enum class ReaderEdge { LEFT, RIGHT }

/** Play/pause, speed, sleep timer and stop for read-aloud, laid out below the reading viewport. */
@Composable
internal fun ReadAloudBar(
    state: ReadAloudState,
    onTogglePlayback: () -> Unit,
    onRateChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit,
    onCycleSleepTimer: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val adjustment = remember { mutableStateOf<ReadAloudAdjustment?>(null) }
    val toggleAdjustment = { target: ReadAloudAdjustment ->
        adjustment.value = if (adjustment.value == target) null else target
    }
    val sliderModifier = Modifier.padding(horizontal = Paddings.screenHorizontal).padding(top = Spacing.sm)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = Radii.extraLarge, topEnd = Radii.extraLarge),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = Elevations.shadowSmall,
        shadowElevation = Elevations.shadowSmall,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            when (adjustment.value) {
                ReadAloudAdjustment.SPEED -> ReadAloudAdjustmentSlider(
                    label = stringResource(R.string.reader_read_aloud_speed_control),
                    value = state.rate,
                    setting = SettingsRegistry.ReadAloudRate,
                    onValueChangeFinished = onRateChange,
                    modifier = sliderModifier,
                )
                ReadAloudAdjustment.PITCH -> ReadAloudAdjustmentSlider(
                    label = stringResource(R.string.reader_read_aloud_pitch_control),
                    value = state.pitch,
                    setting = SettingsRegistry.ReadAloudPitch,
                    onValueChangeFinished = onPitchChange,
                    modifier = sliderModifier,
                )
                null -> Unit
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = Paddings.screenHorizontal, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                IconButton(onClick = onTogglePlayback) {
                    Icon(
                        imageVector = if (state.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = stringResource(
                            if (state.playing) R.string.reader_read_aloud_pause else R.string.reader_read_aloud_play,
                        ),
                    )
                }
                ReadAloudSettingButton(
                    label = stringResource(R.string.reader_read_aloud_speed_control),
                    value = state.rate.asMultiplier(),
                    selected = adjustment.value == ReadAloudAdjustment.SPEED,
                    onClick = { toggleAdjustment(ReadAloudAdjustment.SPEED) },
                )
                ReadAloudSettingButton(
                    label = stringResource(R.string.reader_read_aloud_pitch_control),
                    value = state.pitch.asMultiplier(),
                    selected = adjustment.value == ReadAloudAdjustment.PITCH,
                    onClick = { toggleAdjustment(ReadAloudAdjustment.PITCH) },
                )
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
}

/** A labelled slider that snaps to [setting]'s step while dragging and reports the value once, when released. */
@Composable
internal fun ReadAloudAdjustmentSlider(
    label: String,
    value: Float,
    setting: FloatSetting,
    onValueChangeFinished: (Float) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val pendingValue = remember(value, setting) { mutableFloatStateOf(value) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = label, style = MaterialTheme.typography.labelLarge)
            Text(text = pendingValue.floatValue.asMultiplier(), style = MaterialTheme.typography.labelLarge)
        }
        subtitle?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = pendingValue.floatValue,
            onValueChange = { raw -> pendingValue.floatValue = raw.roundToStep(setting) },
            onValueChangeFinished = { onValueChangeFinished(pendingValue.floatValue) },
            valueRange = setting.range,
            steps = setting.sliderSteps(),
        )
    }
}

@Composable
private fun ReadAloudSettingButton(
    label: String,
    value: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = label, style = MaterialTheme.typography.labelSmall)
            Text(text = value, style = MaterialTheme.typography.labelMedium)
        }
    }
}

private enum class ReadAloudAdjustment { SPEED, PITCH }

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
    onReviewWords: () -> Unit,
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
            if (recap.dueWords > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.reader_return_recap_due_words, recap.dueWords),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onReviewWords) {
                        Text(stringResource(R.string.reader_return_recap_review))
                    }
                }
            }
        }
    }
}

/** Unusual words in the chapter on screen, to learn before reading it. Loads when the panel opens. */
@Composable
internal fun WordsPanel(
    state: ChapterWordsState,
    onLoad: () -> Unit,
    onWordClick: (String) -> Unit,
    onSaveWord: (ChapterWord) -> Unit,
) {
    LaunchedEffect(Unit) { onLoad() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(text = stringResource(R.string.reader_words_title), style = MaterialTheme.typography.titleSmall)
        Text(
            text = stringResource(R.string.reader_words_support),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (state) {
            ChapterWordsState.Idle, ChapterWordsState.Loading ->
                VayanaCircularProgressIndicator(modifier = Modifier.size(Sizes.iconSmall))
            ChapterWordsState.DictionaryRequired -> Text(
                text = stringResource(R.string.reader_words_dictionary_required),
                style = MaterialTheme.typography.bodyMedium,
            )
            is ChapterWordsState.Ready -> if (state.words.isEmpty()) {
                Text(text = stringResource(R.string.reader_words_empty), style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = Sizes.contentMaxWidth)) {
                    items(state.words, key = { it.word }) { word ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onWordClick(word.word) }
                                    .padding(vertical = Spacing.xs),
                            ) {
                                Text(text = word.word, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = word.definition,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (word.saved) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = stringResource(R.string.reader_dictionary_saved_to_vocabulary),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(Spacing.md),
                                )
                            } else {
                                IconButton(onClick = { onSaveWord(word) }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Style,
                                        contentDescription = stringResource(R.string.reader_dictionary_add_to_vocabulary),
                                    )
                                }
                            }
                        }
                    }
                }
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

/** "1×", "1.5×": how a speed or pitch multiplier reads. */
internal fun Float.asMultiplier(): String = "${RateFormat.format(this)}×"
