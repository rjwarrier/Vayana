package com.vayana.core.sync

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes operations that read and rewrite the shared cloud snapshot within this app process. */
@Singleton
class SyncOperationCoordinator @Inject constructor() {
    private val mutex = Mutex()

    suspend fun <T> run(operation: suspend () -> T): T = mutex.withLock { operation() }
}
