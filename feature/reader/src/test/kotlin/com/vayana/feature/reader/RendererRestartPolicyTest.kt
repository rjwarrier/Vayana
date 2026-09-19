package com.vayana.feature.reader

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RendererRestartPolicyTest {
    @Test
    fun allowsRestartsUpToTheBudgetThenRefuses() {
        val policy = RendererRestartPolicy(maxRestarts = 3, windowMillis = 60_000)

        assertTrue(policy.allowRestart(0))
        assertTrue(policy.allowRestart(1_000))
        assertTrue(policy.allowRestart(2_000))
        assertFalse(policy.allowRestart(3_000))
    }

    @Test
    fun crashesOutsideTheWindowDoNotCountAgainstTheBudget() {
        val policy = RendererRestartPolicy(maxRestarts = 2, windowMillis = 60_000)

        assertTrue(policy.allowRestart(0))
        assertTrue(policy.allowRestart(10_000))
        assertFalse(policy.allowRestart(20_000))
        assertTrue(policy.allowRestart(70_001))
    }
}
