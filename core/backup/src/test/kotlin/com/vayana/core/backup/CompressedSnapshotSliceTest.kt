package com.vayana.core.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompressedSnapshotSliceTest {

    @Test
    fun annotationSliceAcceptsZipPath() {
        val compressedPath = "vayana/snapshot-slices/1000/annotations.zip"
        val manifest = """{"slices":{"annotations":"$compressedPath"}}"""

        assertEquals(compressedPath, portableSnapshotSlicePaths(manifest)["annotations"])
    }

    @Test
    fun unsafeCompressedPathsAreIgnored() {
        val manifest = """{"slices":{"annotations":"../annotations.zip"}}"""

        assertTrue(portableSnapshotSlicePaths(manifest).isEmpty())
    }
}
