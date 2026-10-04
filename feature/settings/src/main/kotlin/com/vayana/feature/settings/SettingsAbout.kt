package com.vayana.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.FileProvider
import com.vayana.core.designsystem.theme.PagedLazyColumn
import com.vayana.core.designsystem.theme.LocalFloatingNavigationInset
import com.vayana.core.designsystem.dialog.ExpressiveDialogHeader
import com.vayana.core.designsystem.dialog.ExpressiveDialogSurface
import com.vayana.core.designsystem.tokens.Elevations
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.designsystem.tokens.Strokes
import com.vayana.core.resources.R
import java.io.File
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SettingsAboutBadgeSize = Sizes.badgeLarge
private val SettingsAboutMarkSize = (Sizes.iconLarge + Spacing.sm) * 1.265f
private val SupportButtonYellow = Color(0xFFFFDD00)
private enum class SharePromoTheme(@param:DrawableRes val imageRes: Int) {
    LIGHT(R.drawable.vayana_share_light),
    DARK(R.drawable.vayana_share_dark_reader),
}

@Composable
internal fun HelpAndAboutHubCard(onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.large),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
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
            Surface(
                modifier = Modifier.size(SettingsCategoryBadgeSize),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                        contentDescription = null,
                        modifier = Modifier.size(Sizes.iconMedium),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_help_about_card_title),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.settings_help_about_card_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = stringResource(R.string.settings_category_open_content_description),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun HelpAndAboutDetail(
    contentPadding: PaddingValues,
    onHelpClick: () -> Unit,
    onDiagnosticsClick: () -> Unit,
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
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Radii.large),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = Elevations.none,
                    ) {
                        Column(
                            modifier = Modifier.padding(Paddings.card),
                            verticalArrangement = Arrangement.spacedBy(Spacing.md),
                        ) {
                            SettingsNavigationCard(
                                title = stringResource(R.string.settings_open_help_title),
                                subtitle = stringResource(R.string.settings_open_help_subtitle),
                                icon = Icons.AutoMirrored.Outlined.HelpOutline,
                                onClick = onHelpClick,
                            )
                            SettingsNavigationCard(
                                title = stringResource(R.string.settings_open_diagnostics_title),
                                subtitle = stringResource(R.string.settings_open_diagnostics_subtitle),
                                icon = Icons.Outlined.BugReport,
                                onClick = onDiagnosticsClick,
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.settings_about_section_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    SettingsAboutSection()
                }
            }
        }
    }
}

@Composable
private fun SettingsAboutSection() {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val documentationUrl = stringResource(R.string.vayana_documentation_url)
    val coroutineScope = rememberCoroutineScope()
    var showShareDialog by rememberSaveable { mutableStateOf(false) }
    val version = remember(context) { context.appVersion() }
    val shareTitle = stringResource(R.string.about_share)
    val shareText = stringResource(R.string.about_share_text)
    val initialShareTheme = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        SharePromoTheme.DARK
    } else {
        SharePromoTheme.LIGHT
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radii.large),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = Elevations.none,
        ) {
            Column(
                modifier = Modifier.padding(Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Surface(
                    modifier = Modifier.size(SettingsAboutBadgeSize),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.ic_vayana_mark),
                            contentDescription = null,
                            modifier = Modifier.size(SettingsAboutMarkSize),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.app_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.about_feature_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        text = stringResource(
                            R.string.about_version,
                            version.name ?: "0.1.0",
                            version.code.toString(),
                        ),
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.sm))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(R.string.about_credit),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.about_made_in),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedButton(
                onClick = { uriHandler.openUri(VAYANA_GITHUB_URL) },
                modifier = Modifier.weight(1f),
            ) {
                Icon(imageVector = Icons.Outlined.Code, contentDescription = null, modifier = Modifier.size(Sizes.iconSmall))
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text(text = stringResource(R.string.about_github), textAlign = TextAlign.Center)
            }
            OutlinedButton(
                onClick = { showShareDialog = true },
                modifier = Modifier.weight(1f),
            ) {
                Icon(imageVector = Icons.Outlined.IosShare, contentDescription = null, modifier = Modifier.size(Sizes.iconSmall))
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text(text = shareTitle, textAlign = TextAlign.Center)
            }
        }
        FilledTonalButton(
            onClick = { uriHandler.openUri(documentationUrl) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(imageVector = Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null, modifier = Modifier.size(Sizes.iconSmall))
            Spacer(modifier = Modifier.width(Spacing.xs))
            Text(text = stringResource(R.string.about_documentation), textAlign = TextAlign.Center)
        }
        FilledTonalButton(
            onClick = { uriHandler.openUri(VAYANA_RELEASES_URL) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(imageVector = Icons.Outlined.Language, contentDescription = null, modifier = Modifier.size(Sizes.iconSmall))
            Spacer(modifier = Modifier.width(Spacing.xs))
            Text(text = stringResource(R.string.about_website), textAlign = TextAlign.Center)
        }
        Button(
            onClick = { uriHandler.openUri(VAYANA_SUPPORT_URL) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = SupportButtonYellow,
                contentColor = Color.Black,
            ),
            border = BorderStroke(Strokes.outline, Color.Black),
        ) {
            Icon(imageVector = Icons.Outlined.LocalCafe, contentDescription = null, modifier = Modifier.size(Sizes.iconSmall))
            Spacer(modifier = Modifier.width(Spacing.xs))
            Text(
                text = stringResource(R.string.about_support_dev),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
        OtherAppsCard()
    }

    if (showShareDialog) {
        ShareVayanaDialog(
            initialMessage = shareText,
            initialTheme = initialShareTheme,
            chooserTitle = shareTitle,
            onDismissRequest = { showShareDialog = false },
            onShare = { message, imageRes ->
                coroutineScope.launch { context.shareApp(shareTitle, message, imageRes) }
            },
        )
    }
}

private data class RelatedApp(
    @param:StringRes val nameRes: Int,
    @param:StringRes val taglineRes: Int,
    val playStoreUrl: String,
    val icon: ImageVector,
)

private val relatedApps = listOf(
    RelatedApp(
        nameRes = R.string.about_app_ultra,
        taglineRes = R.string.about_app_ultra_tagline,
        playStoreUrl = "https://play.google.com/store/apps/details?id=com.ultra.reminders",
        icon = Icons.Outlined.Alarm,
    ),
    RelatedApp(
        nameRes = R.string.about_app_yaja,
        taglineRes = R.string.about_app_yaja_tagline,
        playStoreUrl = "https://play.google.com/store/apps/details?id=com.mj.yaja",
        icon = Icons.Outlined.Book,
    ),
    RelatedApp(
        nameRes = R.string.about_app_yata,
        taglineRes = R.string.about_app_yata_tagline,
        playStoreUrl = "https://github.com/rjwarrier/yata/releases",
        icon = Icons.Outlined.CheckCircle,
    ),
    RelatedApp(
        nameRes = R.string.about_app_assetrack,
        taglineRes = R.string.about_app_assetrack_tagline,
        playStoreUrl = "https://play.google.com/store/apps/details?id=com.mj.assetrack",
        icon = Icons.Outlined.Inventory2,
    ),
)

@Composable
private fun OtherAppsCard() {
    val uriHandler = LocalUriHandler.current
    val tileColors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.error,
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.large),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            width = Strokes.outline,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        ),
        tonalElevation = Elevations.none,
    ) {
        Column(
            modifier = Modifier.padding(Paddings.card),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Sizes.iconSmall),
                )
                Text(
                    text = stringResource(R.string.about_other_apps),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            relatedApps.withIndex().chunked(RelatedAppsColumns).forEach { rowApps ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    rowApps.forEach { (appIndex, app) ->
                        val tint = tileColors[appIndex % tileColors.size]
                        Surface(
                            onClick = { uriHandler.openUri(app.playStoreUrl) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(Radii.medium),
                            color = tint.copy(alpha = 0.12f),
                            border = BorderStroke(Strokes.outline, tint.copy(alpha = 0.3f)),
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.md),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                            ) {
                                Surface(
                                    modifier = Modifier.size(Sizes.badge),
                                    shape = CircleShape,
                                    color = tint.copy(alpha = 0.2f),
                                    contentColor = tint,
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = app.icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(Sizes.iconSmall),
                                        )
                                    }
                                }
                                Text(
                                    text = stringResource(app.nameRes),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    text = stringResource(app.taglineRes),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                    repeat(RelatedAppsColumns - rowApps.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareVayanaDialog(
    initialMessage: String,
    initialTheme: SharePromoTheme,
    chooserTitle: String,
    onDismissRequest: () -> Unit,
    onShare: (message: String, imageRes: Int?) -> Unit,
) {
    var message by rememberSaveable { mutableStateOf(initialMessage) }
    var includeImage by rememberSaveable { mutableStateOf(true) }
    var selectedTheme by rememberSaveable { mutableStateOf(initialTheme) }

    ExpressiveDialogSurface(
        onDismissRequest = onDismissRequest,
        scrollable = true,
        animateContentSize = false,
    ) {
        ExpressiveDialogHeader(
            icon = Icons.Outlined.IosShare,
            title = chooserTitle,
            supportingText = stringResource(R.string.share_app_dialog_subtitle),
        )

        if (includeImage) {
            Image(
                painter = painterResource(selectedTheme.imageRes),
                contentDescription = stringResource(R.string.share_app_preview_content_description),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(Radii.large)),
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radii.medium),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = Elevations.none,
        ) {
            Row(
                modifier = Modifier.padding(Paddings.card),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.share_app_include_image),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.share_app_include_image_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = includeImage, onCheckedChange = { includeImage = it })
            }
        }

        if (includeImage) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = stringResource(R.string.share_app_theme_label),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = selectedTheme == SharePromoTheme.LIGHT,
                        onClick = { selectedTheme = SharePromoTheme.LIGHT },
                        label = { Text(stringResource(R.string.share_app_theme_light)) },
                    )
                    FilterChip(
                        selected = selectedTheme == SharePromoTheme.DARK,
                        onClick = { selectedTheme = SharePromoTheme.DARK },
                        label = { Text(stringResource(R.string.share_app_theme_dark)) },
                    )
                }
            }
        }

        OutlinedTextField(
            value = message,
            onValueChange = { message = it },
            label = { Text(stringResource(R.string.share_app_message_label)) },
            minLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = stringResource(R.string.share_app_link_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = VAYANA_RELEASES_URL,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(android.R.string.cancel))
            }
            Button(
                onClick = {
                    onShare(message, selectedTheme.imageRes.takeIf { includeImage })
                    onDismissRequest()
                },
                shape = Radii.buttonShape,
            ) {
                Icon(imageVector = Icons.Outlined.IosShare, contentDescription = null, modifier = Modifier.size(Sizes.iconSmall))
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text(stringResource(R.string.share_app_button, stringResource(R.string.app_name)))
            }
        }
    }
}

private suspend fun android.content.Context.shareApp(
    chooserTitle: String,
    message: String,
    @DrawableRes imageRes: Int?,
) {
    val body = buildString {
        val trimmed = message.trim()
        if (trimmed.isNotEmpty()) {
            append(trimmed)
            append("\n\n")
        }
        append(VAYANA_RELEASES_URL)
    }
    val imageUri = imageRes?.let { withContext(Dispatchers.IO) { stageSharePromoImage(it) } }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = if (imageUri != null) "image/jpeg" else "text/plain"
        putExtra(Intent.EXTRA_TEXT, body)
        if (imageUri != null) {
            putExtra(Intent.EXTRA_STREAM, imageUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    startActivity(Intent.createChooser(intent, chooserTitle))
}

/** Runs on IO. Bundled as WebP to keep the APK small; shared as JPEG, which every share target accepts. */
private fun android.content.Context.stageSharePromoImage(@DrawableRes imageRes: Int): Uri? = runCatching {
    val dir = File(cacheDir, "shared_images").apply { mkdirs() }
    // Encoded once per image per install or update, then reused.
    val prefix = "vayana_share_${resources.getResourceEntryName(imageRes)}_"
    val imageFile = File(dir, "$prefix${packageManager.getPackageInfo(packageName, 0).lastUpdateTime}.jpg")
    if (imageFile.length() == 0L) {
        dir.listFiles { file -> file.name.startsWith(prefix) }?.forEach(File::delete)
        val bitmap = BitmapFactory.decodeResource(resources, imageRes) ?: return@runCatching null
        val partial = File(dir, "${imageFile.name}.partial")
        try {
            partial.outputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.JPEG, SharePromoJpegQuality, output) }
        } finally {
            bitmap.recycle()
        }
        if (!partial.renameTo(imageFile)) return@runCatching null
    }
    FileProvider.getUriForFile(this, "$packageName.fileprovider", imageFile)
}.getOrNull()

private const val SharePromoJpegQuality = 92
private const val RelatedAppsColumns = 2
