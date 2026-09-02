package com.vayana.core.designsystem

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withTextMatching
import com.lemonappdev.konsist.api.verify.assertEmpty
import org.junit.jupiter.api.Test

/**
 * Enforces PROMPT2appbuild.md §0.2 ("No magic numbers in UI code") as a build-time JVM test
 * rather than a custom Android Lint rule — see docs/DECISIONS.md for why.
 *
 * Only this module's own `tokens` package is allowed to spell out a literal `.dp`/`.sp` or
 * `Color(0x...)` — every other Kotlin file in the project must go through the named token
 * objects instead.
 */
class TokenUsageKonsistTest {

    private val bareDpOrSp = Regex("""(?<![\w.])\d+(\.\d+)?\.(dp|sp)\b""")
    private val bareHexColor = Regex("""Color\(\s*0x[0-9A-Fa-f]{6,8}\s*\)""")

    private fun nonTokenProductionFiles() =
        Konsist.scopeFromProduction().files
            .map { it to it.path.replace('\\', '/') }
            .filterNot { (_, normalizedPath) -> normalizedPath.contains("designsystem/tokens/") }
            .filterNot { (_, normalizedPath) -> normalizedPath.contains("/build/") }
            .map { (file, _) -> file }

    @Test
    fun `no bare dp or sp literals outside the tokens package`() {
        nonTokenProductionFiles()
            .withTextMatching(bareDpOrSp)
            .assertEmpty()
    }

    @Test
    fun `no bare hex Color literals outside the tokens package`() {
        nonTokenProductionFiles()
            .withTextMatching(bareHexColor)
            .assertEmpty()
    }
}
