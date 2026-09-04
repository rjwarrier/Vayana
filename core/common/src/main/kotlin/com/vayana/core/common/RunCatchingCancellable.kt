package com.vayana.core.common

import kotlinx.coroutines.CancellationException

/**
 * Like [runCatching], but never swallows [CancellationException]. A plain `runCatching` around a
 * suspend call turns "this coroutine was cancelled" into an ordinary failure value instead of
 * letting the cancellation propagate - which breaks structured concurrency and can leave work
 * running (or state updated) after the caller already gave up on it.
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        Result.failure(throwable)
    }
