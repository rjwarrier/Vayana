package com.vayana.feature.reader

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

/**
 * Hands [text] to a translation app: first whatever answers Android's translate intent, then Google Translate
 * directly (its text-selection "Translate", then plain sharing). Nothing is downloaded and no text leaves the phone
 * except to the app the reader already has. False when no translation app is installed.
 */
internal fun Context.translateText(text: String): Boolean {
    val attempts = listOf(
        Intent(ActionTranslate).putExtra(Intent.EXTRA_TEXT, text),
        Intent(Intent.ACTION_PROCESS_TEXT)
            .setType("text/plain")
            .setPackage(GoogleTranslatePackage)
            .putExtra(Intent.EXTRA_PROCESS_TEXT, text)
            .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true),
        Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .setPackage(GoogleTranslatePackage)
            .putExtra(Intent.EXTRA_TEXT, text),
    )
    return attempts.any { intent ->
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}

// Intent.ACTION_TRANSLATE, spelled out: the constant is API 29 and the app runs from 26.
private const val ActionTranslate = "android.intent.action.TRANSLATE"
private const val GoogleTranslatePackage = "com.google.android.apps.translate"
