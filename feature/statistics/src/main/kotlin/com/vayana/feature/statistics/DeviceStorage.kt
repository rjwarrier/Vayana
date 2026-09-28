package com.vayana.feature.statistics

import java.io.File
import java.nio.file.Files

/** What the app's files on this device are for, in the order the Storage card lists them. */
enum class StorageCategory { BOOKS, COVERS, LIBRARY_DATA, DICTIONARY, FONTS, CACHE, OTHER }

data class CategorySize(val category: StorageCategory, val bytes: Long)

/** Everything the app keeps on this device, by what it's for. The installed app itself isn't counted. */
data class DeviceStorage(
    val totalBytes: Long,
    /** Only categories that take any room, largest first. */
    val categories: List<CategorySize>,
)

/** A directory whose files all belong to [category]. */
data class StorageRule(val directory: File, val category: StorageCategory)

/**
 * Walks [roots] once, filing each file under the first of [rules] whose directory holds it (so list the most specific
 * first) and everything else under [StorageCategory.OTHER]. Symbolic links are skipped: the app's data directory links
 * to its installed native code, which is the app, not its data. A root inside another is walked only once.
 */
fun measureDeviceStorage(roots: List<File>, rules: List<StorageRule>): DeviceStorage {
    val rulePaths = rules.map { rule -> rule.directory.absolutePath.withSeparator() to rule.category }
    val distinctRoots = roots.map { it.absoluteFile }.distinct()
        .filter { root -> distinctRootsContaining(root, roots).isEmpty() }
    val totals = LongArray(StorageCategory.entries.size)
    distinctRoots.forEach { root ->
        root.walkTopDown()
            .onEnter { directory -> directory == root || !Files.isSymbolicLink(directory.toPath()) }
            .filter { it.isFile && !Files.isSymbolicLink(it.toPath()) }
            .forEach { file ->
                val path = file.absolutePath
                val category = rulePaths.firstOrNull { (prefix, _) -> path.startsWith(prefix) }?.second ?: StorageCategory.OTHER
                totals[category.ordinal] += file.length()
            }
    }
    return DeviceStorage(
        totalBytes = totals.sum(),
        categories = StorageCategory.entries
            .map { CategorySize(it, totals[it.ordinal]) }
            .filter { it.bytes > 0 }
            .sortedByDescending { it.bytes },
    )
}

/** The other roots [root] sits inside, which walking it separately would count twice. */
private fun distinctRootsContaining(root: File, roots: List<File>): List<File> =
    roots.map { it.absoluteFile }.filter { other -> other != root && root.absolutePath.startsWith(other.absolutePath.withSeparator()) }

private fun String.withSeparator(): String = if (endsWith(File.separator)) this else this + File.separator
