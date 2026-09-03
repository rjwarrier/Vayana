package com.vayana.reader.web

import android.content.Context
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import com.vayana.reader.api.BookEngine
import com.vayana.reader.api.BookSource
import com.vayana.reader.api.BookStyle
import com.vayana.reader.api.EngineEvent
import com.vayana.reader.api.Locator
import com.vayana.reader.api.NavTarget
import com.vayana.reader.api.OpenBook
import com.vayana.reader.api.ReadTheme
import com.vayana.reader.api.ReaderAnnotation
import com.vayana.reader.api.ReaderAnnotationType
import com.vayana.reader.api.ReaderSelection
import com.vayana.reader.api.TocEntry
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

private const val ORIGIN = "https://appassets.androidplatform.net"
private const val READER_HTML_URL = "$ORIGIN/assets/reader.html"
private const val BOOK_URL = "$ORIGIN/book/current"
private const val ReaderOpenTimeoutMillis = 15_000L

/**
 * `:reader:engine-api`'s default implementation: foliate-js running inside a [WebView], driven
 * through a JS bridge (PROMPT2appbuild.md §3). One instance per [WebView] — construct it right
 * after the WebView is created (e.g. in the `AndroidView` factory) and [close] it when the
 * screen leaves composition.
 */
class FoliateBookEngine(private val webView: WebView, context: Context) : BookEngine {

    private var currentBookFile: File? = null
    private var jsReady = false
    private var pendingOpen: Pair<String, String?>? = null
    private var openResult: CompletableDeferred<Result<OpenBook>>? = null

    private val _location = MutableStateFlow<Locator?>(null)
    override val location: StateFlow<Locator?> = _location

    private val _events = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 16)

    private val appContext = context.applicationContext

    // NOT WebViewAssetLoader.AssetsPathHandler: its automatic MIME-type lookup often can't
    // resolve ".js" -> "text/javascript" on-device (MimeTypeMap has no default mapping for it),
    // and WebView's <script type="module"> loader silently refuses to run a module served with
    // the wrong (or no) MIME type — the exact "blank white screen, no thrown error" symptom.
    // Serving assets ourselves with an explicit extension->MIME map sidesteps that entirely.
    private val assetLoader = WebViewAssetLoader.Builder()
        .addPathHandler("/assets/") { path -> serveAsset(path) }
        .addPathHandler("/book/") { path ->
            val file = currentBookFile ?: return@addPathHandler null
            WebResourceResponse("application/epub+zip", null, file.inputStream())
        }
        .build()

    private fun serveAsset(path: String): WebResourceResponse? {
        val assetPath = path.trimStart('/')
        val stream = runCatching { appContext.assets.open(assetPath) }.getOrNull() ?: return null
        val mimeType = when (assetPath.substringAfterLast('.', "")) {
            "js" -> "text/javascript"
            "html" -> "text/html"
            "json" -> "application/json"
            "css" -> "text/css"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "woff2" -> "font/woff2"
            "wasm" -> "application/wasm"
            else -> "application/octet-stream"
        }
        val encoding = if (mimeType.startsWith("text/") || mimeType == "application/json") "utf-8" else null
        return WebResourceResponse(mimeType, encoding, stream)
    }

    init {
        webView.setBackgroundColor(android.graphics.Color.WHITE)
        webView.settings.javaScriptEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        webView.webViewClient = object : WebViewClientCompat() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                assetLoader.shouldInterceptRequest(request.url)
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                Log.d("FoliateReader", "[${message.messageLevel()}] ${message.message()} (${message.sourceId()}:${message.lineNumber()})")
                return true
            }
        }
        webView.addJavascriptInterface(JsBridge(), "AndroidBridge")
        webView.loadUrl(READER_HTML_URL)
    }

    override suspend fun open(source: BookSource, resumeLocator: Locator?): Result<OpenBook> {
        openResult?.complete(Result.failure(IllegalStateException("Reader open was replaced by a newer request")))
        currentBookFile = File(source.absoluteFilePath)
        val deferred = CompletableDeferred<Result<OpenBook>>()
        openResult = deferred

        val request = BOOK_URL to resumeLocator?.cfi
        if (jsReady) evaluateOpen(request.first, request.second) else pendingOpen = request

        return withTimeoutOrNull(ReaderOpenTimeoutMillis) { deferred.await() }
            ?: Result.failure<OpenBook>(IllegalStateException("Timed out while opening reader")).also {
                if (openResult === deferred) {
                    openResult = null
                    pendingOpen = null
                }
            }
    }

    override suspend fun goTo(target: NavTarget) {
        val js = when (target) {
            is NavTarget.NextPage -> "window.VayanaReader.next()"
            is NavTarget.PreviousPage -> "window.VayanaReader.prev()"
            is NavTarget.ToFraction -> "window.VayanaReader.goToFraction(${target.fraction})"
            is NavTarget.ToHref -> "window.VayanaReader.goToHref(${JSONObject.quote(target.href)})"
            is NavTarget.ToLocator -> target.locator.cfi
                ?.let { "window.VayanaReader.goToHref(${JSONObject.quote(it)})" }
        } ?: return
        webView.evaluateJavascript(js, null)
    }

    override suspend fun applyStyle(style: BookStyle, theme: ReadTheme) {
        val css = buildString {
            append("html{")
            append("background:${theme.backgroundColorArgb.toCssColor()} !important;")
            append("}")
            append("body{")
            append("background:${theme.backgroundColorArgb.toCssColor()} !important;")
            append("color:${theme.textColorArgb.toCssColor()} !important;")
            append("font-size:${style.fontSizePercent}% !important;")
            append("line-height:${style.lineHeight} !important;")
            append("margin-inline:${style.sideMarginPercent}% !important;")
            append("box-sizing:border-box !important;")
            style.fontFamily?.let { append("font-family:${it} !important;") }
            append("}")
        }
        webView.evaluateJavascript("window.VayanaReader.applyStyle(${JSONObject.quote(css)})", null)
    }

    override suspend fun renderAnnotations(annotations: List<ReaderAnnotation>) {
        val payload = JSONArray().apply {
            annotations
                .filter { it.cfi.isNotBlank() }
                .forEach { annotation -> put(annotation.toJson()) }
        }
        webView.evaluateJavascript("window.VayanaReader.renderAnnotations($payload)", null)
    }

    override suspend fun clearSelection() {
        webView.evaluateJavascript("window.VayanaReader.clearSelection()", null)
    }

    override fun events(): Flow<EngineEvent> = _events

    override fun close() {
        openResult?.complete(Result.failure(IllegalStateException("Reader closed before the book opened")))
        openResult = null
        pendingOpen = null
        webView.destroy()
    }

    private fun evaluateOpen(bookUrl: String, lastLocatorCfi: String?) {
        val cfiArg = lastLocatorCfi?.let { JSONObject.quote(it) } ?: "null"
        webView.evaluateJavascript("window.VayanaReader.open(${JSONObject.quote(bookUrl)}, $cfiArg)", null)
    }

    private inner class JsBridge {
        @JavascriptInterface
        fun onEvent(type: String, jsonPayload: String) {
            webView.post { handleEvent(type, JSONObject(jsonPayload)) }
        }
    }

    private fun handleEvent(type: String, payload: JSONObject) {
        Log.d("FoliateReader", "event: $type $payload")
        when (type) {
            "ready" -> {
                jsReady = true
                pendingOpen?.let { (url, cfi) -> evaluateOpen(url, cfi) }
                pendingOpen = null
            }
            "opened" -> {
                val toc = payload.optJSONArray("toc")?.toTocEntries() ?: emptyList()
                openResult?.complete(Result.success(OpenBook(title = payload.optString("title"), toc = toc)))
                openResult = null
            }
            "relocate" -> {
                val locator = Locator(
                    cfi = payload.optStringOrNull("cfi"),
                    href = null,
                    progression = payload.optDouble("fraction", 0.0).toFloat(),
                    chapterTitle = payload.optStringOrNull("tocLabel"),
                )
                _location.value = locator
                _events.tryEmit(EngineEvent.Relocated(locator))
            }
            "selection" -> {
                val selection = payload.toSelectionOrNull()
                _events.tryEmit(EngineEvent.SelectionChanged(selection))
            }
            "log" -> Log.d("FoliateReader", "bridge: $payload")
            "error" -> {
                val message = payload.optString("message", "Unknown reader error")
                openResult?.complete(Result.failure(IllegalStateException(message)))
                openResult = null
                _events.tryEmit(EngineEvent.Error(message))
            }
        }
    }
}

private fun JSONArray.toTocEntries(): List<TocEntry> = buildList {
    for (i in 0 until length()) {
        val obj = getJSONObject(i)
        add(
            TocEntry(
                title = obj.optString("label"),
                href = obj.optString("href"),
                children = obj.optJSONArray("children")?.toTocEntries() ?: emptyList(),
            ),
        )
    }
}

private fun JSONObject.optStringOrNull(name: String): String? =
    if (has(name) && !isNull(name)) getString(name) else null

private fun Int.toCssColor(): String = "#%06X".format(this and 0xFFFFFF)

private fun JSONObject.toSelectionOrNull(): ReaderSelection? {
    val cfi = optStringOrNull("cfi") ?: return null
    val selectedText = optStringOrNull("selectedText")?.takeIf { it.isNotBlank() } ?: return null
    return ReaderSelection(
        cfi = cfi,
        selectedText = selectedText,
        chapterTitle = optStringOrNull("tocLabel"),
    )
}

private fun ReaderAnnotation.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("value", cfi)
    .put("type", type.toFoliateType())
    .put("color", colorKey.toAnnotationColor())
    .put("note", note)

private fun ReaderAnnotationType.toFoliateType(): String = when (this) {
    ReaderAnnotationType.HIGHLIGHT,
    ReaderAnnotationType.NOTE,
    ReaderAnnotationType.BOOKMARK,
    -> "highlight"
    ReaderAnnotationType.UNDERLINE -> "underline"
}

private fun String.toAnnotationColor(): String = when (lowercase()) {
    "yellow" -> "#F6C453"
    "green" -> "#7BAE7F"
    "blue" -> "#5B8DEF"
    "pink" -> "#D77FA1"
    else -> "#F6C453"
}
