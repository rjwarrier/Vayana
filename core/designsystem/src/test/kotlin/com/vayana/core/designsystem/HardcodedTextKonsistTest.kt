package com.vayana.core.designsystem

import com.lemonappdev.konsist.api.Konsist
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Keeps the app translatable: text shown to the reader comes from string resources (`stringResource`, `UiText`),
 * never from a Kotlin literal. A literal made only of interpolated values and symbols (`"$percent%"` is not allowed
 * either - see `percent_value` - but `"$count"` is) passes; one with words in it does not.
 *
 * Diagnostics log messages, protocol strings and the like are not caught by these patterns and are allowed to stay
 * in English.
 */
class HardcodedTextKonsistTest {

    private val uiTextLiteral = Regex(
        """(?:\bText\(\s*(?:text\s*=\s*)?|\b(?:text|contentDescription|title|subtitle|description|supportingText|placeholder)\s*=\s*|""" +
            """\b(?:Failed|Incompatible|recordError)\(\s*)"((?:[^"\\]|\\.)*)"""",
    )
    private val template = Regex("""\$\{[^}]*\}|\$\w+""")
    private val word = Regex("""\p{L}{2,}|%""")

    @Test
    fun `user-facing text comes from string resources`() {
        val offenders = Konsist.scopeFromProduction().files
            .filterNot { file ->
                val path = file.path.replace('\\', '/')
                path.contains("/build/") || path.contains("/.claude/")
            }
            .flatMap { file ->
                uiTextLiteral.findAll(file.text)
                    .filter { match -> word.containsMatchIn(template.replace(match.groupValues[1], "")) }
                    .map { match -> "${file.path}: ${match.value}" }
                    .toList()
            }
        assertTrue(offenders.isEmpty()) { "Move these to core/resources strings.xml:\n" + offenders.joinToString("\n") }
    }
}
