package com.vayana.feature.library

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vayana.core.designsystem.theme.VayanaLinearProgressIndicator
import com.vayana.core.designsystem.tokens.Elevations
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R
import java.net.URLEncoder

internal fun coverImageSearchUrl(bookTitle: String): String =
    "https://www.google.com/search?tbm=isch&safe=active&q=" +
        URLEncoder.encode("$bookTitle cover image", Charsets.UTF_8.name())

@Composable
internal fun CoverImageSearchBrowser(
    bookTitle: String,
    onImageSelected: (CoverImageRequest) -> Unit,
    onDismiss: () -> Unit,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf(coverImageSearchUrl(bookTitle)) }
    var loadingProgress by remember { mutableIntStateOf(0) }
    var pendingImage by remember { mutableStateOf<CoverImageRequest?>(null) }

    fun imageRequest(url: String, userAgent: String?): CoverImageRequest? = url
        .takeIf { it.startsWith("https://", ignoreCase = true) || it.startsWith("data:image/", ignoreCase = true) }
        ?.let {
            CoverImageRequest(
                url = it,
                userAgent = userAgent,
                cookie = if (it.startsWith("https://", ignoreCase = true)) CookieManager.getInstance().getCookie(it) else null,
                referer = currentUrl,
            )
        }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        BackHandler {
            if (webView?.canGoBack() == true) webView?.goBack() else onDismiss()
        }
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.library_cover_search_close))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.library_cover_search_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = currentUrl.removePrefix("https://"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (loadingProgress in 1..99) {
                    VayanaLinearProgressIndicator(
                        progress = { loadingProgress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    HorizontalDivider()
                }
                AndroidView(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    factory = { context ->
                        WebView(context).apply {
                            configureCoverSearchBrowser(
                                onUrlChanged = { currentUrl = it },
                                onProgress = { loadingProgress = it },
                                onImageLongPressed = { url ->
                                    imageRequest(url, settings.userAgentString)?.let { pendingImage = it }
                                },
                                onDownload = { url, userAgent, mimeType ->
                                    if (mimeType.startsWith("image/", ignoreCase = true)) {
                                        imageRequest(url, userAgent)?.let { pendingImage = it }
                                    }
                                },
                            )
                            webView = this
                            loadUrl(currentUrl)
                        }
                    },
                    onRelease = { view ->
                        if (webView === view) webView = null
                        view.stopLoading()
                        view.destroy()
                    },
                )
                Surface(tonalElevation = Elevations.shadowSmall) {
                    Text(
                        text = stringResource(R.string.library_cover_search_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    )
                }
            }
        }
    }

    pendingImage?.let { request ->
        AlertDialog(
            onDismissRequest = { pendingImage = null },
            title = { Text(stringResource(R.string.library_cover_search_confirm_title)) },
            text = { Text(stringResource(R.string.library_cover_search_confirm_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        pendingImage = null
                        onImageSelected(request)
                    },
                ) {
                    Text(stringResource(R.string.library_cover_search_download))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImage = null }) {
                    Text(stringResource(R.string.library_edit_metadata_cancel))
                }
            },
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.configureCoverSearchBrowser(
    onUrlChanged: (String) -> Unit,
    onProgress: (Int) -> Unit,
    onImageLongPressed: (String) -> Unit,
    onDownload: (url: String, userAgent: String, mimeType: String) -> Unit,
) {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
    webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) = onProgress(newProgress)
    }
    webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val scheme = request?.url?.scheme.orEmpty()
            return scheme != "https" && scheme != "http"
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            url?.let(onUrlChanged)
        }
    }
    setDownloadListener { url, userAgent, _, mimeType, _ ->
        if (url != null && userAgent != null && mimeType != null) onDownload(url, userAgent, mimeType)
    }
    setOnLongClickListener {
        val hit = hitTestResult
        when (hit.type) {
            WebView.HitTestResult.IMAGE_TYPE -> {
                hit.extra?.let(onImageLongPressed)
                true
            }
            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                @Suppress("DEPRECATION")
                val handler = Handler(Looper.getMainLooper()) { message ->
                    message.data.getString("src")?.let(onImageLongPressed)
                    true
                }
                requestFocusNodeHref(handler.obtainMessage())
                true
            }
            else -> false
        }
    }
}
