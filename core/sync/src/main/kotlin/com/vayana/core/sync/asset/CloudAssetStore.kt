package com.vayana.core.sync.asset

interface CloudAssetStore {
    suspend fun put(path: String, bytes: ByteArray)

    suspend fun get(path: String): ByteArray

    /** Deletes the asset at [path]; one that is already gone counts as deleted. */
    suspend fun delete(path: String)
}
