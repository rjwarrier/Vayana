package com.vayana.core.homelibrary

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

/** Hands a mirrored book over to Home Library, which owns it. */
object HomeLibraryLauncher {
    /** True when Home Library took the intent; false when it isn't installed or can't show the book. */
    fun showBook(context: Context, syncUuid: String): Boolean {
        val intent = Intent(HomeLibraryContract.ACTION_SHOW_BOOK)
            .setPackage(HomeLibraryContract.PACKAGE)
            .putExtra(HomeLibraryContract.EXTRA_SYNC_UUID, syncUuid)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
