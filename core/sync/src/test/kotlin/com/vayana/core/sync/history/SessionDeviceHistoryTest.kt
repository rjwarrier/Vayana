package com.vayana.core.sync.history

import com.vayana.core.sync.asset.GitHubSnapshotCommit
import com.vayana.core.sync.asset.parseSnapshotHistoryPage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class SessionDeviceHistoryTest {
    private fun commit(author: String, manifest: String, folders: Map<String, Map<String, String>> = emptyMap()) =
        GitHubSnapshotCommit("c-$manifest", "", author, manifest, folders)

    @Test
    fun newestSliceFolderWinsAndRepeatsCollapse() {
        val versions = sessionsVersions(listOf(
            commit("Pixel", "m1"),
            commit("Pixel", "m1"),
            commit("Reader", "m2", mapOf("100" to mapOf("reading-sessions.json" to "s1"), "99" to mapOf("reading-sessions.json" to "s0"))),
            commit("Pixel", "m3", mapOf("100" to mapOf("reading-sessions.json" to "s1"), "200" to mapOf("books.zip" to "b"))),
            commit("Pixel", "m4", mapOf("300" to mapOf("reading-sessions.zip" to "s2"))),
        ))
        assertEquals(listOf(
            SessionsVersion("m1", false, "Pixel"),
            SessionsVersion("s1", false, "Reader"),
            SessionsVersion("s2", true, "Pixel"),
        ), versions)
    }

    @Test
    fun firstPublisherIsCreditedAndOneDeviceStretchesAreNotRead() = runTest {
        // Pixel publishes a, b; Reader adds c; Pixel publishes four more times, adding d and then e.
        val contents = listOf(
            "v0" to setOf("a"), "v1" to setOf("a", "b"), "v2" to setOf("a", "b", "c"),
            "v3" to setOf("a", "b", "c", "d"), "v4" to setOf("a", "b", "c", "d"), "v5" to setOf("a", "b", "c", "d", "e"),
            "v6" to setOf("a", "b", "c", "d", "e"),
        ).toMap()
        val devices = listOf("Pixel", "Pixel", "Reader", "Pixel", "Pixel", "Pixel", "Pixel")
        val versions = contents.keys.mapIndexed { index, sha -> SessionsVersion(sha, false, devices[index]) }
        val read = mutableListOf<String>()
        val credited = creditSessionsToDevices(versions) { read += it.blobSha; contents.getValue(it.blobSha) }
        assertEquals(mapOf("a" to "Pixel", "b" to "Pixel", "c" to "Reader", "d" to "Pixel", "e" to "Pixel"), credited)
        // v4 and v5 sit inside the Pixel-only stretch after v3 and are never downloaded.
        assertEquals(listOf("v0", "v6", "v3", "v1", "v2"), read)
    }

    @Test
    fun historyPageKeepsDataBesideMissingSliceFolderErrors() {
        val page = parseSnapshotHistoryPage("""
            {"data":{"repository":{"ref":{"target":{"history":{
              "pageInfo":{"hasNextPage":true,"endCursor":"abc 39"},
              "nodes":[
                {"oid":"c2","committedDate":"2026-10-19T03:26:42Z","author":{"name":"Reader"},"committer":{"name":"Reader"},
                 "manifest":{"oid":"m2"},"slices":{"object":{"entries":[
                   {"name":"1792378528685","object":{"entries":[{"name":"books.zip","oid":"b"},{"name":"reading-sessions.zip","oid":"s"}]}}]}}},
                {"oid":"c1","committedDate":"2026-09-05T16:21:18Z","author":{"name":null},"committer":{"name":"Pixel"},
                 "manifest":{"oid":"m1"},"slices":null}
              ]}}}}},
             "errors":[{"message":"Could not resolve file for path 'vayana/snapshot-slices'."}]}
        """.trimIndent())
        assertEquals("abc 39", page.nextCursor)
        assertEquals(listOf("Reader", "Pixel"), page.commits.map { it.author })
        assertEquals(mapOf("1792378528685" to mapOf("books.zip" to "b", "reading-sessions.zip" to "s")), page.commits[0].sliceFolders)
        assertEquals(emptyMap(), page.commits[1].sliceFolders)
    }
}
