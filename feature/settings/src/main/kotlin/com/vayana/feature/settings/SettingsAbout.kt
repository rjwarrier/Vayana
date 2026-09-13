package com.vayana.feature.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.annotation.DrawableRes
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
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.FileProvider
import com.vayana.core.designsystem.dialog.ExpressiveDialogHeader
import com.vayana.core.designsystem.dialog.ExpressiveDialogSurface
import com.vayana.core.designsystem.tokens.Elevations
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R
import java.io.File
import android.graphics.Bitmap
import android.graphics.BitmapFactory

private val SettingsAboutBadgeSize = Sizes.badgeLarge
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
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
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
}

@Composable
private fun SettingsAboutSection() {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var showShareDialog by rememberSaveable { mutableStateOf(false) }
    val packageInfo = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    val versionCode = remember(packageInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
    }
    val shareTitle = stringResource(R.string.about_share)
    val shareText = stringResource(R.string.about_share_text)
    val initialShareTheme = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        SharePromoTheme.DARK
    } else {
        SharePromoTheme.LIGHT
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
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
                    imageVector = Icons.Outlined.AutoStories,
                    contentDescription = null,
                    modifier = Modifier.size(Sizes.iconLarge),
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
        Spacer(modifier = Modifier.height(Spacing.sm))
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Text(
                text = stringResource(
                    R.string.about_version,
                    packageInfo.versionName ?: "0.1.0",
                    versionCode.toString(),
                ),
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(modifier = Modifier.height(Spacing.sm))
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
        Spacer(modifier = Modifier.height(Spacing.sm))
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
            onClick = { uriHandler.openUri(VAYANA_RELEASES_URL) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(imageVector = Icons.Outlined.Language, contentDescription = null, modifier = Modifier.size(Sizes.iconSmall))
            Spacer(modifier = Modifier.width(Spacing.xs))
            Text(text = stringResource(R.string.about_website), textAlign = TextAlign.Center)
        }
    }

    if (showShareDialog) {
        ShareVayanaDialog(
            initialMessage = shareText,
            initialTheme = initialShareTheme,
            chooserTitle = shareTitle,
            onDismissRequest = { showShareDialog = false },
            onShare = { message, imageRes ->
                context.shareApp(shareTitle, message, imageRes)
            },
        )
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

private fun android.content.Context.shareApp(
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
    val imageUri = imageRes?.let { stageSharePromoImage(it) }
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

private fun android.content.Context.stageSharePromoImage(@DrawableRes imageRes: Int): Uri? = runCatching {
    val dir = File(cacheDir, "shared_images").apply { mkdirs() }
    val imageFile = File(dir, "vayana_share.jpg")
    // Bundled as WebP to keep the APK small; shared as JPEG, which every share target accepts.
    val bitmap = BitmapFactory.decodeResource(resources, imageRes) ?: return@runCatching null
    try {
        imageFile.outputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.JPEG, SharePromoJpegQuality, output) }
    } finally {
        bitmap.recycle()
    }
    FileProvider.getUriForFile(this, "$packageName.fileprovider", imageFile)
}.getOrNull()

private const val SharePromoJpegQuality = 92
