package com.vayana.feature.library

import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.vayana.core.common.ParsedQuote
import com.vayana.core.common.quoteMatchKey
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

private const val LanguageConfidenceThreshold = 0.5f

private val goodreadsLanguageIdentifier by lazy {
    LanguageIdentification.getClient(
        LanguageIdentificationOptions.Builder()
            .setConfidenceThreshold(LanguageConfidenceThreshold)
            .build(),
    )
}

/** Languages detected in this exact batch; undetermined quotes are never eligible for import. */
data class GoodreadsQuoteLanguages(
    val quotesByLanguage: Map<String, List<ParsedQuote>>,
    val undeterminedCount: Int,
) {
    val defaultLanguageTag: String?
        get() = "en".takeIf { it in quotesByLanguage }
            ?: quotesByLanguage.maxByOrNull { it.value.size }?.key

    fun quotesFor(languageTag: String?): List<ParsedQuote> = quotesByLanguage[languageTag].orEmpty()
}

internal suspend fun detectGoodreadsQuoteLanguages(
    quotes: List<ParsedQuote>,
    onProgress: (GoodreadsQuoteProgress) -> Unit = {},
    identifyLanguage: suspend (String) -> String? = ::identifyGoodreadsQuoteLanguage,
): GoodreadsQuoteLanguages {
    val unique = quotes.filter { hasGoodreadsQuoteMinimumLength(it.quoteText) }
        .distinctBy { quoteMatchKey(it.quoteText) }
    val processed = AtomicInteger()
    val languages = classifyGoodreadsQuotes(unique.map { it.quoteText }) { text ->
        val tag = identifyLanguage(text)?.trim()?.lowercase(Locale.ROOT)?.substringBefore('-')
            ?.takeUnless { it.isBlank() || it == "und" }
        onProgress(GoodreadsQuoteProgress(processed.incrementAndGet(), unique.size))
        tag
    }
    val grouped = linkedMapOf<String, MutableList<ParsedQuote>>()
    unique.zip(languages).forEach { (quote, tag) ->
        if (tag != null) grouped.getOrPut(tag) { mutableListOf() }.add(quote)
    }
    return GoodreadsQuoteLanguages(grouped, languages.count { it == null })
}

private suspend fun identifyGoodreadsQuoteLanguage(text: String): String? = suspendCancellableCoroutine { continuation ->
    goodreadsLanguageIdentifier.identifyLanguage(text)
        .addOnSuccessListener { language ->
            if (continuation.isActive) continuation.resume(language)
        }
        .addOnFailureListener {
            if (continuation.isActive) continuation.resume(null)
        }
}
