package com.vayana.app.wear

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vayana.app.R
import com.vayana.core.wear.WearSyncRules
import com.vayana.core.wear.companionConnection

@Composable
fun WatchConnectionIndicator() {
    val context = LocalContext.current.applicationContext
    val connection = remember(context) { companionConnection(context, WearSyncRules.WATCH) }
    val connected by connection.collectAsStateWithLifecycle(initialValue = false)
    if (connected) {
        Surface(modifier = Modifier.statusBarsPadding(), color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Watch, contentDescription = null)
                Text(stringResource(R.string.watch_connected), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
