package com.vayana.app.widget

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.vayana.core.filesystem.StorageRoots
import kotlin.math.roundToInt

/**
 * Decodes a book cover no taller than [maxHeightPx] - RemoteViews bitmaps travel through a size-limited parcel, so
 * callers pass the largest height they'll actually display it at. Decoded at the nearest power-of-two reduction, then
 * scaled down to exactly that height when still well above it ([oversizeFactor]).
 */
internal fun loadCoverBitmap(storageRoots: StorageRoots, relativePath: String, maxHeightPx: Int, oversizeFactor: Float = 1.25f): Bitmap? =
    runCatching {
        val file = storageRoots.resolve(relativePath).takeIf { it.isFile } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outHeight / (sample * 2) >= maxHeightPx) sample *= 2
        val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        if (decoded.height <= maxHeightPx * oversizeFactor) return decoded
        val width = (decoded.width * maxHeightPx.toFloat() / decoded.height).roundToInt().coerceAtLeast(1)
        Bitmap.createScaledBitmap(decoded, width, maxHeightPx, true).also { if (it !== decoded) decoded.recycle() }
    }.getOrNull()
