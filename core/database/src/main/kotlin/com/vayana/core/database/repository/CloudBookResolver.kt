package com.vayana.core.database.repository

import com.vayana.core.database.dao.BookAliasDao
import com.vayana.core.database.dao.BookDao
import com.vayana.core.database.entity.BookEntity

internal suspend fun BookDao.findActiveBySyncIdOrAlias(
    syncId: String,
    bookAliasDao: BookAliasDao,
): BookEntity? {
    findBySyncId(syncId)?.let { return it }
    val alias = bookAliasDao.findBySyncId(syncId) ?: return null
    return findByHash(alias.fileHash)
}

internal fun shelfMembershipTombstoneSyncId(bookSyncId: String, shelfSyncId: String): String =
    "shelf_membership:$bookSyncId:$shelfSyncId"
