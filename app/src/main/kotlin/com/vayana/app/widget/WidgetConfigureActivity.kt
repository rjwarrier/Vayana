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
                darkVariant = settings.darkVariant,
                motionSetting = settings.motionSetting,
                dynamicColor = settings.dynamicColor,
                dateFormatStyle = settings.dateFormatStyle,
            ) {
                WidgetConfigureScreen(
                    initial = WidgetAppearance(settings.widgetCornerRadius, settings.widgetProgressStyle),
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
            onSaved()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetConfigureScreen(
    initial: WidgetAppearance,
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
    val appearance = WidgetAppearance(cornerRadius, progressStyle)

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
            Text(
                stringResource(R.string.widget_configure_applies_to_all),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionTitle(stringResource(R.string.widget_corners))
            ListItem(
                headlineContent = { Text(stringResource(R.string.widget_corners_match)) },
                supportingContent = { Text(stringResource(R.string.widget_corners_match_subtitle)) },
                trailingContent = {
                    Switch(
                        checked = cornerRadius == WidgetCornerRadiusMatchLauncher,
                        onCheckedChange = { match -> cornerRadius = if (match) WidgetCornerRadiusMatchLauncher else customRadius },
                    )
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            )
            if (cornerRadius != WidgetCornerRadiusMatchLauncher) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Slider(
                        value = cornerRadius.toFloat(),
                        onValueChange = { value ->
                            val stepped = (value / WidgetCornerRadiusStep).toInt().coerceIn(0, WidgetCornerRadiusMax / WidgetCornerRadiusStep) * WidgetCornerRadiusStep
                            cornerRadius = stepped
                            customRadius = stepped
                        },
                        valueRange = 0f..WidgetCornerRadiusMax.toFloat(),
                        steps = WidgetCornerRadiusMax / WidgetCornerRadiusStep - 1,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        stringResource(R.string.widget_corners_value, cornerRadius),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.widthIn(min = Sizes.touchTarget),
                    )
                }
            }

            SectionTitle(stringResource(R.string.widget_progress_style))
            val styles = listOf(
                WidgetProgressStyle.FLAT to stringResource(R.string.widget_progress_flat),
                WidgetProgressStyle.SQUIGGLY to stringResource(R.string.widget_progress_squiggly),
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                styles.forEachIndexed { index, (style, label) ->
                    SegmentedButton(
                        selected = progressStyle == style,
                        onClick = { progressStyle = style },
                        shape = SegmentedButtonDefaults.itemShape(index, styles.size),
                        label = { Text(label) },
                    )
                }
            }
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
