package com.vayana.feature.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R

@Composable
internal fun SpeechPronunciationEditor(controls: ReadAloudVoiceControls) {
    var original by rememberSaveable { mutableStateOf("") }
    var spoken by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(stringResource(R.string.reader_pronunciation_title), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.reader_pronunciation_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        controls.pronunciations.forEach { rule ->
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { original = rule.original; spoken = rule.spoken }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.reader_pronunciation_rule, rule.original, rule.spoken))
                }
                TextButton(onClick = { controls.onRemovePronunciation(rule.original) }) {
                    Text(stringResource(R.string.reader_pronunciation_remove))
                }
            }
        }
        OutlinedTextField(
            value = original, onValueChange = { original = it.take(120) }, singleLine = true,
            label = { Text(stringResource(R.string.reader_pronunciation_original)) }, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = spoken, onValueChange = { spoken = it.take(200) }, singleLine = true,
            label = { Text(stringResource(R.string.reader_pronunciation_spoken)) }, modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            enabled = original.isNotBlank() && spoken.isNotBlank() &&
                (controls.pronunciations.size < 100 || controls.pronunciations.any { it.original.equals(original.trim(), true) }),
            onClick = { controls.onSavePronunciation(original, spoken); original = ""; spoken = "" },
        ) {
            Text(stringResource(R.string.reader_pronunciation_save))
        }
    }
}
