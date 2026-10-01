package com.vayana.feature.opds

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URI
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

/** An OPDS server the reader added: a name, the address of its catalogue feed and, for private ones, a login. */
data class OpdsCatalog(
    val id: String,
    val name: String,
    val url: String,
    val username: String = "",
    val password: String = "",
) {
    val hasLogin: Boolean get() = username.isNotEmpty()
}

/** The address as the reader typed it, made into a usable one: scheme added (HTTPS), http(s) only, a host required. */
internal fun normalizedCatalogUrl(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
    val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
    return runCatching {
        val uri = URI(withScheme)
        val scheme = uri.scheme?.lowercase()
        if ((scheme == "http" || scheme == "https") && !uri.host.isNullOrEmpty()) withScheme else null
    }.getOrNull()
}

/** A server address split into the parts a reader fills in; [port] blank means the scheme's own (80 or 443). */
internal data class OpdsAddress(
    val host: String = "",
    val port: String = "",
    val secure: Boolean = true,
    val path: String = DefaultOpdsPath,
)

/** Where Calibre, Calibre-Web and most other servers publish their OPDS feed. */
internal const val DefaultOpdsPath = "/opds"

/** The address typed into the editor's fields as one URL; null when the host or port can't be right. */
internal fun buildAddress(address: OpdsAddress): String? {
    val host = address.host.trim()
    if (!HostRegex.matches(host)) return null
    val port = address.port.trim()
    if (port.isNotEmpty() && (!port.all(Char::isDigit) || port.toIntOrNull() !in 1..65535)) return null
    val path = address.path.trim().let { if (it.isEmpty() || it.startsWith("/")) it else "/$it" }
    if (path.any { it.isWhitespace() }) return null
    return (if (address.secure) "https://" else "http://") + host + (if (port.isEmpty()) "" else ":$port") + path
}

/** A saved (or pasted) address back into the editor's fields. */
internal fun parseAddress(url: String): OpdsAddress {
    val normalized = normalizedCatalogUrl(url) ?: return OpdsAddress(host = url.trim())
    val uri = java.net.URI(normalized)
    return OpdsAddress(
        host = uri.host.orEmpty(),
        port = if (uri.port > 0) uri.port.toString() else "",
        secure = uri.scheme.equals("https", ignoreCase = true),
        path = (uri.rawPath.orEmpty() + (uri.rawQuery?.let { "?$it" } ?: "")).let { if (it == "/") "" else it },
    )
}

/** A local network name or address (192.168.x.x, nas.local, a bare host): usually plain HTTP, no certificate. */
internal fun looksLikeLocalServer(host: String): Boolean {
    val name = host.trim().lowercase()
    return name.matches(Regex("\\d{1,3}(\\.\\d{1,3}){3}")) || name.endsWith(".local") || (name.isNotEmpty() && '.' !in name)
}

private val HostRegex = Regex("[A-Za-z0-9]([A-Za-z0-9._-]*[A-Za-z0-9])?")

/**
 * The catalogues on this phone. Kept in private app storage (never in backups, exports or sync: logins stay on the
 * device they were typed on).
 */
@Singleton
class OpdsCatalogStore @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("opds_catalogs", Context.MODE_PRIVATE)
    private val _catalogs = MutableStateFlow(load())
    val catalogs: StateFlow<List<OpdsCatalog>> = _catalogs.asStateFlow()

    private val _gridView = MutableStateFlow(preferences.getBoolean(GridKey, true))

    /** Browse as a grid of covers (default) or as a list; one choice for every catalogue. */
    val gridView: StateFlow<Boolean> = _gridView.asStateFlow()

    fun setGridView(grid: Boolean) {
        _gridView.value = grid
        preferences.edit().putBoolean(GridKey, grid).apply()
    }

    fun find(id: String): OpdsCatalog? = _catalogs.value.firstOrNull { it.id == id }

    /** Adds a catalogue, or replaces the one with the same id. */
    fun save(catalog: OpdsCatalog) {
        _catalogs.update { current ->
            val index = current.indexOfFirst { it.id == catalog.id }
            if (index >= 0) current.toMutableList().also { it[index] = catalog } else current + catalog
        }
        persist()
    }

    fun delete(id: String) {
        _catalogs.update { current -> current.filterNot { it.id == id } }
        persist()
    }

    fun newId(): String = UUID.randomUUID().toString()

    private fun persist() {
        val array = JSONArray()
        _catalogs.value.forEach { catalog ->
            array.put(
                JSONObject()
                    .put("id", catalog.id).put("name", catalog.name).put("url", catalog.url)
                    .put("username", catalog.username).put("password", catalog.password),
            )
        }
        preferences.edit().putString(Key, array.toString()).apply()
    }

    private fun load(): List<OpdsCatalog> = runCatching {
        val array = JSONArray(preferences.getString(Key, "[]"))
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            OpdsCatalog(
                id = item.getString("id"),
                name = item.getString("name"),
                url = item.getString("url"),
                username = item.optString("username"),
                password = item.optString("password"),
            )
        }
    }.getOrDefault(emptyList())

    private companion object {
        const val Key = "catalogs"
        const val GridKey = "grid_view"
    }
}
