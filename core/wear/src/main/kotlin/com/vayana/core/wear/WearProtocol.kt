package com.vayana.core.wear

import org.json.JSONArray
import org.json.JSONObject
import com.vayana.core.common.ReadingIntervals

/** Stable book identities, never phone-local database row IDs, cross the Data Layer. */
data class WearBook(val id: String, val title: String, val page: Int, val total: Int?, val version: Long) {
    fun json() = JSONObject().put("id", id).put("title", title).put("page", page)
        .put("total", total).put("version", version)

    companion object {
        fun parse(j: JSONObject) = WearBook(j.getString("id"), j.getString("title"), j.getInt("page"),
            j.optInt("total").takeIf { it > 0 }, j.getLong("version")).also {
            require(it.id.isNotBlank() && it.page >= 0 && (it.total == null || it.page <= it.total))
        }
    }
}

data class WearSession(
    val id: String, val book: WearBook, val startedAt: Long, val endedAt: Long,
    val seconds: Long, val startPage: Int, val endPage: Int,
    val activeIntervals: String? = null,
    val clockChanged: Boolean = false,
    val resolution: String = "auto",
) {
    fun json() = JSONObject().put("version", 1).put("id", id).put("book", book.json())
        .put("startedAt", startedAt).put("endedAt", endedAt).put("seconds", seconds)
        .put("startPage", startPage).put("endPage", endPage)
        .put("activeIntervals", activeIntervals).put("clockChanged", clockChanged).put("resolution", resolution)

    fun fingerprint(): String = java.security.MessageDigest.getInstance("SHA-256").digest(json().toString().toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun validate() {
        require(id.matches(Regex("wear-[0-9a-f-]{36}")))
        require(startedAt > 0 && endedAt >= startedAt && seconds > 0)
        require(seconds <= ((endedAt - startedAt) / 1000).coerceAtLeast(1))
        require(startPage >= 0 && endPage >= 0)
        require(book.total == null || (startPage <= book.total && endPage <= book.total))
        require(resolution in setOf("auto", "separate", "watch_page"))
        activeIntervals?.let { encoded ->
            val ranges = ReadingIntervals.decode(encoded)
            require(ranges.all { it.start >= startedAt && it.end <= endedAt })
            require(seconds == (ReadingIntervals.millis(ranges) / 1000).coerceAtLeast(1))
        }
    }

    companion object {
        fun parse(j: JSONObject): WearSession {
            require(j.getInt("version") == 1)
            return WearSession(j.getString("id"), WearBook.parse(j.getJSONObject("book")),
                j.getLong("startedAt"), j.getLong("endedAt"), j.getLong("seconds"),
                j.getInt("startPage"), j.getInt("endPage"),
                j.optString("activeIntervals").takeUnless { j.isNull("activeIntervals") || !j.has("activeIntervals") },
                j.optBoolean("clockChanged"), j.optString("resolution", "auto")).also { it.validate() }
        }
    }
}

object WearProtocol {
    const val ROOT = "/vayana/wear/v1/"
    const val CATALOG = ROOT + "catalog"
    const val COVER = ROOT + "cover/"
    const val DAILY = ROOT + "daily"
    const val SESSION = ROOT + "session/"
    const val ACK = ROOT + "ack/"
    const val REQUEST = ROOT + "request"
    fun catalog(books: List<WearBook>) = JSONObject().put("version", 1)
        .put("books", JSONArray().also { a -> books.forEach { a.put(it.json()) } }).toString()
    fun books(text: String): List<WearBook> {
        val j = JSONObject(text)
        require(j.getInt("version") == 1)
        val a = j.getJSONArray("books")
        return (0 until a.length()).map { WearBook.parse(a.getJSONObject(it)) }
    }
}
