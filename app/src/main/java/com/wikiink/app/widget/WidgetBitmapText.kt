package com.wikiink.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.SizeF
import android.widget.RemoteViews
import androidx.annotation.FontRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.wikiink.app.R
import com.wikiink.app.data.prefs.WidgetLayout
import com.wikiink.app.data.prefs.WidgetStyle
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import androidx.core.graphics.createBitmap

/**
 * Launchers inflate widgets with system fonts only — an app's bundled `@font` is silently replaced
 * by the default face. Dot Matrix (Doto) and Almanac (Literata) depend on their typefaces, so for
 * those styles the card's text is drawn here, in the real font, into the ImageView twins that
 * tools/gen_widget_layouts.py adds next to each TextView.
 *
 * Bitmaps are ALPHA_8 (one byte per pixel, well under the binder limit) and tinted with the style's
 * ink through `setColorFilter`, so light/dark need no re-render logic of their own.
 */
object WidgetBitmapText {

    fun applies(style: WidgetStyle) = style == WidgetStyle.DOT_MATRIX || style == WidgetStyle.ALMANAC

    private data class Faces(
        @FontRes val chip: Int, val chipSp: Float, val chipTracking: Float,
        @FontRes val hook: Int,
        @FontRes val index: Int, val indexSp: Float,
        @FontRes val title: Int, val titleSp: Float, val titleTracking: Float,
    )

    private fun faces(style: WidgetStyle) = when (style) {
        WidgetStyle.ALMANAC -> Faces(
            R.font.literata_bold, 10f, 0.02f, R.font.literata_semibold,
            R.font.literata_bold, 12f, R.font.literata_regular, 12f, 0f,
        )
        else -> Faces(
            R.font.doto_black, 10f, 0.06f, R.font.doto_black,
            R.font.doto_black, 20f, R.font.doto_black, 12f, 0.02f,
        )
    }

    /** The widget's on-screen size in dp (portrait), or the defaults when the launcher is silent. */
    fun widgetSizeDp(context: Context, appWidgetId: Int): Pair<Int, Int> {
        val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            @Suppress("DEPRECATION")
            val sizes = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            // Phones list a portrait and a landscape size; portrait is the narrower one.
            sizes?.minByOrNull { it.width }?.let { return it.width.toInt() to it.height.toInt() }
        }
        val w = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val h = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        return (if (w > 0) w else WidgetTypeScale.DEFAULT_WIDTH_DP) to (if (h > 0) h else WidgetTypeScale.DEFAULT_HEIGHT_DP)
    }

    fun apply(
        context: Context,
        views: RemoteViews,
        spec: WidgetStyleSpec,
        layout: WidgetLayout,
        sizeDp: Pair<Int, Int>,
        chip: String,
        index: String,
        hook: String,
        title: String,
    ) {
        val f = faces(spec.style)
        val metrics = context.resources.displayMetrics
        val spPx = metrics.density * context.resources.configuration.fontScale
        // [sizeDp] is the card's own size (the provider subtracts the Stack deck's top inset).
        val cardWDp = sizeDp.first.toFloat()

        views.setImageViewBitmap(R.id.widget_item_category_img, line(context, chip, f.chip, f.chipSp * spPx, f.chipTracking, Int.MAX_VALUE))
        // The card number sits in a fixed 24dp footer row, so it's sized in dp: scaling it with the
        // phone's font size (sp) pushed it out of the row and cut it in half at 1.25×.
        val indexPx = minOf(f.indexSp, MAX_INDEX_DP) * metrics.density
        views.setImageViewBitmap(R.id.widget_item_index_img, line(context, index, f.index, indexPx, 0f, Int.MAX_VALUE))
        // Title shares the footer with the index and the 64dp "›" slot.
        val indexWDp = paint(context, f.index, indexPx, 0f).measureText(index) / metrics.density + 10f +
            (if (spec.style == WidgetStyle.ALMANAC) 16f else 0f)
        // One at a time keeps a 64dp slot for "NEXT ›"; Stack cards have no arrow.
        val nextSlotDp = if (layout == WidgetLayout.STACK) 8f else 64f
        val titleMaxPx = ((cardWDp - 26f - indexWDp - nextSlotDp) * metrics.density).toInt().coerceAtLeast(1)
        views.setImageViewBitmap(R.id.widget_item_title_img, line(context, title, f.title, f.titleSp * spPx, f.titleTracking, titleMaxPx))

        // The fact: the largest 12–36sp that fits its area (same rule as the system auto-size).
        // Chrome mirrors the card XML (tools/gen_widget_layouts.py).
        val stack = layout == WidgetLayout.STACK
        val chromeHDp = if (stack) {
            WidgetCards.STACK_TOP_DP + 12f + 32f + 6f + 2f + 24f + (if (spec.style == WidgetStyle.DOT_MATRIX) 6f else 0f)
        } else {
            12f + 12f + 32f + 6f + 6f + 24f + (if (spec.style == WidgetStyle.DOT_MATRIX) 9f else 0f)
        }
        val sideDp = 16f + 10f + (if (stack) WidgetCards.STACK_HOOK_END_DP else 6f)
        val cardHDp = sizeDp.second.toFloat()
        val wPx = ((cardWDp - sideDp) * metrics.density).toInt().coerceAtLeast(1)
        // Never plan for less than two lines at the minimum size: a squeezed card then scales
        // the bitmap down to fit (fitStart) rather than losing the text.
        val minPx = (2 * 1.36f * MIN_SP * spPx).toInt()
        val hPx = ((cardHDp - chromeHDp) * metrics.density).toInt().coerceAtLeast(minPx)
        views.setImageViewBitmap(R.id.widget_item_hook_img, block(context, hook, f.hook, spPx, wPx, hPx))
        views.setContentDescription(R.id.widget_item_hook_img, hook)
        views.setContentDescription(R.id.widget_item_title_img, title)
        views.setContentDescription(R.id.widget_item_category_img, chip)

        fun tint(id: Int, color: Int) = views.setInt(id, "setColorFilter", ContextCompat.getColor(context, color))
        tint(R.id.widget_item_category_img, spec.categoryInk)
        tint(R.id.widget_item_index_img, spec.indexInk)
        tint(R.id.widget_item_hook_img, spec.hookInk)
        tint(R.id.widget_item_title_img, spec.titleInk)
    }

    private val typefaces = ConcurrentHashMap<Int, Typeface>()

    private fun paint(context: Context, @FontRes font: Int, px: Float, tracking: Float) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = typefaces.getOrPut(font) { ResourcesCompat.getFont(context, font) ?: Typeface.DEFAULT }
            textSize = px
            letterSpacing = tracking
            color = Color.BLACK
        }

    /** One line, ellipsized to [maxWidthPx]. */
    private fun line(context: Context, text: String, @FontRes font: Int, px: Float, tracking: Float, maxWidthPx: Int): Bitmap {
        val p = paint(context, font, px, tracking)
        val shown = TextUtils.ellipsize(text, p, maxWidthPx.toFloat(), TextUtils.TruncateAt.END).toString()
        val fm = p.fontMetrics
        val w = ceil(p.measureText(shown)).toInt().coerceAtLeast(1)
        val h = ceil(fm.descent - fm.ascent).toInt().coerceAtLeast(1)
        return createBitmap(w, h, Bitmap.Config.ALPHA_8).also {
            Canvas(it).drawText(shown, 0f, -fm.ascent, p)
        }
    }

    private const val MIN_SP = 12f
    /** Largest card number that fits the 24dp footer with Doto's tall line box. */
    private const val MAX_INDEX_DP = 18f
    private const val MAX_SP = 36f

    /** Wraps [text] at the largest size (half-sp steps) whose layout fits [hPx]; ellipsizes at the minimum. */
    private fun block(context: Context, text: String, @FontRes font: Int, spPx: Float, wPx: Int, hPx: Int): Bitmap {
        fun at(sp: Float, maxLines: Int = Int.MAX_VALUE): StaticLayout {
            val p = paint(context, font, sp * spPx, 0f)
            return StaticLayout.Builder.obtain(text, 0, text.length, p, wPx)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.15f)
                .setIncludePad(true)
                .setMaxLines(maxLines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()
        }
        var lo = 0
        var hi = ((MAX_SP - MIN_SP) * 2).toInt()
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (at(MIN_SP + mid / 2f).height <= hPx) lo = mid else hi = mid - 1
        }
        var layout = at(MIN_SP + lo / 2f)
        if (layout.height > hPx) {
            // Even the minimum overflows: keep the lines that fit and end with an ellipsis.
            val lineH = layout.height.toFloat() / layout.lineCount
            layout = at(MIN_SP, (hPx / lineH).toInt().coerceAtLeast(1))
        }
        // Only as tall as the text: under fitStart the width then sets the scale (≈1:1).
        return createBitmap(wPx, layout.height.coerceAtLeast(1), Bitmap.Config.ALPHA_8).also {
            layout.draw(Canvas(it))
        }
    }
}
