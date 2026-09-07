package com.vayana.feature.settings

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SyncProblem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import com.vayana.core.designsystem.tokens.Elevations
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.diagnostics.DiagnosticCategory
import com.vayana.core.diagnostics.DiagnosticEvent
import com.vayana.core.resources.R
import java.text.DateFormat
import java.util.Date

@Composable
fun DiagnosticsRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: DiagnosticsViewModel = hiltViewModel()
    val events by viewModel.events.collectAsState()

    DiagnosticsScreen(
        modifier = modifier,
        events = events,
        onBack = onBack,
        onClear = viewModel::clear,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiagnosticsScreen(
    modifier: Modifier = Modifier,
    events: List<DiagnosticEvent>,
    onBack: () -> Unit,
    onClear: () -> Unit,
) {
    val context = LocalContext.current
    var confirmingClear by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diagnostics_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.notes_back_content_description),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val shareText = events.joinToString("\n\n") { it.toShareText() }
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(intent, null))
                        },
                        enabled = events.isNotEmpty(),
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.diagnostics_share_content_description))
                    }
                    IconButton(onClick = { confirmingClear = true }, enabled = events.isNotEmpty()) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.diagnostics_clear_content_description))
                    }
                },
            )
        },
    ) { innerPadding ->
        if (events.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.diagnostics_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Paddings.screenHorizontal,
                    end = Paddings.screenHorizontal,
                    top = innerPadding.calculateTopPadding() + Spacing.sm,
                    bottom = innerPadding.calculateBottomPadding() + Spacing.xl,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(events, key = { it.id }) { event -> DiagnosticEventCard(event) }
            }
        }
    }

    if (confirmingClear) {
        AlertDialog(
            onDismissRequest = { confirmingClear = false },
            title = { Text(stringResource(R.string.diagnostics_clear_title)) },
            text = { Text(stringResource(R.string.diagnostics_clear_body)) },
            confirmButton = {
                Button(onClick = { confirmingClear = false; onClear() }) {
                    Text(stringResource(R.string.diagnostics_clear_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingClear = false }) {
                    Text(stringResource(R.string.diagnostics_clear_cancel))
                }
            },
        )
    }
}

@Composable
private fun DiagnosticEventCard(event: DiagnosticEvent) {
    var expanded by remember { mutableStateOf(false) }
    val isCrash = event.category == DiagnosticCategory.CRASH

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = event.detail != null) { expanded = !expanded },
        shape = RoundedCornerShape(Radii.large),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = Elevations.shadowSmall,
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isCrash) Icons.Outlined.BugReport else Icons.Outlined.SyncProblem,
                    contentDescription = null,
                    tint = if (isCrash) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                )
                Spacer(modifier = Modifier.width(Spacing.xs))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = event.source,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = event.timestamp.formatDiagnosticTimestamp(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = event.message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Spacing.xs),
            )
            val detail = event.detail
            if (expanded && detail != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.sm),
                    shape = RoundedCornerShape(Radii.medium),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier.padding(Spacing.sm),
                    )
                }
            }
        }
    }
}

private fun DiagnosticEvent.toShareText(): String = buildString {
    append(category.name).append(" · ").append(timestamp.formatDiagnosticTimestamp()).append('\n')
    append(source).append(": ").append(message)
    detail?.let { append('\n').append(it) }
}

private fun Long.formatDiagnosticTimestamp(): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).format(Date(this))
