package com.vayana.feature.settings

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vayana.core.datastore.settings.BooleanSetting
import com.vayana.core.datastore.settings.ChoiceSetting
import com.vayana.core.datastore.settings.FloatSetting
import com.vayana.core.datastore.settings.IntSetting
import com.vayana.core.datastore.settings.Setting
import com.vayana.core.datastore.settings.SettingsGroup
import com.vayana.core.datastore.settings.SettingsRegistry
import com.vayana.core.datastore.settings.SettingsSnapshot
import com.vayana.core.datastore.settings.StringSetting
import com.vayana.core.designsystem.theme.vayanaAnimateContentSize
import com.vayana.core.designsystem.theme.vayanaContentTransform
import com.vayana.core.designsystem.tokens.Elevations
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val SettingsPagePadding = 20.dp
private val SettingsContentMaxWidth = 840.dp
private val SettingsTwoColumnBreakpoint = 680.dp
private val SettingsCategoryBadgeSize = 42.dp

@Composable
fun SettingsRoute(onBack: () -> Unit, onHelpClick: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val settings by viewModel.settings.collectAsState()
    val backupState by viewModel.backupState.collectAsState()
    val restorePreview by viewModel.restorePreview.collectAsState()
    val githubSyncSettingsTransferState by viewModel.githubSyncSettingsTransferState.collectAsState()
    val githubConnectionTestState by viewModel.githubConnectionTestState.collectAsState()

    SettingsScreen(
        modifier = modifier,
        settings = settings,
        backupState = backupState,
        restorePreview = restorePreview,
        githubSyncSettingsTransferState = githubSyncSettingsTransferState,
        githubConnectionTestState = githubConnectionTestState,
        onBack = onBack,
        onHelpClick = onHelpClick,
        onUpdate = viewModel::update,
        onReset = viewModel::reset,
        onResetAll = viewModel::resetAll,
        onCreateBackup = viewModel::createBackup,
        onPickRestoreFile = viewModel::inspectRestoreFile,
        onConfirmRestore = viewModel::restoreBackup,
        onExportGitHubSyncSettings = viewModel::exportGitHubSyncSettings,
        onImportGitHubSyncSettings = viewModel::importGitHubSyncSettings,
        onTestGitHubConnection = viewModel::testGitHubConnection,
        onDismissGitHubSyncSettingsTransferState = viewModel::dismissGitHubSyncSettingsTransferState,
        onDismissGitHubConnectionTestState = viewModel::dismissGitHubConnectionTestState,
        onDismissRestorePreview = viewModel::dismissRestorePreview,
        onDismissBackupState = viewModel::dismissBackupState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    modifier: Modifier = Modifier,
    settings: SettingsSnapshot,
    backupState: BackupUiState,
    restorePreview: RestorePreviewState,
    githubSyncSettingsTransferState: GitHubSyncSettingsTransferState,
    githubConnectionTestState: GitHubConnectionTestState,
    onBack: () -> Unit,
    onHelpClick: () -> Unit,
    onUpdate: (Setting<Any>, Any) -> Unit,
    onReset: (Setting<out Any>) -> Unit,
    onResetAll: () -> Unit,
    onCreateBackup: (Uri) -> Unit,
    onPickRestoreFile: (Uri) -> Unit,
    onConfirmRestore: (Uri) -> Unit,
    onExportGitHubSyncSettings: (Uri) -> Unit,
    onImportGitHubSyncSettings: (Uri) -> Unit,
    onTestGitHubConnection: () -> Unit,
    onDismissGitHubSyncSettingsTransferState: () -> Unit,
    onDismissGitHubConnectionTestState: () -> Unit,
    onDismissRestorePreview: () -> Unit,
    onDismissBackupState: () -> Unit,
) {
    var showResetAllDialog by remember { mutableStateOf(false) }
    var selectedGroup by remember { mutableStateOf<SettingsGroup?>(null) }
    var query by remember { mutableStateOf("") }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val visibleSettings = remember(query) { SettingsRegistry.all.filterByQuery(query) }

    BackHandler(enabled = selectedGroup != null) { selectedGroup = null }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = selectedGroup?.let { stringResource(it.titleRes) } ?: stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (selectedGroup != null) selectedGroup = null else onBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back_content_description),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onHelpClick) {
                        Icon(
                            imageVector = Icons.Outlined.HelpOutline,
                            contentDescription = stringResource(R.string.settings_help_content_description),
                        )
                    }
                    IconButton(onClick = { showResetAllDialog = true }) {
                        Icon(
                            imageVector = Icons.Outlined.RestartAlt,
                            contentDescription = stringResource(R.string.settings_reset_all_content_description),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        AnimatedContent(
            targetState = selectedGroup,
            transitionSpec = vayanaContentTransform(),
            label = "SettingsNav",
        ) { group ->
            if (group == null) {
                SettingsHub(
                    contentPadding = innerPadding,
                    query = query,
                    visibleSettings = visibleSettings,
                    settings = settings,
                    backupState = backupState,
                    onQueryChange = { query = it },
                    onGroupSelected = { selectedGroup = it },
                    onUpdate = onUpdate,
                    onReset = onReset,
                    onCreateBackup = onCreateBackup,
                    onPickRestoreFile = onPickRestoreFile,
                    onDismissBackupState = onDismissBackupState,
                )
            } else {
                SettingsGroupDetail(
                    contentPadding = innerPadding,
                    group = group,
                    settings = settings,
                    githubSyncSettingsTransferState = githubSyncSettingsTransferState,
                    githubConnectionTestState = githubConnectionTestState,
                    onUpdate = onUpdate,
                    onReset = onReset,
                    onExportGitHubSyncSettings = onExportGitHubSyncSettings,
                    onImportGitHubSyncSettings = onImportGitHubSyncSettings,
                    onTestGitHubConnection = onTestGitHubConnection,
                    onDismissGitHubSyncSettingsTransferState = onDismissGitHubSyncSettingsTransferState,
                    onDismissGitHubConnectionTestState = onDismissGitHubConnectionTestState,
                )
            }
        }
    }

    if (showResetAllDialog) {
        AlertDialog(
            onDismissRequest = { showResetAllDialog = false },
            icon = {
                Surface(
                    shape = RoundedCornerShape(Radii.large),
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.padding(Spacing.md),
                    )
                }
            },
            title = {
                Text(
                    text = stringResource(R.string.settings_reset_all_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.settings_reset_all_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetAllDialog = false
                        onResetAll()
                    },
                    shape = RoundedCornerShape(Radii.full),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text(stringResource(R.string.settings_reset_all_confirm))
                }
            },
            dismissButton = {
                FilledTonalButton(
                    onClick = { showResetAllDialog = false },
                    shape = RoundedCornerShape(Radii.full),
                ) {
                    Text(stringResource(R.string.settings_reset_all_cancel))
                }
            },
            shape = RoundedCornerShape(Radii.extraLargeIncreased),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = Elevations.shadowLarge,
        )
    }

    when (restorePreview) {
        is RestorePreviewState.Loading -> {
            AlertDialog(
                onDismissRequest = onDismissRestorePreview,
                confirmButton = {},
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(Sizes.iconSmall))
                        Text(stringResource(R.string.settings_restore_reading))
                    }
                },
                shape = RoundedCornerShape(Radii.extraLargeIncreased),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = Elevations.shadowLarge,
            )
        }
        is RestorePreviewState.Failed -> {
            AlertDialog(
                onDismissRequest = onDismissRestorePreview,
                icon = {
                    Surface(
                        shape = RoundedCornerShape(Radii.large),
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ) {
                        Icon(imageVector = Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.padding(Spacing.md))
                    }
                },
                title = { Text(stringResource(R.string.settings_restore_confirm_title)) },
                text = { Text(restorePreview.message) },
                confirmButton = {
                    Button(onClick = onDismissRestorePreview, shape = RoundedCornerShape(Radii.full)) {
                        Text(stringResource(R.string.settings_reset_all_cancel))
                    }
                },
                shape = RoundedCornerShape(Radii.extraLargeIncreased),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = Elevations.shadowLarge,
            )
        }
        is RestorePreviewState.Ready -> {
            val inspection = restorePreview.inspection
            AlertDialog(
                onDismissRequest = onDismissRestorePreview,
                icon = {
                    Surface(
                        shape = RoundedCornerShape(Radii.large),
                        color = if (inspection.isCompatible) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        contentColor = if (inspection.isCompatible) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    ) {
                        Icon(imageVector = Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.padding(Spacing.md))
                    }
                },
                title = { Text(stringResource(R.string.settings_restore_confirm_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        if (!inspection.isCompatible) {
                            Text(
                                text = stringResource(R.string.settings_restore_incompatible),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Text(
                            text = stringResource(
                                R.string.settings_restore_summary_created,
                                inspection.manifest.createdAt.formatBackupDate(),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (inspection.manifest.appVersion.isNotBlank()) {
                            Text(
                                text = stringResource(R.string.settings_restore_summary_app_version, inspection.manifest.appVersion),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Text(
                            text = stringResource(R.string.settings_restore_summary_books, inspection.bookCount),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(R.string.settings_restore_summary_notes, inspection.annotationCount),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(R.string.settings_restore_summary_size, inspection.totalBytes.formatByteSize()),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                        Text(
                            text = stringResource(R.string.settings_restore_confirm_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                confirmButton = {
                    if (inspection.isCompatible) {
                        Button(
                            onClick = { onConfirmRestore(restorePreview.uri) },
                            shape = RoundedCornerShape(Radii.full),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        ) {
                            Text(stringResource(R.string.settings_restore_confirm_action))
                        }
                    }
                },
                dismissButton = {
                    FilledTonalButton(onClick = onDismissRestorePreview, shape = RoundedCornerShape(Radii.full)) {
                        Text(stringResource(R.string.settings_reset_all_cancel))
                    }
                },
                shape = RoundedCornerShape(Radii.extraLargeIncreased),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = Elevations.shadowLarge,
            )
        }
        RestorePreviewState.Idle -> Unit
    }
}

private fun Long.formatBackupDate(): String {
    if (this <= 0L) return ""
    return SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(Date(this))
}

private fun Long.formatByteSize(): String {
    if (this < 1024) return "$this B"
    val units = listOf("KB", "MB", "GB")
    var value = this / 1024.0
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return "%.1f %s".format(Locale.getDefault(), value, units[unitIndex])
}

@Composable
private fun SettingsHub(
    contentPadding: PaddingValues,
    query: String,
    visibleSettings: List<Setting<out Any>>,
    settings: SettingsSnapshot,
    backupState: BackupUiState,
    onQueryChange: (String) -> Unit,
    onGroupSelected: (SettingsGroup) -> Unit,
    onUpdate: (Setting<Any>, Any) -> Unit,
    onReset: (Setting<out Any>) -> Unit,
    onCreateBackup: (Uri) -> Unit,
    onPickRestoreFile: (Uri) -> Unit,
    onDismissBackupState: () -> Unit,
) {
    val categoryEntries = SettingsGroup.entries.mapNotNull { group ->
        val settingCount = SettingsRegistry.all.count { it.group == group }
        if (settingCount > 0) group to settingCount else null
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = SettingsPagePadding,
            top = contentPadding.calculateTopPadding() + SettingsPagePadding,
            end = SettingsPagePadding,
            bottom = contentPadding.calculateBottomPadding() + SettingsPagePadding,
        ),
        verticalArrangement = Arrangement.spacedBy(SettingsPagePadding),
    ) {
        item {
            SettingsContentContainer {
                SettingsSearchField(query = query, onQueryChange = onQueryChange)
            }
        }
        if (query.isBlank()) {
            item {
                SettingsContentContainer {
                    BackupRestoreCard(
                        backupState = backupState,
                        onCreateBackup = onCreateBackup,
                        onPickRestoreFile = onPickRestoreFile,
                        onDismissBackupState = onDismissBackupState,
                    )
                }
            }
            item {
                SettingsContentContainer {
                    SettingsCategoryCards(
                        categoryEntries = categoryEntries,
                        onGroupSelected = onGroupSelected,
                    )
                }
            }
        } else if (visibleSettings.isEmpty()) {
            item {
                SettingsContentContainer {
                    SettingsNoMatches()
                }
            }
        } else {
            item {
                SettingsContentContainer {
                    SettingsPanelCard(
                        settingsList = visibleSettings,
                        settings = settings,
                        onUpdate = onUpdate,
                        onReset = onReset,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsGroupDetail(
    contentPadding: PaddingValues,
    group: SettingsGroup?,
    settings: SettingsSnapshot,
    githubSyncSettingsTransferState: GitHubSyncSettingsTransferState,
    githubConnectionTestState: GitHubConnectionTestState,
    onUpdate: (Setting<Any>, Any) -> Unit,
    onReset: (Setting<out Any>) -> Unit,
    onExportGitHubSyncSettings: (Uri) -> Unit,
    onImportGitHubSyncSettings: (Uri) -> Unit,
    onTestGitHubConnection: () -> Unit,
    onDismissGitHubSyncSettingsTransferState: () -> Unit,
    onDismissGitHubConnectionTestState: () -> Unit,
) {
    val groupSettings = SettingsRegistry.all.filter { it.group == group }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = SettingsPagePadding,
            top = contentPadding.calculateTopPadding() + SettingsPagePadding,
            end = SettingsPagePadding,
            bottom = contentPadding.calculateBottomPadding() + SettingsPagePadding,
        ),
        verticalArrangement = Arrangement.spacedBy(SettingsPagePadding),
    ) {
        if (group != null) {
            item {
                SettingsContentContainer {
                    SettingsGroupHeader(group = group, settingCount = groupSettings.size)
                }
            }
        }
        item {
            SettingsContentContainer {
                SettingsPanelCard(
                    settingsList = groupSettings,
                    settings = settings,
                    onUpdate = onUpdate,
                    onReset = onReset,
                )
            }
        }
        if (group == SettingsGroup.SYNC) {
            item {
                SettingsContentContainer {
                    GitHubConnectionTestCard(
                        state = githubConnectionTestState,
                        onTest = onTestGitHubConnection,
                        onDismiss = onDismissGitHubConnectionTestState,
                    )
                }
            }
            item {
                SettingsContentContainer {
                    GitHubSyncSettingsTransferCard(
                        state = githubSyncSettingsTransferState,
                        onExport = onExportGitHubSyncSettings,
                        onImport = onImportGitHubSyncSettings,
                        onDismiss = onDismissGitHubSyncSettingsTransferState,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCategoryCards(
    categoryEntries: List<Pair<SettingsGroup, Int>>,
    onGroupSelected: (SettingsGroup) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= SettingsTwoColumnBreakpoint) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            categoryEntries.chunked(columns).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                ) {
                    row.forEach { (group, settingCount) ->
                        Box(modifier = Modifier.weight(1f)) {
                            SettingsGroupCard(
                                group = group,
                                settingCount = settingCount,
                                onClick = { onGroupSelected(group) },
                            )
                        }
                    }
                    repeat(columns - row.size) {
                        Box(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsContentContainer(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.widthIn(max = SettingsContentMaxWidth)) {
            content()
        }
    }
}

@Composable
private fun GitHubConnectionTestCard(
    state: GitHubConnectionTestState,
    onTest: () -> Unit,
    onDismiss: () -> Unit,
) {
    val working = state is GitHubConnectionTestState.Working
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.large),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = Elevations.none,
    ) {
        Column(
            modifier = Modifier
                .padding(Paddings.card)
                .vayanaAnimateContentSize(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                SettingsIconBubble(icon = Icons.Outlined.Sync, selected = false)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_github_connection_test_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.settings_github_connection_test_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FilledTonalButton(
                onClick = onTest,
                enabled = !working,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (working) {
                    CircularProgressIndicator(modifier = Modifier.size(Sizes.iconSmall))
                } else {
                    Icon(imageVector = Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(Sizes.iconSmall))
                }
                Text(stringResource(R.string.settings_github_connection_test_action), modifier = Modifier.padding(start = Spacing.xs))
            }
            when (state) {
                GitHubConnectionTestState.Working -> Text(
                    text = stringResource(R.string.settings_github_connection_test_working),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                GitHubConnectionTestState.Connected -> BackupStatusRow(
                    message = stringResource(R.string.settings_github_connection_test_connected),
                    isError = false,
                    onDismiss = onDismiss,
                )
                GitHubConnectionTestState.ReadyForInitialSync -> BackupStatusRow(
                    message = stringResource(R.string.settings_github_connection_test_ready_initial),
                    isError = false,
                    onDismiss = onDismiss,
                )
                GitHubConnectionTestState.MissingConfig -> BackupStatusRow(
                    message = stringResource(R.string.settings_github_connection_test_missing_config),
                    isError = true,
                    onDismiss = onDismiss,
                )
                is GitHubConnectionTestState.Failed -> BackupStatusRow(
                    message = stringResource(R.string.settings_github_connection_test_failed, state.message),
                    isError = true,
                    onDismiss = onDismiss,
                )
                GitHubConnectionTestState.Idle -> Unit
            }
        }
    }
}

@Composable
private fun GitHubSyncSettingsTransferCard(
    state: GitHubSyncSettingsTransferState,
    onExport: (Uri) -> Unit,
    onImport: (Uri) -> Unit,
    onDismiss: () -> Unit,
) {
    val exportFileName = remember {
        "vayana-github-sync-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.vayana-ghsync"
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let(onExport)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onImport)
    }
    val working = state is GitHubSyncSettingsTransferState.Working

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.large),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = Elevations.none,
    ) {
        Column(
            modifier = Modifier
                .padding(Paddings.card)
                .vayanaAnimateContentSize(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                SettingsIconBubble(icon = Icons.Outlined.Storage, selected = false)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_github_transfer_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.settings_github_transfer_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                FilledTonalButton(
                    onClick = { exportLauncher.launch(exportFileName) },
                    enabled = !working,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(imageVector = Icons.Outlined.FileUpload, contentDescription = null, modifier = Modifier.size(Sizes.iconSmall))
                    Text(stringResource(R.string.settings_github_transfer_export), modifier = Modifier.padding(start = Spacing.xs))
                }
                FilledTonalButton(
                    onClick = { importLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
                    enabled = !working,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(imageVector = Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(Sizes.iconSmall))
                    Text(stringResource(R.string.settings_github_transfer_import), modifier = Modifier.padding(start = Spacing.xs))
                }
            }
            when (state) {
                GitHubSyncSettingsTransferState.Working -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(Sizes.iconSmall))
                    Text(stringResource(R.string.settings_github_transfer_working), style = MaterialTheme.typography.bodySmall)
                }
                GitHubSyncSettingsTransferState.ExportComplete -> BackupStatusRow(
                    message = stringResource(R.string.settings_github_transfer_export_complete),
                    isError = false,
                    onDismiss = onDismiss,
                )
                GitHubSyncSettingsTransferState.ImportComplete -> BackupStatusRow(
                    message = stringResource(R.string.settings_github_transfer_import_complete),
                    isError = false,
                    onDismiss = onDismiss,
                )
                GitHubSyncSettingsTransferState.MissingPassphrase -> BackupStatusRow(
                    message = stringResource(R.string.settings_github_transfer_missing_passphrase),
                    isError = true,
                    onDismiss = onDismiss,
                )
                is GitHubSyncSettingsTransferState.Failed -> BackupStatusRow(
                    message = stringResource(R.string.settings_github_transfer_failed, state.message),
                    isError = true,
                    onDismiss = onDismiss,
                )
                GitHubSyncSettingsTransferState.Idle -> Unit
            }
        }
    }
}

@Composable
private fun SettingsGroupHeader(group: SettingsGroup, settingCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Surface(
            modifier = Modifier.size(SettingsCategoryBadgeSize),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = group.icon(),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(group.titleRes),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = group.subtitle(settingCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsSearchField(query: String, onQueryChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(Radii.full),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.input_clear_content_description),
                    )
                }
            }
        },
        placeholder = {
            Text(
                text = stringResource(R.string.settings_search_placeholder),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
    )
}

@Composable
private fun BackupRestoreCard(
    backupState: BackupUiState,
    onCreateBackup: (Uri) -> Unit,
    onPickRestoreFile: (Uri) -> Unit,
    onDismissBackupState: () -> Unit,
) {
    val backupFileName = remember {
        "vayana-backup-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.zip"
    }
    val createBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(onCreateBackup)
    }
    val pickRestoreFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onPickRestoreFile)
    }
    val working = backupState is BackupUiState.Working

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.large),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = Elevations.none,
    ) {
        Column(
            modifier = Modifier
                .padding(Paddings.card)
                .vayanaAnimateContentSize(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                SettingsIconBubble(icon = Icons.Outlined.Backup, selected = false)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_backup_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.settings_backup_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                FilledTonalButton(
                    onClick = { createBackupLauncher.launch(backupFileName) },
                    enabled = !working,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(imageVector = Icons.Outlined.Backup, contentDescription = null, modifier = Modifier.size(Sizes.iconSmall))
                    Text(stringResource(R.string.settings_backup_create), modifier = Modifier.padding(start = Spacing.xs))
                }
                FilledTonalButton(
                    onClick = {
                        pickRestoreFileLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                    },
                    enabled = !working,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(imageVector = Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(Sizes.iconSmall))
                    Text(stringResource(R.string.settings_backup_restore), modifier = Modifier.padding(start = Spacing.xs))
                }
            }

            when (backupState) {
                BackupUiState.Working -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(Sizes.iconSmall))
                    Text(stringResource(R.string.settings_backup_working), style = MaterialTheme.typography.bodySmall)
                }
                BackupUiState.BackupComplete -> BackupStatusRow(
                    message = stringResource(R.string.settings_backup_complete),
                    isError = false,
                    onDismiss = onDismissBackupState,
                )
                is BackupUiState.BackupFailed -> BackupStatusRow(
                    message = stringResource(R.string.settings_backup_failed, backupState.message),
                    isError = true,
                    onDismiss = onDismissBackupState,
                )
                is BackupUiState.RestoreFailed -> BackupStatusRow(
                    message = stringResource(R.string.settings_restore_failed, backupState.message),
                    isError = true,
                    onDismiss = onDismissBackupState,
                )
                is BackupUiState.RestoreIncompatible -> BackupStatusRow(
                    message = backupState.message,
                    isError = true,
                    onDismiss = onDismissBackupState,
                )
                BackupUiState.Idle -> Unit
            }
        }
    }
}

@Composable
private fun BackupStatusRow(message: String, isError: Boolean, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.input_clear_content_description),
                modifier = Modifier.size(Sizes.iconSmall),
            )
        }
    }
}

@Composable
private fun SettingsGroupCard(group: SettingsGroup, settingCount: Int, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.large))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(Radii.large),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = Elevations.none,
    ) {
        Row(
            modifier = Modifier.padding(Paddings.card),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Surface(
                modifier = Modifier.size(SettingsCategoryBadgeSize),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = group.icon(),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(group.titleRes),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = group.subtitle(settingCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Surface(
                    shape = RoundedCornerShape(Radii.full),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Text(
                        text = "$settingCount",
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.settings_category_open_content_description),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}

@Composable
private fun SettingsIconBubble(icon: ImageVector, selected: Boolean) {
    Surface(
        modifier = Modifier.size(Sizes.touchTarget),
        shape = RoundedCornerShape(Radii.large),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(Sizes.icon))
        }
    }
}

@Composable
private fun SettingsNoMatches() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.extraLargeIncreased),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = Elevations.shadowSmall,
    ) {
        Column(
            modifier = Modifier.padding(Paddings.card),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Surface(
                shape = RoundedCornerShape(Radii.large),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(Spacing.md)
                        .size(Sizes.iconLarge),
                )
            }
            Text(
                text = stringResource(R.string.settings_no_matches_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.settings_no_matches_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsPanelCard(
    settingsList: List<Setting<out Any>>,
    settings: SettingsSnapshot,
    onUpdate: (Setting<Any>, Any) -> Unit,
    onReset: (Setting<out Any>) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.large),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = Elevations.none,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            settingsList.forEachIndexed { index, setting ->
                SettingRow(
                    setting = setting,
                    value = settings.valueFor(setting),
                    onUpdate = onUpdate,
                    onReset = onReset,
                )
                if (index < settingsList.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = Paddings.card),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingRow(
    setting: Setting<out Any>,
    value: Any,
    onUpdate: (Setting<Any>, Any) -> Unit,
    onReset: (Setting<out Any>) -> Unit,
) {
    val isModified = value != setting.defaultValue

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .vayanaAnimateContentSize(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Paddings.card),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(setting.titleRes),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                setting.subtitleRes?.let { subtitleRes ->
                    Text(
                        text = stringResource(subtitleRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                if (isModified) {
                    IconButton(onClick = { onReset(setting) }) {
                        Icon(
                            imageVector = Icons.Outlined.RestartAlt,
                            contentDescription = stringResource(R.string.settings_reset_one_content_description),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (setting is BooleanSetting) {
                    Switch(
                        checked = value as Boolean,
                        onCheckedChange = { onUpdate(setting.asAny(), it) },
                    )
                }
            }
        }
        when (setting) {
            is BooleanSetting -> Unit
            is IntSetting -> IntSettingControl(setting = setting, value = value as Int, onUpdate = { onUpdate(setting.asAny(), it) })
            is FloatSetting -> FloatSettingControl(setting = setting, value = value as Float, onUpdate = { onUpdate(setting.asAny(), it) })
            is StringSetting -> StringSettingControl(setting = setting, value = value as String, onUpdate = { onUpdate(setting.asAny(), it) })
            is ChoiceSetting<*> -> ChoiceSettingControl(setting = setting, value = value, onUpdate = { onUpdate(setting.asAny(), it) })
        }
    }
}

@Composable
private fun IntSettingControl(setting: IntSetting, value: Int, onUpdate: (Int) -> Unit) {
    val display = setting.intDisplay()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Paddings.card, end = Paddings.card, bottom = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = display(setting.range.first),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                shape = RoundedCornerShape(Radii.full),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Text(
                    text = display(value),
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Text(
                text = display(setting.range.last),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onUpdate((it / setting.step).roundToInt() * setting.step) },
            valueRange = setting.range.first.toFloat()..setting.range.last.toFloat(),
            steps = ((setting.range.last - setting.range.first) / setting.step - 1).coerceAtLeast(0),
        )
    }
}

private fun IntSetting.intDisplay(): (Int) -> String = when (this) {
    SettingsRegistry.ReaderFontSize -> { value -> (value / 100f).formatScale() }
    SettingsRegistry.ReaderSideMargin -> { value -> value.toString() }
    SettingsRegistry.DailyReadingGoalMinutes -> { value -> value.toString() }
    SettingsRegistry.YearlyBooksGoal -> { value -> value.toString() }
    else -> { value -> value.toString() }
}

@Composable
private fun FloatSettingControl(setting: FloatSetting, value: Float, onUpdate: (Float) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Paddings.card, end = Paddings.card, bottom = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = setting.range.start.formatScale(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                shape = RoundedCornerShape(Radii.full),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Text(
                    text = value.formatScale(),
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Text(
                text = setting.range.endInclusive.formatScale(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value,
            onValueChange = { raw -> onUpdate(((raw / setting.step).roundToInt() * setting.step).coerceIn(setting.range.start, setting.range.endInclusive)) },
            valueRange = setting.range,
            steps = (((setting.range.endInclusive - setting.range.start) / setting.step).roundToInt() - 1).coerceAtLeast(0),
        )
    }
}

private fun Float.formatScale(): String {
    val roundedToHundredth = (this * 100).roundToInt() / 100f
    val label = if (roundedToHundredth % 1f == 0f) {
        roundedToHundredth.toInt().toString()
    } else {
        "%.2f".format(Locale.getDefault(), roundedToHundredth).trimEnd('0').trimEnd('.')
    }
    return "${label}x"
}

@Composable
private fun StringSettingControl(setting: StringSetting, value: String, onUpdate: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    var pendingValue by remember(setting.key, value) { mutableStateOf(value) }
    OutlinedTextField(
        value = pendingValue,
        onValueChange = {
            pendingValue = it.take(setting.maxLength)
            onUpdate(pendingValue)
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Paddings.card, end = Paddings.card, bottom = Spacing.md),
        singleLine = true,
        shape = RoundedCornerShape(Radii.large),
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Done,
            keyboardType = if (setting.secure) KeyboardType.Password else KeyboardType.Text,
        ),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        visualTransformation = if (setting.secure) PasswordVisualTransformation() else VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    )
}

@Composable
private fun ChoiceSettingControl(setting: ChoiceSetting<*>, value: Any, onUpdate: (Any) -> Unit) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(start = Paddings.card, end = Paddings.card, bottom = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        setting.options.forEach { option ->
            val selected = option.value == value
            val cornerRadius by animateDpAsState(
                targetValue = if (selected) Radii.full else Radii.small,
                label = "SettingsChoiceChipCorner",
            )
            FilterChip(
                selected = selected,
                onClick = { onUpdate(option.value) },
                label = {
                    Text(
                        text = stringResource(option.labelRes),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        ),
                    )
                },
                leadingIcon = if (selected) {
                    {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(Sizes.iconSmall),
                        )
                    }
                } else {
                    null
                },
                shape = RoundedCornerShape(cornerRadius),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected,
                    borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    selectedBorderColor = MaterialTheme.colorScheme.primary,
                    borderWidth = 1.dp,
                    selectedBorderWidth = 0.dp,
                ),
            )
        }
    }
}

@Suppress("UNCHECKED_CAST")
private fun Setting<out Any>.asAny(): Setting<Any> = this as Setting<Any>

private fun SettingsSnapshot.valueFor(setting: Setting<out Any>): Any = when (setting) {
    SettingsRegistry.ThemeMode -> themeMode
    SettingsRegistry.DisplayProfile -> displayProfile
    SettingsRegistry.DarkVariant -> darkVariant
    SettingsRegistry.Motion -> motionSetting
    SettingsRegistry.ReaderFontSize -> readerFontSizePercent
    SettingsRegistry.ReaderLineHeight -> readerLineHeight
    SettingsRegistry.ReaderFontFamily -> readerFontFamily
    SettingsRegistry.ReaderTheme -> readerTheme
    SettingsRegistry.ReaderSideMargin -> readerSideMarginPercent
    SettingsRegistry.ReaderPublisherStyles -> readerUsePublisherStyles
    SettingsRegistry.ReaderTapZoneMode -> readerTapZoneMode
    SettingsRegistry.ReaderVolumeKeys -> readerVolumeKeys
    SettingsRegistry.ReaderKeepAwake -> readerKeepAwake
    SettingsRegistry.ReaderShowHeaders -> readerShowHeaders
    SettingsRegistry.ReaderShowFooter -> readerShowFooter
    SettingsRegistry.ReaderAutoMarkSelection -> readerAutoMarkSelection
    SettingsRegistry.ReaderBionicReading -> readerBionicReading
    SettingsRegistry.DailyReadingGoalMinutes -> dailyReadingGoalMinutes
    SettingsRegistry.YearlyBooksGoal -> yearlyBooksGoal
    SettingsRegistry.KindleDeviceName -> kindleDeviceName
    SettingsRegistry.GithubSyncEnabled -> githubSyncEnabled
    SettingsRegistry.GithubOwner -> githubOwner
    SettingsRegistry.GithubRepository -> githubRepository
    SettingsRegistry.GithubBranch -> githubBranch
    SettingsRegistry.GithubToken -> githubToken
    SettingsRegistry.GithubSyncPassphrase -> githubSyncPassphrase
    else -> setting.defaultValue
}

private fun List<Setting<out Any>>.filterByQuery(query: String): List<Setting<out Any>> {
    val normalized = query.trim()
    if (normalized.isEmpty()) return this
    return filter { setting ->
        setting.searchTokens().contains(normalized, ignoreCase = true)
    }
}

private fun Setting<out Any>.searchTokens(): String {
    val synonyms = when (this) {
        SettingsRegistry.ThemeMode -> "theme system light dark appearance display"
        SettingsRegistry.DisplayProfile -> "display profile eink e ink contrast screen"
        SettingsRegistry.DarkVariant -> "dark black oled softer night theme"
        SettingsRegistry.Motion -> "motion animation reduce transitions"
        SettingsRegistry.ReaderFontSize -> "reader font size text scale typography"
        SettingsRegistry.ReaderLineHeight -> "reader line height spacing text typography"
        SettingsRegistry.ReaderFontFamily -> "reader font family serif sans mono jetpack typography"
        SettingsRegistry.ReaderTheme -> "reader page theme light sepia dark book"
        SettingsRegistry.ReaderSideMargin -> "reader page margin side layout width"
        SettingsRegistry.ReaderPublisherStyles -> "publisher style css page layout book"
        SettingsRegistry.ReaderTapZoneMode -> "tap zone page turn navigation gestures"
        SettingsRegistry.ReaderVolumeKeys -> "volume keys buttons page turn"
        SettingsRegistry.ReaderKeepAwake -> "keep awake screen sleep reading"
        SettingsRegistry.ReaderShowHeaders -> "reader show hide headers clock session time left"
        SettingsRegistry.ReaderShowFooter -> "reader show hide footer page progress"
        SettingsRegistry.KindleDeviceName -> "kindle device name label sync send to kindle backup transfer"
        SettingsRegistry.GithubSyncEnabled -> "github sync cloud enable repository books notes settings"
        SettingsRegistry.GithubOwner -> "github owner username organization account sync repository"
        SettingsRegistry.GithubRepository -> "github repository repo cloud sync books notes settings"
        SettingsRegistry.GithubBranch -> "github branch main cloud sync repository"
        SettingsRegistry.GithubToken -> "github token personal access token pat credential sync"
        SettingsRegistry.GithubSyncPassphrase -> "github sync passphrase password encryption cloud assets books"
        else -> ""
    }
    return "${key} ${group.name} $synonyms"
}

@Composable
private fun SettingsGroup.icon(): ImageVector = when (this) {
    SettingsGroup.APPEARANCE -> Icons.Outlined.Palette
    SettingsGroup.READER_TYPOGRAPHY -> Icons.Outlined.FormatSize
    SettingsGroup.READER_LAYOUT -> Icons.Outlined.Visibility
    SettingsGroup.READER_BEHAVIOR -> Icons.Outlined.TouchApp
    SettingsGroup.GOALS -> Icons.Outlined.Flag
    SettingsGroup.SYNC -> Icons.Outlined.Storage
    SettingsGroup.MAINTENANCE -> Icons.Outlined.Storage
}

@Composable
private fun SettingsGroup.subtitle(settingCount: Int): String = when (this) {
    SettingsGroup.GOALS,
    SettingsGroup.MAINTENANCE,
    -> stringResource(subtitleRes, settingCount)
    else -> stringResource(subtitleRes)
}
