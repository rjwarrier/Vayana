package com.vayana.core.sync.asset

import java.io.File

interface CloudAssetStore {
    suspend fun put(path: String, bytes: ByteArray)

    /** Production stores can stream files; the default keeps small in-memory stores compatible. */
    suspend fun putFile(path: String, file: File) = put(path, file.readBytes())

    suspend fun get(path: String): ByteArray

    /** Deletes the asset at [path]; one that is already gone counts as deleted. */
    suspend fun delete(path: String)
}

/** Stores with OCR descriptors can remove the associated encrypted archive on a permanent book deletion. */
interface RecognizedTextAssetDeletion {
    suspend fun deleteRecognizedText(bookHash: String)
}
