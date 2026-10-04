package com.vayana.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.vayana.app.widget.AppShortcuts
import com.vayana.app.widget.ContinueReadingWidgetUpdater
import com.vayana.app.widget.OpenBookRequest
import com.vayana.app.widget.OpenBookRequests
import com.vayana.app.widget.ShortcutDestination
import com.vayana.app.widget.ShortcutRequests
import com.vayana.core.common.AppLanguage
import com.vayana.core.designsystem.theme.EinkPageKeys
import com.vayana.core.homelibrary.HomeLibrarySync
import com.vayana.core.designsystem.theme.pageKeyDirection
import com.vayana.core.common.IncomingBookFiles
import com.vayana.core.common.incomingBookUris
import com.vayana.feature.gutenberg.GutenbergCacheWarmer
import com.vayana.feature.library.PhysicalReadingTimerNotification
import com.vayana.feature.library.PhysicalReadingTimerController
import com.vayana.feature.library.PhysicalTimerPhase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var incomingBookFiles: IncomingBookFiles
    @Inject lateinit var openBookRequests: OpenBookRequests
    @Inject lateinit var shortcutRequests: ShortcutRequests
    @Inject lateinit var gutenbergCacheWarmer: GutenbergCacheWarmer
    @Inject lateinit var homeLibrarySync: HomeLibrarySync
    @Inject lateinit var physicalReadingTimer: PhysicalReadingTimerController
    @Inject lateinit var physicalReadingTimerNotification: PhysicalReadingTimerNotification

    private var notificationPermissionSession: String? = null
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) physicalReadingTimerNotification.refresh()
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A recreated activity (rotation, process restore) still holds the original intent; it was handled already.
        if (savedInstanceState == null) handleIncoming(intent)
        setContent {
            VayanaAppRoot()
        }
        gutenbergCacheWarmer.start()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                physicalReadingTimer.session.collect { timer ->
                    val active = timer?.takeIf {
                        it.phase == PhysicalTimerPhase.RUNNING || it.phase == PhysicalTimerPhase.PAUSED
                    } ?: return@collect
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
                        notificationPermissionSession != active.syncId
                    ) {
                        notificationPermissionSession = active.syncId
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Back in front: pick up anything Home Library changed meanwhile (a cheap /info check first).
        homeLibrarySync.onForeground()
    }

    // The hardware page buttons of an e-reader page the list on screen. Only while such a list exists: the reader turns
    // its own pages and gets the key untouched.
    @SuppressLint("RestrictedApi") // Activity's public key dispatch override is required for hardware page keys.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val direction = pageKeyDirection(event.keyCode)
        if (direction == null || !EinkPageKeys.hasHandler) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) EinkPageKeys.dispatch(direction)
        return true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncoming(intent)
    }

    private fun handleIncoming(intent: Intent) {
        val passage = intent.data?.toString()?.let { com.vayana.core.common.parsePassageLink(it) }
        if (intent.action == Intent.ACTION_VIEW && passage != null) {
            openBookRequests.offer(OpenBookRequest(bookSyncId = passage.bookSyncId, annotationSyncId = passage.annotationSyncId))
            return
        }
        when (intent.action) {
            ContinueReadingWidgetUpdater.ActionOpenBook ->
                intent.getLongExtra(ContinueReadingWidgetUpdater.ExtraBookId, NoBook).takeIf { it != NoBook }?.let { bookId ->
                    openBookRequests.offer(
                        OpenBookRequest(bookId, readAloud = intent.getBooleanExtra(ContinueReadingWidgetUpdater.ExtraReadAloud, false)),
                    )
                }
            AppShortcuts.ActionFreeBooks -> shortcutRequests.offer(ShortcutDestination.FREE_BOOKS)
            AppShortcuts.ActionSearch -> shortcutRequests.offer(ShortcutDestination.SEARCH)
            AppShortcuts.ActionStatistics -> shortcutRequests.offer(ShortcutDestination.STATISTICS)
            else -> incomingBookFiles.offer(intent.incomingBookUris())
        }
    }
}

private const val NoBook = -1L
