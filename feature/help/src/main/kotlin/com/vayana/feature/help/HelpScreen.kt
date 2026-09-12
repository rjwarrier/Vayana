package com.vayana.feature.help

import android.content.Intent
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vayana.core.designsystem.tokens.Elevations
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R

private val HelpPagePadding = 20.dp
private val HelpIconBadgeSize = 42.dp
private val AboutIconBadgeSize = 64.dp

private const val VAYANA_GITHUB_URL = "https://github.com/rjwarrier/Vayana"
private const val VAYANA_RELEASES_URL = "https://github.com/rjwarrier/Vayana/releases"

data class HelpSection(
    @param:StringRes val title: Int,
    val description: String,
    val bullets: List<String>,
    val icon: ImageVector,
)

private val helpSections = listOf(
    HelpSection(
        title = R.string.help_section_library_title,
        description = "Your bookshelf — everything you're reading, have read, or plan to.",
        bullets = listOf(
            "Tap a book's cover to jump straight into the reader.",
            "Open a book's details to edit metadata, change covers, add shelves, or remove it.",
            "Use shelves to group books by series, genre, or mood.",
            "Pull down to sync recent changes from your other devices.",
        ),
        icon = Icons.AutoMirrored.Outlined.MenuBook,
    ),
    HelpSection(
        title = R.string.help_section_reader_title,
        description = "The reading view itself, with highlights, notes, and typography controls.",
        bullets = listOf(
            "Tap the center of the page to show or hide the reading toolbar.",
            "Select text to highlight it or attach a note.",
            "Open the type settings to change font, size, line spacing, and theme.",
            "Use the table of contents to jump between chapters instantly.",
            "Your position syncs automatically as you read.",
        ),
        icon = Icons.Outlined.Bookmarks,
    ),
    HelpSection(
        title = R.string.help_section_notes_title,
        description = "All highlights and notes you've captured across every book, in one place.",
        bullets = listOf(
            "Filter notes by book, type, or source to find something quickly.",
            "Tap a note to jump back to that exact spot in the book.",
            "Edit or delete a note directly from this list.",
            "Export notes to share or back them up outside the app.",
        ),
        icon = Icons.Outlined.EditNote,
    ),
    HelpSection(
        title = R.string.help_section_statistics_title,
        description = "Reading streaks, time spent, and vocabulary you've been building.",
        bullets = listOf(
            "Check your daily and weekly reading streaks at a glance.",
            "See time spent per book and overall reading pace.",
            "Review vocabulary you've looked up with spaced-repetition flashcards.",
            "Trends update automatically as you keep reading.",
        ),
        icon = Icons.Outlined.BarChart,
    ),
    HelpSection(
        title = R.string.help_section_search_title,
        description = "Find any book, note, or highlight across your entire library.",
        bullets = listOf(
            "Search matches titles, authors, notes, and highlighted text.",
            "Tap a result to open it at the exact matching location.",
            "Use search from within a book to jump to other mentions of a phrase.",
        ),
        icon = Icons.Outlined.Search,
    ),
    HelpSection(
        title = R.string.help_section_sync_title,
        description = "Keep your library, notes, and progress backed up and synced across devices.",
        bullets = listOf(
            "Connect a GitHub repository to sync your data automatically.",
            "Create a manual backup file any time from Settings.",
            "Restore from a backup file to recover your library on a new device.",
            "Test your connection if sync ever stops working.",
        ),
        icon = Icons.Outlined.Autorenew,
    ),
    HelpSection(
        title = R.string.help_section_trash_title,
        description = "Recently removed books are kept here before they're gone for good.",
        bullets = listOf(
            "Restore a deleted book back to your library from the Recently deleted screen.",
            "Permanently delete a book to free up space immediately.",
            "Use permanent delete only when you are sure the book should be gone.",
        ),
        icon = Icons.Outlined.DeleteOutline,
    ),
    HelpSection(
        title = R.string.help_section_settings_title,
        description = "Customize appearance, reading behavior, goals, and data.",
        bullets = listOf(
            "Search settings directly instead of hunting through categories.",
            "Adjust appearance, typography, and layout to match your taste.",
            "Set reading goals to track progress over time.",
            "Reset a single setting or everything back to defaults.",
        ),
        icon = Icons.Outlined.Settings,
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.help_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.help_back_content_description),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = HelpPagePadding,
                top = innerPadding.calculateTopPadding() + HelpPagePadding,
                end = HelpPagePadding,
                bottom = innerPadding.calculateBottomPadding() + HelpPagePadding,
            ),
            verticalArrangement = Arrangement.spacedBy(HelpPagePadding),
        ) {
            item {
                AboutVayanaCard()
            }
            item {
                AboutActions()
            }
            item {
                Text(
                    text = stringResource(R.string.help_heading),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            items(helpSections) { section ->
                HelpSectionCard(section)
            }
        }
    }
}

@Composable
private fun AboutVayanaCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
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
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.large),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = Elevations.none,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Surface(
                modifier = Modifier.size(AboutIconBadgeSize),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
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
}

@Composable
private fun AboutActions(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val shareTitle = stringResource(R.string.about_share)
    val shareText = stringResource(R.string.about_share_text)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.large),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        tonalElevation = Elevations.none,
    ) {
        Column(
            modifier = Modifier.padding(Paddings.card),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
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
                    onClick = { context.shareApp(shareTitle, shareText) },
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
    }
}

@Composable
private fun HelpSectionCard(section: HelpSection) {
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Surface(
                    modifier = Modifier.size(HelpIconBadgeSize),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = section.icon,
                            contentDescription = null,
                            modifier = Modifier.size(Sizes.iconSmall),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(section.title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        text = section.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                section.bullets.forEach { bullet ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(
                            text = "-",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = bullet,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

private fun android.content.Context.shareApp(chooserTitle: String, shareText: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareText)
    }
    startActivity(Intent.createChooser(intent, chooserTitle))
}
