package com.vayana.core.database.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * The Home Library fields Vayana has no column for, stored as JSON in `books.sourceMetadata` and shown on Book
 * details. Every field is optional; Home Library owns them, so they are only ever replaced wholesale by its sync.
 */
data class HomeLibraryDetails(
    val subtitle: String? = null,
    val originalScriptTitle: String? = null,
    val languageCode: String? = null,
    val isbn13: String? = null,
    val isbn10: String? = null,
    val publisher: String? = null,
    val publishedYear: Int? = null,
    val formatCode: String? = null,
    val mainGenre: String? = null,
    val subGenres: List<String> = emptyList(),
    val readStatusCode: String? = null,
    val room: String? = null,
    val bookcase: String? = null,
    val shelf: String? = null,
    val positionNote: String? = null,
) {
    /** Where the book stands at home, e.g. "Study · Bookcase 2 · Shelf 3"; null when Home Library gave no location. */
    val location: List<String> get() = listOfNotNull(room, bookcase, shelf, positionNote)

    fun toJson(): String = JSONObject().apply {
        subtitle?.let { put("subtitle", it) }
        originalScriptTitle?.let { put("originalScriptTitle", it) }
        languageCode?.let { put("languageCode", it) }
        isbn13?.let { put("isbn13", it) }
        isbn10?.let { put("isbn10", it) }
        publisher?.let { put("publisher", it) }
        publishedYear?.let { put("publishedYear", it) }
        formatCode?.let { put("formatCode", it) }
        mainGenre?.let { put("mainGenre", it) }
        if (subGenres.isNotEmpty()) put("subGenres", JSONArray(subGenres))
        readStatusCode?.let { put("readStatusCode", it) }
        room?.let { put("room", it) }
        bookcase?.let { put("bookcase", it) }
        shelf?.let { put("shelf", it) }
        positionNote?.let { put("positionNote", it) }
    }.toString()

    companion object {
        /** Reads the stored JSON; anything unreadable gives empty details rather than an error. */
        fun fromJson(json: String?): HomeLibraryDetails {
            if (json.isNullOrBlank()) return HomeLibraryDetails()
            val obj = runCatching { JSONObject(json) }.getOrNull() ?: return HomeLibraryDetails()
            fun text(key: String): String? = obj.optString(key, "").takeIf { it.isNotBlank() }
            return HomeLibraryDetails(
                subtitle = text("subtitle"),
                originalScriptTitle = text("originalScriptTitle"),
                languageCode = text("languageCode"),
                isbn13 = text("isbn13"),
                isbn10 = text("isbn10"),
                publisher = text("publisher"),
                publishedYear = if (obj.has("publishedYear")) obj.optInt("publishedYear") else null,
                formatCode = text("formatCode"),
                mainGenre = text("mainGenre"),
                subGenres = obj.optJSONArray("subGenres")?.let { array ->
                    List(array.length()) { array.optString(it, "") }.filter { it.isNotBlank() }
                }.orEmpty(),
                readStatusCode = text("readStatusCode"),
                room = text("room"),
                bookcase = text("bookcase"),
                shelf = text("shelf"),
                positionNote = text("positionNote"),
            )
        }
    }
}
