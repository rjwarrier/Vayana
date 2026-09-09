package com.vayana.core.common

import javax.inject.Qualifier

/**
 * A [kotlinx.coroutines.CoroutineScope] bound to the process, not to any particular
 * ViewModel/Activity. Use for work that must complete even after the caller that
 * started it has been torn down (e.g. a final flush from `ViewModel.onCleared()`,
 * where `viewModelScope` is already cancelled by the time `onCleared()` runs).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
