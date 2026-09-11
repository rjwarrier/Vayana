package com.vayana.core.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.json.JSONObject

class SlicedPortableReadingProgressPatchTest {
    private val manifest = """
        {
          "exportedAt": 1000,
          "books": [
            {"syncId": "book-a", "fileHash": "hash-a", "lastLocator": "epubcfi(/6/2)", "readingPercent": 0.1, "lastReadAt": 900, "updatedAt": 900}
          ],
          "slices": {
            "annotations": "vayana/snapshot-slices/1000/annotations.json",
            "readingSessions": "vayana/snapshot-slices/1000/reading-sessions.json",
            "wordLookupCounters": "vayana/snapshot-slices/1000/word-lookup-counters.json",
            "tombstones": "vayana/snapshot-slices/1000/tombstones.json"
          }
        }
    """.trimIndent()

    private val sessionsSlice = """
        {"readingSessions": [{"syncId": "s-old", "bookSyncId": "book-a", "startedAt": 100, "endedAt": 200, "durationSeconds": 100}]}
    """.trimIndent()

    @Test
    fun patchesBookPositionInManifestAndSessionsInFreshSlice() {
        val result = patchSlicedPortableReadingProgress(
            manifestJson = manifest,
            sliceJsonByKey = mapOf("readingSessions" to sessionsSlice),
            patches = listOf(patch(lastReadAt = 2000)),
            exportedAt = 3000,
            readingSessions = listOf(PortableReadingSession("s-new", "book-a", 1500, 1600, 100)),
        )

        assertEquals(1, result.patched)
        assertEquals(1, result.sessionsAdded)
        val newSessionsPath = "vayana/snapshot-slices/3000/reading-sessions.json"
        assertEquals(setOf(newSessionsPath), result.sliceWrites.keys)
        val sessions = JSONObject(result.sliceWrites.getValue(newSessionsPath)).getJSONArray("readingSessions")
        assertEquals(listOf("s-old", "s-new"), (0 until sessions.length()).map { sessions.getJSONObject(it).getString("syncId") })

        val root = JSONObject(result.manifestJson)
        val book = root.getJSONArray("books").getJSONObject(0)
        assertEquals("epubcfi(/6/8)", book.getString("lastLocator"))
        assertEquals(3000L, root.getLong("exportedAt"))
        val slices = root.getJSONObject("slices")
        assertEquals(newSessionsPath, slices.getString("readingSessions"))
        // Untouched slices keep pointing at their existing files.
        assertEquals("vayana/snapshot-slices/1000/annotations.json", slices.getString("annotations"))
        assertEquals("vayana/snapshot-slices/1000/tombstones.json", slices.getString("tombstones"))
    }

    @Test
    fun nothingNewerLeavesManifestAndSlicesUntouched() {
        val result = patchSlicedPortableReadingProgress(
            manifestJson = manifest,
            sliceJsonByKey = mapOf("readingSessions" to sessionsSlice),
            patches = listOf(patch(lastReadAt = 500)),
            exportedAt = 3000,
            readingSessions = listOf(PortableReadingSession("s-old", "book-a", 100, 200, 100)),
        )

        assertEquals(0, result.changed)
        assertTrue(result.sliceWrites.isEmpty())
        assertEquals(manifest, result.manifestJson)
    }

    @Test
    fun missingSliceIsCreatedFromLocalData() {
        val result = patchSlicedPortableReadingProgress(
            manifestJson = manifest,
            sliceJsonByKey = emptyMap(),
            patches = emptyList(),
            exportedAt = 3000,
            wordLookupCounters = listOf(PortableWordLookupCounter("ephemeral", "en", 2, 1200)),
        )

        assertEquals(1, result.wordLookupCountersMerged)
        val path = "vayana/snapshot-slices/3000/word-lookup-counters.json"
        assertEquals(path, JSONObject(result.manifestJson).getJSONObject("slices").getString("wordLookupCounters"))
        assertEquals(1, JSONObject(result.sliceWrites.getValue(path)).getJSONArray("wordLookupCounters").length())
    }

    private fun patch(lastReadAt: Long) = PortableReadingProgressPatch(
        fileHash = "hash-a",
        lastLocator = "epubcfi(/6/8)",
        readingPercent = 0.5f,
        lastReadAt = lastReadAt,
        startedReadingAt = 100,
        finishedReadingAt = null,
        totalReadingSeconds = 60,
    )
}
