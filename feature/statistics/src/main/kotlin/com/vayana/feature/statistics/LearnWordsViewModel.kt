package com.vayana.feature.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vayana.core.database.model.WordLookupStat
import com.vayana.core.database.repository.WordLookupStatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class LearnWordsSummary(
    val words: List<WordLookupStat> = emptyList(),
) {
    val uniqueWords: Int get() = words.size
    val totalLookups: Int get() = words.sumOf { it.count }
}

@HiltViewModel
class LearnWordsViewModel @Inject constructor(
    wordLookupStatRepository: WordLookupStatRepository,
) : ViewModel() {
    val summary: StateFlow<LearnWordsSummary> = wordLookupStatRepository
        .observeAllAggregated(LearnWordsLimit)
        .map { words -> LearnWordsSummary(words) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LearnWordsSummary())
}

private const val LearnWordsLimit = 500
