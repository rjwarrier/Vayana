package com.vayana.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.vayana.core.designsystem.tokens.Sizes

/** One entry of a [VayanaDropdownMenu]. */
class VayanaMenuItem(
    val label: String,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    /** Null for a plain action; true or false for one choice of a pick-one group (a sort order, say). */
    val selected: Boolean? = null,
    /** A small icon after the label, e.g. the direction of the selected sort. */
    val trailingIcon: ImageVector? = null,
    /** Deletes or removes something: drawn in the error colour. */
    val destructive: Boolean = false,
    val enabled: Boolean = true,
)

/** Items that belong together, optionally under a [label] (e.g. "Group by"). */
class VayanaMenuGroup(val items: List<VayanaMenuItem>, val label: String? = null)

/**
 * The app's overflow and action menus, as Material 3 Expressive grouped menus: each group is its own rounded
 * container spaced from the next instead of split by a divider, a group's heading is a real group label rather than
 * a disabled item, and a pick-one group shows its choice in the selected item style. Choosing an item closes the menu
 * before running it. Empty groups are skipped, so callers can build groups from conditions without leaving gaps.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VayanaDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    groups: List<VayanaMenuGroup>,
    modifier: Modifier = Modifier,
) {
    val shown = groups.filter { it.items.isNotEmpty() }
    DropdownMenuPopup(expanded = expanded, onDismissRequest = onDismissRequest, modifier = modifier) {
        shown.forEachIndexed { groupIndex, group ->
            if (groupIndex > 0) Spacer(modifier = Modifier.height(MenuDefaults.GroupSpacing))
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(groupIndex, shown.size)) {
                group.label?.let { label -> MenuDefaults.DropdownMenuGroupLabel { Text(label) } }
                group.items.forEachIndexed { index, item ->
                    VayanaMenuEntry(
                        item = item,
                        index = index,
                        count = group.items.size,
                        onDismissRequest = onDismissRequest,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VayanaMenuEntry(item: VayanaMenuItem, index: Int, count: Int, onDismissRequest: () -> Unit) {
    val shapes = MenuDefaults.itemShape(index, count)
    // This Material 3 release hides its selectable menu item, so the chosen entry of a pick-one group is drawn the
    // way the Expressive spec shows it: on a tertiary container, in the rounder selected shape, led by a check.
    val selected = item.selected == true
    val shape = if (selected) shapes.selectedShape else shapes.shape
    val contentColor = when {
        selected -> MaterialTheme.colorScheme.onTertiaryContainer
        item.destructive -> MaterialTheme.colorScheme.error
        else -> null
    }
    val leadingIcon = (item.icon ?: Icons.Outlined.Check.takeIf { selected })
    DropdownMenuItem(
        onClick = {
            onDismissRequest()
            item.onClick()
        },
        text = { Text(item.label) },
        shape = shape,
        modifier = if (selected) Modifier.background(MaterialTheme.colorScheme.tertiaryContainer, shape) else Modifier,
        leadingIcon = leadingIcon?.let { icon -> { Icon(icon, contentDescription = null) } },
        trailingContent = item.trailingIcon?.let { icon ->
            { Icon(icon, contentDescription = null, modifier = Modifier.size(Sizes.iconSmall)) }
        },
        enabled = item.enabled,
        colors = if (contentColor != null) {
            MenuDefaults.itemColors(
                textColor = contentColor,
                leadingIconColor = contentColor,
                trailingIconColor = contentColor,
            )
        } else {
            MenuDefaults.itemColors()
        },
    )
}
