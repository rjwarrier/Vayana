package com.vayana.format.ocr

import java.nio.file.Files
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RecognitionAccessTest {
    @Test
    fun `cleanup waits for the last worker write before deleting a book`() = runBlocking {
        val hash = "d".repeat(64)
        val store = OcrStore(Files.createTempDirectory("ocr-ownership").toFile())
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val worker = launch { RecognitionAccess.book(hash) {
            entered.complete(Unit)
            finish.await()
            store.save(hash, 0, RecognizedPage(emptyList()), "eng-fast")
        } }
        entered.await()
        val removal = async { RecognitionAccess.book(hash) { store.delete(hash) } }
        yield()
        assertFalse(removal.isCompleted)
        finish.complete(Unit)
        withTimeout(3000) { worker.join(); removal.await() }
        assertTrue(store.savedPages(hash).isEmpty())
    }

    @Test
    fun `a cancelled worker releases its model after its cleanup finishes`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val model = OcrModel.EnglishFast
        val worker = launch { RecognitionAccess.model(model) { entered.complete(Unit); awaitCancellation() } }
        entered.await()
        assertTrue(RecognitionAccess.models.withLock { RecognitionAccess.modelBusy(model) })
        worker.cancelAndJoin()
        assertFalse(RecognitionAccess.models.withLock { RecognitionAccess.modelBusy(model) })
    }
}
