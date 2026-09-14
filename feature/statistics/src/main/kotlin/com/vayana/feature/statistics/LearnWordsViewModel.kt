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

data class LearnWordsSummary(
    /** The words to list: every looked-up word, or all but the known ones when [hideKnown]. */
    val words: List<WordLookupStat> = emptyList(),
    val uniqueWords: Int = 0,
    val totalLookups: Int = 0,
    val hideKnown: Boolean = true,
    /** Looked-up words that are marked as known, whether or not they're hidden. */
    val hiddenKnownCount: Int = 0,
)

@HiltViewModel
class LearnWordsViewModel @Inject constructor(
    wordLookupStatRepository: WordLookupStatRepository,
    vocabularyCardRepository: VocabularyCardRepository,
) : ViewModel() {
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
}

private const val LearnWordsLimit = 500
