package com.vayana.core.common

data class ParsedQuote(
    val quoteText: String,
    val author: String? = null,
    val sourceTitle: String? = null,
    val tags: List<String> = emptyList(),
    val likesCount: Int = 0,
    val highlightsCount: Int = if (likesCount >= 0) likesCount + 1 else 1,
)

object QuoteParser {
    private val authorPrefixRegex = Regex("""^[―—–\-]{1,3}\s*(.*)$""")
    private val likesRegex = Regex("""(\d+)\s*likes?""", RegexOption.IGNORE_CASE)

    fun parse(rawText: String): List<ParsedQuote> {
        if (rawText.isBlank()) return emptyList()

        val normalized = rawText.replace("\r\n", "\n").replace("\r", "\n").trim()
        val lines = normalized.lines()
        val quotes = mutableListOf<ParsedQuote>()

        var currentQuoteLines = mutableListOf<String>()
        var currentAuthor: String? = null
        var currentSourceTitle: String? = null
        var currentTags = mutableListOf<String>()
        var currentLikes: Int? = null

        fun flushQuote() {
            val rawQuote = currentQuoteLines.joinToString("\n").trim()
            if (rawQuote.isNotBlank()) {
                val cleanedQuote = rawQuote
                    .removePrefix("“").removePrefix("\"").removePrefix("”")
                    .removeSuffix("”").removeSuffix("\"").removeSuffix("“")
                    .trim()

                if (cleanedQuote.isNotBlank()) {
                    val likes = currentLikes ?: 0
                    quotes.add(
                        ParsedQuote(
                            quoteText = cleanedQuote,
                            author = currentAuthor,
                            sourceTitle = currentSourceTitle,
                            tags = currentTags.toList(),
                            likesCount = likes,
                            highlightsCount = likes + 1,
                        )
                    )
                }
            }
            currentQuoteLines = mutableListOf()
            currentAuthor = null
            currentSourceTitle = null
            currentTags = mutableListOf()
            currentLikes = null
        }

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                if (currentAuthor != null || currentLikes != null) {
                    flushQuote()
                } else if (currentQuoteLines.isNotEmpty()) {
                    currentQuoteLines.add("")
                }
                continue
            }

            if (trimmed.equals("Like", ignoreCase = true) || trimmed.equals("Likes", ignoreCase = true)) {
                flushQuote()
                continue
            }

            val authorMatch = authorPrefixRegex.find(trimmed)
            if (authorMatch != null) {
                val authorAndTitle = authorMatch.groupValues[1].trim()
                if (authorAndTitle.contains(",")) {
                    currentAuthor = authorAndTitle.substringBefore(",").trim()
                    currentSourceTitle = authorAndTitle.substringAfter(",").trim()
                } else {
                    currentAuthor = authorAndTitle
                }
                continue
            }

            if (trimmed.startsWith("tags:", ignoreCase = true)) {
                val likeMatch = likesRegex.find(trimmed)
                if (likeMatch != null) {
                    currentLikes = likeMatch.groupValues[1].toIntOrNull()
                }
                val tagsOnly = trimmed
                    .removePrefix("tags:").removePrefix("Tags:")
                    .replace(likesRegex, "")
                    .trim()
                val tagList = tagsOnly.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                currentTags.addAll(tagList)
                continue
            }

            val standaloneLikeMatch = likesRegex.find(trimmed)
            if (standaloneLikeMatch != null && (trimmed.endsWith("likes", ignoreCase = true) || trimmed.endsWith("like", ignoreCase = true))) {
                currentLikes = standaloneLikeMatch.groupValues[1].toIntOrNull()
                continue
            }

            if ((trimmed.startsWith("“") || trimmed.startsWith("\"")) && (currentAuthor != null || currentLikes != null)) {
                flushQuote()
            }

            currentQuoteLines.add(line)
        }

        flushQuote()
        return quotes
    }
}
