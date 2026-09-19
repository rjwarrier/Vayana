package com.vayana.feature.library

import android.graphics.BitmapFactory
import com.vayana.core.common.DispatcherProvider
import com.vayana.core.common.runCatchingCancellable
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
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
            image.bytes.coverImageExtensionOrNull()?.let { extension -> image.copy(extension = extension) }
        }.getOrNull()
    }

    private fun downloadHttpsImage(request: CoverImageRequest): DownloadedCoverImage? {
        var uri = request.url.toSafeHttpsUri() ?: return null
        val credentialHost = uri.host.lowercase()
        repeat(MaxRedirects + 1) { redirectCount ->
            val connection = URL(uri.toASCIIString()).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = TimeoutMillis
                connection.readTimeout = TimeoutMillis
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("Accept", "image/*")
                request.userAgent.safeHeaderValue(MaxUserAgentChars)?.let { connection.setRequestProperty("User-Agent", it) }
                if (uri.host.equals(credentialHost, ignoreCase = true)) {
                    request.cookie.safeHeaderValue(MaxCookieChars)?.let { connection.setRequestProperty("Cookie", it) }
                }
                request.referer.safeHttpsReferer()?.let { connection.setRequestProperty("Referer", it) }
                when (val status = connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> {
                        val mime = connection.contentType?.substringBefore(';')?.trim()?.lowercase() ?: return null
                        val declaredExtension = SupportedImageMimeTypes[mime] ?: return null
                        val declaredLength = connection.contentLengthLong
                        if (declaredLength > MaxCoverBytes) return null
                        val bytes = connection.inputStream.use { input ->
                            val initialCapacity = declaredLength
                                .takeIf { it > 0L }
                                ?.coerceAtMost(MaxInitialBufferBytes.toLong())
                                ?.toInt()
                                ?: BufferBytes
                            val output = ByteArrayOutputStream(initialCapacity)
                            val buffer = ByteArray(BufferBytes)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                if (output.size() > MaxCoverBytes) return null
                            }
                            output.toByteArray()
                        }
                        return DownloadedCoverImage(bytes, declaredExtension)
                    }
                    in 300..399 -> {
                        if (redirectCount == MaxRedirects) return null
                        val location = connection.getHeaderField("Location") ?: return null
                        uri = uri.resolve(location).toString().toSafeHttpsUri() ?: return null
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
    if (value.length > MaxDataImageChars) return null
    val comma = value.indexOf(',')
    if (comma <= 0 || comma > MaxDataImageMetadataChars) return null
    val metadata = value.substring(5, comma)
    val parts = metadata.split(';')
    val extension = SupportedImageMimeTypes[parts.first().lowercase()] ?: return null
    if (parts.none { it.equals("base64", ignoreCase = true) }) return null
    val encoded = value.substring(comma + 1)
    if (encoded.length > MaxEncodedCoverChars) return null
    val bytes = runCatching { Base64.getDecoder().decode(encoded) }.getOrNull() ?: return null
    return bytes.takeIf { it.size <= MaxCoverBytes }?.let { DownloadedCoverImage(it, extension) }
}

private fun ByteArray.coverImageExtensionOrNull(): String? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(this, 0, size, options)
    val validDimensions = options.outWidth > 0 && options.outHeight > 0 &&
        options.outWidth.toLong() * options.outHeight.toLong() <= MaxCoverPixels
    return if (validDimensions) SupportedImageMimeTypes[options.outMimeType?.lowercase()] else null
}

internal fun String.toSafeHttpsUri(
    resolveHost: (String) -> Array<InetAddress> = InetAddress::getAllByName,
): URI? = runCatching { URI(this) }.getOrNull()?.takeIf { uri ->
    val host = uri.host?.lowercase()?.takeIf { it.length <= MaxHostChars } ?: return@takeIf false
    uri.scheme.equals("https", ignoreCase = true) &&
        uri.userInfo == null &&
        (uri.port == -1 || uri.port == 443) &&
        runCatching { resolveHost(host).takeIf(Array<InetAddress>::isNotEmpty)?.all(InetAddress::isPublicAddress) }
            .getOrNull() == true
}

private fun InetAddress.isPublicAddress(): Boolean {
    if (isAnyLocalAddress || isLoopbackAddress || isLinkLocalAddress || isSiteLocalAddress || isMulticastAddress) return false
    val octets = address.map(Byte::toInt).map { it and 0xff }
    return when (this) {
        is Inet4Address -> when {
            octets[0] == 0 || octets[0] >= 224 -> false
            octets[0] == 100 && octets[1] in 64..127 -> false
            octets[0] == 192 && octets[1] == 0 -> false
            octets[0] == 192 && octets[1] == 2 -> false
            octets[0] == 198 && octets[1] in 18..19 -> false
            octets[0] == 198 && octets[1] == 51 && octets[2] == 100 -> false
            octets[0] == 203 && octets[1] == 0 && octets[2] == 113 -> false
            else -> true
        }
        is Inet6Address -> {
            val uniqueLocal = octets[0] and 0xfe == 0xfc
            val documentation = octets.take(4) == listOf(0x20, 0x01, 0x0d, 0xb8)
            !uniqueLocal && !documentation
        }
        else -> false
    }
}

private fun String?.safeHeaderValue(maxChars: Int): String? =
    this?.takeIf { it.isNotBlank() && it.length <= maxChars && '\r' !in it && '\n' !in it }

private fun String?.safeHttpsReferer(): String? = safeHeaderValue(MaxRefererChars)
    ?.takeIf { runCatching { URI(it) }.getOrNull()?.scheme.equals("https", ignoreCase = true) }

private val SupportedImageMimeTypes = mapOf(
    "image/jpeg" to "jpg",
    "image/png" to "png",
    "image/webp" to "webp",
)
private const val MaxCoverBytes = 10 * 1024 * 1024
private const val MaxEncodedCoverChars = 14 * 1024 * 1024
private const val MaxDataImageChars = MaxEncodedCoverChars + 128
private const val MaxDataImageMetadataChars = 128
private const val MaxCoverPixels = 80_000_000L
private const val MaxRedirects = 3
private const val TimeoutMillis = 15_000
private const val BufferBytes = 16 * 1024
private const val MaxInitialBufferBytes = 256 * 1024
private const val MaxHostChars = 253
private const val MaxUserAgentChars = 512
private const val MaxCookieChars = 8 * 1024
private const val MaxRefererChars = 2 * 1024
