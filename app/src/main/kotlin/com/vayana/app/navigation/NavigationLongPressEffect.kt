package com.vayana.app.navigation

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalViewConfiguration
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Adds a long-press action without replacing a Material component's normal click semantics. */
@Composable
internal fun NavigationLongPressEffect(
    interactionSource: MutableInteractionSource,
    onLongPress: () -> Unit,
) {
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val timeoutMillis = LocalViewConfiguration.current.longPressTimeoutMillis

    LaunchedEffect(interactionSource, timeoutMillis) {
        var pendingLongPress: Job? = null
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    pendingLongPress?.cancel()
                    pendingLongPress = launch {
                        delay(timeoutMillis)
                        currentOnLongPress()
                    }
                }
                is PressInteraction.Release,
                is PressInteraction.Cancel,
                -> {
                    pendingLongPress?.cancel()
                    pendingLongPress = null
                }
            }
        }
    }
}
