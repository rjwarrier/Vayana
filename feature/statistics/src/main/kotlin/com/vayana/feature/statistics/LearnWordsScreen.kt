package com.vayana.feature.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.font.FontStyle
import com.vayana.core.designsystem.component.VayanaLoadingIndicator
import com.vayana.core.designsystem.theme.vayanaAnimateContentSize
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.dictionary.api.PartOfSpeech
import com.vayana.dictionary.api.languageDisplayName
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vayana.core.database.model.WordLookupStat
import com.vayana.core.designsystem.theme.LocalFloatingNavigationInset
import com.vayana.core.designsystem.theme.PagedLazyColumn
import com.vayana.core.designsystem.theme.asAppDate
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LearnWordsRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: LearnWordsViewModel = hiltViewModel()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val meanings by viewModel.meanings.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.learn_words_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.notes_back_content_description),
                        )
                    }
                },
                // The app shell already pads for the status bar.
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
    ) { innerPadding ->
        if (summary.uniqueWords == 0) {
            LearnWordsEmptyState(contentPadding = innerPadding)
        } else {
            LearnWordsList(
                contentPadding = innerPadding,
                summary = summary,
                onHideKnownChange = viewModel::setHideKnown,
                onRemoveWord = viewModel::removeWord,
                meanings = meanings,
                onOpenWord = viewModel::loadMeaning,
            )
        }
    }
}

@Composable
private fun LearnWordsList(
    contentPadding: PaddingValues,
    summary: LearnWordsSummary,
    onHideKnownChange: (Boolean) -> Unit,
    onRemoveWord: (String) -> Unit,
    meanings: Map<String, WordMeaning>,
    onOpenWord: (String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    PagedLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Paddings.screenHorizontal,
            top = contentPadding.calculateTopPadding(),
            end = Paddings.screenHorizontal,
            bottom = contentPadding.calculateBottomPadding() + Spacing.lg + LocalFloatingNavigationInset.current,
        ),
        verticalArrangement = Arrangement.spacedBy(Paddings.heatmapCellGap),
    ) {
        item(key = "header") {
            LearnWordsHeader(summary = summary, onHideKnownChange = onHideKnownChange, modifier = Modifier.padding(bottom = Spacing.md))
        }
        itemsIndexed(summary.words, key = { _, word -> word.word }) { index, word ->
            val open = expanded == word.word
            WordLookupRow(
                word = word,
                shape = groupedShape(index, summary.words.size),
                expanded = open,
                meaning = meanings[word.word],
                onToggle = {
                    expanded = if (open) null else word.word
                    if (!open) onOpenWord(word.word)
                },
                onRemove = { onRemoveWord(word.word) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

/** The two totals side by side, then the filter: compact, so the words start high on the screen. */
@Composable
private fun LearnWordsHeader(summary: LearnWordsSummary, onHideKnownChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radii.extraLargeIncreased),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Row(modifier = Modifier.padding(Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                LearnWordsMetric(
                    value = stringResource(R.string.learn_words_unique_value, summary.uniqueWords),
                    label = stringResource(R.string.learn_words_unique_title),
                    modifier = Modifier.weight(1f),
                )
                LearnWordsMetric(
                    value = stringResource(R.string.learn_words_total_value, summary.totalLookups),
                    label = stringResource(R.string.learn_words_total_title),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            FilterChip(
                selected = summary.hideKnown,
                onClick = { onHideKnownChange(!summary.hideKnown) },
                label = { Text(stringResource(R.string.learn_words_hide_known, summary.hiddenKnownCount)) },
            )
            Text(
                text = stringResource(R.string.learn_words_sync_support),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LearnWordsMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = value, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
}

/** M3 Expressive grouped list: one block, outer corners round, inner ones tight. */
private fun groupedShape(index: Int, count: Int): RoundedCornerShape {
    val top = if (index == 0) Radii.extraLarge else Radii.extraSmall
    val bottom = if (index == count - 1) Radii.extraLarge else Radii.extraSmall
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

@Composable
private fun WordLookupRow(
    word: WordLookupStat,
    shape: RoundedCornerShape,
    expanded: Boolean,
    meaning: WordMeaning?,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onToggle,
        modifier = modifier.fillMaxWidth().vayanaAnimateContentSize(),
        shape = shape,
        color = if (expanded) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column {
            Row(
                modifier = Modifier.padding(start = Spacing.lg, top = Spacing.xs, bottom = Spacing.xs, end = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(vertical = Spacing.sm)) {
                    Text(
                        text = word.word,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.learn_words_last_seen, word.lastLookedUpAt.asAppDate()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(Radii.full),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Text(
                        text = stringResource(R.string.learn_words_count, word.count),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.learn_words_remove, word.word),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (expanded) WordMeaningContent(word.word, meaning)
        }
    }
}

/** The opened word's first few senses, each with its part of speech and an example when the dictionary has one. */
@Composable
private fun WordMeaningContent(word: String, meaning: WordMeaning?) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        when (meaning) {
            null, WordMeaning.Loading -> VayanaLoadingIndicator(modifier = Modifier.size(Sizes.iconLarge))
            WordMeaning.NotFound -> MeaningNote(stringResource(R.string.reader_dictionary_not_found, word))
            WordMeaning.NeedsDictionary -> MeaningNote(stringResource(R.string.learn_words_dictionary_needed))
            is WordMeaning.Found -> (meaning.entry.senses.take(MaxSensesShown).map { null to it } +
                meaning.entry.alsoIn.flatMap { other -> other.senses.take(2).mapIndexed { i, sense -> (if (i == 0) other.meaningLanguage else null) to sense } }
            ).forEach { (otherLanguage, sense) ->
                otherLanguage?.let {
                    Text(
                        text = stringResource(R.string.reader_dictionary_also_in, languageDisplayName(it)),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Column {
                    sense.partOfSpeech.shortLabel()?.let { label ->
                        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(text = sense.definition, style = MaterialTheme.typography.bodyMedium)
                    sense.examples.firstOrNull()?.let { example ->
                        Text(
                            text = stringResource(R.string.learn_words_example, example),
                            style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MeaningNote(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun PartOfSpeech.shortLabel(): String? = when (this) {
    PartOfSpeech.NOUN -> stringResource(R.string.dictionary_part_of_speech_noun)
    PartOfSpeech.VERB -> stringResource(R.string.dictionary_part_of_speech_verb)
    PartOfSpeech.ADJECTIVE -> stringResource(R.string.dictionary_part_of_speech_adjective)
    PartOfSpeech.ADVERB -> stringResource(R.string.dictionary_part_of_speech_adverb)
    PartOfSpeech.UNKNOWN -> null
}

private const val MaxSensesShown = 4

@Composable
private fun LearnWordsEmptyState(contentPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(Paddings.screenHorizontal),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.Translate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = Spacing.md),
            )
            Text(text = stringResource(R.string.learn_words_empty_title), style = MaterialTheme.typography.titleLarge)
            Text(
                text = stringResource(R.string.learn_words_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
    }
}
