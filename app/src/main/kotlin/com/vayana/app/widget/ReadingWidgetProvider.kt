package com.vayana.app.widget

import android.content.ContentProvider
import android.content.ContentProviderOperation
import android.content.ContentProviderResult
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import com.vayana.app.VayanaApp

class ReadingWidgetProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?,
    ): Cursor = snapshot(uri, null)

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?, cancellationSignal: CancellationSignal?,
    ): Cursor = snapshot(uri, cancellationSignal)

    override fun query(
        uri: Uri, projection: Array<out String>?, queryArgs: Bundle?, cancellationSignal: CancellationSignal?,
    ): Cursor = snapshot(uri, cancellationSignal)

    private fun snapshot(uri: Uri, cancellationSignal: CancellationSignal?): Cursor {
        val app = context?.applicationContext as? VayanaApp ?: throw SecurityException("Provider context unavailable")
        if (callingPackage != BatteryWidgetPackage || Build.VERSION.SDK_INT < Build.VERSION_CODES.P ||
            !app.packageManager.hasSigningCertificate(
                BatteryWidgetPackage, BatteryWidgetCertificate, PackageManager.CERT_INPUT_SHA256,
            )
        ) {
            throw SecurityException("Caller is not the approved BatteryWidget app")
        }
        require(uri.toString() == SnapshotUri) { "Unknown reading widget URI" }
        cancellationSignal?.throwIfCanceled()
        val result = app.readingWidgetSnapshot()
        cancellationSignal?.throwIfCanceled()
        return MatrixCursor(arrayOf("status", "snapshot_json"), 1).apply {
            addRow(arrayOf(result.status, result.json))
        }
    }

    override fun getType(uri: Uri): String? = unsupported()
    override fun getTypeAnonymous(uri: Uri): String? = unsupported()
    override fun insert(uri: Uri, values: ContentValues?): Uri? = unsupported()
    override fun bulkInsert(uri: Uri, values: Array<out ContentValues>): Int = unsupported()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        unsupported()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = unsupported()
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? = unsupported()
    override fun applyBatch(operations: ArrayList<ContentProviderOperation>): Array<ContentProviderResult> = unsupported()
    override fun canonicalize(uri: Uri): Uri? = unsupported()
    override fun uncanonicalize(uri: Uri): Uri? = unsupported()
    override fun refresh(uri: Uri, extras: Bundle?, cancellationSignal: CancellationSignal?): Boolean = unsupported()

    private fun unsupported(): Nothing = throw UnsupportedOperationException("Only the reading snapshot query is supported")
}

internal const val BatteryWidgetPackage = "com.rj.batterywidget"
private const val SnapshotUri = "content://com.vayana.app.readingwidget/v1/snapshot"
// apksigner verify --print-certs on the BatteryWidget app APK.
private val BatteryWidgetCertificate = "111105bec3ca2b9dea3e6fc5adf2cf4481f7e426e0dcdacf0ce333fe3b5cb9cf"
    .chunked(2).map { it.toInt(16).toByte() }.toByteArray()
