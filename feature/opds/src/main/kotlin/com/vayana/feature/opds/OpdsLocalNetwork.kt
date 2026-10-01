package com.vayana.feature.opds

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import java.net.URI

/**
 * Android 17 keeps apps off the home network (a Calibre server on 192.168.x.x, a NAS, a .local name) until the reader
 * allows "local network" access. Before that, every connection to such an address simply times out.
 */
internal const val LocalNetworkPermission = "android.permission.ACCESS_LOCAL_NETWORK"

private const val LocalNetworkSdk = 37

internal fun Context.hasLocalNetworkAccess(): Boolean =
    Build.VERSION.SDK_INT < LocalNetworkSdk ||
        ContextCompat.checkSelfPermission(this, LocalNetworkPermission) == PackageManager.PERMISSION_GRANTED

/** True for an address the permission guards: an IP address, a .local name, or a bare host name. */
internal fun isLocalAddress(url: String): Boolean =
    runCatching { looksLikeLocalServer(URI(url).host.orEmpty()) }.getOrDefault(false)

/** Asks for local network access; [onResult] says whether it was granted. Where the permission doesn't exist, it is. */
@Composable
internal fun rememberLocalNetworkRequest(onResult: (Boolean) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), onResult)
    return remember(launcher) {
        {
            if (Build.VERSION.SDK_INT < LocalNetworkSdk) onResult(true) else launcher.launch(LocalNetworkPermission)
        }
    }
}
