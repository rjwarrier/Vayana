package com.vayana.app.widget

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.vayana.app.MainActivity
import com.vayana.app.R
import com.vayana.core.common.ApplicationScope
import com.vayana.core.common.DispatcherProvider
import com.vayana.core.database.repository.BookRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.vayana.core.resources.R as Res

/**
 * The app icon's long-press shortcuts: the book being read, by its title, then Free books and Search. Republished
 * only when that book changes, not as it's read, so the launcher's rate limit is never near.
 */
@Singleton
class AppShortcuts @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookRepository: BookRepository,
    private val dispatchers: DispatcherProvider,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch {
            bookRepository.observeAll()
                .map { books -> books.continueReading()?.let { it.id to it.title } }
                .distinctUntilChanged()
                .collect { publish(it) }
        }
    }

    private suspend fun publish(book: Pair<Long, String>?) = withContext(dispatchers.io) {
        val shortcuts = buildList {
            book?.let { (id, title) ->
                add(
                    shortcut(
                        id = IdContinue,
                        shortLabel = title,
                        longLabel = context.getString(Res.string.shortcut_continue_long, title),
                        icon = R.drawable.ic_shortcut_continue,
                        intent = intent(ContinueReadingWidgetUpdater.ActionOpenBook)
                            .putExtra(ContinueReadingWidgetUpdater.ExtraBookId, id),
                    ),
                )
            }
            add(
                shortcut(
                    id = IdFreeBooks,
                    shortLabel = context.getString(Res.string.library_free_books),
                    longLabel = context.getString(Res.string.shortcut_free_books_long),
                    icon = R.drawable.ic_shortcut_free_books,
                    intent = intent(ActionFreeBooks),
                ),
            )
            add(
                shortcut(
                    id = IdSearch,
                    shortLabel = context.getString(Res.string.shortcut_search),
                    longLabel = context.getString(Res.string.shortcut_search_long),
                    icon = R.drawable.ic_shortcut_search,
                    intent = intent(ActionSearch),
                ),
            )
        }
        // A launcher that refuses (rate limit, work profile) leaves the old shortcuts: nothing to recover.
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }
    }

    private fun shortcut(id: String, shortLabel: String, longLabel: String, @DrawableRes icon: Int, intent: Intent) =
        ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(shortLabel)
            .setLongLabel(longLabel)
            .setIcon(IconCompat.createWithResource(context, icon))
            .setIntent(intent)
            .build()

    private fun intent(action: String) = Intent(context, MainActivity::class.java).setAction(action)

    companion object {
        const val ActionFreeBooks = "com.vayana.app.FREE_BOOKS"
        const val ActionSearch = "com.vayana.app.SEARCH"
        private const val IdContinue = "continue_reading"
        private const val IdFreeBooks = "free_books"
        private const val IdSearch = "search"
    }
}

/** Screens a launcher shortcut asked for, waiting for the app's navigation. */
enum class ShortcutDestination { FREE_BOOKS, SEARCH }

@Singleton
class ShortcutRequests @Inject constructor() {
    private val _pending = MutableStateFlow<ShortcutDestination?>(null)
    val pending: StateFlow<ShortcutDestination?> = _pending.asStateFlow()

    fun offer(destination: ShortcutDestination) {
        _pending.value = destination
    }

    fun consume(destination: ShortcutDestination) {
        _pending.compareAndSet(destination, null)
    }
}
