package com.vayana.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.res.stringResource
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
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.settings_back_content_description))
                    }
                },
                actions = {
                    IconButton(onClick = { showResetAllDialog = true }) {
                        Icon(Icons.Outlined.RestartAlt, contentDescription = stringResource(R.string.settings_reset_all_content_description))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Paddings.screenHorizontal,
                top = innerPadding.calculateTopPadding() + Spacing.sm,
                end = Paddings.screenHorizontal,
                bottom = innerPadding.calculateBottomPadding() + Spacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            SettingsGroup.entries.forEach { group ->
                val groupSettings = SettingsRegistry.all.filter { it.group == group }
                if (groupSettings.isNotEmpty()) {
                    item(key = group.name) {
                        Text(
                            text = stringResource(group.titleRes),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.xs),
                        )
                    }
                    groupSettings.forEach { setting ->
                        item(key = setting.key) {
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
private fun SettingRow(
    setting: Setting<out Any>,
    value: Any,
    onUpdate: (Setting<Any>, Any) -> Unit,
    onReset: (Setting<out Any>) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(stringResource(setting.titleRes)) },
            supportingContent = setting.subtitleRes?.let { subtitleRes -> ({ Text(stringResource(subtitleRes)) }) },
            trailingContent = {
                IconButton(onClick = { onReset(setting) }) {
                    Icon(Icons.Outlined.RestartAlt, contentDescription = stringResource(R.string.settings_reset_one_content_description))
                }
            },
        )
        when (setting) {
            is BooleanSetting -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Switch(checked = value as Boolean, onCheckedChange = { onUpdate(setting.asAny(), it) })
                }
            }
            is IntSetting -> IntSettingControl(setting = setting, value = value as Int, onUpdate = { onUpdate(setting.asAny(), it) })
            is FloatSetting -> FloatSettingControl(setting = setting, value = value as Float, onUpdate = { onUpdate(setting.asAny(), it) })
            is ChoiceSetting<*> -> ChoiceSettingControl(setting = setting, value = value, onUpdate = { onUpdate(setting.asAny(), it) })
        }
    }
}

@Composable
private fun IntSettingControl(setting: IntSetting, value: Int, onUpdate: (Int) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
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
    Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
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
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = setting.options.firstOrNull { it.value == value }?.labelRes ?: setting.options.first().labelRes

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg),
    ) {
        TextButton(onClick = { expanded = true }) {
            Text(text = stringResource(selectedLabel), style = MaterialTheme.typography.bodyLarge)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            setting.options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelRes)) },
                    onClick = {
                        expanded = false
                        onUpdate(option.value)
                    },
                )
            }
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
