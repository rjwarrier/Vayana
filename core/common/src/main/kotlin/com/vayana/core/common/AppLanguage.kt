package com.vayana.core.common

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * The language of Vayana's own text, chosen in Settings independently of the phone's. Android 13+ keeps the choice
 * itself (the same one its per-app language settings show); older versions keep it here and every entry point wraps
 * its context with [wrap].
 */
object AppLanguage {
    /** Languages the app is translated into (BCP 47). Keep in step with the `values-*` resource folders. */
    val Supported: List<String> = listOf("en", "es", "pt", "ru", "de", "fr", "it")

    private const val PreferencesName = "app_language"
    private const val TagKey = "language_tag"

    /** The chosen language tag, or null when the app follows the phone. */
    fun current(context: Context): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales
                ?.takeUnless { it.isEmpty }
                ?.get(0)
                ?.language
        } else {
            preferences(context).getString(TagKey, null)
        }

    /** Switches the app to [tag] (null = the phone's language) and redraws [context]'s activity in it. */
    fun set(context: Context, tag: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // The system stores the choice and recreates the app's activities in the new language.
            context.getSystemService(LocaleManager::class.java)?.applicationLocales =
                tag?.let(LocaleList::forLanguageTags) ?: LocaleList.getEmptyLocaleList()
            return
        }
        preferences(context).edit().apply {
            if (tag == null) remove(TagKey) else putString(TagKey, tag)
        }.commit()
        context.findActivity()?.recreate()
    }

    /**
     * Before Android 13: [base] with the chosen language applied, for `attachBaseContext` of the application, the
     * activity and services. Returns [base] unchanged when the app follows the phone or the system handles it.
     */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = preferences(base).getString(TagKey, null) ?: return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(configuration)
    }

    /** [tag]'s name in that language itself ("Español"), so each option is readable to the person who needs it. */
    fun displayName(tag: String): String {
        val locale = Locale.forLanguageTag(tag)
        return locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
    }

    private fun preferences(context: Context) =
        // Not applicationContext: during Application.attachBaseContext there is none yet.
        context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
