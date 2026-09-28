package com.vayana.feature.statistics

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceStorageTest {
    private fun File.file(path: String, bytes: Int) = File(this, path).apply { parentFile.mkdirs(); writeBytes(ByteArray(bytes)) }

    @Test
    fun filesEachFileUnderItsMostSpecificRuleAndTheRestUnderOther() {
        val data = createTempDirectory("storage-data").toFile()
        val external = createTempDirectory("storage-external").toFile()
        try {
            data.file("databases/vayana.db", 300)
            data.file("databases/vayana.db-wal", 20)
            data.file("files/dictionaries/oewn/index.noun", 500)
            data.file("files/diagnostics/events.ndjson", 7)
            data.file("cache/WebView/blob", 40)
            external.file("books/a.epub", 1_000)
            external.file("books/b.pdf", 2_000)
            external.file("covers/a.jpg", 90)
            external.file("stray.tmp", 3)

            val storage = measureDeviceStorage(
                roots = listOf(data, external),
                rules = listOf(
                    StorageRule(File(external, "books"), StorageCategory.BOOKS),
                    StorageRule(File(external, "covers"), StorageCategory.COVERS),
                    StorageRule(File(data, "files/dictionaries"), StorageCategory.DICTIONARY),
                    StorageRule(File(data, "databases"), StorageCategory.LIBRARY_DATA),
                    StorageRule(File(data, "cache"), StorageCategory.CACHE),
                ),
            )

            assertEquals(3_960L, storage.totalBytes)
            assertEquals(
                listOf(
                    CategorySize(StorageCategory.BOOKS, 3_000),
                    CategorySize(StorageCategory.DICTIONARY, 500),
                    CategorySize(StorageCategory.LIBRARY_DATA, 320),
                    CategorySize(StorageCategory.COVERS, 90),
                    CategorySize(StorageCategory.CACHE, 40),
                    CategorySize(StorageCategory.OTHER, 10),
                ),
                storage.categories,
            )
        } finally {
            data.deleteRecursively()
            external.deleteRecursively()
        }
    }

    @Test
    fun aRootInsideAnotherIsCountedOnce() {
        // Without external storage the library root is the app's own files directory, inside its data directory.
        val data = createTempDirectory("storage-data").toFile()
        try {
            data.file("files/books/a.epub", 100)
            val storage = measureDeviceStorage(
                roots = listOf(data, File(data, "files")),
                rules = listOf(StorageRule(File(data, "files/books"), StorageCategory.BOOKS)),
            )

            assertEquals(listOf(CategorySize(StorageCategory.BOOKS, 100)), storage.categories)
        } finally {
            data.deleteRecursively()
        }
    }

    @Test
    fun aRuleDirectoryDoesNotClaimASiblingWithTheSamePrefix() {
        val data = createTempDirectory("storage-data").toFile()
        try {
            data.file("books/a.epub", 10)
            data.file("bookshelf/x", 5)
            val storage = measureDeviceStorage(listOf(data), listOf(StorageRule(File(data, "books"), StorageCategory.BOOKS)))

            assertEquals(
                listOf(CategorySize(StorageCategory.BOOKS, 10), CategorySize(StorageCategory.OTHER, 5)),
                storage.categories,
            )
        } finally {
            data.deleteRecursively()
        }
    }
}
