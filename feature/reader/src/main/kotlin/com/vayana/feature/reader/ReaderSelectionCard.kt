package com.vayana.feature.reader

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.IntSize
import com.vayana.core.designsystem.component.ConnectedButton
import com.vayana.core.designsystem.component.VayanaConnectedButtonGroup
import com.vayana.core.designsystem.component.VayanaLoadingIndicator
import com.vayana.core.designsystem.component.VayanaSwatchButton
import com.vayana.core.designsystem.component.asString
import com.vayana.core.designsystem.component.morphingButtonShapes
import com.vayana.core.designsystem.component.morphingIconButtonShapes
import com.vayana.core.designsystem.theme.DisplayProfile
import com.vayana.core.designsystem.theme.LocalDisplayProfile
import com.vayana.core.designsystem.theme.vayanaFadeIn
import com.vayana.core.designsystem.theme.vayanaFadeOut
import com.vayana.core.designsystem.theme.vayanaSpring
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.designsystem.tokens.Strokes
import com.vayana.core.resources.R
import com.vayana.dictionary.api.DictionaryEntry
import com.vayana.dictionary.api.DictionarySense
import com.vayana.dictionary.api.OnlineDictionarySource
import com.vayana.dictionary.api.PartOfSpeech

/**
 * The card over a selection (or a word looked up from elsewhere): the dictionary on top, then highlight swatches and
 * the selection's actions. M3 Expressive: separated from the page by its surface tier, not a shadow; buttons whose
 * shapes morph when pressed; a connected button group for the actions; state changes fade and resize on springs. On
 * E-Ink all of that snaps, and outlines take the place of tiers.
 */
@Composable
internal fun SelectionActions(
    modifier: Modifier = Modifier,
    selectionActionsEnabled: Boolean,
    readAloudAvailable: Boolean,
    onReadAloud: () -> Unit,
    dictionaryLookup: DictionaryLookupState,
    recentLookups: List<String>,
    onHighlight: (String) -> Unit,
    onUnderline: () -> Unit,
    onCopy: () -> Unit,
    onNote: () -> Unit,
    onShare: () -> Unit,
    onDownloadDictionary: () -> Unit,
    onInstallDictionary: () -> Unit,
    onLookupWord: (String) -> Unit,
    onLookupOnline: (OnlineDictionarySource) -> Unit,
    onSaveLookupAsNote: (DictionaryEntry) -> Unit,
    onSaveLookupAsVocabulary: (DictionaryEntry) -> Unit,
) {
    val eink = LocalDisplayProfile.current == DisplayProfile.E_INK
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight * DictionaryCardMaximumHeightFraction)
                .padding(Paddings.screenHorizontal, Spacing.md),
            color = if (eink) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLargeIncreased,
            border = if (eink) BorderStroke(Strokes.hairlineEink, MaterialTheme.colorScheme.outline) else null,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Paddings.card, vertical = Spacing.lg),
            ) {
                DictionaryLookupContent(
                    state = dictionaryLookup,
                    recentLookups = recentLookups,
                    onDownloadDictionary = onDownloadDictionary,
                    onInstallDictionary = onInstallDictionary,
                    onLookupWord = onLookupWord,
                    onLookupOnline = onLookupOnline,
                    onSaveLookupAsNote = onSaveLookupAsNote,
                    onSaveLookupAsVocabulary = onSaveLookupAsVocabulary,
                )
                if (selectionActionsEnabled) {
                    if (dictionaryLookup != DictionaryLookupState.Hidden) Spacer(modifier = Modifier.height(Spacing.lg))
                    SelectionMarkRow(onHighlight = onHighlight, onUnderline = onUnderline)
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    val copy = stringResource(R.string.reader_selection_copy)
                    val note = stringResource(R.string.reader_selection_note)
                    val share = stringResource(R.string.reader_selection_share)
                    val readAloud = stringResource(R.string.reader_selection_read_aloud)
                    // Remembered: the card recomposes as the selection moves, and a fresh list defeats skipping.
                    val actions = remember(copy, note, share, readAloud, readAloudAvailable, onCopy, onNote, onShare, onReadAloud) {
                        buildList {
                            add(ConnectedButton(copy, Icons.Outlined.ContentCopy, onCopy))
                            add(ConnectedButton(note, Icons.Outlined.EditNote, onNote))
                            add(ConnectedButton(share, Icons.Outlined.Share, onShare))
                            if (readAloudAvailable) add(ConnectedButton(readAloud, Icons.Outlined.Headphones, onReadAloud))
                        }
                    }
                    VayanaConnectedButtonGroup(
                        buttons = actions,
                        iconAboveLabel = true,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                }
            }
        }
    }
}

/** Highlight colours as swatches and underline as an icon: one line that always fits. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SelectionMarkRow(onHighlight: (String) -> Unit, onUnderline: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HighlightColor.entries.forEach { color ->
            VayanaSwatchButton(color = color.swatch, label = stringResource(color.labelRes), onClick = { onHighlight(color.key) })
        }
        IconButton(onClick = onUnderline, shapes = morphingIconButtonShapes()) {
            Icon(
                imageVector = Icons.Outlined.FormatUnderlined,
                contentDescription = stringResource(R.string.reader_selection_underline),
            )
        }
    }
}

@Composable
private fun DictionaryLookupContent(
    state: DictionaryLookupState,
    recentLookups: List<String>,
    onDownloadDictionary: () -> Unit,
    onInstallDictionary: () -> Unit,
    onLookupWord: (String) -> Unit,
    onLookupOnline: (OnlineDictionarySource) -> Unit,
    onSaveLookupAsNote: (DictionaryEntry) -> Unit,
    onSaveLookupAsVocabulary: (DictionaryEntry) -> Unit,
) {
    val enter = vayanaFadeIn()
    val exit = vayanaFadeOut()
    val sizeSpec = vayanaSpring<IntSize>()
    // Keyed by kind of state, so e.g. saving a word to vocabulary updates the entry in place instead of fading it.
    AnimatedContent(
        targetState = state,
        contentKey = { it::class },
        transitionSpec = { enter togetherWith exit using SizeTransform(clip = false) { _, _ -> sizeSpec } },
        label = "dictionaryLookup",
    ) { current ->
        Column(modifier = Modifier.fillMaxWidth()) {
            when (current) {
                DictionaryLookupState.Hidden -> Unit
                is DictionaryLookupState.LookingUp -> LookupProgress(stringResource(R.string.reader_dictionary_looking_up))
                is DictionaryLookupState.Installing -> LookupProgress(stringResource(R.string.reader_dictionary_installing))
                is DictionaryLookupState.PackRequired -> PackRequiredContent(
                    recentLookups = recentLookups,
                    onDownloadDictionary = onDownloadDictionary,
                    onInstallDictionary = onInstallDictionary,
                    onLookupWord = onLookupWord,
                    onLookupOnline = onLookupOnline,
                )
                is DictionaryLookupState.Found -> DictionaryEntryContent(
                    state = current,
                    onLookupWord = onLookupWord,
                    onSaveLookupAsNote = onSaveLookupAsNote,
                    onSaveLookupAsVocabulary = onSaveLookupAsVocabulary,
                )
                is DictionaryLookupState.NotFound -> {
                    LookupMessage(stringResource(R.string.reader_dictionary_not_found, current.word))
                    OnlineLookupButtons(onLookupOnline = onLookupOnline)
                }
                // No offline lookup for a phrase: just the web buttons, compact, since most short selections are
                // made to highlight rather than to look up, and the phrase is already marked on the page.
                is DictionaryLookupState.Phrase -> OnlineLookupButtons(onLookupOnline = onLookupOnline, labelled = false)
                is DictionaryLookupState.Online -> OnlineLookupContent(current, onLookupOnline)
                is DictionaryLookupState.Failed -> {
                    LookupMessage(current.message.asString(), isError = true)
                    OutlinedButton(
                        onClick = onInstallDictionary,
                        shapes = morphingButtonShapes(),
                        modifier = Modifier.padding(top = Spacing.sm),
                    ) {
                        Text(stringResource(R.string.reader_dictionary_choose_another))
                    }
                }
            }
        }
    }
}

@Composable
private fun LookupProgress(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        VayanaLoadingIndicator(modifier = Modifier.size(Sizes.iconLarge))
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun LookupMessage(text: String, isError: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PackRequiredContent(
    recentLookups: List<String>,
    onDownloadDictionary: () -> Unit,
    onInstallDictionary: () -> Unit,
    onLookupWord: (String) -> Unit,
    onLookupOnline: (OnlineDictionarySource) -> Unit,
) {
    LookupMessage(stringResource(R.string.reader_dictionary_pack_required))
    Row(
        modifier = Modifier.padding(top = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        FilledTonalButton(onClick = onDownloadDictionary, shapes = morphingButtonShapes()) {
            Icon(imageVector = Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(Sizes.iconSmall))
            Spacer(modifier = Modifier.size(Spacing.sm))
            Text(stringResource(R.string.reader_dictionary_download))
        }
        OutlinedButton(onClick = onInstallDictionary, shapes = morphingButtonShapes()) {
            Text(stringResource(R.string.reader_dictionary_install_zip))
        }
    }
    OnlineLookupButtons(onLookupOnline = onLookupOnline)
    if (recentLookups.isNotEmpty()) {
        SectionLabel(stringResource(R.string.reader_dictionary_recent_lookups))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            recentLookups.forEach { word ->
                SuggestionChip(onClick = { onLookupWord(word) }, label = { Text(word) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DictionaryEntryContent(
    state: DictionaryLookupState.Found,
    onLookupWord: (String) -> Unit,
    onSaveLookupAsNote: (DictionaryEntry) -> Unit,
    onSaveLookupAsVocabulary: (DictionaryEntry) -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val entry = state.entry
    Text(text = entry.headword, style = MaterialTheme.typography.headlineSmall)
    entry.senses.take(MaxDisplayedDictionarySenses).forEach { sense ->
        DictionarySenseContent(sense = sense, headword = entry.headword, onLookupWord = onLookupWord)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        entry.sourceUrl?.let { url ->
            EntryAction(Icons.AutoMirrored.Outlined.OpenInNew, stringResource(R.string.reader_dictionary_open_source)) {
                runCatching { uriHandler.openUri(url) }
            }
        }
        EntryAction(Icons.Outlined.ContentCopy, stringResource(R.string.reader_dictionary_copy)) {
            val definition = entry.senses.firstOrNull()?.definition.orEmpty()
            context.copyTextToClipboard("${entry.headword}: $definition")
        }
        EntryAction(Icons.Outlined.EditNote, stringResource(R.string.reader_dictionary_add_to_notes)) {
            onSaveLookupAsNote(entry)
        }
        when (state.savedStatus) {
            // The one filled action: saving the word is what a lookup most often leads to.
            SavedWordStatus.NOT_SAVED -> FilledTonalIconButton(
                onClick = { onSaveLookupAsVocabulary(entry) },
                shapes = morphingIconButtonShapes(),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Style,
                    contentDescription = stringResource(R.string.reader_dictionary_add_to_vocabulary),
                    modifier = Modifier.size(Sizes.iconSmall),
                )
            }
            SavedWordStatus.SAVED -> Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.padding(horizontal = Spacing.xs),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = stringResource(R.string.reader_dictionary_saved_to_vocabulary),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(Spacing.sm).size(Sizes.iconSmall),
                )
            }
            SavedWordStatus.KNOWN -> Pill(
                text = stringResource(R.string.reader_dictionary_known_word),
                containerColorIsPrimary = true,
            )
        }
    }
    // On its own line: the licence must stay whole, and beside the actions it wrapped into a cramped block.
    Text(
        text = entry.attribution,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DictionarySenseContent(sense: DictionarySense, headword: String, onLookupWord: (String) -> Unit) {
    Column(modifier = Modifier.padding(top = Spacing.md)) {
        val partOfSpeech = sense.partOfSpeech.shortLabel()
        if (partOfSpeech.isNotEmpty()) {
            Pill(text = partOfSpeech)
            Spacer(modifier = Modifier.height(Spacing.xs))
        }
        Text(text = sense.definition, style = MaterialTheme.typography.bodyLarge)
        sense.examples.firstOrNull()?.let { example ->
            val accent = MaterialTheme.colorScheme.primary
            Text(
                text = stringResource(R.string.quoted_text, example),
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = Spacing.xs)
                    // A quote bar down the example's left edge.
                    .drawBehind { drawRect(color = accent, size = Size(Strokes.emphasis.toPx(), size.height)) }
                    .padding(start = Spacing.sm),
            )
        }
        val otherSynonyms = sense.synonyms.filterNot { it.equals(headword, ignoreCase = true) }
        if (otherSynonyms.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                otherSynonyms.forEach { synonym ->
                    SuggestionChip(onClick = { onLookupWord(synonym) }, label = { Text(synonym) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EntryAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, shapes = morphingIconButtonShapes()) {
        Icon(imageVector = icon, contentDescription = description, modifier = Modifier.size(Sizes.iconSmall))
    }
}

/** A small fully round label: a part of speech, or "Known". */
@Composable
private fun Pill(text: String, containerColorIsPrimary: Boolean = false) {
    Surface(
        shape = CircleShape,
        color = if (containerColorIsPrimary) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.tertiaryContainer
        },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = Paddings.badgeHorizontal, vertical = Paddings.badgeVertical),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.sm),
    )
}

@Composable
private fun OnlineLookupContent(state: DictionaryLookupState.Online, onLookupOnline: (OnlineDictionarySource) -> Unit) {
    val sourceName = state.source.displayName()
    when (state.status) {
        OnlineLookupStatus.LOOKING_UP ->
            LookupProgress(stringResource(R.string.reader_dictionary_online_looking_up, sourceName))
        OnlineLookupStatus.NOT_FOUND -> {
            LookupMessage(stringResource(R.string.reader_dictionary_online_not_found, sourceName, state.word))
            OnlineLookupButtons(onLookupOnline = onLookupOnline)
        }
        OnlineLookupStatus.FAILED -> {
            LookupMessage(stringResource(R.string.reader_dictionary_online_failed, sourceName), isError = true)
            // Both sources again: after a failure, trying the same one is the retry.
            OnlineLookupButtons(onLookupOnline = onLookupOnline)
        }
    }
}

/** Looks the word up on the web: only on this tap, since it sends the word to Wikimedia. */
@Composable
private fun OnlineLookupButtons(onLookupOnline: (OnlineDictionarySource) -> Unit, labelled: Boolean = true) {
    if (labelled) SectionLabel(stringResource(R.string.reader_dictionary_look_up_online))
    val wiktionary = OnlineDictionarySource.WIKTIONARY.displayName()
    val wikipedia = OnlineDictionarySource.WIKIPEDIA.displayName()
    val buttons = remember(wiktionary, wikipedia, onLookupOnline) {
        listOf(
            ConnectedButton(wiktionary, OnlineDictionarySource.WIKTIONARY.icon) { onLookupOnline(OnlineDictionarySource.WIKTIONARY) },
            ConnectedButton(wikipedia, OnlineDictionarySource.WIKIPEDIA.icon) { onLookupOnline(OnlineDictionarySource.WIKIPEDIA) },
        )
    }
    // Unlabelled (a selected phrase) they're an aside to highlighting, so neutral rather than filled.
    VayanaConnectedButtonGroup(
        buttons = buttons,
        containerColor = if (labelled) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
    )
}

private val OnlineDictionarySource.icon: ImageVector
    get() = when (this) {
        OnlineDictionarySource.WIKTIONARY -> Icons.AutoMirrored.Outlined.MenuBook
        OnlineDictionarySource.WIKIPEDIA -> Icons.Outlined.Language
    }

@Composable
private fun OnlineDictionarySource.displayName(): String = stringResource(
    when (this) {
        OnlineDictionarySource.WIKTIONARY -> R.string.reader_dictionary_source_wiktionary
        OnlineDictionarySource.WIKIPEDIA -> R.string.reader_dictionary_source_wikipedia
    },
)

@Composable
private fun PartOfSpeech.shortLabel(): String = when (this) {
    PartOfSpeech.NOUN -> stringResource(R.string.dictionary_part_of_speech_noun)
    PartOfSpeech.VERB -> stringResource(R.string.dictionary_part_of_speech_verb)
    PartOfSpeech.ADJECTIVE -> stringResource(R.string.dictionary_part_of_speech_adjective)
    PartOfSpeech.ADVERB -> stringResource(R.string.dictionary_part_of_speech_adverb)
    PartOfSpeech.UNKNOWN -> ""
}

internal fun DictionaryLookupState.wordOrNull(): String? = when (this) {
    DictionaryLookupState.Hidden -> null
    is DictionaryLookupState.PackRequired -> word
    is DictionaryLookupState.LookingUp -> word
    is DictionaryLookupState.NotFound -> word
    is DictionaryLookupState.Installing -> word
    is DictionaryLookupState.Failed -> word
    is DictionaryLookupState.Online -> word
    is DictionaryLookupState.Phrase -> phrase
    is DictionaryLookupState.Found -> entry.headword
}

private const val MaxDisplayedDictionarySenses = 3
private const val DictionaryCardMaximumHeightFraction = 0.58f
