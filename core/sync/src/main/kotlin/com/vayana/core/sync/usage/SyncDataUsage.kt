package com.vayana.core.sync.usage

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

/** GitHub requests a sync made and their payload bytes. */
data class GitHubTransferStats(val requests: Int, val downBytes: Long, val upBytes: Long)

enum class SyncKind { FULL, BACKGROUND, LAUNCH_CHECK, HISTORY }

/** The network a transfer went over. Mobile is metered cellular data; Other covers Ethernet, VPN-only and the rest. */
enum class DataNetwork { WIFI, MOBILE, OTHER, NONE }

data class SyncDataUsageEntry(
    val at: Long,
    val kind: SyncKind,
    val network: DataNetwork,
    val upBytes: Long,
    val downBytes: Long,
    val requests: Int,
) {
    val totalBytes: Long get() = upBytes + downBytes
}

/**
 * One line per sync with what it sent and received, for Settings → Data usage. File-based like the diagnostics log
 * (one JSON object per line, so a torn write only loses its own line), kept for [RetentionDays].
 */
@Singleton
class SyncDataUsageLog internal constructor(
    private val file: File,
    private val networkNow: () -> DataNetwork,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Inject constructor(@ApplicationContext context: Context) :
        this(File(context.filesDir, "diagnostics/sync-usage.ndjson"), { currentDataNetwork(context) })

    private val lock = Any()

    /** A sync that made no request (nothing to do, or no network) records nothing. */
    fun record(kind: SyncKind, stats: GitHubTransferStats) {
        if (stats.requests <= 0) return
        val entry = SyncDataUsageEntry(clock(), kind, networkNow(), stats.upBytes, stats.downBytes, stats.requests)
        synchronized(lock) {
            runCatching {
                file.parentFile?.mkdirs()
                FileOutputStream(file, true).use { it.write((entry.toJsonLine() + "\n").toByteArray(Charsets.UTF_8)) }
            }
            if (file.length() > MaxFileBytes) trimLocked()
        }
    }

    /** Newest first. */
    fun readAll(): List<SyncDataUsageEntry> = synchronized(lock) {
        if (!file.isFile) return@synchronized emptyList()
        runCatching { file.readLines(Charsets.UTF_8).mapNotNull { it.toEntryOrNull() }.sortedByDescending { it.at } }
            .getOrDefault(emptyList())
    }

    private fun trimLocked() {
        runCatching {
            val cutoff = clock() - RetentionDays * DayMillis
            val kept = file.readLines(Charsets.UTF_8).filter { line -> (line.toEntryOrNull()?.at ?: 0L) >= cutoff }.takeLast(MaxEntries)
            file.writeText(if (kept.isEmpty()) "" else kept.joinToString("\n", postfix = "\n"))
        }
    }

    internal companion object {
        const val RetentionDays = 120L
        const val MaxEntries = 20_000
        const val MaxFileBytes = 1024L * 1024
        const val DayMillis = 24L * 60 * 60 * 1000
    }
}

fun currentDataNetwork(context: Context): DataNetwork = runCatching {
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return@runCatching DataNetwork.NONE
    dataNetworkOf(
        wifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
        cellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
    )
}.getOrDefault(DataNetwork.OTHER)

internal fun dataNetworkOf(wifi: Boolean, cellular: Boolean): DataNetwork = when {
    wifi -> DataNetwork.WIFI
    cellular -> DataNetwork.MOBILE
    else -> DataNetwork.OTHER
}

/** Bytes over Wi-Fi and mobile; anything else (Ethernet, unknown) counts with Wi-Fi as unmetered. */
data class NetworkSplit(val wifiBytes: Long = 0, val mobileBytes: Long = 0) {
    val totalBytes: Long get() = wifiBytes + mobileBytes
    operator fun plus(entry: SyncDataUsageEntry): NetworkSplit =
        if (entry.network == DataNetwork.MOBILE) copy(mobileBytes = mobileBytes + entry.totalBytes)
        else copy(wifiBytes = wifiBytes + entry.totalBytes)
}

data class DayUsage(val date: LocalDate, val split: NetworkSplit)

/** One [DayUsage] per day for the last [days] days ending [today], oldest first, empty days included. */
fun List<SyncDataUsageEntry>.dailyUsage(days: Int, today: LocalDate, zone: ZoneId): List<DayUsage> {
    val first = today.minusDays((days - 1).toLong())
    val byDay = groupBy { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() }
    return (0 until days).map { offset ->
        val date = first.plusDays(offset.toLong())
        DayUsage(date, byDay[date].orEmpty().fold(NetworkSplit()) { split, entry -> split + entry })
    }
}

/** Entries at or after the start of [since] (local time). */
fun List<SyncDataUsageEntry>.since(since: LocalDate, zone: ZoneId): List<SyncDataUsageEntry> {
    val start = since.atStartOfDay(zone).toInstant().toEpochMilli()
    return filter { it.at >= start }
}

private fun SyncDataUsageEntry.toJsonLine(): String = JSONObject()
    .put("at", at).put("kind", kind.name).put("network", network.name)
    .put("up", upBytes).put("down", downBytes).put("requests", requests)
    .toString()

private fun String.toEntryOrNull(): SyncDataUsageEntry? {
    if (isBlank()) return null
    return runCatching {
        val o = JSONObject(this)
        SyncDataUsageEntry(
            at = o.getLong("at"),
            kind = SyncKind.valueOf(o.getString("kind")),
            network = DataNetwork.valueOf(o.getString("network")),
            upBytes = o.getLong("up"),
            downBytes = o.getLong("down"),
            requests = o.optInt("requests"),
        )
    }.getOrNull()
}
