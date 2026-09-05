package com.vayana.core.sync.asset

interface CloudAssetStore {
    suspend fun put(path: String, bytes: ByteArray)

    suspend fun get(path: String): ByteArray
}
