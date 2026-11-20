package com.wikiink.app.widget

import android.content.Context
import android.content.res.Configuration
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.wikiink.app.R
import com.wikiink.app.data.prefs.WidgetLayout
import com.wikiink.app.data.prefs.WidgetPreferences
import com.wikiink.app.data.prefs.WidgetStyle

/**
 * Applies the selected [WidgetStyleSpec] to widget RemoteViews. Colors/backgrounds are set
 * explicitly (not via `values-night`) so forced Light/Dark modes work regardless of system theme.
 */
object WidgetThemeHelper {

    fun isSystemNight(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    fun resolve(context: Context, prefs: WidgetPreferences): WidgetStyleSpec =
        WidgetStyles.specFor(prefs.style, prefs.isDark(isSystemNight(context)))

    private fun color(context: Context, res: Int) = ContextCompat.getColor(context, res)

    /** Outer frame (transparent — cards sit directly on the wallpaper) and the empty-state card. */
    fun applyContainer(context: Context, views: RemoteViews, spec: WidgetStyleSpec) {
        views.setInt(R.id.widget_root, "setBackgroundResource", spec.frameBackground)
        views.setTextColor(R.id.widget_next, color(context, spec.hookInk))
        // `widget_empty` is the <include> root of widget_wiki_empty.xml.
        views.setInt(R.id.widget_empty, "setBackgroundResource", spec.cardBackground)
        views.setTextColor(R.id.widget_empty_title, color(context, spec.hookInk))
        views.setTextColor(R.id.widget_empty_body, color(context, spec.titleInk))
    }

    /** One fact card (any of the three item layouts — they share view IDs). */
    fun applyItem(context: Context, views: RemoteViews, spec: WidgetStyleSpec, layout: WidgetLayout = WidgetLayout.DEFAULT) {
        views.setInt(R.id.widget_item_card, "setBackgroundResource", cardBackground(spec, layout))
        // Dot Matrix / Almanac draw their text into "_img" twins (WidgetBitmapText); skin both.
        val twins = WidgetBitmapText.applies(spec.style)
        if (spec.style == WidgetStyle.ALMANAC) {
            // Almanac moves the "pill" to the card number (a calendar-style highlight box).
            views.setInt(R.id.widget_item_category, "setBackgroundResource", R.drawable.widget_bg_clear)
            views.setInt(R.id.widget_item_index, "setBackgroundResource", spec.pillBackground)
            if (twins) {
                views.setInt(R.id.widget_item_category_img, "setBackgroundResource", R.drawable.widget_bg_clear)
                views.setInt(R.id.widget_item_index_img, "setBackgroundResource", spec.pillBackground)
            }
        } else {
            views.setInt(R.id.widget_item_category, "setBackgroundResource", spec.pillBackground)
            if (twins) views.setInt(R.id.widget_item_category_img, "setBackgroundResource", spec.pillBackground)
        }
        views.setTextColor(R.id.widget_item_category, color(context, spec.categoryInk))
        views.setTextColor(R.id.widget_item_index, color(context, spec.indexInk))
        views.setTextColor(R.id.widget_item_hook, color(context, spec.hookInk))
        views.setTextColor(R.id.widget_item_title, color(context, spec.titleInk))
        views.setTextColor(R.id.widget_item_refresh, color(context, spec.titleInk))
        views.setTextColor(R.id.widget_item_next, color(context, spec.hookInk))
        views.setInt(R.id.widget_item_bookmark, "setColorFilter", color(context, spec.iconTint))
        if (spec.style == WidgetStyle.DOT_MATRIX) {
            views.setInt(
                R.id.widget_item_rule,
                "setBackgroundResource",
                if (spec.dark) R.drawable.bg_dot_rule_dark else R.drawable.bg_dot_rule_light,
            )
        }
    }

    /**
     * Frosted Glass is translucent, which in Stack mode lets the cards behind show through the
     * front one; there it uses a near-opaque frost so the fact stays readable.
     */
    fun cardBackground(spec: WidgetStyleSpec, layout: WidgetLayout): Int =
        if (spec.style == WidgetStyle.FROSTED_GLASS && layout == WidgetLayout.STACK) {
            if (spec.dark) R.drawable.bg_widget_glass_dark_solid else R.drawable.bg_widget_glass_light_solid
        } else {
            spec.cardBackground
        }

    fun applyLoading(context: Context, views: RemoteViews, spec: WidgetStyleSpec) {
        views.setInt(R.id.widget_loading_root, "setBackgroundResource", spec.cardBackground)
        views.setTextColor(R.id.widget_loading_text, color(context, spec.titleInk))
    }
}
