package com.vayana.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.vayana.app.widget.ContinueReadingWidgetUpdater
import com.vayana.app.widget.OpenBookRequests
import com.vayana.core.common.AppLanguage
import com.vayana.core.designsystem.theme.EinkPageKeys
import com.vayana.core.designsystem.theme.pageKeyDirection
import com.vayana.core.common.IncomingBookFiles
import com.vayana.core.common.incomingBookUris
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var incomingBookFiles: IncomingBookFiles
    @Inject lateinit var openBookRequests: OpenBookRequests

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
        if (intent.action == ContinueReadingWidgetUpdater.ActionOpenBook) {
            intent.getLongExtra(ContinueReadingWidgetUpdater.ExtraBookId, NoBook).takeIf { it != NoBook }?.let(openBookRequests::offer)
            return
        }
        incomingBookFiles.offer(intent.incomingBookUris())
    }
}

private const val NoBook = -1L
