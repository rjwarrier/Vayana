package com.vayana.format.ocr

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext

/** Workers hold their book through finalization; cleanup waits for a cancelled worker's last file write. */
internal object RecognitionAccess {
    private val books = ConcurrentHashMap<String, Mutex>()
    val models = Mutex()
    // ponytail: one book bounds bitmap/native memory; raise only after device heap measurements.
    val engines = Semaphore(1)
    private val activeModels = HashMap<OcrModel, Int>()

    suspend fun <T> book(hash: String, action: suspend () -> T): T = books.getOrPut(hash) { Mutex() }.withLock { action() }
    fun knownBooks(): Set<String> = books.keys.toSet()
    fun modelBusy(model: OcrModel): Boolean = activeModels.getOrDefault(model, 0) > 0 // Caller holds models.

    suspend fun <T> model(model: OcrModel, action: suspend () -> T): T {
        models.withLock { activeModels[model] = activeModels.getOrDefault(model, 0) + 1 }
        try { return action() } finally {
            withContext(NonCancellable) { models.withLock {
                val remaining = activeModels.getValue(model) - 1
                if (remaining == 0) activeModels.remove(model) else activeModels[model] = remaining
            } }
        }
    }
}
