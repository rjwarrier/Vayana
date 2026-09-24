package com.vayana.core.sync

import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest

class SyncOperationCoordinatorTest {
    @Test
    fun `cloud snapshot operations do not overlap`() = runTest {
        val coordinator = SyncOperationCoordinator()
        val active = AtomicInteger()
        val peak = AtomicInteger()

        List(3) {
            async {
                coordinator.run {
                    val current = active.incrementAndGet()
                    peak.updateAndGet { previous -> maxOf(previous, current) }
                    delay(10)
                    active.decrementAndGet()
                }
            }
        }.awaitAll()

        assertEquals(1, peak.get())
    }
}
