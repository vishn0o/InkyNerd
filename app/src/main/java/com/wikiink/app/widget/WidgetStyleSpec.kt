package com.wikiink.app.widget

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
import com.wikiink.app.R
import com.wikiink.app.data.prefs.WidgetStyle

/**
 * Single source of truth for each widget style × light/dark variant. Both the RemoteViews widget
 * ([WidgetThemeHelper]) and the in-app Compose preview read from here so they can never drift.
 */
data class WidgetStyleSpec(
    val style: WidgetStyle,
    val dark: Boolean,
    @LayoutRes val itemLayout: Int,
    @DrawableRes val cardBackground: Int,
    @DrawableRes val pillBackground: Int,
    /** Outer widget frame (behind header + deck). */
    @DrawableRes val frameBackground: Int,
    @ColorRes val hookInk: Int,
    @ColorRes val titleInk: Int,
    @ColorRes val categoryInk: Int,
    @ColorRes val indexInk: Int,
    @ColorRes val iconTint: Int,
    @ColorRes val headerInk: Int,
    /** Header uses monospace (E-Ink) vs. sans (Swiss/Glass) — informs the Compose preview. */
    val monoHeader: Boolean,
    val cornerDp: Float,
)

object WidgetStyles {

    fun specFor(style: WidgetStyle, dark: Boolean): WidgetStyleSpec = when (style) {
        WidgetStyle.CLASSIC_EINK -> if (dark) {
            WidgetStyleSpec(
                style, true, R.layout.widget_wiki_item_eink,
                R.drawable.bg_widget_eink_dark, R.drawable.bg_pill_eink_dark, R.drawable.widget_bg_clear,
                hookInk = R.color.eink_ink_dark, titleInk = R.color.eink_ink2_dark,
                categoryInk = R.color.eink_ink_dark, indexInk = R.color.eink_ink2_dark,
                iconTint = R.color.eink_ink_dark, headerInk = R.color.eink_ink_dark,
                monoHeader = true, cornerDp = 4f,
            )
        } else {
            WidgetStyleSpec(
                style, false, R.layout.widget_wiki_item_eink,
                R.drawable.bg_widget_eink_light, R.drawable.bg_pill_eink_light, R.drawable.widget_bg_clear,
                hookInk = R.color.eink_ink_light, titleInk = R.color.eink_ink2_light,
                categoryInk = R.color.eink_ink_light, indexInk = R.color.eink_ink2_light,
                iconTint = R.color.eink_ink_light, headerInk = R.color.eink_ink_light,
                monoHeader = true, cornerDp = 4f,
            )
        }

        WidgetStyle.SWISS_ALABASTER -> if (dark) {
            WidgetStyleSpec(
                style, true, R.layout.widget_wiki_item_swiss,
                R.drawable.bg_widget_swiss_dark, R.drawable.bg_pill_swiss_dark, R.drawable.widget_bg_clear,
                hookInk = R.color.swiss_ink_dark, titleInk = R.color.swiss_ink2_dark,
                categoryInk = R.color.swiss_ink2_dark, indexInk = R.color.swiss_ink2_dark,
                iconTint = R.color.swiss_ink_dark, headerInk = R.color.swiss_ink2_dark,
                monoHeader = false, cornerDp = 28f,
            )
        } else {
            WidgetStyleSpec(
                style, false, R.layout.widget_wiki_item_swiss,
                R.drawable.bg_widget_swiss_light, R.drawable.bg_pill_swiss_light, R.drawable.widget_bg_clear,
                hookInk = R.color.swiss_ink_light, titleInk = R.color.swiss_ink2_light,
                categoryInk = R.color.swiss_ink2_light, indexInk = R.color.swiss_ink2_light,
                iconTint = R.color.swiss_ink_light, headerInk = R.color.swiss_ink2_light,
                monoHeader = false, cornerDp = 28f,
            )
        }

        WidgetStyle.FROSTED_GLASS -> if (dark) {
            WidgetStyleSpec(
                style, true, R.layout.widget_wiki_item_glass,
                R.drawable.bg_widget_glass_dark, R.drawable.bg_pill_glass_dark, R.drawable.widget_bg_clear,
                hookInk = R.color.glass_ink_dark, titleInk = R.color.glass_ink2_dark,
                categoryInk = R.color.glass_ink_dark, indexInk = R.color.glass_ink2_dark,
                iconTint = R.color.glass_ink_dark, headerInk = R.color.glass_ink_dark,
                monoHeader = false, cornerDp = 26f,
            )
        } else {
            WidgetStyleSpec(
                style, false, R.layout.widget_wiki_item_glass,
                R.drawable.bg_widget_glass_light, R.drawable.bg_pill_glass_light, R.drawable.widget_bg_clear,
                hookInk = R.color.glass_ink_light, titleInk = R.color.glass_ink2_light,
                categoryInk = R.color.glass_ink_light, indexInk = R.color.glass_ink2_light,
                iconTint = R.color.glass_ink_light, headerInk = R.color.glass_ink_light,
                monoHeader = false, cornerDp = 26f,
            )
        }

        // Nothing-inspired: pure monochrome, one red accent dot, dot-matrix numerals.
        WidgetStyle.DOT_MATRIX -> if (dark) {
            WidgetStyleSpec(
                style, true, R.layout.widget_wiki_item_dot,
                R.drawable.bg_widget_dot_dark, R.drawable.bg_pill_dot_dark, R.drawable.widget_bg_clear,
                hookInk = R.color.dot_ink_dark, titleInk = R.color.dot_ink2_dark,
                categoryInk = R.color.dot_ink_dark, indexInk = R.color.dot_ink_dark,
                iconTint = R.color.dot_ink_dark, headerInk = R.color.dot_ink_dark,
                monoHeader = true, cornerDp = 24f,
            )
        } else {
            WidgetStyleSpec(
                style, false, R.layout.widget_wiki_item_dot,
                R.drawable.bg_widget_dot_light, R.drawable.bg_pill_dot_light, R.drawable.widget_bg_clear,
                hookInk = R.color.dot_ink_light, titleInk = R.color.dot_ink2_light,
                categoryInk = R.color.dot_ink_light, indexInk = R.color.dot_ink_light,
                iconTint = R.color.dot_ink_light, headerInk = R.color.dot_ink_light,
                monoHeader = true, cornerDp = 24f,
            )
        }

        // Warm paper + bold book serif; the card number sits in a solid ink box (indexInk = card colour).
        WidgetStyle.ALMANAC -> if (dark) {
            WidgetStyleSpec(
                style, true, R.layout.widget_wiki_item_almanac,
                R.drawable.bg_widget_almanac_dark, R.drawable.bg_pill_almanac_dark, R.drawable.widget_bg_clear,
                hookInk = R.color.alm_ink_dark, titleInk = R.color.alm_ink2_dark,
                categoryInk = R.color.alm_ink_dark, indexInk = R.color.alm_card_dark,
                iconTint = R.color.alm_ink_dark, headerInk = R.color.alm_ink_dark,
                monoHeader = false, cornerDp = 28f,
            )
        } else {
            WidgetStyleSpec(
                style, false, R.layout.widget_wiki_item_almanac,
                R.drawable.bg_widget_almanac_light, R.drawable.bg_pill_almanac_light, R.drawable.widget_bg_clear,
                hookInk = R.color.alm_ink_light, titleInk = R.color.alm_ink2_light,
                categoryInk = R.color.alm_ink_light, indexInk = R.color.alm_card_light,
                iconTint = R.color.alm_ink_light, headerInk = R.color.alm_ink_light,
                monoHeader = false, cornerDp = 28f,
            )
        }
    }
}
