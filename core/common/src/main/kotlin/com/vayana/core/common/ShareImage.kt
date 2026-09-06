package com.vayana.core.common

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/** Saves [bitmap] to the app's cache and launches a share sheet for it as PNG. */
fun Context.shareBitmap(bitmap: Bitmap, chooserTitle: String, fileName: String? = null) {
    val dir = File(cacheDir, "shared_images").apply { mkdirs() }
    val safeFileName = fileName
        ?.let { File(it).name }
        ?.takeIf { it.isNotBlank() }
        ?: "share_${System.currentTimeMillis()}.png"
    val file = File(dir, safeFileName)
    FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
    val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(intent, chooserTitle))
}

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
