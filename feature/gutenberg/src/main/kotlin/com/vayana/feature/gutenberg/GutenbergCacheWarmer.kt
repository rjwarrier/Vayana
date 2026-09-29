package com.vayana.feature.gutenberg

import android.util.Log
import com.vayana.core.common.ApplicationScope
import com.vayana.core.common.runCatchingCancellable
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Warms the first Gutenberg catalogue page after a real app launch has settled. The client owns freshness and disk
 * caching; this class only owns launch timing. One request at most is scheduled per process, with no page or cover
 * crawling.
 */
@Singleton
class GutenbergCacheWarmer @Inject constructor(
    private val client: GutenbergClient,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) {
    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        applicationScope.launch {
            delay(WarmupDelayMillis)
            runCatchingCancellable { client.warmDefaultListing() }
                .onFailure { error -> Log.d(Tag, "Background catalogue warm-up skipped", error) }
        }
    }

    private companion object {
        const val Tag = "Gutenberg"
        const val WarmupDelayMillis = 15_000L
    }
}
