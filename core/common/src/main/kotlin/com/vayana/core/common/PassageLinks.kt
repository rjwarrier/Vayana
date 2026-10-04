package com.vayana.core.common

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

data class PassageLink(val bookSyncId: String, val annotationSyncId: String)

fun passageLink(bookSyncId: String, annotationSyncId: String): String =
    "vayana://passage?book=${URLEncoder.encode(bookSyncId, "UTF-8")}&annotation=${URLEncoder.encode(annotationSyncId, "UTF-8")}"

fun parsePassageLink(value: String): PassageLink? = runCatching {
    if (value.length > 4096) return null
    val uri = URI(value)
    if (uri.scheme != "vayana" || uri.host != "passage" || !uri.path.isNullOrEmpty()) return null
    val parameters = uri.rawQuery.orEmpty().split('&').associate { field ->
        val parts = field.split('=', limit = 2)
        parts.first() to URLDecoder.decode(parts.getOrElse(1) { "" }, "UTF-8")
    }
    val book = parameters["book"]?.takeIf { it.isNotBlank() && it.length <= 160 } ?: return null
    val annotation = parameters["annotation"]?.takeIf { it.isNotBlank() && it.length <= 160 } ?: return null
    PassageLink(book, annotation)
}.getOrNull()
