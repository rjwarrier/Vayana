package com.vayana.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vayana.core.common.DispatcherProvider
import com.vayana.core.diagnostics.DiagnosticEvent
import com.vayana.core.diagnostics.DiagnosticsEnvironment
import com.vayana.core.diagnostics.DiagnosticsLogStore
import com.vayana.core.diagnostics.buildDiagnosticsReport
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val diagnosticsLogStore: DiagnosticsLogStore,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val _events = MutableStateFlow<List<DiagnosticEvent>>(emptyList())
    val events: StateFlow<List<DiagnosticEvent>> = _events

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _events.value = withContext(dispatchers.io) { diagnosticsLogStore.readAll() }
        }
    }

    /** Built only when the user copies or shares: redacting every event is too heavy to redo per recomposition. */
    suspend fun buildReport(environment: DiagnosticsEnvironment): String =
        withContext(dispatchers.default) { buildDiagnosticsReport(environment, _events.value) }

    fun clear() {
        viewModelScope.launch {
            withContext(dispatchers.io) { diagnosticsLogStore.clear() }
            _events.value = emptyList()
        }
    }
}
