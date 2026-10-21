package com.vayana.format.ocr

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** A Tesseract model the app can download, pinned to one commit of tessdata_fast and its SHA-256. */
enum class OcrModel(
    val id: String,
    /** Tesseract's language name: `<language>.traineddata` in `tessdata/`. */
    val language: String,
    private val path: String,
    val sizeBytes: Long,
    val sha256: String,
) {
    /** Malayalam; fastest, garbles Latin text. The default for Malayalam books. */
    MalayalamFast("mal-fast", "mal", "mal.traineddata", 5_275_996, "bd05cbf1b197e7810d2903419aedb06f9ef77bfedf50b358673c1d18d707cdb4"),

    /** Malayalam script, Latin letters too: for books that mix English in. */
    MalayalamScript("mal-script-fast", "Malayalam", "script/Malayalam.traineddata", 9_007_302, "822ad7ed9fb2eba0b0da5c788ed8710eba651d91777fc659ce0d700cfb99be06"),
    ;

    val url: String get() = "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/$TessdataCommit/$path"

    /** The language of text recognised with this model, as the text view's EPUB declares it. */
    val textLanguage: String get() = "ml"

    companion object {
        fun byId(id: String?): OcrModel? = entries.firstOrNull { it.id == id }

        // tessdata_fast at the time the hashes above were taken (2026-10-20).
        private const val TessdataCommit = "87416418657359cb625c412a48b6e1d6d41c29bd"
    }
}

/**
 * Downloaded models under `<dataPath>/tessdata/`, the layout Tesseract expects. A model is only ever installed whole
 * and with the expected SHA-256; anything else is deleted.
 */
class OcrModelFiles(val dataPath: File) {
    fun isInstalled(model: OcrModel): Boolean = file(model).length() == model.sizeBytes

    /** Downloads [model] unless it is already installed. Throws on a network error or a file that doesn't match. */
    fun download(model: OcrModel) {
        if (isInstalled(model)) return
        val connection = URL(model.url).openConnection() as HttpURLConnection
        connection.connectTimeout = TimeoutMillis
        connection.readTimeout = TimeoutMillis
        connection.instanceFollowRedirects = false
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${connection.responseCode} for ${model.id}")
            connection.inputStream.use { install(model, it) }
        } finally {
            connection.disconnect()
        }
    }

    /** Writes [source] as [model]'s file if its size and SHA-256 match; otherwise throws and leaves nothing behind. */
    fun install(model: OcrModel, source: InputStream) = installVerified(source, model.id, model.sizeBytes, model.sha256, file(model))

    private fun file(model: OcrModel) = File(File(dataPath, "tessdata"), "${model.language}.traineddata")

    private companion object {
        const val TimeoutMillis = 30_000
    }
}

/** Writes [source] to [target] only if it is exactly [sizeBytes] long with this [sha256]; nothing is left otherwise. */
internal fun installVerified(source: InputStream, name: String, sizeBytes: Long, sha256: String, target: File) {
    target.parentFile?.mkdirs()
    val partial = File(target.parentFile, "${target.name}.part")
    val digest = MessageDigest.getInstance("SHA-256")
    var length = 0L
    try {
        partial.outputStream().use { output ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = source.read(buffer)
                if (read < 0) break
                length += read
                if (length > sizeBytes) throw IOException("$name is larger than expected")
                digest.update(buffer, 0, read)
                output.write(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (length != sizeBytes || actual != sha256) throw IOException("$name does not match its pinned SHA-256")
        if (!partial.renameTo(target)) throw IOException("Could not install $name")
    } finally {
        partial.delete()
    }
}
