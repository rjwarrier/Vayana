package com.vayana.core.diagnostics

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiagnosticsLogStoreTest {
    private lateinit var tempDir: File
    private lateinit var logFile: File
    private lateinit var store: DiagnosticsLogStore

    @BeforeTest
    fun setUp() {
        tempDir = createTempDirectory("diagnostics-test").toFile()
        logFile = File(tempDir, "events.ndjson")
        store = DiagnosticsLogStore(logFile)
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `record then readAll returns newest first`() {
        store.record(DiagnosticCategory.SYNC, "SourceA", "first")
        Thread.sleep(2)
        store.record(DiagnosticCategory.CRASH, "SourceB", "second", detail = "stack trace")

        val events = store.readAll()

        assertEquals(2, events.size)
        assertEquals("second", events.first().message)
        assertEquals(DiagnosticCategory.CRASH, events.first().category)
        assertEquals("stack trace", events.first().detail)
        assertEquals("first", events.last().message)
    }

    @Test
    fun `clear removes all events`() {
        store.record(DiagnosticCategory.SYNC, "SourceA", "first")

        store.clear()

        assertTrue(store.readAll().isEmpty())
    }

    @Test
    fun `readAll on missing file returns empty list`() {
        assertTrue(store.readAll().isEmpty())
    }

    @Test
    fun `malformed line is skipped rather than failing the whole read`() {
        store.record(DiagnosticCategory.SYNC, "SourceA", "valid")
        logFile.appendText("not json\n")

        val events = store.readAll()

        assertEquals(1, events.size)
        assertEquals("valid", events.first().message)
    }

    @Test
    fun `parent directory is created lazily on first write`() {
        val nestedFile = File(tempDir, "nested/events.ndjson")
        val nestedStore = DiagnosticsLogStore(nestedFile)

        nestedStore.record(DiagnosticCategory.CRASH, "Source", "message")

        assertTrue(nestedFile.isFile)
        assertEquals(1, nestedStore.readAll().size)
    }
}
