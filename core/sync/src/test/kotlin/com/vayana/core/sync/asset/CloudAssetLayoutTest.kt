package com.vayana.core.sync.asset

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CloudAssetLayoutTest {
    @Test
    fun buildsStableShardedGithubPath() {
        assertEquals(
            "vayana/assets/ab/cd/abcdEFGH1234_wxyz.bin",
            CloudAssetLayout.pathFor("abcdEFGH1234_wxyz"),
        )
    }

    @Test
    fun validatesOpaqueAssetIds() {
        assertTrue(CloudAssetLayout.isValidAssetId("abcdEFGH1234_wxyz"))
        assertFalse(CloudAssetLayout.isValidAssetId("../escape"))
        assertFalse(CloudAssetLayout.isValidAssetId("short"))
        assertFailsWith<IllegalArgumentException> {
            CloudAssetLayout.pathFor("../escape")
        }
    }
}
