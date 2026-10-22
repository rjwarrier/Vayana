package com.vayana.format.ocr

import android.app.ActivityManager
import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.work.Constraints
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import androidx.work.await
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How far a book's recognition has got: [done] of [total] pages, or finished. */
data class RecognitionProgress(val done: Int, val total: Int, val running: Boolean)

/**
 * Recognising a whole book in the background (docs/SCANNED_PDF_TEXT_PLAN.md). One unique job per book; each page is
 * saved as it is done, so a job Android stops (or that hits WorkManager's time limit) carries on from where it was
 * when it runs again.
 */
object BookRecognition {
    fun store(context: Context) = OcrStore(File(context.filesDir, "ocr"))

    fun models(context: Context) = OcrModelFiles(File(context.filesDir, "ocr-models"))

    /** The model last started, offered first for a book whose language has no model of its own. */
    fun lastModel(context: Context): OcrModel? =
        OcrModel.byId(runCatching { File(context.filesDir, LastModelFile).readText().trim() }.getOrNull())

    /**
     * Starts recognising [pages] (every page when null) of [pdf], saved under [book] (its file's SHA-256), from
     * [startPage] outwards. A job already running for the book is left alone. Waits for Wi-Fi if the model must
     * be downloaded first.
     */
    suspend fun start(
        context: Context,
        pdf: File,
        book: String,
        model: OcrModel,
        startPage: Int,
        pages: IntArray? = null,
        title: String? = null,
    ) = RecognitionAccess.models.withLock {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .setRequiredNetworkType(if (models(context).isInstalled(model)) NetworkType.NOT_REQUIRED else NetworkType.UNMETERED)
            .build()
        val request = OneTimeWorkRequestBuilder<BookRecognitionWorker>()
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .setConstraints(constraints)
            .setInputData(workDataOf(
                KeyPdf to pdf.absolutePath,
                KeyBook to book,
                KeyModel to model.id,
                KeyStartPage to startPage,
                KeyPages to pages,
                KeyTitle to title,
            ))
            .addTag(Tag)
            .addTag(bookTag(book))
            .addTag(modelTag(model))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(book), ExistingWorkPolicy.KEEP, request).await()
        runCatching { File(context.filesDir, LastModelFile).writeText(model.id) }
        Unit
    }

    fun stop(context: Context, book: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(book))
    }

    /** Stops every book's recognition. */
    fun stopAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(Tag)
    }

    fun jobs(context: Context): Flow<List<WorkInfo>> = WorkManager.getInstance(context).getWorkInfosByTagFlow(Tag)
    fun bookTag(book: String) = "ocr-book:$book"
    fun modelTag(model: OcrModel) = "ocr-model:${model.id}"
    fun preparationTag(book: String) = "prepare-ocr:$book"
    const val PreparationTag = "vayana-prepare-ocr"

    suspend fun remove(context: Context, book: String) {
        val manager = WorkManager.getInstance(context)
        manager.cancelAllWorkByTag(preparationTag(book)).await()
        manager.cancelUniqueWork(workName(book)).await()
        RecognitionAccess.book(book) { store(context).delete(book) }
    }

    suspend fun removeAll(context: Context) {
        val manager = WorkManager.getInstance(context)
        manager.cancelAllWorkByTag(PreparationTag).await()
        manager.cancelAllWorkByTag(Tag).await()
        val store = store(context)
        (RecognitionAccess.knownBooks() + store.bookUsage().keys).forEach { book ->
            RecognitionAccess.book(book) { store.delete(book) }
        }
    }

    /** Pending jobs and engines already stopping both protect their model. Enqueue uses the same lock. */
    suspend fun removeModel(context: Context, model: OcrModel): Boolean = RecognitionAccess.models.withLock {
        val busy = WorkManager.getInstance(context).getWorkInfosByTagFlow(modelTag(model)).first().any { !it.state.isFinished } ||
            jobs(context).first().any { !it.state.isFinished && it.tags.none { tag -> tag.startsWith("ocr-model:") } }
        if (busy || RecognitionAccess.modelBusy(model)) false else models(context).remove(model)
    }

    /** Older recognized books are finalized lazily by the reader, using the same ownership as worker/cleanup. */
    suspend fun finishTextIfComplete(context: Context, book: String, totalPages: Int, title: String, author: String?) =
        RecognitionAccess.book(book) {
            val store = store(context)
            val pages = store.savedPages(book)
            if (totalPages > 0 && pages.size >= totalPages) {
                val addJoins = !store.hasLineJoins(book)
                if (addJoins) store.saveLineJoins(book)
                if (addJoins || store.textEpub(book) == null) {
                    val language = (OcrModel.byId(pages.firstOrNull()?.let { store.model(book, it) }) ?: OcrModel.MalayalamFast).textLanguage
                    store.buildTextEpub(book, title, author, language)
                }
            }
        }

    /** The book's running or last job, or null when it never had one. */
    fun jobInfo(context: Context, book: String): Flow<WorkInfo?> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(workName(book)).map { infos ->
            infos.lastOrNull()
        }

    fun progress(context: Context, book: String): Flow<RecognitionProgress?> =
        jobInfo(context, book).map { info ->
            info ?: return@map null
            val data = if (info.state.isFinished) info.outputData else info.progress
            RecognitionProgress(
                done = data.getInt(KeyDone, 0),
                total = data.getInt(KeyTotal, 0),
                running = info.state == WorkInfo.State.RUNNING || info.state == WorkInfo.State.ENQUEUED,
            )
        }

    private fun workName(book: String) = "ocr-$book"

    internal const val KeyPdf = "pdf"
    internal const val KeyBook = "book"
    internal const val KeyModel = "model"
    internal const val KeyStartPage = "startPage"
    internal const val KeyPages = "pages"
    internal const val KeyTitle = "title"
    internal const val KeyDone = "done"
    internal const val KeyTotal = "total"
    private const val Tag = "vayana-ocr"
    private const val LastModelFile = "ocr-last-model"
}

class BookRecognitionWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val pdf = inputData.getString(BookRecognition.KeyPdf)?.let(::File)?.takeIf { it.isFile } ?: return@withContext Result.failure()
        val book = inputData.getString(BookRecognition.KeyBook) ?: return@withContext Result.failure()
        val model = OcrModel.byId(inputData.getString(BookRecognition.KeyModel)) ?: return@withContext Result.failure()
        if (!RecognitionAccess.engines.tryAcquire()) return@withContext Result.retry()
        try { RecognitionAccess.book(book) { RecognitionAccess.model(model) { recognize(pdf, book, model) } } }
        finally { RecognitionAccess.engines.release() }
    }

    private suspend fun recognize(pdf: File, book: String, model: OcrModel): Result {
        val models = BookRecognition.models(applicationContext)
        try {
            models.download(model)
        } catch (_: IOException) {
            return Result.retry()
        }
        val store = BookRecognition.store(applicationContext)
        val pageCount = openRenderer(pdf) { it.pageCount }
        val pages = inputData.getIntArray(BookRecognition.KeyPages)?.filter { it in 0 until pageCount }
            ?: (0 until pageCount).toList()
        val queue = ConcurrentLinkedQueue(pagesToRecognize(pages, store.savedPages(book), inputData.getInt(BookRecognition.KeyStartPage, 0)))
        val done = AtomicInteger(pages.size - queue.size)
        val notification = RecognitionNotification(applicationContext, book, inputData.getString(BookRecognition.KeyTitle), id)
        try {
            setProgress(progressData(done.get(), pages.size))
            notification.show(done.get(), pages.size)
            coroutineScope {
                repeat(threadCount()) {
                    launch {
                        PageRecognizer(models.dataPath, model.language).use { recognizer ->
                            openRenderer(pdf) { renderer ->
                                while (!isStopped) {
                                    val page = queue.poll() ?: break
                                    store.save(book, page, recognizer.recognize(renderer, page), model.id)
                                    val count = done.incrementAndGet()
                                    setProgress(progressData(count, pages.size))
                                    notification.show(count, pages.size)
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            notification.cancel()
        }
        if (isStopped) return Result.retry()
        // The whole book is known now: words broken across lines can be told from separate ones.
        store.saveLineJoins(book)
        // the text view: the book as running text
        runCatching { store.buildTextEpub(book, inputData.getString(BookRecognition.KeyTitle).orEmpty(), null, model.textLanguage) }
        return Result.success(progressData(done.get(), pages.size))
    }

    // Two engines where there is memory to spare: ~0.7 s a page instead of ~1.1 s on a recent phone.
    private fun threadCount(): Int {
        val activityManager = applicationContext.getSystemService(ActivityManager::class.java)
        return if (activityManager?.isLowRamDevice == false && Runtime.getRuntime().availableProcessors() >= 4) 2 else 1
    }

    private fun progressData(done: Int, total: Int) = workDataOf(BookRecognition.KeyDone to done, BookRecognition.KeyTotal to total)

    private inline fun <T> openRenderer(pdf: File, use: (PdfRenderer) -> T): T =
        ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use(use)
        }
}
