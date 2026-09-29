package com.vayana.feature.reader

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vayana.core.common.AppLanguage
import com.vayana.core.designsystem.theme.VayanaTheme
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R
import com.vayana.dictionary.api.DictionaryEntry
import com.vayana.dictionary.api.OnlineDictionarySource
import dagger.hilt.android.AndroidEntryPoint

/**
 * "Define" in any app's text-selection menu: a card over that app with Vayana's dictionary entry for the word, so the
 * dictionary isn't only for books. Tapping outside, Close or Back returns to the app underneath.
 */
@AndroidEntryPoint
class DefineActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.trim().orEmpty()
        setContent {
            val viewModel: DefineViewModel = hiltViewModel()
            LaunchedEffect(viewModel, text) { viewModel.define(text) }
            val settings = viewModel.settings.collectAsStateWithLifecycle().value ?: return@setContent
            val lookup by viewModel.lookup.collectAsStateWithLifecycle()
            VayanaTheme(
                themeMode = settings.themeMode,
                displayProfile = settings.displayProfile,
                darkVariant = settings.darkVariant,
                motionSetting = settings.motionSetting,
                dynamicColor = settings.dynamicColor,
                dateFormatStyle = settings.dateFormatStyle,
            ) {
                DefineCard(
                    text = text,
                    lookup = lookup,
                    onLookupWord = viewModel::lookupWord,
                    onLookupOnline = viewModel::lookupOnline,
                    onSaveToVocabulary = viewModel::saveToVocabulary,
                    onClose = ::finish,
                )
            }
        }
    }
}

@Composable
private fun DefineCard(
    text: String,
    lookup: DictionaryLookupState,
    onLookupWord: (String) -> Unit,
    onLookupOnline: (OnlineDictionarySource) -> Unit,
    onSaveToVocabulary: (DictionaryEntry) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // The other app, dimmed: a tap on it closes the card.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = ScrimAlpha))
                .clickable(interactionSource = null, indication = null, onClick = onClose),
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(Paddings.screenHorizontal, Spacing.md)
                .widthIn(max = Sizes.contentMaxWidth)
                .fillMaxWidth()
                .heightIn(max = maxHeight * MaxHeightFraction),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLargeIncreased,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Paddings.card, vertical = Spacing.lg),
            ) {
                when (lookup) {
                    // Nothing to look up: an empty selection, or more than a phrase.
                    DictionaryLookupState.Hidden -> LookupMessage(stringResource(R.string.define_nothing))
                    is DictionaryLookupState.PackRequired -> {
                        LookupMessage(stringResource(R.string.define_pack_missing))
                        OnlineLookupButtons(onLookupOnline = onLookupOnline)
                    }
                    else -> DictionaryLookupContent(
                        state = lookup,
                        recentLookups = emptyList(),
                        onDownloadDictionary = {},
                        onInstallDictionary = {},
                        onLookupWord = onLookupWord,
                        onLookupOnline = onLookupOnline,
                        onSaveLookupAsNote = null,
                        onSaveLookupAsVocabulary = onSaveToVocabulary,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.End),
                ) {
                    if (text.isNotEmpty()) {
                        TextButton(onClick = {
                            if (!context.translateText(text)) {
                                Toast.makeText(context, R.string.reader_translate_unavailable, Toast.LENGTH_LONG).show()
                            }
                        }) {
                            Text(stringResource(R.string.reader_selection_translate))
                        }
                    }
                    TextButton(onClick = onClose) { Text(stringResource(R.string.define_close)) }
                }
            }
        }
    }
}

private const val ScrimAlpha = 0.32f
private const val MaxHeightFraction = 0.75f
