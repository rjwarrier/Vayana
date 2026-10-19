package com.vayana.feature.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vayana.core.database.model.WordLookupStat
import com.vayana.core.database.repository.VocabularyCardRepository
import com.vayana.core.database.repository.WordLookupStatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import com.vayana.core.common.runCatchingCancellable
import com.vayana.dictionary.api.DictionaryEntry
import com.vayana.dictionary.api.DictionaryPackState
import com.vayana.dictionary.api.DictionaryRepository
import kotlinx.coroutines.launch

data class LearnWordsSummary(
    /** The words to list: every looked-up word, or all but the known ones when [hideKnown]. */
    val words: List<WordLookupStat> = emptyList(),
    val uniqueWords: Int = 0,
    val totalLookups: Int = 0,
    val hideKnown: Boolean = true,
    /** Looked-up words that are marked as known, whether or not they're hidden. */
    val hiddenKnownCount: Int = 0,
)

/** A word's meaning in the offline dictionary, fetched when its row is opened. */
sealed interface WordMeaning {
    data object Loading : WordMeaning
    data class Found(val entry: DictionaryEntry) : WordMeaning
    data object NotFound : WordMeaning
    data object NeedsDictionary : WordMeaning
}

@HiltViewModel
class LearnWordsViewModel @Inject constructor(
    private val wordLookupStatRepository: WordLookupStatRepository,
    vocabularyCardRepository: VocabularyCardRepository,
    private val dictionaryRepository: DictionaryRepository,
) : ViewModel() {
    private val _meanings = MutableStateFlow<Map<String, WordMeaning>>(emptyMap())
    val meanings: StateFlow<Map<String, WordMeaning>> = _meanings

    /**
     * Words saved before only dictionary words counted (names, typos, foreign words): checked once each against the
     * offline dictionary and removed, on every device, when it has no entry.
     */
    private val checkedWords = HashSet<String>()

    init {
        viewModelScope.launch {
            wordLookupStatRepository.observeAllAggregated(LearnWordsLimit).collect { words ->
                if (dictionaryRepository.englishPackState.value !is DictionaryPackState.Installed) return@collect
                words.map { it.word }.filter(checkedWords::add).forEach { word ->
                    val missing = runCatchingCancellable { dictionaryRepository.lookup(word) == null }.getOrDefault(false)
                    if (missing) wordLookupStatRepository.removeWord(word)
                }
            }
        }
    }

    /** Looks [word] up once; reviewing here doesn't count as another lookup. */
    fun loadMeaning(word: String) {
        if (_meanings.value[word].let { it != null && it != WordMeaning.NeedsDictionary }) return
        if (dictionaryRepository.englishPackState.value !is DictionaryPackState.Installed) {
            _meanings.update { it + (word to WordMeaning.NeedsDictionary) }
            return
        }
        _meanings.update { it + (word to WordMeaning.Loading) }
        viewModelScope.launch {
            val entry = runCatchingCancellable { dictionaryRepository.lookup(word) }.getOrNull()
            _meanings.update { it + (word to (entry?.let(WordMeaning::Found) ?: WordMeaning.NotFound)) }
        }
    }

    private val hideKnown = MutableStateFlow(true)

    val summary: StateFlow<LearnWordsSummary> = combine(
        wordLookupStatRepository.observeAllAggregated(LearnWordsLimit),
        vocabularyCardRepository.observeKnownWords(),
        hideKnown,
    ) { words, knownWords, hide ->
        val known = knownWords.mapTo(HashSet()) { it.lowercase() }
        LearnWordsSummary(
            words = if (hide) words.filterNot { it.word.lowercase() in known } else words,
            uniqueWords = words.size,
            totalLookups = words.sumOf { it.count },
            hideKnown = hide,
            hiddenKnownCount = words.count { it.word.lowercase() in known },
        )
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LearnWordsSummary())

    fun setHideKnown(hide: Boolean) {
        hideKnown.value = hide
    }

    /** A slip of the finger, not a word to learn: gone from this list on every synced device. */
    fun removeWord(word: String) {
        viewModelScope.launch { wordLookupStatRepository.removeWord(word) }
    }
}

private const val LearnWordsLimit = 500
