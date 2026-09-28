package com.vayana.feature.onboarding

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vayana.core.designsystem.component.asString
import com.vayana.core.designsystem.component.morphingButtonShapes
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import com.vayana.core.designsystem.theme.DisplayProfile
import com.vayana.core.designsystem.theme.vayanaContentTransform
import com.vayana.core.designsystem.tokens.Elevations
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R

@Composable
fun OnboardingRoute(
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    OnboardingScreen(
        modifier = modifier,
        suggestedDeviceName = viewModel.suggestedDeviceName,
        syncStatus = syncStatus,
        onDisplayProfileSelected = viewModel::chooseDisplayProfile,
        onSyncModeChanged = { viewModel.resetSyncStatus() },
        onImportSetup = viewModel::importSetup,
        onTestFreshSync = viewModel::testFreshSync,
        onComplete = viewModel::complete,
    )
}

@Composable
private fun OnboardingScreen(
    suggestedDeviceName: String,
    syncStatus: SyncSetupStatus,
    onDisplayProfileSelected: (DisplayProfile) -> Unit,
    onSyncModeChanged: () -> Unit,
    onImportSetup: (file: Uri, passphrase: String, deviceName: String) -> Unit,
    onTestFreshSync: (FreshSyncConfig) -> Unit,
    onComplete: (deviceName: String, freshSync: FreshSyncConfig?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pageIndex by rememberSaveable { mutableIntStateOf(0) }
    val page = OnboardingPage.entries[pageIndex]
    val isLastPage = pageIndex == OnboardingPage.entries.lastIndex
    // Held here, above the pages, so going back and forth keeps what was typed. Secrets aren't saved to instance
    // state, which the system may write out.
    var deviceName by rememberSaveable { mutableStateOf(suggestedDeviceName) }
    val sync = rememberSyncSetupForm()
    val finish = {
        onComplete(deviceName, sync.freshConfig().takeIf { sync.mode == SyncSetupMode.FRESH && it.isComplete })
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .padding(Paddings.screenHorizontal),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = page,
                    transitionSpec = vayanaContentTransform(),
                    label = "OnboardingPage",
                ) { targetPage ->
                    OnboardingPageContent(
                        page = targetPage,
                        onDisplayProfileSelected = onDisplayProfileSelected,
                    ) {
                        when (targetPage) {
                            OnboardingPage.DEVICE -> OutlinedTextField(
                                value = deviceName,
                                onValueChange = { deviceName = it.take(MaxDeviceNameChars) },
                                label = { Text(stringResource(R.string.settings_kindle_device_name_title)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OnboardingPage.SYNC -> SyncSetupStep(
                                form = sync,
                                status = syncStatus,
                                onModeChanged = onSyncModeChanged,
                                onImport = { file -> onImportSetup(file, sync.importPassphrase, deviceName) },
                                onTest = { onTestFreshSync(sync.freshConfig()) },
                            )
                            else -> Unit
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PageDots(pageIndex = pageIndex)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalButton(
                        onClick = finish,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(Radii.full),
                    ) {
                        Text(stringResource(R.string.onboarding_skip))
                    }
                    Button(
                        onClick = {
                            if (isLastPage) {
                                finish()
                            } else {
                                pageIndex += 1
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(Radii.full),
                    ) {
                        Text(stringResource(if (isLastPage) R.string.onboarding_finish else R.string.onboarding_next))
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(
    page: OnboardingPage,
    onDisplayProfileSelected: (DisplayProfile) -> Unit,
    extra: @Composable () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        // The sync step's form can outgrow the screen, the more so with the keyboard up.
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
    ) {
        Surface(
            shape = RoundedCornerShape(Radii.extraLarge),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            tonalElevation = Elevations.none,
        ) {
            if (page == OnboardingPage.WELCOME) {
                Image(
                    painter = painterResource(R.drawable.ic_vayana_app),
                    contentDescription = null,
                    modifier = Modifier.size(OnboardingHeroIconSize),
                )
            } else {
                Icon(
                    imageVector = requireNotNull(page.icon),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(Spacing.xl)
                        .size(Sizes.iconLarge),
                )
            }
        }
        Text(
            text = page.title(),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center,
        )
        Text(
            text = page.body(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (page == OnboardingPage.APPEARANCE) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { onDisplayProfileSelected(DisplayProfile.STANDARD) },
                    shape = RoundedCornerShape(Radii.full),
                ) {
                    Text(stringResource(R.string.onboarding_display_standard))
                }
                OutlinedButton(
                    onClick = { onDisplayProfileSelected(DisplayProfile.E_INK) },
                    shape = RoundedCornerShape(Radii.full),
                ) {
                    Text(stringResource(R.string.onboarding_display_eink))
                }
            }
        }
        extra()
    }
}

@Composable
private fun PageDots(pageIndex: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        OnboardingPage.entries.forEachIndexed { index, _ ->
            Surface(
                modifier = Modifier
                    .size(width = if (index == pageIndex) Sizes.iconSmall else Spacing.sm, height = Spacing.sm),
                shape = RoundedCornerShape(Radii.full),
                color = if (index == pageIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                content = {},
            )
        }
    }
}

private val OnboardingHeroIconSize = Sizes.iconLarge + (Spacing.xl * 2)

private enum class OnboardingPage(val icon: ImageVector?) {
    WELCOME(null),
    APPEARANCE(Icons.Outlined.Contrast),
    DEVICE(Icons.Outlined.Smartphone),
    IMPORT(Icons.Outlined.FileOpen),
    SYNC(Icons.Outlined.CloudSync),
    DONE(Icons.Outlined.Check),
}

@Composable
private fun OnboardingPage.title(): String = when (this) {
    OnboardingPage.WELCOME -> stringResource(R.string.onboarding_welcome_title)
    OnboardingPage.APPEARANCE -> stringResource(R.string.onboarding_appearance_title)
    OnboardingPage.DEVICE -> stringResource(R.string.onboarding_device_title)
    OnboardingPage.IMPORT -> stringResource(R.string.onboarding_import_title)
    OnboardingPage.SYNC -> stringResource(R.string.onboarding_sync_title)
    OnboardingPage.DONE -> stringResource(R.string.onboarding_done_title)
}

@Composable
private fun OnboardingPage.body(): String = when (this) {
    OnboardingPage.WELCOME -> stringResource(R.string.onboarding_welcome_body)
    OnboardingPage.APPEARANCE -> stringResource(R.string.onboarding_appearance_body)
    OnboardingPage.DEVICE -> stringResource(R.string.onboarding_device_body)
    OnboardingPage.IMPORT -> stringResource(R.string.onboarding_import_body)
    OnboardingPage.SYNC -> stringResource(R.string.onboarding_sync_setup_body)
    OnboardingPage.DONE -> stringResource(R.string.onboarding_done_body)
}

private const val MaxDeviceNameChars = 40

/** What the sync step's form holds; secrets live only in memory. */
@Stable
private class SyncSetupForm(mode: SyncSetupMode, owner: String, repository: String, branch: String) {
    var mode by mutableStateOf(mode)
    var owner by mutableStateOf(owner)
    var repository by mutableStateOf(repository)
    var branch by mutableStateOf(branch)
    var token by mutableStateOf("")
    var passphrase by mutableStateOf("")
    var importFile by mutableStateOf<Uri?>(null)
    var importFileName by mutableStateOf<String?>(null)
    var importPassphrase by mutableStateOf("")

    fun freshConfig() = FreshSyncConfig(owner, repository, branch, token, passphrase)

    companion object {
        val Saver: Saver<SyncSetupForm, Any> = listSaver(
            save = { listOf(it.mode.name, it.owner, it.repository, it.branch) },
            restore = { SyncSetupForm(SyncSetupMode.valueOf(it[0]), it[1], it[2], it[3]) },
        )
    }
}

@Composable
private fun rememberSyncSetupForm(): SyncSetupForm =
    rememberSaveable(saver = SyncSetupForm.Saver) { SyncSetupForm(SyncSetupMode.LATER, "", "", DefaultBranch) }

private const val DefaultBranch = "main"

/**
 * Not now, a new sync (repository, token, passphrase; testable here), or another device's exported setup opened with
 * its passphrase.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SyncSetupStep(
    form: SyncSetupForm,
    status: SyncSetupStatus,
    onModeChanged: () -> Unit,
    onImport: (Uri) -> Unit,
    onTest: () -> Unit,
) {
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            form.importFile = uri
            form.importFileName = context.displayNameOf(uri)
            onModeChanged()
        }
    }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SyncSetupMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = form.mode == mode,
                    onClick = {
                        if (form.mode != mode) {
                            form.mode = mode
                            onModeChanged()
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = SyncSetupMode.entries.size),
                    label = { Text(mode.label(), maxLines = 2, textAlign = TextAlign.Center) },
                )
            }
        }
        when (form.mode) {
            SyncSetupMode.LATER -> Unit
            SyncSetupMode.FRESH -> {
                SyncHint(stringResource(R.string.onboarding_sync_fresh_hint))
                SyncTextField(form.owner, { form.owner = it.trim() }, R.string.settings_github_owner_title)
                SyncTextField(form.repository, { form.repository = it.trim() }, R.string.settings_github_repository_title)
                SyncTextField(form.branch, { form.branch = it.trim() }, R.string.settings_github_branch_title)
                SyncTextField(form.token, { form.token = it.trim() }, R.string.settings_github_token_title, secret = true)
                SyncTextField(form.passphrase, { form.passphrase = it }, R.string.settings_github_passphrase_title, secret = true)
                OutlinedButton(
                    onClick = onTest,
                    enabled = status != SyncSetupStatus.Working,
                    shapes = morphingButtonShapes(),
                ) {
                    Text(stringResource(R.string.settings_github_connection_test_action))
                }
            }
            SyncSetupMode.IMPORT -> {
                SyncHint(stringResource(R.string.onboarding_sync_import_hint))
                OutlinedButton(
                    onClick = { filePicker.launch(arrayOf("*/*")) },
                    shapes = morphingButtonShapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.FileOpen, contentDescription = null, modifier = Modifier.size(Sizes.iconSmall))
                    Spacer(modifier = Modifier.size(Spacing.sm))
                    Text(
                        text = form.importFileName ?: stringResource(R.string.onboarding_sync_choose_file),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                SyncTextField(form.importPassphrase, { form.importPassphrase = it }, R.string.settings_github_passphrase_title, secret = true)
                val file = form.importFile
                Button(
                    onClick = { file?.let(onImport) },
                    enabled = file != null && form.importPassphrase.isNotEmpty() && status != SyncSetupStatus.Working,
                    shapes = morphingButtonShapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.onboarding_sync_import_action))
                }
            }
        }
        if (form.mode != SyncSetupMode.LATER) SyncStatusText(status)
    }
}

@Composable
private fun SyncSetupMode.label(): String = stringResource(
    when (this) {
        SyncSetupMode.LATER -> R.string.onboarding_sync_mode_later
        SyncSetupMode.FRESH -> R.string.onboarding_sync_mode_fresh
        SyncSetupMode.IMPORT -> R.string.onboarding_sync_mode_import
    },
)

@Composable
private fun SyncHint(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SyncTextField(value: String, onValueChange: (String) -> Unit, @StringRes label: Int, secret: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (secret) KeyboardType.Password else KeyboardType.Text,
            autoCorrectEnabled = false,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SyncStatusText(status: SyncSetupStatus) {
    val (text, isError) = when (status) {
        SyncSetupStatus.Idle -> return
        SyncSetupStatus.Working -> stringResource(R.string.onboarding_sync_working) to false
        is SyncSetupStatus.Connected -> stringResource(
            if (status.hasSnapshot) R.string.settings_github_connection_test_connected else R.string.settings_github_connection_test_ready_initial,
        ) to false
        SyncSetupStatus.MissingConfig -> stringResource(R.string.settings_github_connection_test_missing_config) to true
        SyncSetupStatus.Imported -> stringResource(R.string.onboarding_sync_imported) to false
        is SyncSetupStatus.Failed -> status.message.asString() to true
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
    )
}

private fun Context.displayNameOf(uri: Uri): String? =
    runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()
