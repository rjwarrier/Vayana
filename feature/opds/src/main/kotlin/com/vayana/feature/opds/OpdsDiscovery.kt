package com.vayana.feature.opds

import com.vayana.core.common.DispatcherProvider
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** A catalogue found on the home network: where it is, its own name if it gave one, and whether it wants a login. */
data class OpdsServerFound(val host: String, val port: Int, val title: String?, val needsLogin: Boolean)

/**
 * Looks for OPDS servers on the phone's own Wi-Fi network: the usual Calibre and Calibre-Web ports on each address of
 * the local /24, one quick connection test each, then a real request for `/opds` where something answers. Started by
 * the reader (the editor's "Find on my network"), never in the background, and needs local network access.
 */
@Singleton
class OpdsDiscovery @Inject constructor(private val dispatchers: DispatcherProvider) {
    fun scan(): Flow<OpdsServerFound> = channelFlow {
        val own = ownAddress() ?: return@channelFlow
        val gate = Semaphore(ConcurrentProbes)
        for (host in subnetHosts(own)) {
            for (port in Ports) {
                launch {
                    gate.withPermit {
                        probe(host, port)?.let { send(it) }
                    }
                }
            }
        }
    }.flowOn(dispatchers.io)

    private suspend fun probe(host: String, port: Int): OpdsServerFound? {
        currentCoroutineContext().ensureActive()
        // Most addresses and ports are closed or empty: a bare connection test is far cheaper than a request.
        val open = runCatching { Socket().use { it.connect(InetSocketAddress(host, port), ConnectMillis) } }.isSuccess
        if (!open) return null
        currentCoroutineContext().ensureActive()
        val connection = runCatching { URL("http://$host:$port/opds").openConnection() as HttpURLConnection }.getOrNull() ?: return null
        return try {
            connection.connectTimeout = RequestMillis
            connection.readTimeout = RequestMillis
            connection.instanceFollowRedirects = false
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val head = connection.inputStream.use { readHead(it) }
                    if ("<feed" in head) OpdsServerFound(host, port, feedTitle(head), needsLogin = false) else null
                }
                // Calibre-Web and a Calibre server with accounts ask for a login even to list the catalogue.
                HttpURLConnection.HTTP_UNAUTHORIZED ->
                    if (connection.getHeaderField("WWW-Authenticate") != null) OpdsServerFound(host, port, null, needsLogin = true) else null
                else -> null
            }
        } catch (error: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    /** The first [HeadBytes] of the response (readNBytes needs a newer Android than the app's minimum). */
    private fun readHead(input: java.io.InputStream): String {
        val buffer = ByteArray(HeadBytes)
        var filled = 0
        while (filled < buffer.size) {
            val count = input.read(buffer, filled, buffer.size - filled)
            if (count < 0) break
            filled += count
        }
        return String(buffer, 0, filled, Charsets.UTF_8)
    }

    private fun ownAddress(): ByteArray? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && it.name.startsWith("wlan") }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }
            ?.address
    }.getOrNull()

    internal companion object {
        /** Calibre's content server, Calibre-Web, and the usual alternatives. */
        val Ports = listOf(8080, 8083, 8081, 80)
        private const val ConcurrentProbes = 48
        private const val ConnectMillis = 250
        private const val RequestMillis = 1_500
        private const val HeadBytes = 4096

        /** Every other address of the phone's /24, as text. */
        fun subnetHosts(own: ByteArray): List<String> {
            if (own.size != 4) return emptyList()
            val prefix = own.take(3).joinToString(".") { (it.toInt() and 0xFF).toString() }
            val self = own[3].toInt() and 0xFF
            return (1..254).filter { it != self }.map { "$prefix.$it" }
        }

        /** The feed's own title: the first one in the document, which is the feed's rather than an entry's. */
        fun feedTitle(head: String): String? =
            Regex("<title[^>]*>([^<]*)</title>").find(head)?.groupValues?.get(1)?.trim()?.ifEmpty { null }
    }
}
