package com.vayana.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vayana.core.common.runCatchingCancellable
import com.vayana.core.database.repository.VocabularyCardRepository
import com.vayana.core.database.repository.WordLookupStatRepository
import com.vayana.core.datastore.settings.SettingsRepository
import com.vayana.core.datastore.settings.SettingsSnapshot
import com.vayana.dictionary.api.DictionaryEntry
import com.vayana.dictionary.api.DictionaryPackState
import com.vayana.dictionary.api.DictionaryRepository
import com.vayana.dictionary.api.OnlineDictionary
import com.vayana.dictionary.api.OnlineDictionarySource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The "Define" popup offered in other apps' text selection: Vayana's offline dictionary for a word, the web (on tap)
 * for a phrase or a word it lacks, and the word saved to vocabulary like one looked up while reading.
 */
@HiltViewModel
class DefineViewModel @Inject constructor(
    private val dictionaryRepository: DictionaryRepository,
    private val onlineDictionary: OnlineDictionary,
    private val vocabularyCardRepository: VocabularyCardRepository,
    private val wordLookupStatRepository: WordLookupStatRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val settings: StateFlow<SettingsSnapshot?> = settingsRepository.snapshot
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _lookup = MutableStateFlow<DictionaryLookupState>(DictionaryLookupState.Hidden)
    val lookup: StateFlow<DictionaryLookupState> = _lookup

    /** The text as selected in the other app; null until [define]. */
    var selectedText: String? = null
        private set

    private var job: Job? = null

    /** Once per popup: a rotation keeps the lookup already made. */
    fun define(text: String) {
        if (selectedText != null) return
        selectedText = text
        val word = text.toDictionaryWord()
        if (word == null) {
            _lookup.value = text.toLookupPhrase()?.let(DictionaryLookupState::Phrase) ?: DictionaryLookupState.Hidden
            return
        }
        lookupWord(word)
    }

    fun lookupWord(word: String) {
        job?.cancel()
        if (dictionaryRepository.englishPackState.value !is DictionaryPackState.Installed) {
            _lookup.value = DictionaryLookupState.PackRequired(word)
            return
        }
        job = viewModelScope.launch {
            _lookup.value = DictionaryLookupState.LookingUp(word)
            wordLookupStatRepository.recordLookup(word, settingsRepository.snapshot.first().lookupWriterOrigin())
            val entry = runCatchingCancellable { dictionaryRepository.lookupEnglish(word) }.getOrNull()
            _lookup.value = if (entry == null) {
                DictionaryLookupState.NotFound(word)
            } else {
                DictionaryLookupState.Found(entry, vocabularyCardRepository.findByWord(entry.headword).toSavedWordStatus())
            }
        }
    }

    fun lookupOnline(source: OnlineDictionarySource) {
        val word = _lookup.value.wordOrNull() ?: selectedText?.toLookupPhrase() ?: return
        job?.cancel()
        job = viewModelScope.launch {
            _lookup.value = DictionaryLookupState.Online(word, source, OnlineLookupStatus.LOOKING_UP)
            _lookup.value = runCatchingCancellable { onlineDictionary.lookup(word, source) }.fold(
                onSuccess = { entry ->
                    if (entry == null) {
                        DictionaryLookupState.Online(word, source, OnlineLookupStatus.NOT_FOUND)
                    } else {
                        DictionaryLookupState.Found(entry, vocabularyCardRepository.findByWord(entry.headword).toSavedWordStatus())
                    }
                },
                onFailure = { DictionaryLookupState.Online(word, source, OnlineLookupStatus.FAILED) },
            )
        }
    }

    /** A vocabulary card, with the selected passage as its example when it's more than the word itself. */
    fun saveToVocabulary(entry: DictionaryEntry) {
        val definition = entry.senses.firstOrNull()?.definition ?: return
        val sentence = selectedText?.takeIf { it.isNotBlank() && !it.equals(entry.headword, ignoreCase = true) }
        viewModelScope.launch {
            vocabularyCardRepository.save(entry.headword, definition, sentence, bookId = null, bookTitle = null)
            _lookup.update { current ->
                if (current is DictionaryLookupState.Found && current.entry.headword == entry.headword) {
                    current.copy(savedStatus = SavedWordStatus.SAVED)
                } else {
                    current
                }
            }
        }
    }
}
