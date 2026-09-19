package com.vayana.feature.reader

/**
 * Decides whether the reader may rebuild its engine after the WebView's renderer died. A book that kills the renderer
 * every time it is opened would otherwise loop forever, so only [maxRestarts] restarts are allowed within [windowMillis].
 */
internal class RendererRestartPolicy(
    private val maxRestarts: Int = MaxRendererRestarts,
    private val windowMillis: Long = RendererRestartWindowMillis,
) {
    private val restarts = ArrayDeque<Long>()

    /** Records a crash at [nowMillis]; true if the engine may be rebuilt, false once the budget is spent. */
    fun allowRestart(nowMillis: Long): Boolean {
        while (restarts.isNotEmpty() && nowMillis - restarts.first() > windowMillis) restarts.removeFirst()
        if (restarts.size >= maxRestarts) return false
        restarts.addLast(nowMillis)
        return true
    }
}

private const val MaxRendererRestarts = 3
private const val RendererRestartWindowMillis = 2 * 60 * 1000L
