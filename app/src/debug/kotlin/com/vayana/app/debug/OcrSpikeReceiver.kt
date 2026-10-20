package com.vayana.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfRenderer
import android.os.Debug
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import com.vayana.format.ocr.BookRecognition
import com.vayana.format.ocr.OcrModel
import com.vayana.format.ocr.PageRecognizer
import java.io.File
import kotlin.concurrent.thread

/**
 * Debug builds only: phase 1 of docs/SCANNED_PDF_TEXT_PLAN.md. Recognises pages of a PDF on the device and logs
 * time, memory and text under the tag "OcrSpike". Models go in files/ocr-models/tessdata/.
 *
 * adb shell am broadcast -n com.vayana.app/.debug.OcrSpikeReceiver --es pdf <path> --es pages 10,12,50
 *     [--es lang mal] [--ei threads 1]
 */
class OcrSpikeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // The background job (phase 2): --es job start|stop --es book <file sha256> [--es model mal-fast] [--ei start 0]
        when (intent.getStringExtra("job")) {
            "start" -> {
                val model = OcrModel.byId(intent.getStringExtra("model")) ?: OcrModel.MalayalamFast
                BookRecognition.start(context, File(intent.getStringExtra("pdf") ?: return), intent.getStringExtra("book") ?: return,
                    model, intent.getIntExtra("start", 0))
                Log.i(Tag, "job started ${model.id}")
                return
            }
            "stop" -> {
                BookRecognition.stop(context, intent.getStringExtra("book") ?: return)
                Log.i(Tag, "job stopped")
                return
            }
        }
        val pdf = File(intent.getStringExtra("pdf") ?: return)
        val pages = intent.getStringExtra("pages")?.split(',')?.mapNotNull { it.trim().toIntOrNull() }.orEmpty()
        val language = intent.getStringExtra("lang") ?: "mal"
        val threads = intent.getIntExtra("threads", 1).coerceIn(1, 4)
        val dataPath = File(context.filesDir, "ocr-models")
        thread(name = "ocr-spike") {
            runCatching { run(pdf, pages, language, threads, dataPath) }
                .onFailure { Log.e(Tag, "failed", it) }
        }
    }

    private fun run(pdf: File, pages: List<Int>, language: String, threads: Int, dataPath: File) {
        val started = SystemClock.elapsedRealtime()
        Log.i(Tag, "start ${pdf.name} pages=$pages lang=$language threads=$threads native=${nativeMb()}MB")
        val queue = java.util.concurrent.ConcurrentLinkedQueue(pages)
        val workers = (1..threads).map { worker ->
            thread(name = "ocr-spike-$worker") {
                val initStart = SystemClock.elapsedRealtime()
                PageRecognizer(dataPath, language).use { recognizer ->
                    Log.i(Tag, "worker $worker init ${SystemClock.elapsedRealtime() - initStart}ms native=${nativeMb()}MB")
                    ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                        PdfRenderer(descriptor).use { renderer ->
                            while (true) {
                                val page = queue.poll() ?: break
                                val pageStart = SystemClock.elapsedRealtime()
                                val result = recognizer.recognize(renderer, page)
                                val words = result.lines.flatten()
                                val confidence = words.map { it.confidence }.average()
                                Log.i(
                                    Tag,
                                    "page $page ${SystemClock.elapsedRealtime() - pageStart}ms lines=${result.lines.size} " +
                                        "words=${words.size} conf=${"%.0f".format(confidence)} native=${nativeMb()}MB",
                                )
                                result.lines.take(3).forEach { line ->
                                    Log.i(Tag, "  " + line.joinToString(" ") { it.text })
                                }
                                val first = words.firstOrNull()
                                if (first != null) Log.i(Tag, "  first box pt: ${first.left},${first.top} - ${first.right},${first.bottom}")
                            }
                        }
                    }
                }
            }
        }
        workers.forEach { it.join() }
        Log.i(Tag, "done ${pages.size} pages in ${SystemClock.elapsedRealtime() - started}ms native=${nativeMb()}MB")
    }

    private fun nativeMb() = Debug.getNativeHeapAllocatedSize() / (1024 * 1024)

    private companion object {
        const val Tag = "OcrSpike"
    }
}
