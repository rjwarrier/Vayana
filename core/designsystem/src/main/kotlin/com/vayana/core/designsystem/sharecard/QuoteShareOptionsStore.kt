package com.vayana.core.designsystem.sharecard

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * The quote editor's last look, kept on this device so the next quote opens the way the reader left the last one.
 * Plain preferences, not the app's synced settings: it's a per-device styling choice (like the seal itself).
 * Unknown or missing values fall back to the defaults, so options added later just start at theirs.
 */
internal object QuoteShareOptionsStore {
    fun load(context: Context): QuoteShareOptions {
        val p = prefs(context)
        val d = QuoteShareOptions()
        return QuoteShareOptions(
            layout = p.enum("layout", d.layout),
            theme = p.enum("theme", d.theme),
            palette = p.enum("palette", d.palette),
            shape = p.enum("shape", d.shape),
            font = p.enum("font", d.font),
            titleFont = p.enum("titleFont", d.titleFont),
            weight = p.enum("weight", d.weight),
            italic = p.getBoolean("italic", d.italic),
            align = p.enum("align", d.align),
            backdropOpacity = p.getFloat("backdropOpacity", d.backdropOpacity),
            detailsGap = p.getFloat("detailsGap", d.detailsGap),
            showDetailsDivider = p.getBoolean("showDetailsDivider", d.showDetailsDivider),
            showCover = p.getBoolean("showCover", d.showCover),
            showQuoteMark = p.getBoolean("showQuoteMark", d.showQuoteMark),
            showBookTitle = p.getBoolean("showBookTitle", d.showBookTitle),
            showAuthor = p.getBoolean("showAuthor", d.showAuthor),
            showSeries = p.getBoolean("showSeries", d.showSeries),
            showCaption = p.getBoolean("showCaption", d.showCaption),
            showTagline = p.getBoolean("showTagline", d.showTagline),
            showSeal = p.getBoolean("showSeal", d.showSeal),
            showProfileName = p.getBoolean("showProfileName", d.showProfileName),
            sealScale = p.getFloat("sealScale", d.sealScale),
            quoteFontScale = p.getFloat("quoteFontScale", d.quoteFontScale),
        )
    }

    fun save(context: Context, o: QuoteShareOptions) {
        prefs(context).edit {
            putString("layout", o.layout.name)
            putString("theme", o.theme.name)
            putString("palette", o.palette.name)
            putString("shape", o.shape.name)
            putString("font", o.font.name)
            putString("titleFont", o.titleFont.name)
            putString("weight", o.weight.name)
            putBoolean("italic", o.italic)
            putString("align", o.align.name)
            putFloat("backdropOpacity", o.backdropOpacity)
            putFloat("detailsGap", o.detailsGap)
            putBoolean("showDetailsDivider", o.showDetailsDivider)
            putBoolean("showCover", o.showCover)
            putBoolean("showQuoteMark", o.showQuoteMark)
            putBoolean("showBookTitle", o.showBookTitle)
            putBoolean("showAuthor", o.showAuthor)
            putBoolean("showSeries", o.showSeries)
            putBoolean("showCaption", o.showCaption)
            putBoolean("showTagline", o.showTagline)
            putBoolean("showSeal", o.showSeal)
            putBoolean("showProfileName", o.showProfileName)
            putFloat("sealScale", o.sealScale)
            putFloat("quoteFontScale", o.quoteFontScale)
        }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

    private inline fun <reified E : Enum<E>> SharedPreferences.enum(key: String, default: E): E =
        getString(key, null)?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

    private const val PrefsName = "quote_share_options"
}
