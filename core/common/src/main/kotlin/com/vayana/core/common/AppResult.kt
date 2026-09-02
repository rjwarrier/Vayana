package com.vayana.core.common

import androidx.annotation.StringRes

/**
 * Replacement for throwing/swallowing exceptions across module boundaries (§0.8).
 * [Error.messageRes] is a resource id, never a literal string, so every failure path
 * stays localizable without the caller doing anything extra.
 */
sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Error(@param:StringRes val messageRes: Int, val cause: Throwable? = null) : AppResult<Nothing>
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(value))
    is AppResult.Error -> this
}

inline fun <T> AppResult<T>.onSuccess(action: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) action(value)
    return this
}

inline fun <T> AppResult<T>.onError(action: (AppResult.Error) -> Unit): AppResult<T> {
    if (this is AppResult.Error) action(this)
    return this
}
