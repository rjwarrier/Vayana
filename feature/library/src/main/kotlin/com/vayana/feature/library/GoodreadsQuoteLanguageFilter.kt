package com.vayana.feature.library

import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

private const val EnglishLanguageTag = "en"
private const val LanguageConfidenceThreshold = 0.5f

private val goodreadsLanguageIdentifier by lazy {
    LanguageIdentification.getClient(
        LanguageIdentificationOptions.Builder()
            .setConfidenceThreshold(LanguageConfidenceThreshold)
            .build(),
    )
}

/** Keeps only quotes confidently identified as English; undetermined text is intentionally skipped. */
internal suspend fun isEnglishGoodreadsQuote(
    text: String,
    identifyLanguage: suspend (String) -> String? = ::identifyGoodreadsQuoteLanguage,
): Boolean = identifyLanguage(text)?.substringBefore('-') == EnglishLanguageTag

/** The same eligibility rule used for newly scraped and previously imported Goodreads quotes. */
internal suspend fun isEligibleGoodreadsQuote(text: String): Boolean =
    hasGoodreadsQuoteMinimumLength(text) && isEnglishGoodreadsQuote(text)

private suspend fun identifyGoodreadsQuoteLanguage(text: String): String? = suspendCancellableCoroutine { continuation ->
    goodreadsLanguageIdentifier.identifyLanguage(text)
        .addOnSuccessListener { language ->
            if (continuation.isActive) continuation.resume(language)
        }
        .addOnFailureListener {
            if (continuation.isActive) continuation.resume(null)
        }
}
