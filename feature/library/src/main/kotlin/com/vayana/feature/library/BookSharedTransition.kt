package com.vayana.feature.library

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.vayana.core.designsystem.theme.isMotionEnabled

/** The Books-screen element that opens Book Details. */
enum class BookOpenTransitionSource {
    HERO_CARD,
    COVER,
    READ_NEXT_COVER,
}

private data class BookSharedTransitionKey(
    val bookId: Long,
    val source: BookOpenTransitionSource,
)

private val LocalBookSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
private val LocalBookAnimatedVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/** Supplies Navigation Compose's shared-transition scopes to the library feature. */
@Composable
fun ProvideBookSharedTransitionScopes(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalBookSharedTransitionScope provides sharedTransitionScope,
        LocalBookAnimatedVisibilityScope provides animatedVisibilityScope,
        content = content,
    )
}

/** A book cover that remains visually continuous while Book Details opens or closes. */
@Composable
internal fun Modifier.bookSharedElement(
    bookId: Long,
    source: BookOpenTransitionSource,
    enabled: Boolean = true,
): Modifier {
    if (!enabled || !isMotionEnabled()) return this
    val sharedTransitionScope = LocalBookSharedTransitionScope.current ?: return this
    val animatedVisibilityScope = LocalBookAnimatedVisibilityScope.current ?: return this
    return with(sharedTransitionScope) {
        sharedElement(
            sharedContentState = rememberSharedContentState(BookSharedTransitionKey(bookId, source)),
            animatedVisibilityScope = animatedVisibilityScope,
        )
    }
}

/** Container transform used when the larger Currently Reading card is the source. */
@Composable
internal fun Modifier.bookSharedBounds(
    bookId: Long,
    enabled: Boolean = true,
): Modifier {
    if (!enabled || !isMotionEnabled()) return this
    val sharedTransitionScope = LocalBookSharedTransitionScope.current ?: return this
    val animatedVisibilityScope = LocalBookAnimatedVisibilityScope.current ?: return this
    return with(sharedTransitionScope) {
        sharedBounds(
            sharedContentState = rememberSharedContentState(
                BookSharedTransitionKey(bookId, BookOpenTransitionSource.HERO_CARD),
            ),
            animatedVisibilityScope = animatedVisibilityScope,
        )
    }
}
