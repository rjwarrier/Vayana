package com.vayana.app.wear

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.vayana.core.wear.WearCover
import java.io.ByteArrayOutputStream
import java.io.File

/** Bound decoding memory and transfer size; cover failure must never delay reading-session sync. */
internal fun watchCover(bookId: String, file: File?): WearCover = WearCover(bookId, runCatching {
    if (file == null || !file.isFile) return@runCatching null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
    var sample = 1
    while (bounds.outWidth / sample > 256 || bounds.outHeight / sample > 384) sample *= 2
    val source = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: return@runCatching null
    try {
        val scale = minOf(128f / source.width, 192f / source.height, 1f)
        val thumbnail = Bitmap.createScaledBitmap(source, (source.width * scale).toInt().coerceAtLeast(1),
            (source.height * scale).toInt().coerceAtLeast(1), true)
        try {
            val output = ByteArrayOutputStream()
            thumbnail.compress(Bitmap.CompressFormat.JPEG, 80, output)
            Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP).takeIf { it.length <= WearCover.MAX_ENCODED_SIZE }
        } finally { if (thumbnail !== source) thumbnail.recycle() }
    } finally { source.recycle() }
}.getOrNull())
