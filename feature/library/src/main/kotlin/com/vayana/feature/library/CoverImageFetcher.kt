package com.vayana.feature.library

import android.graphics.BitmapFactory
import com.vayana.core.common.DispatcherProvider
import com.vayana.core.common.runCatchingCancellable
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

internal data class CoverImageRequest(
    val url: String,
    val userAgent: String? = null,
    val cookie: String? = null,
    val referer: String? = null,
)

internal data class DownloadedCoverImage(val bytes: ByteArray, val extension: String)

@Singleton
class CoverImageFetcher @Inject constructor(
    private val dispatchers: DispatcherProvider,
) {
    internal suspend fun fetch(request: CoverImageRequest): DownloadedCoverImage? = withContext(dispatchers.io) {
        runCatchingCancellable {
            val image = if (request.url.startsWith("data:image/", ignoreCase = true)) {
                decodeDataImage(request.url)
            } else {
                downloadHttpsImage(request)
            } ?: return@runCatchingCancellable null
            image.takeIf { it.bytes.isDecodableCoverImage() }
        }.getOrNull()
    }

    private fun downloadHttpsImage(request: CoverImageRequest): DownloadedCoverImage? {
        var uri = request.url.toSafeImageUri() ?: return null
        repeat(MaxRedirects + 1) { redirectCount ->
            val connection = URL(uri.toASCIIString()).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = TimeoutMillis
                connection.readTimeout = TimeoutMillis
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("Accept", "image/*")
                request.userAgent?.takeIf { it.isNotBlank() }?.let { connection.setRequestProperty("User-Agent", it) }
                request.cookie?.takeIf { it.isNotBlank() }?.let { connection.setRequestProperty("Cookie", it) }
                request.referer?.takeIf { it.startsWith("https://") }?.let { connection.setRequestProperty("Referer", it) }
                when (val status = connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> {
                        val mime = connection.contentType?.substringBefore(';')?.trim()?.lowercase() ?: return null
                        val extension = SupportedImageMimeTypes[mime] ?: return null
                        if (connection.contentLengthLong > MaxCoverBytes) return null
                        val bytes = connection.inputStream.use { input ->
                            val output = ByteArrayOutputStream()
                            val buffer = ByteArray(BufferBytes)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                if (output.size() > MaxCoverBytes) return null
                            }
                            output.toByteArray()
                        }
                        return DownloadedCoverImage(bytes, extension)
                    }
                    in 300..399 -> {
                        if (redirectCount == MaxRedirects) return null
                        val location = connection.getHeaderField("Location") ?: return null
                        uri = uri.resolve(location).toString().toSafeImageUri() ?: return null
                    }
                    else -> return null
                }
            } finally {
                connection.disconnect()
            }
        }
        return null
    }
}

internal fun decodeDataImage(value: String): DownloadedCoverImage? {
    val comma = value.indexOf(',')
    if (comma <= 0) return null
    val metadata = value.substring(5, comma)
    val parts = metadata.split(';')
    val extension = SupportedImageMimeTypes[parts.first().lowercase()] ?: return null
    if (parts.none { it.equals("base64", ignoreCase = true) }) return null
    val encoded = value.substring(comma + 1)
    if (encoded.length > MaxEncodedCoverChars) return null
    val bytes = runCatching { Base64.getDecoder().decode(encoded) }.getOrNull() ?: return null
    return bytes.takeIf { it.size <= MaxCoverBytes }?.let { DownloadedCoverImage(it, extension) }
}

private fun String.toSafeImageUri(): URI? = runCatching { URI(this) }.getOrNull()?.takeIf { uri ->
    uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() && uri.userInfo == null
}

private fun ByteArray.isDecodableCoverImage(): Boolean {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(this, 0, size, options)
    return options.outWidth > 0 && options.outHeight > 0 &&
        options.outWidth.toLong() * options.outHeight.toLong() <= MaxCoverPixels
}

private val SupportedImageMimeTypes = mapOf(
    "image/jpeg" to "jpg",
    "image/png" to "png",
    "image/webp" to "webp",
)
private const val MaxCoverBytes = 10 * 1024 * 1024
private const val MaxEncodedCoverChars = 14 * 1024 * 1024
private const val MaxCoverPixels = 80_000_000L
private const val MaxRedirects = 3
private const val TimeoutMillis = 15_000
private const val BufferBytes = 16 * 1024
