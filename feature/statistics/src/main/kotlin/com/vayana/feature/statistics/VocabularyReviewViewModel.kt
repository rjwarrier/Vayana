package com.vayana.feature.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vayana.core.database.model.VocabularyCard
import com.vayana.core.database.repository.ReviewGrade
import com.vayana.core.database.repository.VocabularyCardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class VocabularyReviewViewModel @Inject constructor(
    private val vocabularyCardRepository: VocabularyCardRepository,
) : ViewModel() {

    private val _cards = MutableStateFlow<List<VocabularyCard>>(emptyList())
    val cards: StateFlow<List<VocabularyCard>> = _cards

    private val _index = MutableStateFlow(0)
    val index: StateFlow<Int> = _index

    private val _flipped = MutableStateFlow(false)
    val flipped: StateFlow<Boolean> = _flipped

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded

    init {
        loadBatch()
    }

    /** Loads the next batch of due words after finishing one, instead of forcing the user to leave and re-enter. */
    fun reviewMore() {
        loadBatch()
    }

    private fun loadBatch() {
        viewModelScope.launch {
            _cards.value = vocabularyCardRepository.getForReview(ReviewBatchSize)
            _index.value = 0
            _flipped.value = false
            _loaded.value = true
        }
    }

    fun flip() {
        _flipped.value = true
    }

    /** Schedules the current word's next review from how this one went. */
    fun grade(grade: ReviewGrade) = advance { card -> vocabularyCardRepository.review(card.id, grade) }

    /** Takes the current word out of review for good. */
    fun markKnown() = advance { card -> vocabularyCardRepository.markKnown(card.id) }

    /** Every saved word in [format]; [onReady] gets the file content once it's built. */
    fun export(format: VocabularyExportFormat, markdownTitle: String, otherWordsHeading: String, onReady: (String) -> Unit) {
        viewModelScope.launch {
            val cards = vocabularyCardRepository.observeAll().first()
            onReady(
                when (format) {
                    VocabularyExportFormat.ANKI_CSV -> vocabularyAnkiCsv(cards)
                    VocabularyExportFormat.MARKDOWN -> vocabularyMarkdown(cards, markdownTitle, otherWordsHeading)
                },
            )
        }
    }

    private fun advance(record: suspend (VocabularyCard) -> Unit) {
        val card = _cards.value.getOrNull(_index.value) ?: return
        viewModelScope.launch { record(card) }
        _flipped.value = false
        _index.value += 1
    }
}

private const val ReviewBatchSize = 5
