package com.vayana.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import com.vayana.core.datastore.settings.BooleanSetting
import com.vayana.core.datastore.settings.ChoiceSetting
import com.vayana.core.datastore.settings.FloatSetting
import com.vayana.core.datastore.settings.IntSetting
import com.vayana.core.datastore.settings.Setting
import com.vayana.core.datastore.settings.SettingsGroup
import com.vayana.core.datastore.settings.SettingsRegistry
import com.vayana.core.datastore.settings.SettingsSnapshot
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R
import kotlin.math.roundToInt

@Composable
fun SettingsRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val settings by viewModel.settings.collectAsState()

    SettingsScreen(
        modifier = modifier,
        settings = settings,
        onBack = onBack,
        onUpdate = viewModel::update,
        onReset = viewModel::reset,
        onResetAll = viewModel::resetAll,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    modifier: Modifier = Modifier,
    settings: SettingsSnapshot,
    onBack: () -> Unit,
    onUpdate: (Setting<Any>, Any) -> Unit,
    onReset: (Setting<out Any>) -> Unit,
    onResetAll: () -> Unit,
) {
    var showResetAllDialog by remember { mutableStateOf(false) }
    var selectedGroup by remember { mutableStateOf<SettingsGroup?>(null) }
    var query by remember { mutableStateOf("") }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val visibleSettings = remember(query) { SettingsRegistry.all.filterByQuery(query) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(selectedGroup?.let { stringResource(it.titleRes) } ?: stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = { if (selectedGroup != null) selectedGroup = null else onBack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.settings_back_content_description))
                    }
                },
                actions = {
                    IconButton(onClick = { showResetAllDialog = true }) {
                        Icon(Icons.Outlined.RestartAlt, contentDescription = stringResource(R.string.settings_reset_all_content_description))
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        if (selectedGroup == null) {
            SettingsHub(
                contentPadding = innerPadding,
                query = query,
                visibleSettings = visibleSettings,
                settings = settings,
                onQueryChange = { query = it },
                onGroupSelected = { selectedGroup = it },
                onUpdate = onUpdate,
                onReset = onReset,
            )
        } else {
            SettingsGroupDetail(
                contentPadding = innerPadding,
                group = selectedGroup,
                settings = settings,
                onUpdate = onUpdate,
                onReset = onReset,
            )
        }
    }

    if (showResetAllDialog) {
        AlertDialog(
            onDismissRequest = { showResetAllDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetAllDialog = false
                        onResetAll()
                    },
                ) { Text(stringResource(R.string.settings_reset_all_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetAllDialog = false }) {
                    Text(stringResource(R.string.settings_reset_all_cancel))
                }
            },
            title = { Text(stringResource(R.string.settings_reset_all_title)) },
            text = { Text(stringResource(R.string.settings_reset_all_body)) },
        )
    }
}

@Composable
private fun SettingsHub(
    contentPadding: PaddingValues,
    query: String,
    visibleSettings: List<Setting<out Any>>,
    settings: SettingsSnapshot,
    onQueryChange: (String) -> Unit,
    onGroupSelected: (SettingsGroup) -> Unit,
    onUpdate: (Setting<Any>, Any) -> Unit,
    onReset: (Setting<out Any>) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Paddings.screenHorizontal,
            top = contentPadding.calculateTopPadding() + Spacing.md,
            end = Paddings.screenHorizontal,
            bottom = contentPadding.calculateBottomPadding() + Spacing.xl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        item {
            SettingsSearchField(query = query, onQueryChange = onQueryChange)
        }
        if (query.isBlank()) {
            item { SettingsProfileCard() }
            items(SettingsGroup.entries, key = { it.name }) { group ->
                val groupSettings = SettingsRegistry.all.filter { it.group == group }
                if (groupSettings.isNotEmpty()) {
                    SettingsGroupCard(
                        group = group,
                        settingCount = groupSettings.size,
                        onClick = { onGroupSelected(group) },
                    )
                }
            }
        } else if (visibleSettings.isEmpty()) {
            item { SettingsNoMatches() }
        } else {
            items(visibleSettings, key = { it.key }) { setting ->
                SettingRow(
                    setting = setting,
                    value = settings.valueFor(setting),
                    onUpdate = onUpdate,
                    onReset = onReset,
                )
            }
        }
    }
}

@Composable
private fun SettingsGroupDetail(
    contentPadding: PaddingValues,
    group: SettingsGroup?,
    settings: SettingsSnapshot,
    onUpdate: (Setting<Any>, Any) -> Unit,
    onReset: (Setting<out Any>) -> Unit,
) {
    val groupSettings = SettingsRegistry.all.filter { it.group == group }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Paddings.screenHorizontal,
            top = contentPadding.calculateTopPadding() + Spacing.md,
            end = Paddings.screenHorizontal,
            bottom = contentPadding.calculateBottomPadding() + Spacing.xl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(groupSettings, key = { it.key }) { setting ->
            SettingRow(
                setting = setting,
                value = settings.valueFor(setting),
                onUpdate = onUpdate,
                onReset = onReset,
            )
        }
    }
}

@Composable
private fun SettingsSearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(Radii.full),
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        placeholder = {
            Text(
                text = stringResource(R.string.settings_search_placeholder),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@Composable
private fun SettingsProfileCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.largeIncreased),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(Paddings.card),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SettingsIconBubble(
                icon = Icons.Outlined.AutoStories,
                selected = true,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_profile_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.settings_profile_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Surface(
                shape = RoundedCornerShape(Radii.full),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Text(
                    text = stringResource(R.string.app_tagline),
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SettingsGroupCard(group: SettingsGroup, settingCount: Int, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(Radii.largeIncreased),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(Paddings.card),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SettingsIconBubble(icon = group.icon(), selected = false)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(group.titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = group.subtitle(settingCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
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
private fun SettingsIconBubble(icon: ImageVector, selected: Boolean) {
    Surface(
        modifier = Modifier.size(Sizes.touchTarget),
        shape = CircleShape,
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
        shape = RoundedCornerShape(Radii.largeIncreased),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(Paddings.card),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                modifier = Modifier.size(Sizes.iconLarge),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.settings_no_matches_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = Spacing.md),
            )
            Text(
                text = stringResource(R.string.settings_no_matches_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.large),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Paddings.card),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                SettingsIconBubble(icon = setting.group.icon(), selected = false)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(setting.titleRes),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    setting.subtitleRes?.let { subtitleRes ->
                        Text(
                            text = stringResource(subtitleRes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (setting is BooleanSetting) {
                        Switch(checked = value as Boolean, onCheckedChange = { onUpdate(setting.asAny(), it) })
                    }
                    IconButton(onClick = { onReset(setting) }) {
                        Icon(Icons.Outlined.RestartAlt, contentDescription = stringResource(R.string.settings_reset_one_content_description))
                    }
                }
            }
            when (setting) {
                is BooleanSetting -> Unit
                is IntSetting -> IntSettingControl(setting = setting, value = value as Int, onUpdate = { onUpdate(setting.asAny(), it) })
                is FloatSetting -> FloatSettingControl(setting = setting, value = value as Float, onUpdate = { onUpdate(setting.asAny(), it) })
                is ChoiceSetting<*> -> ChoiceSettingControl(setting = setting, value = value, onUpdate = { onUpdate(setting.asAny(), it) })
            }
        }
    }
}

@Composable
private fun IntSettingControl(setting: IntSetting, value: Int, onUpdate: (Int) -> Unit) {
    Column(modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.md)) {
        Text(text = value.toString(), style = MaterialTheme.typography.labelLarge)
        Slider(
            value = value.toFloat(),
            onValueChange = { onUpdate((it / setting.step).roundToInt() * setting.step) },
            valueRange = setting.range.first.toFloat()..setting.range.last.toFloat(),
            steps = ((setting.range.last - setting.range.first) / setting.step - 1).coerceAtLeast(0),
        )
    }
}

@Composable
private fun FloatSettingControl(setting: FloatSetting, value: Float, onUpdate: (Float) -> Unit) {
    Column(modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.md)) {
        Text(text = "%.1f".format(value), style = MaterialTheme.typography.labelLarge)
        Slider(
            value = value,
            onValueChange = { raw -> onUpdate(((raw / setting.step).roundToInt() * setting.step).coerceIn(setting.range.start, setting.range.endInclusive)) },
            valueRange = setting.range,
            steps = (((setting.range.endInclusive - setting.range.start) / setting.step).roundToInt() - 1).coerceAtLeast(0),
        )
    }
}

@Composable
private fun ChoiceSettingControl(setting: ChoiceSetting<*>, value: Any, onUpdate: (Any) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        setting.options.forEach { option ->
            val selected = option.value == value
            FilterChip(
                selected = selected,
                onClick = { onUpdate(option.value) },
                label = { Text(stringResource(option.labelRes)) },
                leadingIcon = if (selected) {
                    { Icon(Icons.Outlined.CheckCircle, contentDescription = null) }
                } else {
                    null
                },
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
    SettingsGroup.MAINTENANCE -> Icons.Outlined.Storage
}

@Composable
private fun SettingsGroup.subtitle(settingCount: Int): String = when (this) {
    SettingsGroup.MAINTENANCE -> stringResource(subtitleRes, settingCount)
    else -> stringResource(subtitleRes)
}
