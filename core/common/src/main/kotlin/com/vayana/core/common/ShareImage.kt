package com.vayana.core.common

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Encoding for [shareBitmap]: JPEG keeps photo-heavy cards small; PNG stays lossless for flat art. */
enum class ShareImageFormat(
    val compressFormat: Bitmap.CompressFormat,
    val quality: Int,
    val extension: String,
    val mimeType: String,
) {
    PNG(Bitmap.CompressFormat.PNG, 100, "png", "image/png"),
    JPEG(Bitmap.CompressFormat.JPEG, 95, "jpg", "image/jpeg"),
}

/**
 * Saves [bitmap] to the app's cache and launches a share sheet for it. Encoding and the file write run on
 * [Dispatchers.IO], so only the chooser launch touches the caller's thread. [fileName]'s extension is
 * replaced by [format]'s. Shared images older than an hour are pruned on the way, so the cache doesn't grow
 * with every share while a share target may still be reading a recent one.
 */
suspend fun Context.shareBitmap(
    bitmap: Bitmap,
    chooserTitle: String,
    fileName: String? = null,
    format: ShareImageFormat = ShareImageFormat.PNG,
) {
    val uri = withContext(Dispatchers.IO) {
        val dir = File(cacheDir, "shared_images").apply { mkdirs() }
        val baseName = fileName
            ?.let { File(it).nameWithoutExtension }
            ?.takeIf { it.isNotBlank() }
            ?: "share_${System.currentTimeMillis()}"
        val file = File(dir, "$baseName.${format.extension}")
        pruneSharedImages(dir, keep = file)
        FileOutputStream(file).use { out -> bitmap.compress(format.compressFormat, format.quality, out) }
        FileProvider.getUriForFile(this@shareBitmap, "$packageName.fileprovider", file)
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = format.mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(intent, chooserTitle))
}

private fun pruneSharedImages(dir: File, keep: File) {
    val cutoff = System.currentTimeMillis() - SharedImageMaxAgeMillis
    dir.listFiles()?.forEach { file ->
        if (file != keep && file.lastModified() < cutoff) file.delete()
    }
}

private const val SharedImageMaxAgeMillis = 60 * 60 * 1000L

/** Shares plain [text] via the system share sheet. */
fun Context.shareText(text: String, chooserTitle: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    startActivity(Intent.createChooser(intent, chooserTitle))
}

/** Writes [content] to a cache file named [fileName] and shares it as [mimeType] via the system share sheet. */
fun Context.shareFile(content: String, fileName: String, mimeType: String, chooserTitle: String) {
    val dir = File(cacheDir, "shared_files").apply { mkdirs() }
    val file = File(dir, fileName)
    file.writeText(content)
    val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(intent, chooserTitle))
}
