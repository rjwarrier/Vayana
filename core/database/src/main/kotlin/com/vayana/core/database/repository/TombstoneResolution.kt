package com.vayana.core.database.repository

import com.vayana.core.database.dao.TombstoneDao
import com.vayana.core.database.entity.TombstoneEntity

/**
 * Call from a `mergeCloudX(record)` function, before applying [incomingVersion] over local data that
 * [tombstone] may already cover. Returns true (skip the merge, the deletion still wins) when neither the
 * incoming record nor the existing local row (if any) is newer than the tombstone; otherwise clears the
 * now-stale [tombstone] via [TombstoneDao.deleteBySyncId] and returns false so the merge proceeds.
 *
 * [existingLocalVersion] is null for join-row entities (e.g. shelf membership) that have no independent
 * "existing" version of their own beyond the incoming record's own timestamp.
 */
suspend fun TombstoneDao.supersedes(
    tombstone: TombstoneEntity,
    incomingVersion: Long,
    existingLocalVersion: Long? = null,
): Boolean {
    if (incomingVersion <= tombstone.deletedAt && (existingLocalVersion == null || existingLocalVersion <= tombstone.deletedAt)) {
        return true
    }
    deleteBySyncId(tombstone.syncId)
    return false
}

/**
 * Call when applying an incoming deletion [tombstone] over a local entity whose current version is
 * [localVersion]. Returns true (apply the deletion) when local hasn't changed since the tombstone;
 * otherwise clears the now-stale [tombstone] (local wins) and returns false.
 */
suspend fun TombstoneDao.appliesOver(tombstone: TombstoneEntity, localVersion: Long): Boolean {
    if (localVersion > tombstone.deletedAt) {
        deleteBySyncId(tombstone.syncId)
        return false
    }
    return true
}
