package com.vayana.core.designsystem.theme

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Extra clearance for content and floating buttons when phone navigation overlays the screen. */
val LocalFloatingNavigationInset = staticCompositionLocalOf { 0.dp }

@Composable
fun VayanaSnackbarHost(hostState: SnackbarHostState) {
    SnackbarHost(
        hostState = hostState,
        modifier = Modifier.padding(bottom = LocalFloatingNavigationInset.current),
    )
}
