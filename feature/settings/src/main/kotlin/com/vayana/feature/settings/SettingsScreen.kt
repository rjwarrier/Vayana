package com.vayana.feature.settings

import androidx.core.content.ContextCompat
import androidx.compose.runtime.LaunchedEffect
import android.os.Build
import android.content.pm.PackageManager
import android.Manifest
import com.vayana.core.designsystem.component.asString
import androidx.compose.ui.platform.LocalContext
import android.content.res.Resources
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import com.vayana.core.designsystem.theme.PagedLazyColumn
import com.vayana.core.datastore.settings.ImportedFont
import com.vayana.core.datastore.settings.Setting
import com.vayana.core.datastore.settings.SettingsGroup
import com.vayana.core.datastore.settings.SettingsRegistry
import com.vayana.core.datastore.settings.SettingsSnapshot
import com.vayana.core.designsystem.theme.DisplayProfile
import com.vayana.core.designsystem.theme.VayanaCircularProgressIndicator
import com.vayana.core.designsystem.theme.LocalFloatingNavigationInset
import com.vayana.core.designsystem.theme.vayanaAnimateContentSize
import com.vayana.core.designsystem.theme.vayanaContentTransform
import com.vayana.core.designsystem.tokens.Elevations
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.designsystem.tokens.Strokes
import com.vayana.core.resources.R

internal val SettingsPagePadding = Paddings.page
private val SettingsContentMaxWidth = Sizes.settingsContentMaxWidth
private val SettingsTwoColumnBreakpoint = Sizes.twoColumnBreakpoint
internal val SettingsCategoryBadgeSize = Sizes.badge
private fun settingsCategoryEntries(settings: SettingsSnapshot): List<Pair<SettingsGroup, Int>> =
    SettingsGroup.entries.mapNotNull { group ->
        val settingCount = SettingsRegistry.all.count { it.group == group && it.isVisibleFor(settings) }
        if (settingCount > 0 || group == SettingsGroup.BACKUP) group to settingCount else null
    }

internal const val VAYANA_GITHUB_URL = "https://github.com/rjwarrier/Vayana"
internal const val VAYANA_RELEASES_URL = "https://github.com/rjwarrier/Vayana/releases"
internal const val VAYANA_SUPPORT_URL = "https://www.buymeacoffee.com/ranjithj"

private data class SettingsDestinationState(
    val group: SettingsGroup? = null,
    val helpAndAboutOpen: Boolean = false,
)

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onHelpClick: () -> Unit,
    onDiagnosticsClick: () -> Unit,
    onOpenLibrary: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val backupState by viewModel.backupState.collectAsStateWithLifecycle()
    val automaticBackup by viewModel.automaticBackup.collectAsStateWithLifecycle()
    val backupFolderFiles by viewModel.backupFolderFiles.collectAsStateWithLifecycle()
    val restorePreview by viewModel.restorePreview.collectAsStateWithLifecycle()
    val githubSyncSettingsTransferState by viewModel.githubSyncSettingsTransferState.collectAsStateWithLifecycle()
    val githubConnectionTestState by viewModel.githubConnectionTestState.collectAsStateWithLifecycle()
    val pendingCloudDeletions by viewModel.pendingCloudDeletions.collectAsStateWithLifecycle()
    val readerFontImportState by viewModel.readerFontImportState.collectAsStateWithLifecycle()
    RequestNotificationPermissionFor(settings.readingReminderEnabled)

    SettingsScreen(
        modifier = modifier,
        settings = settings,
        backupState = backupState,
        automaticBackup = automaticBackup,
        backupFolderFiles = backupFolderFiles,
        restorePreview = restorePreview,
        githubSyncSettingsTransferState = githubSyncSettingsTransferState,
        githubConnectionTestState = githubConnectionTestState,
        pendingCloudDeletions = pendingCloudDeletions,
        readerFontImportState = readerFontImportState,
        onBack = onBack,
        onOpenLibrary = onOpenLibrary,
        onHelpClick = onHelpClick,
        onDiagnosticsClick = onDiagnosticsClick,
        onUpdate = viewModel::update,
        onReset = viewModel::reset,
        onResetAll = viewModel::resetAll,
        onCreateBackup = viewModel::createBackup,
        onChooseAutomaticBackupFolder = viewModel::chooseAutomaticBackupFolder,
        onSetAutomaticBackupKeepCount = viewModel::setAutomaticBackupKeepCount,
        onSetAutomaticBackupFrequency = viewModel::setAutomaticBackupFrequency,
        onDisableAutomaticBackup = viewModel::disableAutomaticBackup,
        onRefreshBackupFolderFiles = viewModel::refreshBackupFolderFiles,
        onPickRestoreFile = viewModel::inspectRestoreFile,
        onConfirmRestore = viewModel::restoreBackup,
        onExportGitHubSyncSettings = viewModel::exportGitHubSyncSettings,
        onImportGitHubSyncSettings = viewModel::importGitHubSyncSettings,
        onTestGitHubConnection = viewModel::testGitHubConnection,
        onImportReaderFont = viewModel::importReaderFont,
        onSelectReaderCustomFont = viewModel::selectReaderCustomFont,
        onDismissGitHubSyncSettingsTransferState = viewModel::dismissGitHubSyncSettingsTransferState,
        onDismissGitHubConnectionTestState = viewModel::dismissGitHubConnectionTestState,
        onDismissRestorePreview = viewModel::dismissRestorePreview,
        onDismissBackupState = viewModel::dismissBackupState,
        onDismissReaderFontImportState = viewModel::dismissReaderFontImportState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    modifier: Modifier = Modifier,
    settings: SettingsSnapshot,
    backupState: BackupUiState,
    automaticBackup: com.vayana.feature.settings.backup.AutomaticBackupState,
    backupFolderFiles: BackupFolderFilesState,
    restorePreview: RestorePreviewState,
    githubSyncSettingsTransferState: GitHubSyncSettingsTransferState,
    githubConnectionTestState: GitHubConnectionTestState,
    pendingCloudDeletions: Int,
    readerFontImportState: ReaderFontImportState,
    onBack: () -> Unit,
    onOpenLibrary: () -> Unit,
    onHelpClick: () -> Unit,
    onDiagnosticsClick: () -> Unit,
    onUpdate: (Setting<Any>, Any) -> Unit,
    onReset: (Setting<out Any>) -> Unit,
    onResetAll: () -> Unit,
    onCreateBackup: (Uri) -> Unit,
    onChooseAutomaticBackupFolder: (Uri) -> Unit,
    onSetAutomaticBackupKeepCount: (Int) -> Unit,
    onSetAutomaticBackupFrequency: (com.vayana.feature.settings.backup.AutomaticBackupFrequency) -> Unit,
    onDisableAutomaticBackup: () -> Unit,
    onRefreshBackupFolderFiles: () -> Unit,
    onPickRestoreFile: (Uri) -> Unit,
    onConfirmRestore: (Uri) -> Unit,
    onExportGitHubSyncSettings: (Uri) -> Unit,
    onImportGitHubSyncSettings: (Uri) -> Unit,
    onTestGitHubConnection: () -> Unit,
    onImportReaderFont: (Uri) -> Unit,
    onSelectReaderCustomFont: (String?) -> Unit,
    onDismissGitHubSyncSettingsTransferState: () -> Unit,
    onDismissGitHubConnectionTestState: () -> Unit,
    onDismissReaderFontImportState: () -> Unit,
    onDismissRestorePreview: () -> Unit,
    onDismissBackupState: () -> Unit,
) {
    var showResetAllDialog by remember { mutableStateOf(false) }
    var selectedGroup by remember { mutableStateOf<SettingsGroup?>(null) }
    var helpAndAboutOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val resources = LocalContext.current.resources
    val visibleSettings = remember(query, settings.displayProfile, resources) {
        SettingsRegistry.all.filter { it.isVisibleFor(settings) }.filterByQuery(query, resources)
    }
    val useTwoPane = LocalConfiguration.current.screenWidthDp.dp >= SettingsTwoColumnBreakpoint

    val destinationContent: @Composable (SettingsDestinationState, PaddingValues) -> Unit = { destination, contentPadding ->
        when {
            destination.helpAndAboutOpen -> {
                HelpAndAboutDetail(
                    contentPadding = contentPadding,
                    onHelpClick = onHelpClick,
                    onDiagnosticsClick = onDiagnosticsClick,
                )
            }
            destination.group == null -> {
                if (useTwoPane) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.settings_tablet_select_category),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    SettingsHub(
                        contentPadding = contentPadding,
                        query = query,
                        visibleSettings = visibleSettings,
                        settings = settings,
                        onHelpAndAboutClick = { helpAndAboutOpen = true },
                        onQueryChange = { query = it },
                        onGroupSelected = {
                            selectedGroup = it
                            helpAndAboutOpen = false
                        },
                        onUpdate = onUpdate,
                        onReset = onReset,
                    )
                }
            }
            else -> SettingsGroupDetail(
                contentPadding = contentPadding,
                group = destination.group,
                showBackupGroupHeader = useTwoPane,
                settings = settings,
                githubSyncSettingsTransferState = githubSyncSettingsTransferState,
                githubConnectionTestState = githubConnectionTestState,
                pendingCloudDeletions = pendingCloudDeletions,
                readerFontImportState = readerFontImportState,
                backupState = backupState,
                automaticBackup = automaticBackup,
                backupFolderFiles = backupFolderFiles,
                onUpdate = onUpdate,
                onReset = onReset,
                onCreateBackup = onCreateBackup,
                onChooseAutomaticBackupFolder = onChooseAutomaticBackupFolder,
                onSetAutomaticBackupKeepCount = onSetAutomaticBackupKeepCount,
                onSetAutomaticBackupFrequency = onSetAutomaticBackupFrequency,
                onDisableAutomaticBackup = onDisableAutomaticBackup,
                onRefreshBackupFolderFiles = onRefreshBackupFolderFiles,
                onPickRestoreFile = onPickRestoreFile,
                onDismissBackupState = onDismissBackupState,
                onExportGitHubSyncSettings = onExportGitHubSyncSettings,
                onImportGitHubSyncSettings = onImportGitHubSyncSettings,
                onTestGitHubConnection = onTestGitHubConnection,
                onImportReaderFont = onImportReaderFont,
                onSelectReaderCustomFont = onSelectReaderCustomFont,
                onDismissGitHubSyncSettingsTransferState = onDismissGitHubSyncSettingsTransferState,
                onDismissGitHubConnectionTestState = onDismissGitHubConnectionTestState,
                onDismissReaderFontImportState = onDismissReaderFontImportState,
                onOpenLibrary = onOpenLibrary,
            )
        }
    }

    BackHandler(enabled = !useTwoPane && (selectedGroup != null || helpAndAboutOpen)) {
        selectedGroup = null
        helpAndAboutOpen = false
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when {
                            useTwoPane -> stringResource(R.string.settings_title)
                            helpAndAboutOpen -> stringResource(R.string.settings_help_about_card_title)
                            selectedGroup != null -> stringResource(selectedGroup!!.titleRes)
                            else -> stringResource(R.string.settings_title)
                        },
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (!useTwoPane && (selectedGroup != null || helpAndAboutOpen)) {
                            selectedGroup = null
                            helpAndAboutOpen = false
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back_content_description),
                        )
                    }
                },
                actions = {
                    if (selectedGroup != SettingsGroup.BACKUP) {
                        IconButton(onClick = { showResetAllDialog = true }) {
                            Icon(
                                imageVector = Icons.Outlined.RestartAlt,
                                contentDescription = stringResource(R.string.settings_reset_all_content_description),
                            )
                        }
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
        if (useTwoPane) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(0.42f)) {
                SettingsHub(
                    contentPadding = innerPadding,
                    query = query,
                    visibleSettings = visibleSettings,
                    settings = settings,
                    onHelpAndAboutClick = { helpAndAboutOpen = true },
                    onQueryChange = { query = it },
                    onGroupSelected = {
                        selectedGroup = it
                        helpAndAboutOpen = false
                    },
                    onUpdate = onUpdate,
                    onReset = onReset,
                )
                }
                Surface(
                    modifier = Modifier
                        .width(Strokes.hairline)
                        .fillMaxHeight(),
                    color = MaterialTheme.colorScheme.outlineVariant,
                    content = {},
                )
                AnimatedContent(
                    targetState = SettingsDestinationState(selectedGroup, helpAndAboutOpen),
                    transitionSpec = vayanaContentTransform(),
                    modifier = Modifier.weight(0.58f),
                    label = "TabletSettingsDetail",
                ) { destination ->
                    destinationContent(destination, innerPadding)
                }
            }
        } else {
            AnimatedContent(
                targetState = SettingsDestinationState(selectedGroup, helpAndAboutOpen),
                transitionSpec = vayanaContentTransform(),
                label = "SettingsNav",
            ) { destination ->
                destinationContent(destination, innerPadding)
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

    if (backupState == BackupUiState.Restoring) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.settings_restore_working_title)) },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    VayanaCircularProgressIndicator(modifier = Modifier.size(Sizes.iconSmall))
                    Text(stringResource(R.string.settings_restore_working))
                }
            },
            confirmButton = {},
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
                        VayanaCircularProgressIndicator(modifier = Modifier.size(Sizes.iconSmall))
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
                title = { Text(stringResource(R.string.settings_restore_read_error_title)) },
                text = { Text(restorePreview.message.asString()) },
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
                title = { Text(stringResource(R.string.settings_restore_review_title)) },
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

@Composable
private fun SettingsHub(
    contentPadding: PaddingValues,
    query: String,
    visibleSettings: List<Setting<out Any>>,
    settings: SettingsSnapshot,
    onHelpAndAboutClick: () -> Unit,
    onQueryChange: (String) -> Unit,
    onGroupSelected: (SettingsGroup) -> Unit,
    onUpdate: (Setting<Any>, Any) -> Unit,
    onReset: (Setting<out Any>) -> Unit,
) {
    PagedLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = SettingsPagePadding,
            top = contentPadding.calculateTopPadding() + SettingsPagePadding,
            end = SettingsPagePadding,
            bottom = contentPadding.calculateBottomPadding() + SettingsPagePadding + LocalFloatingNavigationInset.current,
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
                    SettingsCategoryCards(
                        categoryEntries = settingsCategoryEntries(settings),
                        onGroupSelected = onGroupSelected,
                    )
                }
            }
            item {
                SettingsContentContainer {
                    HelpAndAboutHubCard(onClick = onHelpAndAboutClick)
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
    showBackupGroupHeader: Boolean,
    settings: SettingsSnapshot,
    githubSyncSettingsTransferState: GitHubSyncSettingsTransferState,
    githubConnectionTestState: GitHubConnectionTestState,
    pendingCloudDeletions: Int,
    readerFontImportState: ReaderFontImportState,
    backupState: BackupUiState,
    automaticBackup: com.vayana.feature.settings.backup.AutomaticBackupState,
    backupFolderFiles: BackupFolderFilesState,
    onUpdate: (Setting<Any>, Any) -> Unit,
    onReset: (Setting<out Any>) -> Unit,
    onCreateBackup: (Uri) -> Unit,
    onChooseAutomaticBackupFolder: (Uri) -> Unit,
    onSetAutomaticBackupKeepCount: (Int) -> Unit,
    onSetAutomaticBackupFrequency: (com.vayana.feature.settings.backup.AutomaticBackupFrequency) -> Unit,
    onDisableAutomaticBackup: () -> Unit,
    onRefreshBackupFolderFiles: () -> Unit,
    onPickRestoreFile: (Uri) -> Unit,
    onDismissBackupState: () -> Unit,
    onExportGitHubSyncSettings: (Uri) -> Unit,
    onImportGitHubSyncSettings: (Uri) -> Unit,
    onTestGitHubConnection: () -> Unit,
    onImportReaderFont: (Uri) -> Unit,
    onSelectReaderCustomFont: (String?) -> Unit,
    onDismissGitHubSyncSettingsTransferState: () -> Unit,
    onDismissGitHubConnectionTestState: () -> Unit,
    onDismissReaderFontImportState: () -> Unit,
    onOpenLibrary: () -> Unit,
) {
    val groupSettings = remember(group, settings.displayProfile) {
        SettingsRegistry.all.filter { it.group == group && it.isVisibleFor(settings) }
    }
    PagedLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = SettingsPagePadding,
            top = contentPadding.calculateTopPadding() + SettingsPagePadding,
            end = SettingsPagePadding,
            bottom = contentPadding.calculateBottomPadding() + SettingsPagePadding + LocalFloatingNavigationInset.current,
        ),
        verticalArrangement = Arrangement.spacedBy(SettingsPagePadding),
    ) {
        if (group != null && (group != SettingsGroup.BACKUP || showBackupGroupHeader)) {
            item {
                SettingsContentContainer {
                    SettingsGroupHeader(group = group, settingCount = groupSettings.size)
                }
            }
        }
        if (group == SettingsGroup.LIBRARY) {
            item {
                SettingsContentContainer {
                    SettingsNavigationCard(
                        title = stringResource(R.string.settings_library_import_title),
                        subtitle = stringResource(R.string.settings_library_import_subtitle),
                        icon = Icons.Outlined.AutoStories,
                        onClick = onOpenLibrary,
                    )
                }
            }
        }
        if (group == SettingsGroup.LIBRARY) {
            item {
                SettingsContentContainer { HomeLibraryCard() }
            }
        }
        if (group == SettingsGroup.APPEARANCE) {
            item {
                SettingsContentContainer { AppLanguageCard() }
            }
        }
        if (group == SettingsGroup.SYNC) {
            item {
                SettingsContentContainer {
                    GitHubSyncHealthCard(settings = settings, connectionState = githubConnectionTestState, pendingCloudDeletions = pendingCloudDeletions)
                }
            }
        }
        if (groupSettings.isNotEmpty()) {
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
        }
        if (group == SettingsGroup.BACKUP) {
            item {
                SettingsContentContainer {
                    BackupRestoreCard(
                        backupState = backupState,
                        automaticBackup = automaticBackup,
                        backupFolderFiles = backupFolderFiles,
                        onCreateBackup = onCreateBackup,
                        onChooseAutomaticBackupFolder = onChooseAutomaticBackupFolder,
                        onSetAutomaticBackupKeepCount = onSetAutomaticBackupKeepCount,
                        onSetAutomaticBackupFrequency = onSetAutomaticBackupFrequency,
                        onDisableAutomaticBackup = onDisableAutomaticBackup,
                        onRefreshBackupFolderFiles = onRefreshBackupFolderFiles,
                        onPickRestoreFile = onPickRestoreFile,
                        onDismissBackupState = onDismissBackupState,
                    )
                }
            }
        }
        if (group == SettingsGroup.READER_TEXT) {
            item {
                SettingsContentContainer {
                    ReaderCustomFontsCard(
                        fonts = settings.readerImportedFonts,
                        selectedFontId = settings.readerCustomFontId,
                        importState = readerFontImportState,
                        onImport = onImportReaderFont,
                        onSelectFont = onSelectReaderCustomFont,
                        onDismissImportState = onDismissReaderFontImportState,
                    )
                }
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
internal fun SettingsContentContainer(content: @Composable () -> Unit) {
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
internal fun SettingsNavigationCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.medium),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(Strokes.outline, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        tonalElevation = Elevations.none,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(Paddings.card),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SettingsIconBubble(icon = icon, selected = false)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReaderCustomFontsCard(
    fonts: List<ImportedFont>,
    selectedFontId: String?,
    importState: ReaderFontImportState,
    onImport: (Uri) -> Unit,
    onSelectFont: (String?) -> Unit,
    onDismissImportState: () -> Unit,
) {
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onImport)
    }
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
                SettingsIconBubble(icon = Icons.Outlined.FormatSize, selected = false)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_reader_custom_fonts_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.settings_reader_custom_fonts_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            FilledTonalButton(
                onClick = { importLauncher.launch(arrayOf("font/*", "application/x-font-ttf", "application/x-font-otf", "application/font-woff", "application/font-woff2", "application/octet-stream", "*/*")) },
                enabled = importState !is ReaderFontImportState.Working,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (importState is ReaderFontImportState.Working) {
                    VayanaCircularProgressIndicator(modifier = Modifier.size(Sizes.iconSmall))
                } else {
                    Icon(imageVector = Icons.Outlined.FileUpload, contentDescription = null, modifier = Modifier.size(Sizes.iconSmall))
                }
                Text(stringResource(R.string.settings_reader_custom_fonts_import), modifier = Modifier.padding(start = Spacing.xs))
            }

            if (fonts.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    fonts.forEach { font ->
                        FilterChip(
                            selected = selectedFontId == font.id,
                            onClick = { onSelectFont(font.id) },
                            label = { Text(font.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = if (selectedFontId == font.id) {
                                { Icon(imageVector = Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(Sizes.iconSmall)) }
                            } else {
                                null
                            },
                        )
                    }
                }
                TextButton(onClick = { onSelectFont(null) }) {
                    Text(stringResource(R.string.settings_reader_custom_fonts_use_builtin))
                }
            }

            when (importState) {
                ReaderFontImportState.Idle -> Unit
                ReaderFontImportState.Working -> Text(
                    text = stringResource(R.string.settings_reader_custom_fonts_importing),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                is ReaderFontImportState.Imported -> BackupStatusRow(
                    message = stringResource(R.string.settings_reader_custom_fonts_imported, importState.displayName),
                    isError = false,
                    onDismiss = onDismissImportState,
                )
                is ReaderFontImportState.Failed -> BackupStatusRow(
                    message = stringResource(
                        R.string.settings_reader_custom_fonts_failed,
                        importState.message.asString(),
                    ),
                    isError = true,
                    onDismiss = onDismissImportState,
                )
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
                    modifier = Modifier.size(Sizes.iconMedium),
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
                text = stringResource(group.subtitleRes),
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
                        modifier = Modifier.size(Sizes.iconMedium),
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
                    text = stringResource(group.subtitleRes),
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
                if (settingCount > 0) {
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
internal fun SettingsIconBubble(icon: ImageVector, selected: Boolean) {
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

@Suppress("UNCHECKED_CAST")
internal fun Setting<out Any>.asAny(): Setting<Any> = this as Setting<Any>

private fun SettingsSnapshot.valueFor(setting: Setting<out Any>): Any = when (setting) {
    SettingsRegistry.ThemeMode -> themeMode
    SettingsRegistry.DisplayProfile -> displayProfile
    SettingsRegistry.EinkAudioFeatures -> einkAudioFeaturesEnabled
    SettingsRegistry.DarkVariant -> darkVariant
    SettingsRegistry.Motion -> motionSetting
    SettingsRegistry.NavigationMode -> navigationMode
    SettingsRegistry.ReaderFontSize -> readerFontSizePercent
    SettingsRegistry.ReaderLineHeight -> readerLineHeight
    SettingsRegistry.ReaderFontFamily -> readerFontFamily
    SettingsRegistry.ReaderTheme -> readerTheme
    SettingsRegistry.ReaderSideMargin -> readerSideMarginPercent
    SettingsRegistry.ReaderHeaderGap -> readerHeaderGapDp
    SettingsRegistry.ReaderFooterGap -> readerFooterGapDp
    SettingsRegistry.ReaderPublisherStyles -> readerUsePublisherStyles
    SettingsRegistry.ReaderTapZoneMode -> readerTapZoneMode
    SettingsRegistry.ReaderControlsTapMode -> readerControlsTapMode
    SettingsRegistry.ReaderVolumeKeys -> readerVolumeKeys
    SettingsRegistry.ReaderKeepAwake -> readerKeepAwake
    SettingsRegistry.ReaderPersonalPace -> readerPersonalPace
    SettingsRegistry.ReaderShowHeaders -> readerShowHeaders
    SettingsRegistry.ReaderShowFooter -> readerShowFooter
    SettingsRegistry.ReaderAutoMarkSelection -> readerAutoMarkSelection
    SettingsRegistry.ReaderBionicReading -> readerBionicReading
    SettingsRegistry.ReaderBolderText -> readerBolderText
    SettingsRegistry.ReaderTextAlign -> readerTextAlign
    SettingsRegistry.ReaderHyphenation -> readerHyphenation
    SettingsRegistry.ReaderFullScreen -> readerFullScreen
    SettingsRegistry.ReaderPageTurnAnimation -> readerPageTurnAnimation
    SettingsRegistry.EinkRefreshEveryPages -> einkRefreshEveryPages
    SettingsRegistry.FinishedPercent -> finishedPercent
    SettingsRegistry.ReadingAutoSyncEveryPages -> readingAutoSyncEveryPages
    SettingsRegistry.DynamicColor -> dynamicColor
    SettingsRegistry.DateFormat -> dateFormatStyle
    SettingsRegistry.StartScreen -> startScreen
    SettingsRegistry.RecentlyDeletedRetention -> recentlyDeletedRetention
    SettingsRegistry.WeekStart -> weekStart
    SettingsRegistry.ReadAloudRate -> readAloudRate
    SettingsRegistry.ReadAloudPitch -> readAloudPitch
    SettingsRegistry.ReaderBrightness -> readerBrightnessPercent
    SettingsRegistry.ReaderWarmLight -> readerWarmLightPercent
    SettingsRegistry.ReaderEdgeSwipeLight -> readerEdgeSwipeLight
    SettingsRegistry.DailyReadingGoalMinutes -> dailyReadingGoalMinutes
    SettingsRegistry.ReadingReminderEnabled -> readingReminderEnabled
    SettingsRegistry.ReadingReminderHour -> readingReminderHour
    SettingsRegistry.BorrowReminders -> borrowRemindersEnabled
    SettingsRegistry.YearlyBooksGoal -> yearlyBooksGoal
    SettingsRegistry.DefaultCoverSource -> defaultCoverSource
    SettingsRegistry.HomeLibrarySync -> homeLibrarySyncEnabled
    SettingsRegistry.LandscapeTwoColumnLayout -> landscapeTwoColumnLayout
    SettingsRegistry.KindleDeviceName -> kindleDeviceName
    SettingsRegistry.GithubSyncEnabled -> githubSyncEnabled
    SettingsRegistry.GithubOwner -> githubOwner
    SettingsRegistry.GithubRepository -> githubRepository
    SettingsRegistry.GithubBranch -> githubBranch
    SettingsRegistry.GithubToken -> githubToken
    SettingsRegistry.GithubSyncPassphrase -> githubSyncPassphrase
    else -> setting.defaultValue
}

private fun List<Setting<out Any>>.filterByQuery(query: String, resources: Resources): List<Setting<out Any>> {
    val normalized = query.trim()
    if (normalized.isEmpty()) return this
    return filter { setting ->
        setting.searchTokens(resources).contains(normalized, ignoreCase = true)
    }
}

private fun Setting<out Any>.isVisibleFor(settings: SettingsSnapshot): Boolean =
    this != SettingsRegistry.EinkAudioFeatures || settings.displayProfile == DisplayProfile.E_INK

private fun Setting<out Any>.searchTokens(resources: Resources): String {
    // Title, subtitle and keywords are resources, so settings search works in the reader's language.
    val keywords: Int? = when (this) {
        SettingsRegistry.ThemeMode -> R.string.settings_search_keywords_theme_mode
        SettingsRegistry.DisplayProfile -> R.string.settings_search_keywords_display_profile
        SettingsRegistry.EinkAudioFeatures -> R.string.settings_search_keywords_eink_audio_features
        SettingsRegistry.DarkVariant -> R.string.settings_search_keywords_dark_variant
        SettingsRegistry.Motion -> R.string.settings_search_keywords_motion
        SettingsRegistry.NavigationMode -> R.string.settings_search_keywords_navigation_mode
        SettingsRegistry.ReaderFontSize -> R.string.settings_search_keywords_reader_font_size
        SettingsRegistry.ReaderLineHeight -> R.string.settings_search_keywords_reader_line_height
        SettingsRegistry.ReaderFontFamily -> R.string.settings_search_keywords_reader_font_family
        SettingsRegistry.ReaderTheme -> R.string.settings_search_keywords_reader_theme
        SettingsRegistry.ReaderSideMargin -> R.string.settings_search_keywords_reader_side_margin
        SettingsRegistry.ReaderHeaderGap -> R.string.settings_search_keywords_reader_header_gap
        SettingsRegistry.ReaderFooterGap -> R.string.settings_search_keywords_reader_footer_gap
        SettingsRegistry.ReaderPublisherStyles -> R.string.settings_search_keywords_reader_publisher_styles
        SettingsRegistry.ReaderTapZoneMode -> R.string.settings_search_keywords_reader_tap_zone_mode
        SettingsRegistry.ReaderControlsTapMode -> R.string.settings_search_keywords_reader_controls_tap_mode
        SettingsRegistry.ReaderVolumeKeys -> R.string.settings_search_keywords_reader_volume_keys
        SettingsRegistry.ReaderKeepAwake -> R.string.settings_search_keywords_reader_keep_awake
        SettingsRegistry.ReaderShowHeaders -> R.string.settings_search_keywords_reader_show_headers
        SettingsRegistry.ReaderShowFooter -> R.string.settings_search_keywords_reader_show_footer
        SettingsRegistry.ReaderAutoMarkSelection -> R.string.settings_search_keywords_reader_auto_mark_selection
        SettingsRegistry.ReaderBionicReading -> R.string.settings_search_keywords_reader_bionic_reading
        SettingsRegistry.ReaderBolderText -> R.string.settings_search_keywords_reader_bolder_text
        SettingsRegistry.ReaderTextAlign -> R.string.settings_search_keywords_reader_text_align
        SettingsRegistry.ReaderHyphenation -> R.string.settings_search_keywords_reader_hyphenation
        SettingsRegistry.ReaderFullScreen -> R.string.settings_search_keywords_reader_full_screen
        SettingsRegistry.ReaderPageTurnAnimation -> R.string.settings_search_keywords_reader_page_turn_animation
        SettingsRegistry.EinkRefreshEveryPages -> R.string.settings_search_keywords_eink_refresh_every_pages
        SettingsRegistry.FinishedPercent -> R.string.settings_search_keywords_finished_percent
        SettingsRegistry.ReadingAutoSyncEveryPages -> R.string.settings_search_keywords_reading_auto_sync_every_pages
        SettingsRegistry.DynamicColor -> R.string.settings_search_keywords_dynamic_color
        SettingsRegistry.DateFormat -> R.string.settings_search_keywords_date_format
        SettingsRegistry.StartScreen -> R.string.settings_search_keywords_start_screen
        SettingsRegistry.RecentlyDeletedRetention -> R.string.settings_search_keywords_recently_deleted_retention
        SettingsRegistry.WeekStart -> R.string.settings_search_keywords_week_start
        SettingsRegistry.ReadAloudRate -> R.string.settings_search_keywords_read_aloud_rate
        SettingsRegistry.ReadAloudPitch -> R.string.settings_search_keywords_read_aloud_pitch
        SettingsRegistry.ReaderBrightness -> R.string.settings_search_keywords_reader_brightness
        SettingsRegistry.ReaderWarmLight -> R.string.settings_search_keywords_reader_warm_light
        SettingsRegistry.ReaderEdgeSwipeLight -> R.string.settings_search_keywords_reader_edge_swipe_light
        SettingsRegistry.DefaultCoverSource -> R.string.settings_search_keywords_default_cover_source
        SettingsRegistry.LandscapeTwoColumnLayout -> R.string.settings_search_keywords_landscape_two_column_layout
        SettingsRegistry.KindleDeviceName -> R.string.settings_search_keywords_kindle_device_name
        SettingsRegistry.GithubSyncEnabled -> R.string.settings_search_keywords_github_sync_enabled
        SettingsRegistry.GithubOwner -> R.string.settings_search_keywords_github_owner
        SettingsRegistry.GithubRepository -> R.string.settings_search_keywords_github_repository
        SettingsRegistry.GithubBranch -> R.string.settings_search_keywords_github_branch
        SettingsRegistry.GithubToken -> R.string.settings_search_keywords_github_token
        SettingsRegistry.GithubSyncPassphrase -> R.string.settings_search_keywords_github_sync_passphrase
        else -> null
    }
    return listOfNotNull(
        key,
        group.name,
        resources.getString(titleRes),
        subtitleRes?.let(resources::getString),
        keywords?.let(resources::getString),
    ).joinToString(" ")
}

@Composable
internal fun SettingsGroup.icon(): ImageVector = when (this) {
    SettingsGroup.APPEARANCE -> Icons.Outlined.Palette
    SettingsGroup.LIBRARY -> Icons.Outlined.LocalLibrary
    SettingsGroup.READER_TEXT -> Icons.Outlined.FormatSize
    SettingsGroup.READER_PAGE -> Icons.Outlined.AutoStories
    SettingsGroup.READER_CONTROLS -> Icons.Outlined.TouchApp
    SettingsGroup.GOALS -> Icons.Outlined.Flag
    SettingsGroup.SYNC -> Icons.Outlined.Sync
    SettingsGroup.BACKUP -> Icons.Outlined.Backup
}

/**
 * Turning the reading reminder on needs Android 13's notification permission; asked for then, when it matters, and
 * once per turn-on. (Borrowed-book reminders ride on the same permission once granted.)
 */
@Composable
private fun RequestNotificationPermissionFor(enabled: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(enabled) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (enabled && !granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
