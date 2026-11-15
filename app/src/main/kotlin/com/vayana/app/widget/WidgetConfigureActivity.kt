package com.vayana.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.annotation.StringRes
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vayana.core.common.AppLanguage
import com.vayana.core.datastore.settings.SettingsRepository
import com.vayana.core.datastore.settings.SettingsRegistry
import com.vayana.core.datastore.settings.SettingsSnapshot
import com.vayana.core.datastore.settings.WidgetChartDaysMax
import com.vayana.core.datastore.settings.WidgetChartDaysMin
import com.vayana.core.datastore.settings.WidgetChartGridOpacityStep
import com.vayana.core.datastore.settings.WidgetChartStyle
import com.vayana.core.datastore.settings.WidgetCornerRadiusMatchLauncher
import com.vayana.core.datastore.settings.WidgetCornerRadiusMax
import com.vayana.core.datastore.settings.WidgetCornerRadiusStep
import com.vayana.core.datastore.settings.WidgetProgressStyle
import com.vayana.core.designsystem.theme.VayanaTheme
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The one configure screen for every Vayana widget: the home screen opens it when a widget is added (optional from
 * Android 12) or edited. It styles all Vayana widgets at once, with a live preview of the widget it was opened for.
 */
@AndroidEntryPoint
class WidgetConfigureActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        // Leaving without Done while adding a widget cancels the add, as Android expects.
        setResult(RESULT_CANCELED, resultIntent(widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)?.provider?.className
        setContent {
            val viewModel: WidgetConfigureViewModel = hiltViewModel()
            val settings = viewModel.settings.collectAsStateWithLifecycle().value ?: return@setContent
            VayanaTheme(
                themeMode = settings.themeMode,
                displayProfile = settings.displayProfile,
                einkPalette = settings.einkPalette,
                darkVariant = settings.darkVariant,
                motionSetting = settings.motionSetting,
                dynamicColor = settings.dynamicColor,
                dateFormatStyle = settings.dateFormatStyle,
            ) {
                WidgetConfigureScreen(
                    initial = settings.widgetAppearance(),
                    // Each widget shows only the options it draws; the rest keep their saved values. The overview
                    // widget charts too, but has no header/fill option of its own - that's the reading-time widget's
                    // own layout toggle.
                    showsChart = provider == ReadingTimeWidget::class.java.name || provider == OverviewWidget::class.java.name,
                    chartLayout = provider == ReadingTimeWidget::class.java.name,
                    preview = { appearance -> viewModel.preview(provider, appearance) },
                    onCancel = ::finish,
                    onDone = { appearance ->
                        viewModel.save(appearance) {
                            setResult(RESULT_OK, resultIntent(widgetId))
                            finish()
                        }
                    },
                )
            }
        }
    }

    private fun resultIntent(widgetId: Int) = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
}

@HiltViewModel
class WidgetConfigureViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val widgets: Set<@JvmSuppressWildcards VayanaWidget>,
) : ViewModel() {
    val settings: StateFlow<SettingsSnapshot?> = settingsRepository.snapshot
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The widget being configured, drawn as it would be; null for a provider that isn't one of Vayana's. */
    suspend fun preview(providerClassName: String?, appearance: WidgetAppearance): RemoteViews? =
        widgets.firstOrNull { it.provider.name == providerClassName }?.preview(appearance)

    /** Saves for every widget; each redraws itself from the setting. */
    fun save(appearance: WidgetAppearance, onSaved: () -> Unit) {
        viewModelScope.launch {
            settingsRepository.update(SettingsRegistry.WidgetCornerRadius, appearance.cornerRadiusDp)
            settingsRepository.update(SettingsRegistry.WidgetProgressBar, appearance.progressStyle)
            settingsRepository.update(SettingsRegistry.WidgetBorder, appearance.border)
            settingsRepository.update(SettingsRegistry.WidgetChart, appearance.chartStyle)
            settingsRepository.update(SettingsRegistry.WidgetChartAxisLabels, appearance.chartAxisLabels)
            settingsRepository.update(SettingsRegistry.WidgetChartGridOpacity, appearance.chartGridOpacity)
            settingsRepository.update(SettingsRegistry.WidgetChartDays, appearance.chartDays)
            settingsRepository.update(SettingsRegistry.WidgetChartHeader, appearance.chartHeader)
            settingsRepository.update(SettingsRegistry.WidgetChartFill, appearance.chartFill)
            onSaved()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetConfigureScreen(
    initial: WidgetAppearance,
    showsChart: Boolean,
    chartLayout: Boolean,
    preview: suspend (WidgetAppearance) -> RemoteViews?,
    onCancel: () -> Unit,
    onDone: (WidgetAppearance) -> Unit,
) {
    var cornerRadius by rememberSaveable { mutableIntStateOf(initial.cornerRadiusDp) }
    // The last chosen radius, so turning "match home screen" off returns to it.
    var customRadius by rememberSaveable {
        mutableIntStateOf(initial.cornerRadiusDp.takeIf { it >= 0 } ?: DefaultCustomRadius)
    }
    var progressStyle by rememberSaveable { mutableStateOf(initial.progressStyle) }
    var chartStyle by rememberSaveable { mutableStateOf(initial.chartStyle) }
    var chartAxisLabels by rememberSaveable { mutableStateOf(initial.chartAxisLabels) }
    var chartGridOpacity by rememberSaveable { mutableIntStateOf(initial.chartGridOpacity) }
    var chartDays by rememberSaveable { mutableIntStateOf(initial.chartDays) }
    var chartHeader by rememberSaveable { mutableStateOf(initial.chartHeader) }
    var chartFill by rememberSaveable { mutableStateOf(initial.chartFill) }
    var border by rememberSaveable { mutableStateOf(initial.border) }
    val appearance = WidgetAppearance(
        cornerRadius, progressStyle, chartStyle, chartAxisLabels, chartGridOpacity, chartDays, chartHeader, chartFill, border,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.widget_configure_title)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.define_close))
                    }
                },
            )
        },
        bottomBar = {
            Box(modifier = Modifier.fillMaxWidth().padding(Paddings.screenHorizontal, Spacing.md), contentAlignment = Alignment.CenterEnd) {
                Button(onClick = { onDone(appearance) }) { Text(stringResource(R.string.widget_configure_done)) }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Paddings.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            WidgetPreview(appearance, preview)

            // This widget's own options first; the style every Vayana widget shares comes last.
            if (showsChart) {
                if (chartLayout) {
                    SectionTitle(stringResource(R.string.widget_section_layout))
                    SwitchRow(R.string.widget_chart_header, R.string.widget_chart_header_subtitle, chartHeader) { chartHeader = it }
                    // Filling the widget takes the header's room, so it is offered only without one.
                    if (!chartHeader) {
                        SwitchRow(R.string.widget_chart_fill, R.string.widget_chart_fill_subtitle, chartFill) { chartFill = it }
                    }
                }

                SectionTitle(stringResource(R.string.widget_chart_style))
                ChoiceRow(
                    options = listOf(
                        WidgetChartStyle.LINE to stringResource(R.string.widget_chart_line),
                        WidgetChartStyle.CURVE to stringResource(R.string.widget_chart_curve),
                        WidgetChartStyle.BARS to stringResource(R.string.widget_chart_bars),
                    ),
                    selected = chartStyle,
                    onSelect = { chartStyle = it },
                )
                SliderRow(
                    label = stringResource(R.string.widget_chart_days),
                    valueText = pluralStringResource(R.plurals.widget_chart_days_value, chartDays, chartDays),
                    value = chartDays,
                    range = WidgetChartDaysMin..WidgetChartDaysMax,
                    step = 1,
                    onChange = { chartDays = it },
                )
                // A filled graph has no room for labels, so the choice is hidden rather than ignored.
                if (!appearance.chartFillsWidget) {
                    SwitchRow(R.string.widget_chart_axis_labels, R.string.widget_chart_axis_labels_subtitle, chartAxisLabels) {
                        chartAxisLabels = it
                    }
                }
                SliderRow(
                    label = stringResource(R.string.widget_chart_grid),
                    valueText = stringResource(R.string.percent_value, chartGridOpacity),
                    value = chartGridOpacity,
                    range = 0..100,
                    step = WidgetChartGridOpacityStep,
                    onChange = { chartGridOpacity = it },
                )
            } else {
                SectionTitle(stringResource(R.string.widget_progress_style))
                ChoiceRow(
                    options = listOf(
                        WidgetProgressStyle.FLAT to stringResource(R.string.widget_progress_flat),
                        WidgetProgressStyle.SQUIGGLY to stringResource(R.string.widget_progress_squiggly),
                    ),
                    selected = progressStyle,
                    onSelect = { progressStyle = it },
                )
            }

            SectionTitle(stringResource(R.string.widget_section_all))
            Text(
                stringResource(R.string.widget_configure_applies_to_all),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SwitchRow(R.string.widget_corners_match, R.string.widget_corners_match_subtitle, cornerRadius == WidgetCornerRadiusMatchLauncher) {
                match -> cornerRadius = if (match) WidgetCornerRadiusMatchLauncher else customRadius
            }
            if (cornerRadius != WidgetCornerRadiusMatchLauncher) {
                SliderRow(
                    label = stringResource(R.string.widget_corners),
                    valueText = stringResource(R.string.widget_corners_value, cornerRadius),
                    value = cornerRadius,
                    range = 0..WidgetCornerRadiusMax,
                    step = WidgetCornerRadiusStep,
                    onChange = {
                        cornerRadius = it
                        customRadius = it
                    },
                )
            }
            SwitchRow(R.string.widget_border, R.string.widget_border_subtitle, border) { border = it }
        }
    }
}

/** The actual widget, drawn from its own RemoteViews, on a tonal backdrop standing in for the home screen. */
@Composable
private fun WidgetPreview(appearance: WidgetAppearance, preview: suspend (WidgetAppearance) -> RemoteViews?) {
    val views by produceState<RemoteViews?>(null, appearance) { value = preview(appearance) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.large),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Box(modifier = Modifier.padding(Spacing.lg), contentAlignment = Alignment.Center) {
            val remoteViews = views
            if (remoteViews != null) {
                AndroidView(
                    factory = { context -> FrameLayout(context) },
                    update = { frame ->
                        frame.removeAllViews()
                        frame.addView(remoteViews.apply(frame.context, frame))
                    },
                    modifier = Modifier.widthIn(max = Sizes.contentMaxWidth).fillMaxWidth().height(Sizes.widgetPreviewHeight),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = selected == value,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun SwitchRow(@StringRes title: Int, @StringRes subtitle: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(subtitle)) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

/** A labelled slider over whole [step]s of [range], its label in line with the switch rows' text. */
@Composable
private fun SliderRow(label: String, valueText: String, value: Int, range: IntRange, step: Int, onChange: (Int) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Slider(
                value = value.toFloat(),
                onValueChange = { raw ->
                    onChange((range.first + ((raw - range.first) / step).roundToInt() * step).coerceIn(range))
                },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                steps = (range.last - range.first) / step - 1,
                modifier = Modifier.weight(1f),
            )
            Text(valueText, style = MaterialTheme.typography.labelLarge, modifier = Modifier.widthIn(min = Sizes.touchTarget))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = Spacing.sm),
    )
}

private const val DefaultCustomRadius = 24

internal fun SettingsSnapshot.widgetAppearance() =
    WidgetAppearance(
        widgetCornerRadius, widgetProgressStyle, widgetChartStyle, widgetChartAxisLabels, widgetChartGridOpacity, widgetChartDays,
        widgetChartHeader, widgetChartFill, widgetBorder,
    )
