package com.vayana.core.designsystem.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.vayana.core.designsystem.theme.DisplayProfile
import com.vayana.core.designsystem.theme.LocalDisplayProfile
import com.vayana.core.designsystem.theme.VayanaCircularProgressIndicator
import com.vayana.core.designsystem.theme.vayanaSpring
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.designsystem.tokens.Strokes

/**
 * M3 Expressive's shape-morphing loading indicator. On E-Ink, where it would animate forever, the static ring of
 * [VayanaCircularProgressIndicator] instead.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VayanaLoadingIndicator(modifier: Modifier = Modifier) {
    if (LocalDisplayProfile.current == DisplayProfile.E_INK) {
        VayanaCircularProgressIndicator(modifier = modifier)
    } else {
        LoadingIndicator(modifier = modifier)
    }
}

/** A pill that squares off while pressed: M3 Expressive's press shape morph for buttons. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun morphingButtonShapes(): ButtonShapes = ButtonShapes(shape = ButtonDefaults.shape, pressedShape = ButtonDefaults.pressedShape)

/** The same press morph for icon buttons: round at rest, a rounded square while pressed. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun morphingIconButtonShapes(): IconButtonShapes = IconButtonDefaults.shapes()

/** One button of a [VayanaConnectedButtonGroup]. */
@Immutable
data class ConnectedButton(val label: String, val icon: ImageVector?, val onClick: () -> Unit)

/**
 * An M3 Expressive connected button group: equal-width buttons a hair apart, the group's two ends fully round and the
 * corners between buttons tight. Pressing a button rounds its inner corners, on a spring (a snap on E-Ink, where each
 * button is outlined instead of filled). [iconAboveLabel] suits a row of three or four short actions.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VayanaConnectedButtonGroup(
    buttons: List<ConnectedButton>,
    modifier: Modifier = Modifier,
    iconAboveLabel: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = contentColorFor(containerColor),
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        buttons.forEachIndexed { index, button ->
            ConnectedButtonItem(
                button = button,
                leading = index == 0,
                trailing = index == buttons.lastIndex,
                iconAboveLabel = iconAboveLabel,
                containerColor = containerColor,
                contentColor = contentColor,
            )
        }
    }
}

@Composable
private fun RowScope.ConnectedButtonItem(
    button: ConnectedButton,
    leading: Boolean,
    trailing: Boolean,
    iconAboveLabel: Boolean,
    containerColor: Color,
    contentColor: Color,
) {
    val eink = LocalDisplayProfile.current == DisplayProfile.E_INK
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val innerRadius by animateDpAsState(
        targetValue = if (pressed) Radii.extraLarge else Radii.small,
        animationSpec = vayanaSpring(),
        label = "connectedButtonInnerCorner",
    )
    val outer = CornerSize(percent = FullyRoundPercent)
    val inner = CornerSize(innerRadius)
    val shape = RoundedCornerShape(
        topStart = if (leading) outer else inner,
        bottomStart = if (leading) outer else inner,
        topEnd = if (trailing) outer else inner,
        bottomEnd = if (trailing) outer else inner,
    )
    Surface(
        onClick = button.onClick,
        modifier = Modifier.weight(1f).heightIn(min = if (eink) Sizes.touchTargetEink else Sizes.touchTarget),
        shape = shape,
        color = if (eink) MaterialTheme.colorScheme.surface else containerColor,
        contentColor = if (eink) MaterialTheme.colorScheme.onSurface else contentColor,
        border = if (eink) BorderStroke(Strokes.hairlineEink, MaterialTheme.colorScheme.outline) else null,
        interactionSource = interactionSource,
    ) {
        if (iconAboveLabel) {
            Column(
                modifier = Modifier.padding(vertical = Spacing.sm, horizontal = Spacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                button.icon?.let { Icon(imageVector = it, contentDescription = null, modifier = Modifier.size(Sizes.iconMedium)) }
                Text(
                    text = button.label,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Row(
                modifier = Modifier.padding(vertical = Spacing.sm, horizontal = Spacing.md),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                button.icon?.let {
                    Icon(imageVector = it, contentDescription = null, modifier = Modifier.size(Sizes.iconSmall))
                    Spacer(modifier = Modifier.width(Spacing.sm))
                }
                Text(
                    text = button.label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * A colour swatch button: a circle that morphs to a rounded square while pressed, the swatch equivalent of the
 * Expressive press shape. On E-Ink the swatch is outlined so pale colours still read as buttons.
 */
@Composable
fun VayanaSwatchButton(color: Color, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val eink = LocalDisplayProfile.current == DisplayProfile.E_INK
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val cornerPercent by animateIntAsState(
        targetValue = if (pressed) PressedSwatchCornerPercent else FullyRoundPercent,
        animationSpec = vayanaSpring(),
        label = "swatchCorner",
    )
    Box(
        modifier = modifier.size(if (eink) Sizes.touchTargetEink else Sizes.touchTarget),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(Sizes.iconLarge).semantics { contentDescription = label },
            shape = RoundedCornerShape(percent = cornerPercent),
            color = color,
            border = if (eink) BorderStroke(Strokes.hairlineEink, MaterialTheme.colorScheme.outline) else null,
            interactionSource = interactionSource,
        ) {}
    }
}

private const val FullyRoundPercent = 50
private const val PressedSwatchCornerPercent = 30
