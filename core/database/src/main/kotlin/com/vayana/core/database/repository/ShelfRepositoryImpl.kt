package com.vayana.core.database.repository

import androidx.room.withTransaction
import com.vayana.core.database.VayanaDatabase
import com.vayana.core.database.dao.BookAliasDao
import com.vayana.core.database.dao.BookDao
import com.vayana.core.database.dao.ShelfDao
import com.vayana.core.database.dao.TombstoneDao
import com.vayana.core.database.entity.BookShelfCrossRefEntity
import com.vayana.core.database.entity.ShelfEntity
import com.vayana.core.database.entity.TombstoneEntity
import com.vayana.core.database.model.Book
import com.vayana.core.database.model.Shelf
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ShelfRepositoryImpl @Inject constructor(
    private val database: VayanaDatabase,
    private val shelfDao: ShelfDao,
    private val bookDao: BookDao,
    private val bookAliasDao: BookAliasDao,
    private val tombstoneDao: TombstoneDao,
) : ShelfRepository {

    override fun observeAll(): Flow<List<Shelf>> =
        shelfDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun create(name: String): Shelf {
        val now = System.currentTimeMillis()
        val entity = ShelfEntity(name = name, createdAt = now, updatedAt = now)
        val id = shelfDao.insert(entity)
        return entity.copy(id = id).toDomain()
    }

    override suspend fun rename(id: Long, name: String) {
        shelfDao.rename(id, name, System.currentTimeMillis())
    }

    override suspend fun delete(id: Long) {
        database.withTransaction {
            shelfDao.getById(id)?.let { shelf ->
                tombstoneDao.upsert(TombstoneEntity(syncId = shelf.syncId, entityType = TombstoneEntityType.SHELF, deletedAt = System.currentTimeMillis()))
            }
            shelfDao.delete(id)
        }
    }

    override fun observeBooksForShelf(shelfId: Long): Flow<List<Book>> =
        shelfDao.observeBooksForShelf(shelfId).map { entities -> entities.map { it.toDomain() } }

    override fun observeShelvesForBook(bookId: Long): Flow<List<Shelf>> =
        shelfDao.observeShelvesForBook(bookId).map { entities -> entities.map { it.toDomain() } }

    override fun observeShelfBookCount(shelfId: Long): Flow<Int> = shelfDao.observeShelfBookCount(shelfId)

    override suspend fun addBookToShelf(bookId: Long, shelfId: Long) {
        database.withTransaction {
            val book = bookDao.getById(bookId)
            val shelf = shelfDao.getById(shelfId)
            if (book != null && shelf != null) {
                tombstoneDao.deleteBySyncId(shelfMembershipTombstoneSyncId(book.syncId, shelf.syncId))
            }
            shelfDao.addBookToShelf(BookShelfCrossRefEntity(bookId = bookId, shelfId = shelfId, createdAt = System.currentTimeMillis()))
        }
    }

    override suspend fun removeBookFromShelf(bookId: Long, shelfId: Long) {
        database.withTransaction {
            val book = bookDao.getById(bookId)
            val shelf = shelfDao.getById(shelfId)
            if (book != null && shelf != null) {
                tombstoneDao.upsert(
                    TombstoneEntity(
                        syncId = shelfMembershipTombstoneSyncId(book.syncId, shelf.syncId),
                        entityType = TombstoneEntityType.SHELF_MEMBERSHIP,
                        deletedAt = System.currentTimeMillis(),
                    ),
                )
            }
            shelfDao.removeBookFromShelf(bookId, shelfId)
        }
    }

    override suspend fun mergeCloudShelf(record: CloudShelfRecord): ShelfMergeResult = database.withTransaction {
        if (record.syncId.isBlank() || record.name.isBlank() || record.createdAt <= 0L || record.updatedAt <= 0L) {
            return@withTransaction ShelfMergeResult.SKIPPED
        }
        val tombstone = tombstoneDao.findBySyncId(record.syncId)
        val existing = shelfDao.findBySyncId(record.syncId)
        if (tombstone != null) {
            if (record.updatedAt <= tombstone.deletedAt && (existing == null || existing.updatedAt <= tombstone.deletedAt)) {
                return@withTransaction ShelfMergeResult.SKIPPED
            }
            tombstoneDao.deleteBySyncId(record.syncId)
        }
        if (existing == null) {
            shelfDao.insert(ShelfEntity(syncId = record.syncId, name = record.name, createdAt = record.createdAt, updatedAt = record.updatedAt))
            return@withTransaction ShelfMergeResult.CREATED
        }
        if (record.updatedAt <= existing.updatedAt) return@withTransaction ShelfMergeResult.SKIPPED
        shelfDao.update(existing.copy(name = record.name, updatedAt = record.updatedAt))
        ShelfMergeResult.UPDATED
    }

    override suspend fun mergeCloudMembership(record: CloudShelfMembershipRecord): ShelfMembershipMergeResult = database.withTransaction {
        val book = bookDao.findActiveBySyncIdOrAlias(record.bookSyncId, bookAliasDao) ?: return@withTransaction ShelfMembershipMergeResult.SKIPPED
        val shelf = shelfDao.findBySyncId(record.shelfSyncId) ?: return@withTransaction ShelfMembershipMergeResult.SKIPPED
        if (tombstoneDao.findBySyncId(record.bookSyncId) != null || tombstoneDao.findBySyncId(record.shelfSyncId) != null) {
            return@withTransaction ShelfMembershipMergeResult.SKIPPED
        }
        val membershipTombstoneId = shelfMembershipTombstoneSyncId(record.bookSyncId, record.shelfSyncId)
        val membershipTombstone = tombstoneDao.findBySyncId(membershipTombstoneId)
        if (membershipTombstone != null) {
            if (record.createdAt <= membershipTombstone.deletedAt) return@withTransaction ShelfMembershipMergeResult.SKIPPED
            tombstoneDao.deleteBySyncId(membershipTombstoneId)
        }
        val inserted = shelfDao.addBookToShelfIfAbsent(
            BookShelfCrossRefEntity(bookId = book.id, shelfId = shelf.id, createdAt = record.createdAt),
        )
        if (inserted >= 0L) ShelfMembershipMergeResult.CREATED else ShelfMembershipMergeResult.SKIPPED
    }
}

private fun ShelfEntity.toDomain(): Shelf = Shelf(id = id, name = name, createdAt = createdAt, updatedAt = updatedAt)
