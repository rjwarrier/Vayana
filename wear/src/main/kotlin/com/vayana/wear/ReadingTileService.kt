package com.vayana.wear

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.wear.tiles.TileService
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders as L
import androidx.wear.protolayout.ModifiersBuilders as M
import androidx.wear.protolayout.ResourceBuilders as R
import androidx.wear.protolayout.TimelineBuilders as T
import com.google.common.util.concurrent.Futures
import com.vayana.core.wear.*
import java.io.ByteArrayOutputStream

/** A glanceable shortcut backed solely by the same durable local state as the watch app. */
class ReadingTileService : TileService() {
    public override fun onTileRequest(requestParams: RequestBuilders.TileRequest): com.google.common.util.concurrent.ListenableFuture<TileBuilders.Tile> {
        val store = WatchStore(this)
        val state = store.read().recover(store.bootCount(), SystemClock.elapsedRealtime())
        val active = state.displayActive(SystemClock.elapsedRealtime(), store.bootCount())
        val book = active?.book ?: state.books.firstOrNull()
        val action = ActionBuilders.AndroidActivity.Builder().setPackageName(packageName)
            .setClassName(WatchActivity::class.java.name)
        book?.let { action.addKeyToExtraMapping("bookId", ActionBuilders.AndroidStringExtra.Builder().setValue(it.id).build()) }
        val click = M.Clickable.Builder().setId("reading-timer")
            .setOnClick(ActionBuilders.LaunchAction.Builder().setAndroidActivity(action.build()).build()).build()
        fun text(value: String, size: Float, color: Int = 0xfff2f5ed.toInt(), lines: Int = 1) = L.Text.Builder()
            .setText(value).setMaxLines(lines).setFontStyle(L.FontStyle.Builder().setSize(sp(size)).setColor(argb(color)).build()).build()
        val column = L.Column.Builder().setWidth(dp(176f)).setHorizontalAlignment(L.HORIZONTAL_ALIGN_CENTER)
            .addContent(text("VAYANA", 12f, 0xffcfe6a1.toInt()))
            .addContent(L.Spacer.Builder().setHeight(dp(8f)).build())
        if (book != null) {
            column.addContent(L.Row.Builder().setWidth(dp(176f)).setHeight(dp(60f))
                .setVerticalAlignment(L.VERTICAL_ALIGN_CENTER)
                .addContent(L.Image.Builder().setResourceId(WearCover.key(book.id)).setWidth(dp(34f)).setHeight(dp(51f))
                    .setContentScaleMode(L.CONTENT_SCALE_MODE_CROP).build())
                .addContent(L.Spacer.Builder().setWidth(dp(10f)).build())
                .addContent(L.Column.Builder().setWidth(dp(132f))
                    .addContent(text(book.title, 14f, lines = 2))
                    .addContent(text("Page ${active?.page ?: book.page}" + (book.total?.let { " / $it" } ?: ""), 12f, 0xffaab8ab.toInt()))
                    .build()).build())
            if (active != null) {
                val seconds = active.timer.elapsedMillis(SystemClock.elapsedRealtime()) / 1000
                column.addContent(text((if (active.timer.phase == PhysicalTimerPhase.RUNNING) "Reading" else "Paused") +
                    " · %d:%02d:%02d".format(java.util.Locale.ROOT, seconds / 3600, seconds / 60 % 60, seconds % 60), 13f, 0xffcfe6a1.toInt()))
            }
        } else column.addContent(text("Choose a physical book on your phone", 14f, lines = 2))
        column.addContent(L.Spacer.Builder().setHeight(dp(8f)).build())
        val caption = when {
            active?.timer?.phase == PhysicalTimerPhase.RUNNING -> "Open timer"
            active != null -> "Resume"
            book != null -> "Start timer"
            else -> "Open Vayana"
        }
        column.addContent(L.Box.Builder().setWidth(dp(150f)).setHeight(dp(48f))
            .setModifiers(M.Modifiers.Builder().setClickable(click)
                .setSemantics(M.Semantics.Builder().setContentDescription(caption).build())
                .setBackground(M.Background.Builder().setColor(argb(0xffcfe6a1.toInt()))
                    .setCorner(M.Corner.Builder().setRadius(dp(24f)).build()).build()).build())
            .addContent(text(caption, 14f, 0xff152312.toInt())).build())
        val root = L.Box.Builder().setWidth(expand()).setHeight(expand())
            .setModifiers(M.Modifiers.Builder().setClickable(click)
                .setBackground(M.Background.Builder().setColor(argb(0xff000000.toInt())).build()).build())
            .addContent(column.build()).build()
        return Futures.immediateFuture(TileBuilders.Tile.Builder().setResourcesVersion("covers-" +
            getSharedPreferences("reading", MODE_PRIVATE).getLong("coversRevision", 0) + "-" +
            WearCover.key((state.books + listOfNotNull(active?.book)).map { it.id }.distinct().sorted().joinToString()))
            .setFreshnessIntervalMillis(60_000)
            .setTileTimeline(T.Timeline.Builder().addTimelineEntry(T.TimelineEntry.Builder()
                .setLayout(L.Layout.Builder().setRoot(root).build()).build()).build()).build())
    }

    public override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): com.google.common.util.concurrent.ListenableFuture<R.Resources> {
        val store = WatchStore(this)
        val state = store.read().recover(store.bootCount(), SystemClock.elapsedRealtime())
        val resources = R.Resources.Builder().setVersion(requestParams.version)
        val covers = WatchCoverCache(this)
        (state.books + listOfNotNull(state.active?.book)).distinctBy { it.id }.forEach { book ->
            val bitmap = covers.bitmap(book.id)
            val image = R.ImageResource.Builder()
            if (bitmap == null) image.setAndroidResourceByResId(R.AndroidImageResourceByResId.Builder()
                .setResourceId(com.vayana.wear.R.drawable.ic_reading_timer).build())
            else try {
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                image.setInlineResource(R.InlineImageResource.Builder().setData(stream.toByteArray())
                    .setWidthPx(bitmap.width).setHeightPx(bitmap.height).build())
            } finally { bitmap.recycle() }
            resources.addIdToImageMapping(WearCover.key(book.id), image.build())
        }
        return Futures.immediateFuture(resources.build())
    }
}
