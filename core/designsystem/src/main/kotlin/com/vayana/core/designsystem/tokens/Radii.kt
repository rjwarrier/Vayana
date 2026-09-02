package com.vayana.core.designsystem.tokens

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * M3 Expressive shape scale with contrast, not uniform roundness (PROMPT1uidesign.md §1):
 * 0 / 4 / 8 / 12 / 16 / 20 / 28 / 32 / 48 / full. Handoff README pins cards at 12dp,
 * buttons/chips at 8dp, bottom sheets/dialogs at 16–24dp (we use the 32dp increased-XL step
 * for sheets, per PROMPT1's "sheets use the 32dp increased extra-large corner").
 */
object Radii {
    val none = 0.dp
    val extraSmall = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp
    val extraLarge = 20.dp
    val largeIncreased = 24.dp
    val extraLargeIncreased = 28.dp
    val sheetTop = 32.dp
    val huge = 48.dp

    val full = 9999.dp

    val cardShape = RoundedCornerShape(medium)
    val buttonShape = RoundedCornerShape(full)
    val chipShape = RoundedCornerShape(full)
    val fabShape = RoundedCornerShape(large)
    val sheetShape = RoundedCornerShape(topStart = sheetTop, topEnd = sheetTop)
    val coverShape = RoundedCornerShape(small)
}
