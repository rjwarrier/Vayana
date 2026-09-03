package com.vayana.feature.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vayana.core.database.model.Annotation
import com.vayana.core.database.repository.AnnotationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val annotationRepository: AnnotationRepository,
) : ViewModel() {
    val annotations: StateFlow<List<Annotation>> = annotationRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun updateNote(annotation: Annotation, readerNote: String) {
        viewModelScope.launch {
            annotationRepository.update(annotation.copy(readerNote = readerNote.takeIf { it.isNotBlank() }))
        }
    }

    fun deleteAnnotation(annotationId: Long) {
        viewModelScope.launch {
            annotationRepository.delete(annotationId)
        }
    }
}
