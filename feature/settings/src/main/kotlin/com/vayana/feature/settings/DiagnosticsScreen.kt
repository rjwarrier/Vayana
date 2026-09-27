package com.vayana.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SyncProblem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.vayana.core.designsystem.component.VayanaDropdownMenu
import com.vayana.core.designsystem.component.VayanaMenuGroup
import com.vayana.core.designsystem.component.VayanaMenuItem
import com.vayana.core.designsystem.theme.PagedLazyColumn
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.designsystem.tokens.Strokes
import com.vayana.core.diagnostics.DiagnosticCategory
import com.vayana.core.diagnostics.DiagnosticEvent
import com.vayana.core.diagnostics.DiagnosticsEnvironment
import com.vayana.core.diagnostics.buildDiagnosticsReport
import com.vayana.core.resources.R
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
fun DiagnosticsRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: DiagnosticsViewModel = hiltViewModel()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val environment = remember(context) { context.diagnosticsEnvironment() }
    val report = remember(environment, events) { buildDiagnosticsReport(environment, events) }

    DiagnosticsScreen(
        modifier = modifier,
        events = events,
        onBack = onBack,
        onClear = viewModel::clear,
        onCopyReport = { context.copyDiagnosticsReport(report) },
        onShareReport = { context.shareDiagnosticsReport(report) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiagnosticsScreen(
    modifier: Modifier = Modifier,
    events: List<DiagnosticEvent>,
    onBack: () -> Unit,
    onClear: () -> Unit,
    onCopyReport: () -> Unit,
    onShareReport: () -> Unit,
) {
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
                    if (events.isNotEmpty()) {
                        var menuExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.MoreVert,
                                    contentDescription = stringResource(R.string.diagnostics_more_options),
                                )
                            }
                            VayanaDropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                                groups = listOf(
                                    VayanaMenuGroup(
                                        listOf(
                                            VayanaMenuItem(
                                                label = stringResource(R.string.diagnostics_clear_all),
                                                icon = Icons.Outlined.DeleteOutline,
                                                destructive = true,
                                                onClick = { confirmingClear = true },
                                            ),
                                        ),
                                    ),
                                ),
                            )
                        }
                    }
                },
                // The app shell already pads for the status bar, as on Settings and Help.
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
    ) { innerPadding ->
        PagedLazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Paddings.screenHorizontal,
                end = Paddings.screenHorizontal,
                top = innerPadding.calculateTopPadding() + Spacing.sm,
                bottom = innerPadding.calculateBottomPadding() + Spacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item {
                SettingsContentContainer {
                    DiagnosticsShareCard(
                        events = events,
                        onCopyReport = onCopyReport,
                        onShareReport = onShareReport,
                    )
                }
            }
            item {
                SettingsContentContainer {
                    DiagnosticsSectionHeader()
                }
            }
            if (events.isEmpty()) {
                item { SettingsContentContainer { DiagnosticsEmptyCard() } }
            } else {
                items(events, key = { it.id }) { event ->
                    SettingsContentContainer { DiagnosticEventCard(event) }
                }
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
private fun DiagnosticsShareCard(
    events: List<DiagnosticEvent>,
    onCopyReport: () -> Unit,
    onShareReport: () -> Unit,
) {
    val crashCount = events.count { it.category == DiagnosticCategory.CRASH }
    val syncCount = events.count { it.category == DiagnosticCategory.SYNC }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.large),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(Strokes.outline, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier.padding(Paddings.card),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Surface(
                    modifier = Modifier.size(Sizes.touchTarget),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.BugReport,
                            contentDescription = null,
                            modifier = Modifier.size(Sizes.iconMedium),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.diagnostics_share_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.diagnostics_share_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                DiagnosticCountPill(
                    text = pluralStringResource(R.plurals.diagnostics_crash_count, crashCount, crashCount),
                    icon = Icons.Outlined.BugReport,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
                DiagnosticCountPill(
                    text = pluralStringResource(R.plurals.diagnostics_sync_count, syncCount, syncCount),
                    icon = Icons.Outlined.SyncProblem,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }

            Button(onClick = onShareReport, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(Sizes.iconSmall))
                Spacer(modifier = Modifier.width(Spacing.sm))
                Text(stringResource(R.string.diagnostics_share_with_developer))
            }
            TextButton(onClick = onCopyReport, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(Sizes.iconSmall))
                Spacer(modifier = Modifier.width(Spacing.sm))
                Text(stringResource(R.string.diagnostics_copy_report))
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Sizes.iconSmall),
                )
                Text(
                    text = stringResource(R.string.diagnostics_privacy_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DiagnosticCountPill(
    text: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(Radii.full),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(Sizes.iconSmall))
            Text(text = text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun DiagnosticsSectionHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.diagnostics_recent_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.diagnostics_recent_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DiagnosticsEmptyCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.medium),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(Strokes.outline, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                modifier = Modifier.size(Sizes.touchTarget),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(Sizes.iconMedium),
                    )
                }
            }
            Spacer(modifier = Modifier.height(Spacing.md))
            Text(
                text = stringResource(R.string.diagnostics_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.diagnostics_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DiagnosticEventCard(event: DiagnosticEvent) {
    var expanded by remember { mutableStateOf(false) }
    val isCrash = event.category == DiagnosticCategory.CRASH
    val eventContainer = if (isCrash) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.tertiaryContainer
    }
    val eventContent = if (isCrash) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onTertiaryContainer
    }
    val expansionModifier = if (event.detail != null) {
        Modifier.clickable { expanded = !expanded }
    } else {
        Modifier
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(expansionModifier),
        shape = RoundedCornerShape(Radii.medium),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(Strokes.outline, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier.padding(Paddings.card),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Surface(
                    modifier = Modifier.size(Sizes.badge),
                    shape = CircleShape,
                    color = eventContainer,
                    contentColor = eventContent,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isCrash) Icons.Outlined.BugReport else Icons.Outlined.SyncProblem,
                            contentDescription = null,
                            modifier = Modifier.size(Sizes.iconMedium),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            if (isCrash) R.string.diagnostics_crash_label else R.string.diagnostics_sync_issue_label,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = event.timestamp.formatDiagnosticTimestamp(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (event.detail != null) {
                    Icon(
                        imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = stringResource(
                            if (expanded) R.string.diagnostics_hide_details else R.string.diagnostics_show_details,
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = event.message,
                style = MaterialTheme.typography.bodyMedium,
            )
            Surface(
                shape = RoundedCornerShape(Radii.full),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Text(
                    text = stringResource(R.string.diagnostics_source_value, event.source),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        horizontal = Paddings.badgeHorizontal,
                        vertical = Paddings.badgeVertical,
                    ),
                )
            }
            val detail = event.detail
            if (expanded && detail != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                Text(
                    text = stringResource(R.string.diagnostics_technical_details),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
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

@Suppress("DEPRECATION")
private fun Context.diagnosticsEnvironment(): DiagnosticsEnvironment {
    val packageInfo = packageManager.getPackageInfo(packageName, 0)
    val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo.longVersionCode
    } else {
        packageInfo.versionCode.toLong()
    }
    return DiagnosticsEnvironment(
        appVersionName = packageInfo.versionName.orEmpty(),
        appVersionCode = versionCode,
        androidVersion = Build.VERSION.RELEASE,
        sdkInt = Build.VERSION.SDK_INT,
        manufacturer = Build.MANUFACTURER,
        model = Build.MODEL,
    )
}

private fun Context.copyDiagnosticsReport(report: String) {
    val clipboard = getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.diagnostics_title), report))
    Toast.makeText(this, R.string.diagnostics_report_copied, Toast.LENGTH_SHORT).show()
}

private fun Context.shareDiagnosticsReport(report: String) {
    runCatching {
        val directory = File(filesDir, DiagnosticsShareDirectory).apply { mkdirs() }
        val reportFile = File(directory, DiagnosticsReportFileName).apply { writeText(report) }
        val reportUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", reportFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.diagnostics_share_subject))
            putExtra(Intent.EXTRA_TEXT, getString(R.string.diagnostics_share_message))
            putExtra(Intent.EXTRA_STREAM, reportUri)
            clipData = ClipData.newRawUri(getString(R.string.diagnostics_title), reportUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.diagnostics_share_with_developer)))
    }.onFailure {
        Toast.makeText(this, R.string.diagnostics_share_failed, Toast.LENGTH_LONG).show()
    }
}

private fun Long.formatDiagnosticTimestamp(): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).format(Date(this))

private const val DiagnosticsShareDirectory = "shared_files"
private const val DiagnosticsReportFileName = "vayana-diagnostics.txt"
