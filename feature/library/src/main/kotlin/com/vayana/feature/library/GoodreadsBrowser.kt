package com.vayana.feature.library

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vayana.core.common.ParsedQuote
import com.vayana.core.common.runCatchingCancellable
import com.vayana.core.designsystem.theme.VayanaLinearProgressIndicator
import com.vayana.core.designsystem.tokens.Elevations
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONTokener

/** What the in-app browser hands back: the book's metadata and, if its quote pages could be read, their quotes. */
internal data class GoodreadsBrowserCapture(val metadata: GoodreadsBookMetadata, val quotes: List<ParsedQuote>?)

private sealed interface CaptureStatus {
    data object Book : CaptureStatus
    data class Quotes(
        val page: Int,
        val progress: GoodreadsQuoteProgress? = null,
    ) : CaptureStatus
}

/**
 * A plain, visible Goodreads browser - the fallback for when Goodreads blocks the direct import. The user finds
 * the book themselves; "Import this book" then reads the page they're on and steps through its quote pages in
 * this same view, where they can watch it and stop it (Close or Back). Top-level navigation stays on goodreads.com.
 */
@Composable
internal fun GoodreadsBrowserDialog(
    startUrl: String,
    onCaptured: (GoodreadsBrowserCapture) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val pageReader = remember { GoodreadsPageReader() }
    var currentUrl by remember { mutableStateOf(startUrl) }
    var loadingProgress by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf<CaptureStatus?>(null) }
    var notReady by remember { mutableStateOf(false) }
    var captureJob by remember { mutableStateOf<Job?>(null) }
    val bookId = goodreadsBookIdOf(currentUrl)

    fun close() {
        captureJob?.cancel()
        onDismiss()
    }

    fun startCapture() {
        val id = bookId ?: return
        notReady = false
        captureJob = scope.launch {
            try {
                status = CaptureStatus.Book
                val metadata = pageReader.readBookNextData()?.let { nextData ->
                    withContext(Dispatchers.Default) {
                        runCatchingCancellable { parseBookNextData(nextData, id, goodreadsBookUrl(id)) }.getOrNull()
                    }
                }
                if (metadata == null) {
                    notReady = true
                    return@launch
                }
                val quotes = metadata.workId?.let { workId ->
                    var currentPage = 1
                    collectGoodreadsQuotes(
                        onProgress = { progress -> status = CaptureStatus.Quotes(currentPage, progress) },
                    ) { page ->
                        currentPage = page
                        status = CaptureStatus.Quotes(page)
                        pageReader.loadHtml(goodreadsQuotesUrl(workId, page))
                    }
                }
                onCaptured(GoodreadsBrowserCapture(metadata, quotes))
            } finally {
                status = null
            }
        }
    }

    Dialog(
        onDismissRequest = ::close,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, decorFitsSystemWindows = false),
    ) {
        BackHandler {
            val view = pageReader.webView
            when {
                status != null -> captureJob?.cancel()
                view?.canGoBack() == true -> view.goBack()
                else -> close()
            }
        }
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .imePadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = ::close) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.library_goodreads_browser_close))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.library_goodreads_browser_title), style = MaterialTheme.typography.titleMedium)
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
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    factory = { context ->
                        WebView(context).apply {
                            configureForGoodreads(
                                onUrlChanged = { currentUrl = it },
                                onProgress = { loadingProgress = it },
                                onPageFinished = pageReader::onPageFinished,
                            )
                            pageReader.webView = this
                            loadUrl(startUrl)
                        }
                    },
                    onRelease = { view ->
                        if (pageReader.webView === view) pageReader.webView = null
                        view.stopLoading()
                        view.destroy()
                    },
                )
                Surface(tonalElevation = Elevations.shadowSmall) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        Text(
                            text = when (val current = status) {
                                CaptureStatus.Book -> stringResource(R.string.library_goodreads_browser_reading_book)
                                is CaptureStatus.Quotes -> current.progress
                                    ?.takeIf { it.total > GoodreadsQuoteProgressThreshold }
                                    ?.let { progress ->
                                        stringResource(
                                            R.string.library_goodreads_browser_reading_quotes_progress,
                                            progress.processed,
                                            progress.total,
                                        )
                                    }
                                    ?: stringResource(R.string.library_goodreads_browser_reading_quotes, current.page)
                                null -> if (notReady) {
                                    stringResource(R.string.library_goodreads_browser_not_ready)
                                } else {
                                    stringResource(R.string.library_goodreads_browser_hint)
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (notReady && status == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Button(onClick = ::startCapture, enabled = bookId != null && status == null) {
                            Text(stringResource(R.string.library_goodreads_browser_import))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Reads pages out of the visible browser. Everything here runs on the main thread, as WebView requires; page
 * content comes back through `evaluateJavascript`, so no JavaScript interface is ever exposed to the page.
 */
private class GoodreadsPageReader {
    var webView: WebView? = null
    private var pageLoad: CompletableDeferred<Unit>? = null

    fun onPageFinished() {
        pageLoad?.complete(Unit)
    }

    /** The current page's `__NEXT_DATA__` JSON, or null if it isn't a (fully loaded) book page. */
    suspend fun readBookNextData(): String? = webView?.evaluate(NextDataScript)

    /**
     * Loads [url] in the view and returns its HTML once a real page is up. A bot check the browser is still
     * clearing reloads itself into the real page, so this polls past it rather than trusting the first load.
     */
    suspend fun loadHtml(url: String): String? {
        val view = webView ?: return null
        val load = CompletableDeferred<Unit>().also { pageLoad = it }
        view.loadUrl(url)
        return withTimeoutOrNull(PageLoadTimeoutMillis) {
            load.await()
            var html: String? = null
            while (html == null) {
                val candidate = view.evaluate(OuterHtmlScript)
                if (candidate != null && view.progress == FullyLoaded && !candidate.looksLikeGoodreadsChallenge()) {
                    html = candidate
                } else {
                    delay(PollIntervalMillis)
                }
            }
            html
        }
    }
}

@SuppressLint("SetJavaScriptEnabled") // Goodreads (and its bot check) needs JavaScript; no JS interface is added.
private fun WebView.configureForGoodreads(
    onUrlChanged: (String) -> Unit,
    onProgress: (Int) -> Unit,
    onPageFinished: () -> Unit,
) {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    settings.setSupportMultipleWindows(false)
    webViewClient = object : WebViewClient() {
        // Only the top-level page is kept on goodreads.com; its own images and scripts load from wherever they like.
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            request.isForMainFrame && !request.url.isGoodreadsPage()

        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
            onUrlChanged(url)
        }

        override fun onPageFinished(view: WebView, url: String) {
            onUrlChanged(url)
            onPageFinished()
        }
    }
    webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            onProgress(newProgress)
        }
    }
}

private fun Uri.isGoodreadsPage(): Boolean = scheme == "https" && isGoodreadsHost(host.orEmpty())

private suspend fun WebView.evaluate(script: String): String? = suspendCancellableCoroutine { continuation ->
    evaluateJavascript(script) { raw -> continuation.resume(raw.decodeJsString()) }
}

/** `evaluateJavascript` hands back a JSON literal: a quoted string, or `null`. */
private fun String?.decodeJsString(): String? =
    if (this == null || this == "null") null else runCatching { JSONTokener(this).nextValue() as? String }.getOrNull()

private const val NextDataScript =
    "(function(){var e=document.getElementById('__NEXT_DATA__');return e?e.textContent:null;})()"
private const val OuterHtmlScript = "document.documentElement.outerHTML"
private const val PageLoadTimeoutMillis = 30_000L
private const val PollIntervalMillis = 500L
private const val FullyLoaded = 100
