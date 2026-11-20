package com.wikiink.app.widget

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import androidx.core.content.res.ResourcesCompat
import android.widget.RemoteViews
import com.wikiink.app.R
import com.wikiink.app.data.prefs.WidgetLayout
import com.wikiink.app.data.prefs.WidgetStyle
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Type sizes for one card, derived from the widget's real size so every style fills its space the
 * same way. All five card layouts share one template (see tools/gen_widget_layouts.py), so the
 * fixed chrome below is the same for each; only the rule line and glyph width differ by style.
 *
 * The fact text is fitted per card: the largest size (12–26sp) at which *that* hook fits the
 * text area, so short hooks on a big widget read large and long ones still fit a small widget.
 */
data class WidgetTypeScale(
    val hookSp: Float,
    val hookMaxLines: Int,
    val metaSp: Float,
    val titleSp: Float,
) {
    companion object {
        const val DEFAULT_WIDTH_DP = 250
        const val DEFAULT_HEIGHT_DP = 110
        const val MIN_HOOK_SP = 12f
        const val MAX_HOOK_SP = 26f

        /** Card padding (12 + 12) + 32dp header + 6dp hook gap + 24dp footer + 6dp footer gap. */
        private const val CHROME_DP = 92f
        /** Card padding (16 + 10) + the hook's 6dp end margin. */
        private const val SIDE_DP = 32f
        private const val LINE_HEIGHT = 1.36f
        /** Word-wrap slack: lines rarely fill to the last character. */
        private const val WRAP_SLACK = 1.12f
        private val STACK_W = StackGeometry.FRONT_WIDTH
        private val STACK_H = StackGeometry.FRONT_HEIGHT

        /**
         * Exact fit: lays the hook out with Android's own text engine ([StaticLayout]) in the
         * style's real typeface at the card's real pixel size, and picks the largest size that fits.
         * [fit] (an estimate) remains the fallback and the unit-tested model.
         */
        fun measure(
            context: Context,
            hook: String,
            widthDp: Int,
            heightDp: Int,
            layout: WidgetLayout,
            style: WidgetStyle,
        ): WidgetTypeScale {
            val estimate = fit(hook.length, widthDp, heightDp, layout, style, context.resources.configuration.fontScale)
            val metrics = context.resources.displayMetrics
            val stack = layout == WidgetLayout.STACK
            val wDp = ((if (widthDp > 0) widthDp else DEFAULT_WIDTH_DP) * (if (stack) STACK_W else 1f)) - SIDE_DP
            val hDp = ((if (heightDp > 0) heightDp else DEFAULT_HEIGHT_DP) * (if (stack) STACK_H else 1f)) -
                CHROME_DP - ruleDp(style)
            val wPx = (wDp * metrics.density).toInt()
            val hPx = hDp * metrics.density
            if (wPx <= 0 || hPx <= 0f) return estimate.copy(hookSp = MIN_HOOK_SP, hookMaxLines = 2)

            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = hookTypeface(context, style) }
            // TextView converts sp with the font-scaled density, so measure the same way.
            val spToPx = metrics.density * context.resources.configuration.fontScale
            fun layoutAt(sp: Float): StaticLayout {
                paint.textSize = sp * spToPx
                return StaticLayout.Builder.obtain(hook, 0, hook.length, paint, wPx)
                    .setLineSpacing(0f, LINE_SPACING)
                    .setIncludePad(true)
                    .build()
            }

            // Binary search on half-sp steps for the largest size whose layout fits the height.
            var lo = 0
            var hi = ((MAX_HOOK_SP - MIN_HOOK_SP) * 2).toInt()
            while (lo < hi) {
                val mid = (lo + hi + 1) / 2
                if (layoutAt(MIN_HOOK_SP + mid / 2f).height <= hPx) lo = mid else hi = mid - 1
            }
            val sp = MIN_HOOK_SP + lo / 2f
            val fitted = layoutAt(sp)
            val lines = if (fitted.height <= hPx) fitted.lineCount
            else (hPx / (fitted.height.toFloat() / fitted.lineCount)).toInt()
            return estimate.copy(hookSp = sp, hookMaxLines = lines.coerceIn(2, 16))
        }

        private const val LINE_SPACING = 1.15f
        private val typefaces = java.util.concurrent.ConcurrentHashMap<WidgetStyle, Typeface>()

        /** The hook's typeface per style — must match the card layouts. */
        private fun hookTypeface(context: Context, style: WidgetStyle): Typeface = typefaces.getOrPut(style) {
            when (style) {
                WidgetStyle.CLASSIC_EINK -> Typeface.SERIF
                WidgetStyle.DOT_MATRIX -> ResourcesCompat.getFont(context, R.font.doto_black) ?: Typeface.MONOSPACE
                WidgetStyle.ALMANAC -> ResourcesCompat.getFont(context, R.font.literata_semibold) ?: Typeface.SERIF
                else -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
        }

        val DEFAULT = fit(hookLength = 140, widthDp = DEFAULT_WIDTH_DP, heightDp = DEFAULT_HEIGHT_DP, layout = WidgetLayout.SINGLE, style = WidgetStyle.DEFAULT)

        /** Extra vertical chrome for styles with a divider rule. */
        private fun ruleDp(style: WidgetStyle) = when (style) {
            WidgetStyle.CLASSIC_EINK -> 7f
            WidgetStyle.DOT_MATRIX -> 9f
            else -> 0f
        }

        /** Average glyph width as a fraction of the font size (wider faces wrap sooner). */
        private fun glyphWidth(style: WidgetStyle) = when (style) {
            WidgetStyle.CLASSIC_EINK -> 0.52f
            WidgetStyle.ALMANAC -> 0.57f
            WidgetStyle.DOT_MATRIX -> 0.62f
            else -> 0.53f
        }

        /**
         * @param hookLength characters in this card's fact
         * @param widthDp portrait width of the widget (OPTION_APPWIDGET_MIN_WIDTH), 0 = unknown
         * @param heightDp portrait height of the widget (OPTION_APPWIDGET_MAX_HEIGHT), 0 = unknown
         * @param fontScale the phone's font-size setting, so large system text still fits
         */
        fun fit(
            hookLength: Int,
            widthDp: Int,
            heightDp: Int,
            layout: WidgetLayout,
            style: WidgetStyle,
            fontScale: Float = 1f,
        ): WidgetTypeScale {
            val stack = layout == WidgetLayout.STACK
            val w = ((if (widthDp > 0) widthDp else DEFAULT_WIDTH_DP) * (if (stack) STACK_W else 1f)) - SIDE_DP
            val h = ((if (heightDp > 0) heightDp else DEFAULT_HEIGHT_DP) * (if (stack) STACK_H else 1f)) -
                CHROME_DP - ruleDp(style)
            val scale = fontScale.coerceAtLeast(0.5f)
            val glyph = glyphWidth(style)

            var sp = MAX_HOOK_SP
            while (sp > MIN_HOOK_SP) {
                val px = sp * scale
                val perLine = floor(w / (px * glyph)).coerceAtLeast(8f)
                val lines = ceil(hookLength.coerceAtLeast(1) * WRAP_SLACK / perLine)
                if (lines * px * LINE_HEIGHT <= h) break
                sp -= 0.5f
            }
            val lineDp = sp * scale * LINE_HEIGHT
            val maxLines = floor(h.coerceAtLeast(lineDp * 2) / lineDp).toInt().coerceIn(2, 14)

            // Labels scale gently with the widget, not with the hook, so they stay steady while flipping.
            val size = minOf(w + SIDE_DP, (if (heightDp > 0) heightDp else DEFAULT_HEIGHT_DP) * 1.8f)
            return WidgetTypeScale(
                hookSp = sp,
                hookMaxLines = maxLines,
                metaSp = (9.5f + (size - 200f) / 60f).coerceIn(10f, 13f).roundHalf(),
                titleSp = (11f + (size - 200f) / 50f).coerceIn(11f, 15f).roundHalf(),
            )
        }

        private fun Float.roundHalf() = Math.round(this * 2f) / 2f

        /** Dot Matrix shows the card number as a large dot-matrix figure. */
        fun dotIndexSp(type: WidgetTypeScale): Float = (type.titleSp * 1.6f).coerceIn(17f, 26f).roundHalf()

        fun apply(views: RemoteViews, type: WidgetTypeScale, style: WidgetStyle) {
            val sp = TypedValue.COMPLEX_UNIT_SP
            views.setTextViewTextSize(R.id.widget_item_hook, sp, type.hookSp)
            views.setInt(R.id.widget_item_hook, "setMaxLines", type.hookMaxLines)
            views.setTextViewTextSize(R.id.widget_item_category, sp, type.metaSp)
            views.setTextViewTextSize(
                R.id.widget_item_index,
                sp,
                when (style) {
                    WidgetStyle.DOT_MATRIX -> dotIndexSp(type)
                    WidgetStyle.ALMANAC -> type.metaSp + 1f
                    else -> type.metaSp
                },
            )
            views.setTextViewTextSize(R.id.widget_item_title, sp, type.titleSp)
        }
    }
}

/**
 * Where StackView puts its front card, as fractions of the widget (measured from real screenshots):
 * the cards behind peek out above and to the right, so the front card is ~77% wide, ~51% tall,
 * starting 7% in from the left and 47% down from the top.
 */
object StackGeometry {
    const val FRONT_LEFT = 0.07f
    const val FRONT_TOP = 0.47f
    const val FRONT_WIDTH = 0.77f
    const val FRONT_HEIGHT = 0.51f
    const val FRONT_RIGHT_INSET = 1f - FRONT_LEFT - FRONT_WIDTH
}
