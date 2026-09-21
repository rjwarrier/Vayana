package com.vayana.core.database.model

/**
 * One tag as stored: control characters and whitespace runs collapse to single spaces, capped in length. The literal
 * "null" is blank: earlier builds let a JSON null read back as that text and merged it in with real tags.
 */
fun String.normalizedBookTag(): String =
    buildString(length) { this@normalizedBookTag.forEach { append(if (Character.isISOControl(it)) ' ' else it) } }
        .trim()
        .replace(TagWhitespace, " ")
        .take(MaxBookTagChars)
        .trim()
        .takeUnless { it.equals(NullTagText, ignoreCase = true) }
        .orEmpty()

/** Whether [csv] carries the literal "null" tag [normalizedBookTag] now drops. */
fun hasNullBookTag(csv: String?): Boolean =
    csv?.split(",")?.any { it.trim().equals(NullTagText, ignoreCase = true) } == true

/** A comma-separated tag list as stored: tags normalised, blank and case-insensitively repeated tags dropped, capped. */
fun String?.normalizedBookTagsCsv(): String? =
    this?.split(",")
        ?.map { it.normalizedBookTag() }
        ?.filter { it.isNotEmpty() }
        ?.distinctBy { it.lowercase() }
        ?.take(MaxBookTags)
        ?.joinToString(", ")
        ?.take(MaxBookTagsCsvChars)
        ?.trimEnd(',', ' ')
        ?.ifBlank { null }

const val MaxBookTags = 32
const val MaxBookTagChars = 40
const val MaxBookTagsCsvChars = 1_024

private const val NullTagText = "null"

private val TagWhitespace = Regex("""\s+""")
