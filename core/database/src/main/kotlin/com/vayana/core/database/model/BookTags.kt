package com.vayana.core.database.model

/** One tag as stored: control characters and whitespace runs collapse to single spaces, capped in length. */
fun String.normalizedBookTag(): String =
    buildString(length) { this@normalizedBookTag.forEach { append(if (Character.isISOControl(it)) ' ' else it) } }
        .trim()
        .replace(TagWhitespace, " ")
        .take(MaxBookTagChars)
        .trim()

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

private val TagWhitespace = Regex("""\s+""")
