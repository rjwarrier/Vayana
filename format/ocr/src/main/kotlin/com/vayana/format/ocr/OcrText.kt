package com.vayana.format.ocr

import java.text.Normalizer

/**
 * Recognised text in the form the reader's search and dictionaries use. Tesseract writes Malayalam chillus the old
 * way (consonant + virama + zero-width joiner, ന്‍) where printed books and typed text use the atomic letters (ൻ),
 * and leaves stray zero-width joiners and non-joiners; both would keep a recognised word from matching.
 */
fun normalizeRecognizedText(text: String): String {
    val composed = Normalizer.normalize(text, Normalizer.Form.NFC)
    val chillus = OldStyleChillu.replace(composed) { match -> AtomicChillus.getValue(match.groupValues[1]) }
    return chillus.replace(ZeroWidthJoiners, "")
}

private val AtomicChillus = mapOf(
    "ണ" to "ൺ",
    "ന" to "ൻ",
    "ര" to "ർ",
    "ല" to "ൽ",
    "ള" to "ൾ",
    "ക" to "ൿ",
)
private val OldStyleChillu = Regex("([ണനരലളക])്‍")
private val ZeroWidthJoiners = Regex("[‌‍]")
