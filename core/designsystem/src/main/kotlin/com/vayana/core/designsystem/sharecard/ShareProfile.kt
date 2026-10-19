package com.vayana.core.designsystem.sharecard

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.core.content.edit
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R

/**
 * The reader's name for share cards, set in Settings → Profile and shown at the bottom right of a card when its
 * Profile name chip is on. Device local, like the seal: not synced or backed up.
 */
object ShareProfile {
    private var state: MutableState<String>? = null

    private fun state(context: Context): MutableState<String> = state ?: mutableStateOf(
        prefs(context).getString(NameKey, null).orEmpty(),
    ).also { state = it }

    /** The name (observable from composition), or blank when none is set. */
    fun name(context: Context): String = state(context).value

    fun setName(context: Context, name: String) {
        val value = name.take(MaxNameChars)
        state(context).value = value
        prefs(context).edit { putString(NameKey, value.trim()) }
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

    private const val PrefsName = "share_profile"
    private const val NameKey = "name"
    private const val MaxNameChars = 40
}

/** The profile name field, in Settings → Profile. */
@Composable
fun ShareProfileNameCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.extraLarge),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedTextField(
                value = ShareProfile.name(context),
                onValueChange = { ShareProfile.setName(context, it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.share_profile_name)) },
                supportingText = { Text(stringResource(R.string.share_profile_name_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            )
        }
    }
}

/** The bottom of every share card: [left] lines (caption, tagline) on the left, the profile name on the right. */
@Composable
internal fun ShareCardFooter(left: List<Pair<String, androidx.compose.ui.graphics.Color>>, profileName: String?, colors: ShareCardColors) {
    if (left.isEmpty() && profileName == null) return
    Column {
        androidx.compose.material3.HorizontalDivider(color = colors.divider)
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = Spacing.md))
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = androidx.compose.ui.Alignment.Bottom,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                left.forEach { (text, color) -> ShareCardCaption(text = text, color = color) }
            }
            profileName?.let { ShareCardCaption(text = it, color = colors.primaryText) }
        }
    }
}

/** The profile name when [show] and one is set; null otherwise. */
@Composable
internal fun shareProfileName(show: Boolean): String? {
    val name = ShareProfile.name(LocalContext.current).trim()
    return name.takeIf { show && it.isNotEmpty() }
}

/** Whether a profile name is set, so editors only offer its chip when it would show something. */
@Composable
fun hasShareProfileName(): Boolean = ShareProfile.name(LocalContext.current).isNotBlank()
