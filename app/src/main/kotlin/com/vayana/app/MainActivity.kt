package com.vayana.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.vayana.feature.library.IncomingBookFiles
import com.vayana.feature.library.incomingBookUris
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var incomingBookFiles: IncomingBookFiles

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A recreated activity (rotation, process restore) still holds the original intent; it was handled already.
        if (savedInstanceState == null) handleIncoming(intent)
        setContent {
            VayanaAppRoot()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncoming(intent)
    }

    private fun handleIncoming(intent: Intent) {
        incomingBookFiles.offer(intent.incomingBookUris())
    }
}
