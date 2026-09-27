package com.vayana.feature.help

import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vayana.core.designsystem.tokens.Elevations
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R

private val HelpPagePadding = Paddings.page
private val HelpIconBadgeSize = Sizes.badge

data class HelpSection(
    @param:StringRes val title: Int,
    @param:StringRes val description: Int,
    @param:ArrayRes val bullets: Int,
    val icon: ImageVector,
)

private val helpSections = listOf(
    HelpSection(
        title = R.string.help_section_library_title,
        description = R.string.help_section_library_description,
        bullets = R.array.help_section_library_tips,
        icon = Icons.AutoMirrored.Outlined.MenuBook,
    ),
    HelpSection(
        title = R.string.help_section_reader_title,
        description = R.string.help_section_reader_description,
        bullets = R.array.help_section_reader_tips,
        icon = Icons.Outlined.Bookmarks,
    ),
    HelpSection(
        title = R.string.help_section_notes_title,
        description = R.string.help_section_notes_description,
        bullets = R.array.help_section_notes_tips,
        icon = Icons.Outlined.EditNote,
    ),
    HelpSection(
        title = R.string.help_section_statistics_title,
        description = R.string.help_section_statistics_description,
        bullets = R.array.help_section_statistics_tips,
        icon = Icons.Outlined.BarChart,
    ),
    HelpSection(
        title = R.string.help_section_search_title,
        description = R.string.help_section_search_description,
        bullets = R.array.help_section_search_tips,
        icon = Icons.Outlined.Search,
    ),
    HelpSection(
        title = R.string.help_section_sync_title,
        description = R.string.help_section_sync_description,
        bullets = R.array.help_section_sync_tips,
        icon = Icons.Outlined.Autorenew,
    ),
    HelpSection(
        title = R.string.help_section_trash_title,
        description = R.string.help_section_trash_description,
        bullets = R.array.help_section_trash_tips,
        icon = Icons.Outlined.DeleteOutline,
    ),
    HelpSection(
        title = R.string.help_section_settings_title,
        description = R.string.help_section_settings_description,
        bullets = R.array.help_section_settings_tips,
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
                        text = stringResource(section.description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                stringArrayResource(section.bullets).forEach { bullet ->
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
