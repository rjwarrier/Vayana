package com.vayana.reader.web

import android.content.Context
import android.util.Base64
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import com.vayana.reader.api.BookEngine
import com.vayana.reader.api.BookHyphenation
import com.vayana.reader.api.BookSource
import com.vayana.reader.api.BookStyle
import com.vayana.reader.api.BookTextAlign
import com.vayana.reader.api.EngineEvent
import com.vayana.reader.api.Footnote
import com.vayana.reader.api.FootnoteOpened
import com.vayana.reader.api.Locator
import com.vayana.reader.api.MergedRange
import com.vayana.reader.api.SpeechChunk
import com.vayana.reader.api.SpeechSentence
import com.vayana.reader.api.NavTarget
import com.vayana.reader.api.OpenBook
import com.vayana.reader.api.ReadTheme
import com.vayana.reader.api.ReaderAnnotation
import com.vayana.reader.api.ReaderAnnotationType
import com.vayana.reader.api.ReaderSelection
import com.vayana.reader.api.SearchResult
import com.vayana.reader.api.TocEntry
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipFile
import kotlin.math.ceil
import kotlin.math.min
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val LogTag = "FoliateReader"
private const val ORIGIN = "https://appassets.androidplatform.net"
private const val READER_HTML_URL = "$ORIGIN/assets/reader.html"
private const val BOOK_URL = "$ORIGIN/book/current"
private const val BOOK_PATH = "/book/current"
private const val IMPORTED_FONT_FAMILY = "VayanaImportedReaderFont"
private const val ReaderOpenTimeoutMillis = 60_000L
private const val EinkBackgroundArgb = -0x1
private const val EinkForegroundArgb = -0x1000000

/** Width of the outline added to every letter for bolder text: enough to thicken a hairline serif, not to blur a letter. */
internal const val BoldTextStrokePx = "0.4px"

/**
 * Paragraphs only, and not those the book itself centres or aligns by hand (poems, epigraphs, signatures): forcing
 * them into the chosen alignment would wreck their layout.
 */
private const val ParagraphSelector =
    "body p:not([align]):not([style*='text-align']):not(.center):not(.centre):not(.centered):not(.right):not(.poem)"

internal fun textAlignCss(align: BookTextAlign): String? = when (align) {
    BookTextAlign.BOOK -> null
    BookTextAlign.JUSTIFIED -> "$ParagraphSelector,body li,body blockquote p{text-align:justify !important;}"
    BookTextAlign.LEFT -> "$ParagraphSelector,body li,body blockquote p{text-align:left !important;}"
}

internal fun hyphenationCss(hyphenation: BookHyphenation): String? = when (hyphenation) {
    BookHyphenation.BOOK -> null
    BookHyphenation.ON -> "body,body *{-webkit-hyphens:auto !important;hyphens:auto !important;}"
    BookHyphenation.OFF -> "body,body *{-webkit-hyphens:none !important;hyphens:none !important;}"
}

/**
 * `:reader:engine-api`'s default implementation: foliate-js running inside a [WebView], driven
 * through a JS bridge (docs/PRODUCT_SPEC.md §3). One instance per [WebView] — construct it right
 * after the WebView is created (e.g. in the `AndroidView` factory) and [close] it when the
 * screen leaves composition.
 */
class FoliateBookEngine(private val webView: WebView, context: Context) : BookEngine {

    @Volatile
    private var currentBookFile: File? = null
    private var jsReady = false
    private var pendingOpen: Pair<String, String?>? = null
    private var openResult: CompletableDeferred<Result<OpenBook>>? = null
    @Volatile private var waitingForPdfPassword = false

    private val _location = MutableStateFlow<Locator?>(null)
    override val location: StateFlow<Locator?> = _location
    private var lastTocPages: Map<String, Int> = emptyMap()

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

    // Images need no rewriting by foliate, so rather than the base64 bridge above they are streamed straight out
    // of the open EPUB: id -> (zip entry name, MIME type). See JsBridge.entryUrl and Loader.loadItem in epub.js.
    private val entryResources = ConcurrentHashMap<String, Pair<String, String>>()
    private val bookZipLock = Any()
    private var bookZip: ZipFile? = null

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
        .addPathHandler("/entry/") { path ->
            val (entryName, mimeType) = entryResources[path.removePrefix("/entry/")] ?: return@addPathHandler null
            val stream = runCatching {
                openBookZip()?.let { zip -> zip.getEntry(entryName)?.let(zip::getInputStream) }
            }.getOrNull() ?: return@addPathHandler null
            WebResourceResponse(mimeType, null, 200, "OK", mapOf("Cache-Control" to "no-store"), stream)
        }
        .addPathHandler("/fonts/") { path -> serveFont(path) }
        .build()

    /** Streams either the complete book or the single byte range requested by PDF.js. */
    private fun serveBook(request: WebResourceRequest): WebResourceResponse? {
        val file = currentBookFile ?: return null
        val length = file.length()
        val rangeHeader = request.requestHeaders.entries
            .firstOrNull { it.key.equals("Range", ignoreCase = true) }
            ?.value
        if (rangeHeader == null) {
            return WebResourceResponse(
                file.readerMimeType(),
                null,
                200,
                "OK",
                mapOf(
                    "Cache-Control" to "no-store",
                    "Content-Length" to length.toString(),
                    "Accept-Ranges" to "bytes",
                ),
                file.inputStream(),
            )
        }

        val range = parseByteRange(rangeHeader, length)
            ?: return WebResourceResponse(
                "text/plain",
                "utf-8",
                416,
                "Range Not Satisfiable",
                mapOf(
                    "Cache-Control" to "no-store",
                    "Content-Range" to "bytes */$length",
                    "Accept-Ranges" to "bytes",
                ),
                ByteArray(0).inputStream(),
            )
        val stream = FileInputStream(file).also { it.channel.position(range.start) }
        return WebResourceResponse(
            file.readerMimeType(),
            null,
            206,
            "Partial Content",
            mapOf(
                "Cache-Control" to "no-store",
                "Content-Length" to range.length.toString(),
                "Content-Range" to "bytes ${range.start}-${range.endInclusive}/$length",
                "Accept-Ranges" to "bytes",
            ),
            LimitedInputStream(stream, range.length),
        )
    }

    private fun serveAsset(path: String): WebResourceResponse? {
        val assetPath = path.trimStart('/')
        val stream = runCatching { appContext.assets.open(assetPath) }.getOrNull() ?: return null
        val mimeType = when (assetPath.substringAfterLast('.', "")) {
            "js", "mjs" -> "text/javascript"
            "html" -> "text/html"
            "json" -> "application/json"
            "css" -> "text/css"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "woff2" -> "font/woff2"
            "ttf" -> "font/ttf"
            "wasm" -> "application/wasm"
            else -> "application/octet-stream"
        }
        val encoding = if (mimeType.startsWith("text/") || mimeType == "application/json") "utf-8" else null
        return WebResourceResponse(mimeType, encoding, stream)
    }

    private fun serveFont(path: String): WebResourceResponse? {
        val fontFileName = path.trimStart('/').substringAfterLast('/')
        if (fontFileName.isBlank()) return null
        val fontsDir = File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, "fonts")
        val file = File(fontsDir, fontFileName)
        val safeRoot = fontsDir.canonicalFile
        val safeFile = file.canonicalFile
        if (!safeFile.path.startsWith(safeRoot.path) || !safeFile.isFile || !safeFile.canRead()) return null
        return WebResourceResponse(
            safeFile.readerFontMimeType(),
            null,
            200,
            "OK",
            mapOf("Cache-Control" to "max-age=31536000"),
            safeFile.inputStream(),
        )
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
                if (url.path == BOOK_PATH) {
                    return serveBook(request) ?: blockedResponse(statusCode = 404, reasonPhrase = "Not Found")
                }
                return assetLoader.shouldInterceptRequest(url) ?: blockedResponse(statusCode = 404, reasonPhrase = "Not Found")
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                return url.scheme != "https" || url.host != "appassets.androidplatform.net"
            }

            // Without this the app dies with the renderer (crash or out-of-memory kill). The WebView is unusable
            // afterwards, so unblock whoever waits on it and tell the reader to build a new engine.
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                jsReady = false
                openResult?.complete(Result.failure(IllegalStateException("The reader engine stopped unexpectedly")))
                openResult = null
                pendingOpen = null
                bridgeRequests.values.forEach { it.complete(null) }
                bridgeRequests.clear()
                _events.tryEmit(EngineEvent.RendererGone)
                return true
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                if (Log.isLoggable(LogTag, Log.DEBUG)) {
                    Log.d(LogTag, "[${message.messageLevel()}] ${message.message()} (${message.sourceId()}:${message.lineNumber()})")
                }
                return true
            }
        }
        webView.addJavascriptInterface(JsBridge(), "AndroidBridge")
        webView.loadUrl(READER_HTML_URL)
    }

    override suspend fun open(source: BookSource, resumeLocator: Locator?): Result<OpenBook> {
        lastTocPages = emptyMap()
        waitingForPdfPassword = false
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
        closeBookZip()
        currentBookFile = bookFile
        resources.clear()
        entryResources.clear()
        val deferred = CompletableDeferred<Result<OpenBook>>()
        openResult = deferred

        val request = BOOK_URL to resumeLocator?.cfi
        if (jsReady) evaluateOpen(request.first, request.second) else pendingOpen = request

        val timedResult = withTimeoutOrNull(ReaderOpenTimeoutMillis) { deferred.await() }
        if (timedResult != null) return timedResult
        if (waitingForPdfPassword) return deferred.await()
        return Result.failure<OpenBook>(IllegalStateException("Timed out while opening reader")).also {
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
            is NavTarget.ToPage -> "window.VayanaReader.goToPage(${target.pageIndex})"
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
            style.customFontFileName?.takeIf { it.isNotBlank() }?.let { fileName ->
                append("@font-face{")
                append("font-family:'$IMPORTED_FONT_FAMILY';")
                append("src:url('$ORIGIN/fonts/$fileName');")
                append("font-display:swap;")
                append("}")
            }
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
            if (style.overridePublisherTypography) {
                // A declaration on a child beats a value inherited from body, even when the body's
                // declaration is important. Apply the selected family to text elements while leaving
                // code/preformatted content alone, and keep heading sizes while normalizing body copy.
                append("body p,body div,body span,body li,body blockquote,body dd,body dt,body a,body em,body strong,")
                append("body h1,body h2,body h3,body h4,body h5,body h6{")
                append("font-family:inherit !important;")
                append("}")
                append("body p,body li,body blockquote,body dd,body dt{")
                append("font-size:inherit !important;")
                append("}")
            }
            if (style.boldText) {
                // Inherited, so the body alone reaches every letter; each keeps its own colour.
                append("body{-webkit-text-stroke:$BoldTextStrokePx currentColor !important;}")
            }
            textAlignCss(style.textAlign)?.let(::append)
            hyphenationCss(style.hyphenation)?.let(::append)
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
            }
        }
        // One trip across the bridge for the whole style.
        webView.evaluateJavascript(
            "window.VayanaReader.applyStyle(${JSONObject.quote(css)}, $margin);" +
                "window.VayanaReader.setBionicReading(${style.bionicReading});" +
                "window.VayanaReader.setPageTurnAnimation(${style.pageTurnAnimation});" +
                // Highlights live in an overlay outside the book's document, so the CSS above cannot reach them.
                "window.VayanaReader.setInkMarks($isEinkTheme);" +
                // PDF pages are drawn, not styled: pdf.js repaints them in the theme's colours instead.
                "window.VayanaReader.setPageColors(" +
                "${JSONObject.quote(theme.backgroundColorArgb.toCssColor())}, " +
                "${JSONObject.quote(theme.textColorArgb.toCssColor())});" +
                "window.VayanaReader.setPdfLayout(${pdfLayoutJson(style)})",
            null,
        )
    }

    override suspend fun renderAnnotations(annotations: List<ReaderAnnotation>) {
        val payload = withContext(Dispatchers.Default) { JSONArray().apply {
            annotations
                .filter { it.cfi.isNotBlank() }
                .forEach { annotation -> put(annotation.toJson()) }
        }.toString() }
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

    private val bridgeRequests = ConcurrentHashMap<Long, CompletableDeferred<JSONObject?>>()
    private val nextBridgeRequestId = AtomicLong()

    override suspend fun startSpeech(fromCfi: String?): SpeechChunk =
        requestBridge("startSpeech", fromCfi?.let(JSONObject::quote) ?: "null")?.toSpeechChunk() ?: EndOfBookChunk

    override suspend fun nextSpeechChunk(): SpeechChunk = requestBridge("nextSpeechChunk")?.toSpeechChunk() ?: EndOfBookChunk

    override suspend fun markSpeech(id: String, start: Int, end: Int) {
        webView.evaluateJavascript("window.VayanaReader.markSpeech(${JSONObject.quote(id)}, $start, $end)", null)
    }

    override suspend fun stopSpeech() {
        webView.evaluateJavascript("window.VayanaReader.stopSpeech()", null)
    }

    override suspend fun chapterWordCounts(minLength: Int): Map<String, Int> =
        requestBridge("chapterWordCounts", minLength.toString())?.optJSONObject("counts")?.toIntMap() ?: emptyMap()

    override suspend fun mergeRanges(cfi: String, others: List<String>): MergedRange? {
        if (others.isEmpty()) return null
        val reply = requestBridge("mergeRanges", JSONObject.quote(cfi), JSONArray(others).toString()) ?: return null
        val merged = reply.optJSONArray("merged") ?: return null
        return MergedRange(
            cfi = reply.optStringOrNull("cfi") ?: return null,
            text = reply.optString("text"),
            merged = List(merged.length()) { index -> merged.optString(index) },
        )
    }

    override suspend fun pageThumbnail(pageIndex: Int, maxWidthPx: Int): ByteArray? {
        val dataUrl = requestBridge(
            "pageThumbnail",
            pageIndex.toString(),
            maxWidthPx.coerceIn(80, 480).toString(),
        )?.optStringOrNull("dataUrl") ?: return null
        val encoded = dataUrl.substringAfter(',', missingDelimiterValue = "")
        return encoded.takeIf { it.isNotBlank() }?.let { Base64.decode(it, Base64.DEFAULT) }
    }

    override suspend fun providePdfPassword(password: String?) {
        val value = password?.let(JSONObject::quote) ?: "null"
        webView.evaluateJavascript("window.VayanaReader.providePdfPassword($value)", null)
    }

    /**
     * Calls `window.VayanaReader.[function](requestId, ...[arguments])` and waits for the bridge's "reply" event carrying
     * that id. Null when no reply comes in time or the engine closes first.
     */
    private suspend fun requestBridge(function: String, vararg arguments: String): JSONObject? {
        val id = nextBridgeRequestId.incrementAndGet()
        val deferred = CompletableDeferred<JSONObject?>()
        bridgeRequests[id] = deferred
        val callArguments = (listOf(id.toString()) + arguments).joinToString(", ")
        try {
            webView.evaluateJavascript("window.VayanaReader.$function($callArguments)", null)
            return withTimeoutOrNull(BridgeRequestTimeoutMillis) { deferred.await() }
        } finally {
            bridgeRequests.remove(id)
        }
    }

    private var closed = false

    override fun close() {
        // Idempotent: ReaderViewModel.onCleared() and the AndroidView's own onRelease callback can both
        // reach the same engine instance under teardown-ordering races (rotation, fast back-navigation),
        // and WebView.destroy() is not safe to call twice.
        if (closed) return
        closed = true
        waitingForPdfPassword = false
        openResult?.complete(Result.failure(IllegalStateException("Reader closed before the book opened")))
        openResult = null
        pendingOpen = null
        bridgeRequests.values.forEach { it.complete(null) }
        bridgeRequests.clear()
        resources.clear()
        entryResources.clear()
        closeBookZip()
        webView.destroy()
    }

    /** The current book opened as a zip, on first use; reads through it are safe from any thread. */
    private fun openBookZip(): ZipFile? = synchronized(bookZipLock) {
        bookZip ?: currentBookFile?.let { file -> runCatching { ZipFile(file) }.getOrNull() }?.also { bookZip = it }
    }

    private fun closeBookZip() {
        synchronized(bookZipLock) {
            runCatching { bookZip?.close() }
            bookZip = null
        }
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

        /** Metadata only: bridge.js uses it to expose PDFs as a range-backed file without copying their bytes. */
        @JavascriptInterface
        fun bookInfo(): String {
            val file = currentBookFile ?: return "{}"
            return JSONObject()
                .put("name", file.name)
                .put("type", file.readerMimeType())
                .put("size", file.length())
                .toString()
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

        /**
         * A URL streaming the image stored at [href] in the open EPUB, or "" (so epub.js falls back to
         * registerResource) when it isn't an image or the entry can't be found under that name.
         */
        @JavascriptInterface
        fun entryUrl(href: String, mimeType: String): String {
            if (!mimeType.startsWith("image/")) return ""
            val zip = openBookZip() ?: return ""
            if (runCatching { zip.getEntry(href) }.getOrNull() == null) return ""
            val id = nextResourceId.getAndIncrement().toString()
            entryResources[id] = href to mimeType
            return "$ORIGIN/entry/$id"
        }
    }

    private fun handleEvent(type: String, payload: JSONObject) {
        if (Log.isLoggable(LogTag, Log.DEBUG)) {
            Log.d(LogTag, "event: $type $payload")
        }
        when (type) {
            "ready" -> {
                jsReady = true
                pendingOpen?.let { (url, cfi) -> evaluateOpen(url, cfi) }
                pendingOpen = null
            }
            "opened" -> {
                val toc = payload.optJSONArray("toc")?.toTocEntries() ?: emptyList()
                val openBook = OpenBook(
                    title = payload.optString("title"),
                    toc = toc,
                    fixedLayout = payload.optBoolean("fixedLayout"),
                    pageLabels = payload.optJSONArray("pageLabels")?.let { labels ->
                        List(labels.length()) { index -> labels.optString(index, (index + 1).toString()) }
                    }.orEmpty(),
                )
                waitingForPdfPassword = false
                openResult?.complete(Result.success(openBook))
                openResult = null
            }
            "relocate" -> {
                val locator = Locator(
                    cfi = payload.optStringOrNull("cfi"),
                    href = payload.optStringOrNull("tocHref"),
                    progression = payload.optDouble("fraction", 0.0).toFloat(),
                    chapterTitle = payload.optStringOrNull("tocLabel"),
                    currentPage = payload.optIntOrNull("currentPage"),
                    totalPages = payload.optIntOrNull("totalPages"),
                    chapterMinutesLeft = payload.optMinutesOrNull("chapterMinutesLeft"),
                    bookMinutesLeft = payload.optMinutesOrNull("bookMinutesLeft"),
                    tocPages = payload.optJSONObject("tocPages")?.toIntMap()?.also { lastTocPages = it } ?: lastTocPages,
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
            "footnote" -> {
                val text = payload.optStringOrNull("text")?.takeIf { it.isNotBlank() } ?: return
                _events.tryEmit(FootnoteOpened(Footnote(text = text, href = payload.optString("href"))))
            }
            "storyEnd" -> _events.tryEmit(EngineEvent.StoryEndReached)
            "pageScrollable" -> _events.tryEmit(EngineEvent.PageScrollableChanged(payload.optBoolean("scrollable")))
            "pdfPasswordRequired" -> {
                waitingForPdfPassword = true
                _events.tryEmit(EngineEvent.PdfPasswordRequired(payload.optBoolean("incorrect")))
            }
            "controlsRequested" -> _events.tryEmit(EngineEvent.ControlsRequested)
            "reply" -> bridgeRequests.remove(payload.optLong("requestId"))?.complete(payload)
            "log" -> if (Log.isLoggable(LogTag, Log.DEBUG)) Log.d(LogTag, "bridge: $payload")
            "error" -> {
                waitingForPdfPassword = false
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

private fun JSONObject.toIntMap(): Map<String, Int> = buildMap {
    for (key in keys()) optIntOrNull(key)?.let { put(key, it) }
}

private fun JSONObject.optMinutesOrNull(name: String): Int? =
    if (has(name) && !isNull(name)) ceil(getDouble(name)).toInt().coerceAtLeast(0) else null

private fun pdfLayoutJson(style: BookStyle): String = JSONObject()
    .put("cropMargins", style.pdfCropMargins)
    .put("fitWidth", style.pdfFitWidth)
    .put("darken", style.boldText)
    .put("rotationDegrees", style.pdfRotationDegrees)
    .toString()

private fun Int.toCssColor(): String = "#%06X".format(this and 0xFFFFFF)

private fun File.readerFontMimeType(): String =
    when (extension.lowercase()) {
        "ttf" -> "font/ttf"
        "otf" -> "font/otf"
        "woff" -> "font/woff"
        "woff2" -> "font/woff2"
        else -> "application/octet-stream"
    }

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

internal data class ByteRange(val start: Long, val endInclusive: Long) {
    val length: Long get() = endInclusive - start + 1
}

/** Parses one HTTP byte range. Multiple ranges are deliberately rejected because PDF.js never requests them. */
internal fun parseByteRange(header: String, resourceLength: Long): ByteRange? {
    if (resourceLength <= 0L) return null
    val match = Regex("^bytes=(\\d*)-(\\d*)$").matchEntire(header.trim()) ?: return null
    val startText = match.groupValues[1]
    val endText = match.groupValues[2]
    if (startText.isEmpty() && endText.isEmpty()) return null

    if (startText.isEmpty()) {
        val suffixLength = endText.toLongOrNull()?.takeIf { it > 0L } ?: return null
        val start = (resourceLength - suffixLength).coerceAtLeast(0L)
        return ByteRange(start, resourceLength - 1L)
    }

    val start = startText.toLongOrNull()?.takeIf { it in 0 until resourceLength } ?: return null
    val requestedEnd = if (endText.isEmpty()) resourceLength - 1L else endText.toLongOrNull() ?: return null
    if (requestedEnd < start) return null
    return ByteRange(start, min(requestedEnd, resourceLength - 1L))
}

/** Stops WebView at the requested range boundary while still closing the underlying file descriptor. */
private class LimitedInputStream(
    private val source: InputStream,
    private var remaining: Long,
) : InputStream() {
    override fun read(): Int {
        if (remaining <= 0L) return -1
        val value = source.read()
        if (value >= 0) remaining--
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining <= 0L) return -1
        val count = source.read(buffer, offset, min(length.toLong(), remaining).toInt())
        if (count > 0) remaining -= count
        return count
    }

    override fun available(): Int = min(source.available().toLong(), remaining).toInt()

    override fun close() = source.close()
}

private fun JSONObject.toSelectionOrNull(): ReaderSelection? {
    val cfi = optStringOrNull("cfi") ?: return null
    val selectedText = optStringOrNull("selectedText")?.takeIf { it.isNotBlank() } ?: return null
    return ReaderSelection(
        cfi = cfi,
        selectedText = selectedText,
        chapterTitle = optStringOrNull("tocLabel"),
        top = optDoubleOrNull("top")?.toFloat()?.coerceIn(0f, 1f),
        bottom = optDoubleOrNull("bottom")?.toFloat()?.coerceIn(0f, 1f),
        isWordLookup = optBoolean("wordLookup"),
    )
}

private fun JSONObject.toSpeechChunk(): SpeechChunk {
    val array = optJSONArray("sentences") ?: JSONArray()
    val sentences = buildList {
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val text = obj.optString("text").trim()
            if (text.isNotEmpty()) add(SpeechSentence(id = obj.getString("id"), text = text))
        }
    }
    return SpeechChunk(sentences, endOfBook = optBoolean("endOfBook"))
}

private const val BridgeRequestTimeoutMillis = 15_000L
private val EndOfBookChunk = SpeechChunk(emptyList(), endOfBook = true)

private fun JSONObject.optDoubleOrNull(name: String): Double? =
    if (has(name) && !isNull(name)) optDouble(name).takeIf(Double::isFinite) else null

private fun ReaderAnnotation.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("value", cfi)
    .put("type", type.toFoliateType())
    .put("color", colorKey.toAnnotationColor())
    .put("popular", colorKey.equals("popular", ignoreCase = true))
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
