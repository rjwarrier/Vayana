package com.vayana.core.wear

import org.json.JSONObject

/** Thumbnails travel separately so reading-session payloads and fingerprints stay unchanged. */
data class WearCover(val bookId: String, val image: String?) {
    fun json() = JSONObject().put("bookId", bookId).put("image", image ?: JSONObject.NULL).toString()

    companion object {
        const val MAX_ENCODED_SIZE = 48_000
        fun key(bookId: String): String = java.security.MessageDigest.getInstance("SHA-256")
            .digest(bookId.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        fun path(bookId: String) = WearProtocol.COVER + key(bookId)
        fun parse(text: String): WearCover {
            val j = JSONObject(text)
            return WearCover(j.getString("bookId"), if (j.isNull("image")) null else j.getString("image")).also {
                require(it.bookId.isNotBlank() && it.bookId.length <= 200)
                require(it.image == null || it.image.length <= MAX_ENCODED_SIZE)
            }
        }
    }
}
