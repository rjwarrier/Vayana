package com.vayana.app.widget

import android.appwidget.AppWidgetProvider
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.DrawableRes
import com.vayana.app.R
import com.vayana.core.datastore.settings.WidgetCornerRadiusMatchLauncher
import com.vayana.core.datastore.settings.WidgetProgressStyle
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * How every Vayana home-screen widget looks, chosen once on the widget configure screen and applied to all of them.
 * New appearance options go here, and every widget that calls [applyTo] picks them up.
 */
data class WidgetAppearance(
    val cornerRadiusDp: Int = WidgetCornerRadiusMatchLauncher,
    val progressStyle: WidgetProgressStyle = WidgetProgressStyle.FLAT,
) {
    val matchesLauncher: Boolean get() = cornerRadiusDp == WidgetCornerRadiusMatchLauncher

    /** Styles a widget's root: [rootId] must be the view carrying the widget's background. */
    fun applyTo(views: RemoteViews, rootId: Int = android.R.id.background) {
        views.setInt(rootId, "setBackgroundResource", backgroundRes())
    }

    /**
     * Shows progress in the chosen style: a widget with a progress bar has a flat one ([flatId]) and a squiggly one
     * ([wavyId]) in its layout, and this sets whichever shows.
     */
    fun applyProgress(views: RemoteViews, flatId: Int, wavyId: Int, max: Int, progress: Int) {
        val squiggly = progressStyle == WidgetProgressStyle.SQUIGGLY
        val shown = if (squiggly) wavyId else flatId
        views.setViewVisibility(flatId, if (squiggly) View.GONE else View.VISIBLE)
        views.setViewVisibility(wavyId, if (squiggly) View.VISIBLE else View.GONE)
        views.setProgressBar(shown, max, progress, false)
    }

    /** The background shape for the chosen corners: one drawable per step, since RemoteViews can't set a radius before Android 12. */
    @DrawableRes
    fun backgroundRes(): Int = if (matchesLauncher) R.drawable.widget_background else CornerBackgrounds.getValue(cornerRadiusDp)

    private companion object {
        val CornerBackgrounds = mapOf(
            0 to R.drawable.widget_background_r0,
            4 to R.drawable.widget_background_r4,
            8 to R.drawable.widget_background_r8,
            12 to R.drawable.widget_background_r12,
            16 to R.drawable.widget_background_r16,
            20 to R.drawable.widget_background_r20,
            24 to R.drawable.widget_background_r24,
            28 to R.drawable.widget_background_r28,
            32 to R.drawable.widget_background_r32,
        )
    }
}

/**
 * A Vayana home-screen widget, as the shared configure screen sees it. To add a widget: implement this in its updater
 * (drawing with [WidgetAppearance.applyTo] and redrawing when the appearance setting changes), bind it into the set in
 * [WidgetModule], and give its appwidget-provider XML `android:configure` = [WidgetConfigureActivity] with
 * `android:widgetFeatures="reconfigurable|configuration_optional"`.
 */
interface VayanaWidget {
    /** The provider this widget is placed as, to find it from an appWidgetId. */
    val provider: Class<out AppWidgetProvider>

    /** The widget as it would draw now in [appearance], for the configure screen's live preview. */
    suspend fun preview(appearance: WidgetAppearance): RemoteViews
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WidgetModule {
    @Binds
    @IntoSet
    abstract fun continueReading(widget: ContinueReadingWidgetUpdater): VayanaWidget
}
