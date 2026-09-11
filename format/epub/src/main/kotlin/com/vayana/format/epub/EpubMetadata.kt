package com.vayana.format.epub

class EpubMetadata(
    val title: String,
    val author: String?,
    val series: String?,
    val seriesNumber: String?,
    val description: String?,
    val tags: List<String>,
    val coverBytes: ByteArray?,
)
