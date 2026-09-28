package com.vayana.dictionary.online

import com.vayana.core.common.DispatcherProvider
import com.vayana.dictionary.api.DictionaryEntry
import com.vayana.dictionary.api.OnlineDictionary
import com.vayana.dictionary.api.OnlineDictionarySource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.LinkedHashMap
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * English Wiktionary and Wikipedia through Wikimedia's REST API: no key, but a User-Agent naming the app, as their
 * policy asks. English, like the offline dictionary; Wiktionary also defines other languages' words in English.
 */
@Singleton
internal class WikimediaDictionary @Inject constructor(
    private val dispatchers: DispatcherProvider,
) : OnlineDictionary {
    private val cache = object : LinkedHashMap<Pair<OnlineDictionarySource, String>, DictionaryEntry?>(CacheSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<OnlineDictionarySource, String>, DictionaryEntry?>?) =
            size > CacheSize
    }

    override suspend fun lookup(word: String, source: OnlineDictionarySource): DictionaryEntry? = withContext(dispatchers.io) {
        // Books curl their apostrophes (“can’t”); Wikimedia titles use the straight one.
        val query = word.trim().replace('’', '\'')
        if (query.isEmpty()) return@withContext null
        val key = source to query
        synchronized(cache) { if (cache.containsKey(key)) return@withContext cache[key] }
        val entry = when (source) {
            OnlineDictionarySource.WIKTIONARY -> lookUpWiktionary(query)
            OnlineDictionarySource.WIKIPEDIA -> lookUpWikipedia(query)
        }
        synchronized(cache) { cache[key] = entry }
        entry
    }

    private suspend fun lookUpWiktionary(word: String): DictionaryEntry? {
        // Wiktionary titles are case-sensitive: a capitalised word from the start of a sentence is usually listed in
        // lower case.
        for (title in listOf(word, word.lowercase(Locale.ROOT)).distinct()) {
            currentCoroutineContext().ensureActive()
            val path = title.toTitlePath()
            val body = get("$WiktionaryHost/api/rest_v1/page/definition/$path") ?: continue
            WikimediaParser.wiktionary(title, body, "$WiktionaryHost/wiki/$path")?.let { return it }
        }
        return null
    }

    /**
     * Wikipedia capitalises a title's first letter itself and follows redirects in the summary, which covers most
     * words and phrases. The rest of a title is case-sensitive, so on a miss this searches for the title and takes a
     * result only if it is the query itself in other capitalisation ("hagia sophia"), never a merely similar page.
     */
    private suspend fun lookUpWikipedia(query: String): DictionaryEntry? {
        get(summaryUrl(query))?.let { return WikimediaParser.wikipedia(it) }
        // Each request is blocking and can't be interrupted, but a cancelled lookup needn't start the next one.
        currentCoroutineContext().ensureActive()
        val encodedQuery = URLEncoder.encode(query, Charsets.UTF_8.name())
        val title = get("$WikipediaHost/w/api.php?action=opensearch&format=json&namespace=0&limit=$SearchLimit&search=$encodedQuery")
            ?.let(WikimediaParser::openSearchTitles)
            ?.firstOrNull { it != query && it.equals(query, ignoreCase = true) }
            ?: return null
        currentCoroutineContext().ensureActive()
        return get(summaryUrl(title))?.let(WikimediaParser::wikipedia)
    }

    private fun summaryUrl(title: String) = "$WikipediaHost/api/rest_v1/page/summary/${title.toTitlePath()}"

    /** The body of a 200 response, or null for a 404 (no such page). Anything else throws. */
    private fun get(url: String): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TimeoutMillis
            connection.readTimeout = TimeoutMillis
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", UserAgent)
            connection.setRequestProperty("Accept", "application/json")
            return when (val status = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> connection.inputStream.use { it.readAtMost(MaxResponseBytes) }.decodeToString()
                HttpURLConnection.HTTP_NOT_FOUND -> null
                else -> throw IOException("HTTP $status")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun InputStream.readAtMost(maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = read(buffer)
            if (count < 0) return output.toByteArray()
            if (output.size() + count > maxBytes) throw IOException("Response too large")
            output.write(buffer, 0, count)
        }
    }

    private fun String.toTitlePath(): String =
        URLEncoder.encode(replace(' ', '_'), Charsets.UTF_8.name()).replace("+", "%20")

    private companion object {
        const val WiktionaryHost = "https://en.wiktionary.org"
        const val WikipediaHost = "https://en.wikipedia.org"
        const val UserAgent = "Vayana/1.0 (https://github.com/rjwarrier/Vayana)"
        const val TimeoutMillis = 10_000
        const val MaxResponseBytes = 1024 * 1024
        const val CacheSize = 32
        const val SearchLimit = 5
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class OnlineDictionaryModule {
    @Binds
    abstract fun bindOnlineDictionary(implementation: WikimediaDictionary): OnlineDictionary
}
