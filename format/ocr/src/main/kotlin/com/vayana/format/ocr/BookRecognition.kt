package com.vayana.format.ocr

import android.app.ActivityManager
import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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

    /**
     * Starts recognising [pages] (every page when null) of [pdf], saved under [book] (its file's SHA-256), from
     * [startPage] outwards. A job already running for the book is left alone. Waits for Wi-Fi if the model must
     * be downloaded first.
     */
    fun start(
        context: Context,
        pdf: File,
        book: String,
        model: OcrModel,
        startPage: Int,
        pages: IntArray? = null,
        title: String? = null,
    ) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .setRequiredNetworkType(if (models(context).isInstalled(model)) NetworkType.NOT_REQUIRED else NetworkType.UNMETERED)
            .build()
        val request = OneTimeWorkRequestBuilder<BookRecognitionWorker>()
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
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(book), ExistingWorkPolicy.KEEP, request)
    }

    fun stop(context: Context, book: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(book))
    }

    /** The book's running or last job, or null when it never had one. */
    fun progress(context: Context, book: String): Flow<RecognitionProgress?> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(workName(book)).map { infos ->
            val info = infos.lastOrNull() ?: return@map null
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
}

class BookRecognitionWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val pdf = inputData.getString(BookRecognition.KeyPdf)?.let(::File)?.takeIf { it.isFile } ?: return@withContext Result.failure()
        val book = inputData.getString(BookRecognition.KeyBook) ?: return@withContext Result.failure()
        val model = OcrModel.byId(inputData.getString(BookRecognition.KeyModel)) ?: return@withContext Result.failure()
        val models = BookRecognition.models(applicationContext)
        try {
            models.download(model)
        } catch (_: IOException) {
            return@withContext Result.retry()
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
        if (isStopped) Result.retry() else Result.success(progressData(done.get(), pages.size))
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
