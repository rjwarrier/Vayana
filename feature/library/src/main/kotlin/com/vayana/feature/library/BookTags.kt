package com.vayana.feature.library

/** One tag as stored: control characters and whitespace runs collapse to single spaces, capped in length. */
internal fun String.normalizedBookTag(): String =
    map { if (Character.isISOControl(it)) ' ' else it }
        .joinToString("")
        .trim()
        .replace(Regex("\\s+"), " ")
        .take(MaxBookTagChars)
        .trim()

internal const val MaxBookTagChars = 40
