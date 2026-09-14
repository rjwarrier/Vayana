package com.vayana.core.sync.asset

import com.vayana.core.database.dao.PendingCloudDeletionDao
import com.vayana.core.database.entity.PendingCloudDeletionEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking

class CloudAssetDeletionProcessorTest {
    @Test
    fun deletesQueuedAssetsAndClearsThem() = runBlocking {
        val queue = FakePendingDeletions("fileasset00000001", "coverasset0000001")
        val store = FakeStore()

        val summary = CloudAssetDeletionProcessor(queue).deletePending(store)

        assertEquals(
            listOf(CloudAssetLayout.pathFor("fileasset00000001"), CloudAssetLayout.pathFor("coverasset0000001")),
            store.deletedPaths,
        )
        assertEquals(CloudAssetDeletionSummary(deleted = 2, failed = 0, remaining = 0), summary)
    }

    @Test
    fun failedDeletesStayQueuedAndAuthFailuresStopTheBatch() = runBlocking {
        val queue = FakePendingDeletions("asset500000000001", "asset403000000002", "assetafter0000003")
        val store = FakeStore(
            failures = mapOf(
                "asset500000000001" to GitHubAssetStoreException("server error", 500, ""),
                "asset403000000002" to GitHubAssetStoreException("forbidden", 403, ""),
            ),
        )

        val summary = CloudAssetDeletionProcessor(queue).deletePending(store)

        assertEquals(listOf("asset500000000001", "asset403000000002"), store.attemptedAssetIds)
        assertEquals(0, summary.deleted)
        assertEquals(2, summary.failed)
        assertEquals(3, summary.remaining)
        assertEquals("forbidden: HTTP 403", summary.failureMessage)
        assertEquals(1, queue.rows.getValue("asset500000000001").attempts)
        assertEquals("server error: HTTP 500", queue.rows.getValue("asset500000000001").lastError)
    }

    @Test
    fun invalidIdsAreDroppedWithoutCallingTheStore() = runBlocking {
        val queue = FakePendingDeletions("not a valid id")
        val store = FakeStore()

        val summary = CloudAssetDeletionProcessor(queue).deletePending(store)

        assertEquals(emptyList(), store.attemptedAssetIds)
        assertEquals(CloudAssetDeletionSummary(), summary)
    }

    @Test
    fun onlyTheBatchIsDeletedInOneRun() = runBlocking {
        val queue = FakePendingDeletions("asset000000000001", "asset000000000002", "asset000000000003")

        val summary = CloudAssetDeletionProcessor(queue).deletePending(FakeStore(), batchSize = 2)

        assertEquals(2, summary.deleted)
        assertEquals(1, summary.remaining)
        assertEquals(listOf("asset000000000003"), queue.rows.keys.toList())
    }
}

private class FakeStore(private val failures: Map<String, Throwable> = emptyMap()) : CloudAssetStore {
    val deletedPaths = mutableListOf<String>()
    val attemptedAssetIds = mutableListOf<String>()

    override suspend fun put(path: String, bytes: ByteArray) = error("not used")

    override suspend fun get(path: String): ByteArray = error("not used")

    override suspend fun delete(path: String) {
        val assetId = path.substringAfterLast('/').removeSuffix(".bin")
        attemptedAssetIds += assetId
        failures[assetId]?.let { throw it }
        deletedPaths += path
    }
}

private class FakePendingDeletions(vararg assetIds: String) : PendingCloudDeletionDao {
    val rows = linkedMapOf<String, PendingCloudDeletionEntity>().apply {
        assetIds.forEachIndexed { index, id -> put(id, PendingCloudDeletionEntity(assetId = id, kind = "book_file", queuedAt = index.toLong())) }
    }

    override suspend fun insertAll(deletions: List<PendingCloudDeletionEntity>) {
        deletions.forEach { rows.putIfAbsent(it.assetId, it) }
    }

    override suspend fun getBatch(limit: Int): List<PendingCloudDeletionEntity> =
        rows.values.sortedWith(compareBy({ it.queuedAt }, { it.assetId })).take(limit)

    override fun observeCount(): Flow<Int> = flowOf(rows.size)

    override suspend fun count(): Int = rows.size

    override suspend fun delete(assetId: String) {
        rows.remove(assetId)
    }

    override suspend fun recordFailure(assetId: String, error: String) {
        rows[assetId]?.let { rows[assetId] = it.copy(attempts = it.attempts + 1, lastError = error) }
    }
}
