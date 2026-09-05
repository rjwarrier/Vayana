package com.vayana.reader.web

import android.content.Context
import android.util.Base64
import android.util.Log
import android.view.View
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
import com.vayana.reader.api.SearchResult
import com.vayana.reader.api.TocEntry
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil
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
private const val ReaderOpenTimeoutMillis = 60_000L
private const val EinkBackgroundArgb = -0x1
private const val EinkForegroundArgb = -0x1000000

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
    // foliate-js normally serves each EPUB resource (section HTML, CSS, images) to its
    // sandboxed iframe via `blob:` URLs (URL.createObjectURL). On this WebView build those
    // blob URLs silently fail to load inside a same-origin sandboxed iframe (empty `<body>`,
    // no error, no load-failure — the exact "blank white screen" symptom), so instead we hand
    // each resource to Kotlin (see JsBridge.registerResource) and serve it back over the same
    // https://appassets.androidplatform.net origin the rest of the reader already uses.
    private val resources = ConcurrentHashMap<String, Pair<String, ByteArray>>()
    private val nextResourceId = AtomicLong()

    private val assetLoader = WebViewAssetLoader.Builder()
        .addPathHandler("/assets/") { path -> serveAsset(path) }
        .addPathHandler("/resource/") { path ->
            val id = path.removePrefix("/resource/")
            val (mimeType, bytes) = resources[id] ?: return@addPathHandler null
            WebResourceResponse(
                mimeType,
                null,
                200,
                "OK",
                mapOf("Cache-Control" to "no-store"),
                bytes.inputStream(),
            )
        }
        .addPathHandler("/book/") { path ->
            val file = currentBookFile ?: return@addPathHandler null
            val mimeType = file.readerMimeType()
            WebResourceResponse(
                mimeType,
                null,
                200,
                "OK",
                mapOf(
                    "Cache-Control" to "no-store",
                    "Content-Length" to file.length().toString(),
                    "Accept-Ranges" to "none",
                ),
                file.inputStream(),
            )
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
        // Pinch-zoom is a continuous multi-frame gesture with nothing for a reflowable page to
        // zoom into (font size is app-controlled) - on E-Ink it's pure ghosting for no benefit.
        webView.settings.setSupportZoom(false)
        webView.settings.builtInZoomControls = false
        webView.settings.displayZoomControls = false
        webView.webViewClient = object : WebViewClientCompat() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url
                if (url.scheme != "https" || url.host != "appassets.androidplatform.net") {
                    return blockedResponse()
                }
                return assetLoader.shouldInterceptRequest(url) ?: blockedResponse(statusCode = 404, reasonPhrase = "Not Found")
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                return url.scheme != "https" || url.host != "appassets.androidplatform.net"
            }
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
        val bookFile = File(source.absoluteFilePath)
        if (!bookFile.isFile) {
            return Result.failure(IllegalStateException("Book file is missing from this device"))
        }
        if (!bookFile.canRead()) {
            return Result.failure(IllegalStateException("Book file cannot be read"))
        }
        if (bookFile.length() <= 0L) {
            return Result.failure(IllegalStateException("Book file is empty"))
        }
        currentBookFile = bookFile
        resources.clear()
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
        val margin = style.sideMarginPercent.coerceIn(0, 24)
        val lineHeight = style.lineHeight.coerceIn(1.2f, 4.0f)
        val isEinkTheme = theme.backgroundColorArgb == EinkBackgroundArgb && theme.textColorArgb == EinkForegroundArgb
        // GPU-composited (hardware) layers hand the frame to the display pipeline as a diff/blend,
        // which is what most E-Ink drivers ghost on; a software layer forces a plain full-bitmap
        // draw that the OEM's E-Ink refresh logic handles far more cleanly.
        webView.setLayerType(if (isEinkTheme) View.LAYER_TYPE_SOFTWARE else View.LAYER_TYPE_HARDWARE, null)
        val css = buildString {
            append("html{")
            append("background:${theme.backgroundColorArgb.toCssColor()} !important;")
            append("}")
            append("body{")
            append("background:${theme.backgroundColorArgb.toCssColor()} !important;")
            append("color:${theme.textColorArgb.toCssColor()} !important;")
            append("font-size:${style.fontSizePercent}% !important;")
            append("line-height:${lineHeight} !important;")
            append("box-sizing:border-box !important;")
            style.fontFamily?.let { append("font-family:${it} !important;") }
            append("}")
            append("body p,body div,body span,body li,body blockquote,body dd,body dt,body a,body em,body strong{")
            append("line-height:inherit !important;")
            append("}")
            if (isEinkTheme) {
                append("*,*::before,*::after{")
                append("animation:none !important;")
                append("transition:none !important;")
                append("text-shadow:none !important;")
                append("box-shadow:none !important;")
                append("filter:none !important;")
                append("}")
                append("a{")
                append("color:${theme.textColorArgb.toCssColor()} !important;")
                append("text-decoration:underline !important;")
                append("}")
                append("img,svg,video,canvas{")
                append("filter:grayscale(1) contrast(1.15) !important;")
                append("}")
                // Highlight fills render at 0.3 opacity by default (overlayer.js); once the rule
                // above grayscales them, a tinted highlight color washes out to a barely-visible
                // pale grey. Multiply + higher opacity keeps it legible without hardware color.
                append(":root{")
                append("--overlayer-highlight-opacity:0.55;")
                append("--overlayer-highlight-blend-mode:multiply;")
                append("}")
            }
        }
        webView.evaluateJavascript("window.VayanaReader.applyStyle(${JSONObject.quote(css)}, $margin)", null)
        webView.evaluateJavascript("window.VayanaReader.setBionicReading(${style.bionicReading})", null)
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

    override suspend fun search(query: String) {
        webView.evaluateJavascript("window.VayanaReader.search(${JSONObject.quote(query)})", null)
    }

    override suspend fun clearSearch() {
        webView.evaluateJavascript("window.VayanaReader.clearSearch()", null)
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

    private fun blockedResponse(statusCode: Int = 403, reasonPhrase: String = "Forbidden"): WebResourceResponse =
        WebResourceResponse(
            "text/plain",
            "utf-8",
            statusCode,
            reasonPhrase,
            mapOf("Cache-Control" to "no-store"),
            "Blocked by Vayana reader isolation".byteInputStream(),
        )

    private inner class JsBridge {
        @JavascriptInterface
        fun onEvent(type: String, jsonPayload: String) {
            webView.post { handleEvent(type, JSONObject(jsonPayload)) }
        }

        // Called synchronously from JS (see foliate/epub.js Loader.createURL) with the resource's
        // bytes base64-encoded. Returns the https:// URL to load it from instead of a blob: URL.
        @JavascriptInterface
        fun registerResource(mimeType: String, base64Data: String): String {
            val id = nextResourceId.getAndIncrement().toString()
            resources[id] = mimeType to Base64.decode(base64Data, Base64.NO_WRAP)
            return "$ORIGIN/resource/$id"
        }

        @JavascriptInterface
        fun unregisterResource(id: String) {
            resources.remove(id)
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
                    currentPage = payload.optIntOrNull("currentPage"),
                    totalPages = payload.optIntOrNull("totalPages"),
                    chapterMinutesLeft = payload.optMinutesOrNull("chapterMinutesLeft"),
                    bookMinutesLeft = payload.optMinutesOrNull("bookMinutesLeft"),
                )
                _location.value = locator
                _events.tryEmit(EngineEvent.Relocated(locator))
            }
            "selection" -> {
                val selection = payload.toSelectionOrNull()
                _events.tryEmit(EngineEvent.SelectionChanged(selection))
            }
            "searchResults" -> {
                val query = payload.optString("query")
                val results = payload.optJSONArray("results")?.toSearchResults() ?: emptyList()
                _events.tryEmit(EngineEvent.SearchCompleted(query, results))
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

private fun JSONArray.toSearchResults(): List<SearchResult> = buildList {
    for (i in 0 until length()) {
        val obj = getJSONObject(i)
        val cfi = obj.optStringOrNull("cfi") ?: continue
        add(
            SearchResult(
                cfi = cfi,
                excerpt = obj.optString("excerpt"),
                chapterTitle = obj.optStringOrNull("tocLabel"),
            ),
        )
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

private fun JSONObject.optIntOrNull(name: String): Int? =
    if (has(name) && !isNull(name)) getInt(name) else null

private fun JSONObject.optMinutesOrNull(name: String): Int? =
    if (has(name) && !isNull(name)) ceil(getDouble(name)).toInt().coerceAtLeast(0) else null

private fun Int.toCssColor(): String = "#%06X".format(this and 0xFFFFFF)

private fun File.readerMimeType(): String =
    when (extension.lowercase()) {
        "epub" -> "application/epub+zip"
        "pdf" -> "application/pdf"
        "mobi" -> "application/x-mobipocket-ebook"
        "azw3" -> "application/vnd.amazon.ebook"
        "fb2" -> "application/x-fictionbook+xml"
        "txt" -> "text/plain"
        else -> "application/octet-stream"
    }

private fun JSONObject.toSelectionOrNull(): ReaderSelection? {
    val cfi = optStringOrNull("cfi") ?: return null
    val selectedText = optStringOrNull("selectedText")?.takeIf { it.isNotBlank() } ?: return null
    return ReaderSelection(
        cfi = cfi,
        selectedText = selectedText,
        chapterTitle = optStringOrNull("tocLabel"),
        verticalPosition = optDoubleOrNull("verticalPosition")?.toFloat()?.coerceIn(0f, 1f),
    )
}

private fun JSONObject.optDoubleOrNull(name: String): Double? =
    if (has(name) && !isNull(name)) optDouble(name).takeIf(Double::isFinite) else null

private fun ReaderAnnotation.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("value", cfi)
    .put("type", type.toFoliateType())
    .put("color", colorKey.toAnnotationColor())
    .put("note", note)
    .put("text", text)

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
    "popular" -> "#6366F1"
    else -> "#6366F1"
}
