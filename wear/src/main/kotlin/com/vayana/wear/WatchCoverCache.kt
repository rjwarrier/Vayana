package com.vayana.wear

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.vayana.core.wear.WearCover
import java.io.File

/** Private persistent files keep thumbnails available when the phone is offline. */
class WatchCoverCache(context: Context) {
    private val directory = File(context.filesDir, "book-covers").apply { mkdirs() }
    private fun file(id: String) = File(directory, WearCover.key(id) + ".jpg")
    fun bitmap(id: String): Bitmap? = runCatching { BitmapFactory.decodeFile(file(id).path) }.getOrNull()
    fun apply(cover: WearCover): Boolean {
        val target = file(cover.bookId)
        val image = cover.image ?: return target.exists() && target.delete()
        val bytes = Base64.decode(image, Base64.NO_WRAP)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth in 1..128 && bounds.outHeight in 1..192)
        if (target.isFile && target.readBytes().contentEquals(bytes)) return false
        val temporary = File(directory, target.name + ".tmp")
        temporary.writeBytes(bytes)
        check(temporary.renameTo(target)) { "Could not save book cover" }
        return true
    }
    fun retain(bookIds: Set<String>): Boolean {
        val names = bookIds.map { WearCover.key(it) + ".jpg" }.toSet()
        var changed = false
        directory.listFiles()?.filter { it.extension == "jpg" && it.name !in names }?.forEach {
            changed = it.delete() || changed
        }
        return changed
    }
}
