package com.vayana.core.resources

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/**
 * Text for the reader, decided outside the UI (view models, backup, sync) but only turned into words where it is
 * shown, so it follows the app's language. [Res] and [Plural] are string resources; their arguments may themselves be
 * [UiText]. [Raw] is for text that has no translation: a server's or the system's own message, or a user's input.
 */
sealed interface UiText {
    data class Res(@param:StringRes val id: Int, val args: List<Any> = emptyList()) : UiText {
        constructor(@StringRes id: Int, vararg args: Any) : this(id, args.toList())
    }

    data class Plural(@param:PluralsRes val id: Int, val count: Int, val args: List<Any> = listOf(count)) : UiText

    data class Raw(val text: String) : UiText

    fun resolve(resources: Resources): String = when (this) {
        is Res -> if (args.isEmpty()) resources.getString(id) else resources.getString(id, *args.resolved(resources))
        is Plural -> resources.getQuantityString(id, count, *args.resolved(resources))
        is Raw -> text
    }
}

private fun List<Any>.resolved(resources: Resources): Array<Any> =
    Array(size) { index ->
        val argument = this[index]
        if (argument is UiText) argument.resolve(resources) else argument
    }

/**
 * A failure whose message is a string resource, for code that reports errors by throwing; [uiText] shows it
 * translated. Its plain [message], for logs, only names the resource.
 */
class LocalizedException(val text: UiText, cause: Throwable? = null) : IllegalStateException(text.toString(), cause) {
    constructor(@StringRes id: Int, vararg args: Any) : this(UiText.Res(id, args.toList()))
}

/** Like `check`, but the failure's message is the string resource [message] (formatted with [args]). */
fun failUnless(condition: Boolean, @StringRes message: Int, vararg args: Any) {
    if (!condition) throw LocalizedException(UiText.Res(message, args.toList()))
}

/**
 * What to show for [this] failure: a [LocalizedException]'s own text, else the throwable's message (from the system
 * or a server, so not translatable), else [fallback].
 */
fun Throwable.uiText(@StringRes fallback: Int): UiText =
    (this as? LocalizedException)?.text
        ?: message?.takeIf { it.isNotBlank() }?.let(UiText::Raw)
        ?: UiText.Res(fallback)
