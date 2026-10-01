package com.vayana.feature.reader

import android.text.SpannableString
import android.text.Spanned
import android.text.style.TtsSpan
import java.time.LocalDate
import java.util.Locale

internal enum class SpeechNumberKind { MONEY, MEASURE, DATE, TIME }
internal data class SpeechNumberHint(
    val start: Int, val end: Int, val kind: SpeechNumberKind, val arguments: Map<String, String>,
)

// These formats and unit names use English conventions. Other languages keep their engine's own parsing.
internal fun speechNumberHints(text: String, locale: Locale): List<SpeechNumberHint> {
    if (locale.language != "en") return emptyList()
    val hints = mutableListOf<SpeechNumberHint>()
    fun add(match: MatchResult, kind: SpeechNumberKind, arguments: Map<String, String>) {
        val start = match.range.first
        val end = match.range.last + 1
        if (hints.none { start < it.end && end > it.start }) hints += SpeechNumberHint(start, end, kind, arguments)
    }
    MoneyPattern.findAll(text).forEach { match ->
        val currency = when (match.groupValues[1]) {
            "₹" -> "INR"; "£" -> "GBP"; "€" -> "EUR"
            else -> when (locale.country) { "AU" -> "AUD"; "CA" -> "CAD"; "NZ" -> "NZD"; else -> "USD" }
        }
        val parts = match.groupValues[2].replace(",", "").split('.')
        add(match, SpeechNumberKind.MONEY, mapOf("currency" to currency, "integer" to parts[0], "fraction" to parts.getOrElse(1) { "" }))
    }
    DatePattern.findAll(text).forEach { match ->
        val date = runCatching { LocalDate.parse(match.value) }.getOrNull() ?: return@forEach
        add(match, SpeechNumberKind.DATE, mapOf("year" to date.year.toString(), "month" to date.monthValue.toString(), "day" to date.dayOfMonth.toString()))
    }
    MeasurePattern.findAll(text).forEach { match ->
        val parts = match.groupValues[1].split('.')
        val unit = MeasureUnits.getValue(match.groupValues[2])
        add(match, SpeechNumberKind.MEASURE, mapOf("unit" to unit, "integer" to parts[0], "fraction" to parts.getOrElse(1) { "" }))
    }
    TimePattern.findAll(text).forEach { match ->
        val prefix = text.substring(0, match.range.first)
        val suffix = text.substring(match.range.last + 1)
        // Bare 1:20 can be a ratio or a reference. Only hint a time with clock context.
        if (!ClockPrefix.containsMatchIn(prefix) && !ClockSuffix.containsMatchIn(suffix)) return@forEach
        add(match, SpeechNumberKind.TIME, mapOf("hours" to match.groupValues[1], "minutes" to match.groupValues[2]))
    }
    return hints.sortedBy { it.start }
}

internal fun speechWithNumberHints(text: String, locale: Locale): CharSequence {
    val hints = speechNumberHints(text, locale)
    if (hints.isEmpty()) return text
    val spanned = SpannableString(text)
    hints.forEach { hint ->
        val args = hint.arguments
        val span = when (hint.kind) {
            SpeechNumberKind.MONEY -> TtsSpan.MoneyBuilder().setCurrency(args.getValue("currency"))
                .setIntegerPart(args.getValue("integer")).apply {
                    args["fraction"]?.takeIf { it.isNotEmpty() }?.let(::setFractionalPart)
                }.build()
            SpeechNumberKind.MEASURE -> TtsSpan.MeasureBuilder().setUnit(args.getValue("unit"))
                .setIntegerPart(args.getValue("integer")).apply {
                    args["fraction"]?.takeIf { it.isNotEmpty() }?.let(::setFractionalPart)
                }.build()
            SpeechNumberKind.DATE -> TtsSpan.DateBuilder().setYear(args.getValue("year").toInt())
                .setMonth(TtsSpan.MONTH_JANUARY + args.getValue("month").toInt() - 1)
                .setDay(args.getValue("day").toInt()).build()
            SpeechNumberKind.TIME -> TtsSpan.TimeBuilder(args.getValue("hours").toInt(), args.getValue("minutes").toInt()).build()
        }
        spanned.setSpan(span, hint.start, hint.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    return spanned
}

private val MoneyPattern = Regex("(?<![\\p{L}\\p{N}_])([$€£₹])\\s*([+-]?(?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d{1,2})?)(?![\\p{L}\\p{N}_,]|\\.\\d)")
private val DatePattern = Regex("(?<![\\p{L}\\p{N}_-])\\d{4}-\\d{2}-\\d{2}(?![\\p{L}\\p{N}_-])")
private val MeasureUnits = mapOf(
    "%" to "percent", "kg" to "kilogram", "g" to "gram", "mg" to "milligram",
    "km" to "kilometer", "m" to "meter", "cm" to "centimeter", "mm" to "millimeter",
    "L" to "liter", "ml" to "milliliter", "mL" to "milliliter", "mph" to "mile per hour",
    "km/h" to "kilometer per hour", "°C" to "degree Celsius", "°F" to "degree Fahrenheit",
)
private val MeasurePattern = Regex("(?<![\\p{L}\\p{N}_.,])([+-]?\\d+(?:\\.\\d+)?)\\s*(km/h|mph|kg|mg|km|cm|mm|ml|mL|°C|°F|g|m|L|%)(?![\\p{L}\\p{N}_])")
private val TimePattern = Regex("(?<![\\p{L}\\p{N}.:])([01]?\\d|2[0-3]):([0-5]\\d)(?![\\p{L}\\p{N}:])")
private val ClockPrefix = Regex("\\b(?:at|around|by|until|from|to)\\s*$", RegexOption.IGNORE_CASE)
private val ClockSuffix = Regex("^\\s*[ap]\\s*\\.?\\s*m\\b", RegexOption.IGNORE_CASE)
