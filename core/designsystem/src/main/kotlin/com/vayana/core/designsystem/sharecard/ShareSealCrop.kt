package com.vayana.core.designsystem.sharecard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.designsystem.tokens.Strokes
import com.vayana.core.resources.R
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The seal as a bitmap to crop, scaled down to at most [MaxCropSidePx] on its longer side. */
internal suspend fun loadSealForCrop(context: Context): Bitmap? = withContext(Dispatchers.IO) {
    val file = ShareSeal.file(context).takeIf { it.isFile } ?: return@withContext null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / sample > MaxCropSidePx) sample *= 2
    BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
}

/** Where the shape actually is: the box around every pixel that isn't (nearly) transparent, as fractions; null if none. */
internal fun Bitmap.opaqueBounds(): Rect? {
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    var left = width; var top = height; var right = -1; var bottom = -1
    for (y in 0 until height) {
        val row = y * width
        for (x in 0 until width) {
            if ((pixels[row + x] ushr 24) > OpaqueAlpha) {
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
    }
    if (right < 0) return null
    return Rect(left.toFloat() / width, top.toFloat() / height, (right + 1f) / width, (bottom + 1f) / height)
}

/** Saves the [crop] (fractions of [bitmap]) as the new seal PNG, keeping its transparency. */
internal suspend fun saveCroppedSeal(context: Context, bitmap: Bitmap, crop: Rect): Boolean {
    val saved = withContext(Dispatchers.IO) {
        val x = (crop.left * bitmap.width).roundToInt().coerceIn(0, bitmap.width - 1)
        val y = (crop.top * bitmap.height).roundToInt().coerceIn(0, bitmap.height - 1)
        val w = (crop.width * bitmap.width).roundToInt().coerceIn(1, bitmap.width - x)
        val h = (crop.height * bitmap.height).roundToInt().coerceIn(1, bitmap.height - y)
        val cropped = Bitmap.createBitmap(bitmap, x, y, w, h)
        val staging = File(context.filesDir, "${ShareSeal.file(context).name}.crop")
        staging.outputStream().use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val target = ShareSeal.file(context)
        staging.renameTo(target) || run { target.delete(); staging.renameTo(target) }
    }
    if (saved) ShareSeal.changed()
    return saved
}

/**
 * Crops the seal: drag inside the frame to move it, drag a corner to resize it. Fit to shape trims the transparent
 * margin a PNG often carries (which would otherwise make the seal look small on the card); Full image starts over.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SealCropDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var crop by remember { mutableStateOf(FullCrop) }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        bitmap = loadSealForCrop(context)
        if (bitmap == null) onDismiss()
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.share_quote_close))
                        }
                    },
                    title = { Text(stringResource(R.string.share_seal_crop_title)) },
                    actions = {
                        TextButton(
                            enabled = bitmap != null && !saving,
                            onClick = {
                                val source = bitmap ?: return@TextButton
                                saving = true
                                scope.launch {
                                    try { if (saveCroppedSeal(context, source, crop)) onDismiss() } finally { saving = false }
                                }
                            },
                        ) { Text(stringResource(R.string.share_seal_crop_save)) }
                    },
                )
            },
            bottomBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = Paddings.screenHorizontal, vertical = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    OutlinedButton(
                        onClick = { bitmap?.opaqueBounds()?.let { crop = it } },
                        enabled = bitmap != null,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.share_seal_crop_fit)) }
                    OutlinedButton(onClick = { crop = FullCrop }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.share_seal_crop_full))
                    }
                }
            },
        ) { padding ->
            val source = bitmap
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(Paddings.screenHorizontal),
                contentAlignment = Alignment.Center,
            ) {
                if (source != null) CropArea(source, crop, onCropChange = { crop = it })
            }
        }
    }
}

/** The image fitted into the space, with the crop frame over it: dimmed outside, corner handles to resize. */
@Composable
private fun CropArea(bitmap: Bitmap, crop: Rect, onCropChange: (Rect) -> Unit) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val colors = MaterialTheme.colorScheme
    val handleRadius = with(LocalDensity.current) { Spacing.sm.toPx() }
    val touchSlop = with(LocalDensity.current) { Sizes.touchTarget.toPx() / 2 }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val boxW = constraints.maxWidth.toFloat()
        val boxH = constraints.maxHeight.toFloat()
        val scale = min(boxW / bitmap.width, boxH / bitmap.height)
        val imageSize = Size(bitmap.width * scale, bitmap.height * scale)
        val origin = Offset((boxW - imageSize.width) / 2, (boxH - imageSize.height) / 2)
        val imageRect = Rect(origin, imageSize)
        var current by remember { mutableStateOf(crop) }
        current = crop
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            // A white seal would vanish on a light theme; show it in the app's text colour instead.
            colorFilter = ColorFilter.tint(colors.onSurface, BlendMode.SrcIn),
            modifier = Modifier.fillMaxSize(),
        )
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(imageRect) {
                    var mode = DragMode.NONE
                    detectDragGestures(
                        onDragStart = { start ->
                            val frame = current.toPixels(imageRect)
                            mode = when {
                                (start - frame.topLeft).getDistance() < touchSlop -> DragMode.TOP_LEFT
                                (start - frame.topRight).getDistance() < touchSlop -> DragMode.TOP_RIGHT
                                (start - frame.bottomLeft).getDistance() < touchSlop -> DragMode.BOTTOM_LEFT
                                (start - frame.bottomRight).getDistance() < touchSlop -> DragMode.BOTTOM_RIGHT
                                frame.contains(start) -> DragMode.MOVE
                                else -> DragMode.NONE
                            }
                        },
                        onDrag = { change, drag ->
                            change.consume()
                            val dx = drag.x / imageRect.width
                            val dy = drag.y / imageRect.height
                            current = current.dragged(mode, dx, dy)
                            onCropChange(current)
                        },
                    )
                },
        ) {
            val frame = current.toPixels(imageRect)
            val scrim = colors.scrim.copy(alpha = CropScrimAlpha)
            // Dim everything outside the frame.
            drawRect(scrim, topLeft = Offset.Zero, size = Size(size.width, frame.top))
            drawRect(scrim, topLeft = Offset(0f, frame.bottom), size = Size(size.width, size.height - frame.bottom))
            drawRect(scrim, topLeft = Offset(0f, frame.top), size = Size(frame.left, frame.height))
            drawRect(scrim, topLeft = Offset(frame.right, frame.top), size = Size(size.width - frame.right, frame.height))
            drawRect(colors.primary, topLeft = frame.topLeft, size = frame.size, style = Stroke(width = Strokes.emphasis.toPx()))
            listOf(frame.topLeft, frame.topRight, frame.bottomLeft, frame.bottomRight).forEach {
                drawCircle(colors.primary, radius = handleRadius, center = it)
            }
        }
    }
}

private enum class DragMode { NONE, MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

private fun Rect.toPixels(image: Rect) = Rect(
    image.left + left * image.width,
    image.top + top * image.height,
    image.left + right * image.width,
    image.top + bottom * image.height,
)

/** The frame after a drag of ([dx], [dy]) image fractions, kept inside the image and at least [MinCropFraction] wide/tall. */
private fun Rect.dragged(mode: DragMode, dx: Float, dy: Float): Rect = when (mode) {
    DragMode.NONE -> this
    DragMode.MOVE -> {
        val x = (left + dx).coerceIn(0f, 1f - width)
        val y = (top + dy).coerceIn(0f, 1f - height)
        Rect(x, y, x + width, y + height)
    }
    DragMode.TOP_LEFT -> copy(left = (left + dx).coerceIn(0f, right - MinCropFraction), top = (top + dy).coerceIn(0f, bottom - MinCropFraction))
    DragMode.TOP_RIGHT -> copy(right = (right + dx).coerceIn(left + MinCropFraction, 1f), top = (top + dy).coerceIn(0f, bottom - MinCropFraction))
    DragMode.BOTTOM_LEFT -> copy(left = (left + dx).coerceIn(0f, right - MinCropFraction), bottom = (bottom + dy).coerceIn(top + MinCropFraction, 1f))
    DragMode.BOTTOM_RIGHT -> copy(right = (right + dx).coerceIn(left + MinCropFraction, 1f), bottom = (bottom + dy).coerceIn(top + MinCropFraction, 1f))
}

private val FullCrop = Rect(0f, 0f, 1f, 1f)
private const val MinCropFraction = 0.1f
private const val MaxCropSidePx = 2048
private const val OpaqueAlpha = 16
private const val CropScrimAlpha = 0.6f
