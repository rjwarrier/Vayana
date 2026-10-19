package com.vayana.core.designsystem.sharecard

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The reader's own mark for share cards: a PNG kept in the app's private storage and drawn at the top right of a
 * quote card in the card's accent colour, so a white shape on a transparent background takes any palette. Device
 * local: it is not synced or backed up.
 */
object ShareSeal {
    /** Bumped on every change, so anything showing the seal reloads it. */
    var version by mutableIntStateOf(0)
        private set

    fun file(context: Context): File = File(context.filesDir, FileName)

    fun exists(context: Context): Boolean = file(context).isFile

    /** Copies the image at [uri] in as the seal; false if it is too large or not an image Android can read. */
    suspend fun import(context: Context, uri: Uri): Boolean {
        val saved = withContext(Dispatchers.IO) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(ChunkBytes)
                while (out.size() <= MaxBytes) {
                    val read = input.read(chunk)
                    if (read < 0) break
                    out.write(chunk, 0, read)
                }
                out.toByteArray().takeIf { it.size <= MaxBytes }
            } ?: return@withContext false
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext false
            val target = file(context)
            val staging = File(context.filesDir, "$FileName.new")
            staging.writeBytes(bytes)
            staging.renameTo(target) || run { target.delete(); staging.renameTo(target) }
        }
        if (saved) version++
        return saved
    }

    /** Tells everything showing the seal that its file changed. */
    internal fun changed() { version++ }

    suspend fun remove(context: Context) {
        withContext(Dispatchers.IO) { file(context).delete() }
        version++
    }

    private const val FileName = "share_seal.png"
    private const val MaxBytes = 2 * 1024 * 1024
    private const val ChunkBytes = 16 * 1024
}

/** The seal tinted [tint], or nothing when none is set. */
@Composable
fun ShareSealImage(tint: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version = ShareSeal.version
    val file = remember(version) { ShareSeal.file(context).takeIf { it.isFile } } ?: return
    val request = remember(version) {
        ImageRequest.Builder(context)
            .data(file)
            .memoryCacheKey("share-seal-$version")
            .diskCachePolicy(CachePolicy.DISABLED)
            // Decoded large, not at its on-screen size: the card is scaled up for the preview and recorded at
            // the export size (several times the screen), where a screen-sized bitmap would turn soft.
            .size(SealDecodePx)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(tint, BlendMode.SrcIn),
        filterQuality = FilterQuality.High,
        modifier = modifier,
    )
}

/** Add, change or remove the seal, with a preview in the app's colours. Used in Settings → Profile and the quote editor. */
@Composable
fun ShareSealCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var failed by remember { mutableStateOf(false) }
    var cropping by remember { mutableStateOf(false) }
    val hasSeal = remember(ShareSeal.version) { ShareSeal.exists(context) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            failed = !ShareSeal.import(context, uri)
            // A fresh seal usually wants its margins trimmed, so go straight to cropping it.
            if (!failed) cropping = true
        }
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.extraLarge),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(Sizes.touchTarget)
                    .clip(RoundedCornerShape(Radii.medium))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                ShareSealImage(tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(Sizes.iconLarge))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(stringResource(R.string.share_seal_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.share_seal_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (failed) {
                    Text(stringResource(R.string.share_seal_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FilledTonalButton(onClick = { picker.launch(arrayOf("image/png", "image/*")) }) {
                        Text(stringResource(if (hasSeal) R.string.share_seal_change else R.string.share_seal_add))
                    }
                    if (hasSeal) {
                        TextButton(onClick = { cropping = true }) { Text(stringResource(R.string.share_seal_crop)) }
                        TextButton(onClick = { scope.launch { ShareSeal.remove(context); failed = false } }) {
                            Text(stringResource(R.string.share_seal_remove))
                        }
                    }
                }
            }
        }
    }
    if (cropping) SealCropDialog(onDismiss = { cropping = false })
}

/** Longest side the seal is decoded at; ample for the 1600 px export at the largest seal size. */
private const val SealDecodePx = 1024
