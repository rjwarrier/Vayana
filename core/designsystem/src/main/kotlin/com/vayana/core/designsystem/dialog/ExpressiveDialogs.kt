package com.vayana.core.designsystem.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.Dialog
import com.vayana.core.designsystem.theme.vayanaAnimateContentSize
import com.vayana.core.designsystem.tokens.Elevations
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing

@Composable
fun ExpressiveDialogSurface(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    scrollable: Boolean = false,
    animateContentSize: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .widthIn(max = Sizes.contentMaxWidth),
            shape = RoundedCornerShape(Radii.extraLargeIncreased),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = Elevations.shadowLarge,
        ) {
            val columnModifier = Modifier
                .padding(Spacing.lg)
                .then(if (animateContentSize) Modifier.vayanaAnimateContentSize() else Modifier)
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            Column(
                modifier = columnModifier,
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                content = content,
            )
        }
    }
}

@Composable
fun ExpressiveDialogHeader(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Surface(
            shape = CircleShape,
            color = containerColor,
            contentColor = contentColor,
            tonalElevation = Elevations.level1,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(Spacing.sm),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            supportingText?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun ConfirmActionDialog(
    onDismissRequest: () -> Unit,
    icon: ImageVector,
    title: String,
    body: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    destructive: Boolean = false,
) {
    ExpressiveDialogSurface(onDismissRequest = onDismissRequest) {
        ExpressiveDialogHeader(
            icon = icon,
            title = title,
            supportingText = body,
            containerColor = if (destructive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
            contentColor = if (destructive) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(
                onClick = onDismissRequest,
                shape = Radii.buttonShape,
            ) {
                Text(dismissLabel)
            }
            Button(
                onClick = onConfirm,
                shape = Radii.buttonShape,
                colors = if (destructive) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) {
                Text(confirmLabel)
            }
        }
    }
}
