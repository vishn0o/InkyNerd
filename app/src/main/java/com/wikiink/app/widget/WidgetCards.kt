package com.wikiink.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.wikiink.app.R
import com.wikiink.app.appContainer
import com.wikiink.app.data.local.FactEntity
import com.wikiink.app.data.prefs.WidgetLayout
import com.wikiink.app.data.prefs.WidgetStyle
import com.wikiink.app.data.repository.Subject
import com.wikiink.app.data.repository.WikiText

/** The cards one widget shows: its topic's facts (or the mix) plus which articles are saved. */
data class WidgetDeck(val facts: List<FactEntity>, val topic: WidgetTopic, val bookmarked: Set<String>)

/**
 * Builds one fact card. One at a time draws it straight into the widget (direct PendingIntents);
 * Stack serves it from [WikiWidgetService] (fill-in intents), so both render identically.
 */
object WidgetCards {
    const val MAX_CARDS = 60
    /** Stack cards: top padding (topic chip sits high) and the fact's right margin. */
    const val STACK_TOP_DP = 4
    const val STACK_HOOK_END_DP = 30f

    /**
     * A Stack card's size for a widget of [widgetDp]: StackView gives each card 90% of the
     * widget (the other 10% is the peeking deck), minus its 4dp card frame on each side.
     */
    fun stackCardDp(widgetDp: Pair<Int, Int>): Pair<Int, Int> =
        (widgetDp.first * 0.9f - 8f).toInt() to (widgetDp.second * 0.9f - 8f).toInt()

    /**
     * How much larger than the widget the Stack deck is drawn. Pixel-style launchers fan the deck
     * by ~10% in total, so 1×. HyperOS (Xiaomi/Redmi/POCO) shrinks each card to ~68% and fans the
     * rest out in wide steps, leaving the front card small. Drawing the deck 1.28× bigger (anchored
     * bottom-start, so the excess spills past the widget's top and right edges and is clipped)
     * makes the front card fill ~95% of the widget, with only a thin strip of the next card showing.
     */
    fun deckScale(context: Context): Float = if (homePackage(context) in FANNING_LAUNCHERS) HYPEROS_DECK_SCALE else 1f

    private const val HYPEROS_DECK_SCALE = 1.28f
    private val FANNING_LAUNCHERS = setOf("com.miui.home", "com.mi.android.globallauncher")

    @Volatile private var cachedHome: String? = null

    private fun homePackage(context: Context): String = cachedHome ?: runCatching {
        context.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            android.content.pm.PackageManager.MATCH_DEFAULT_ONLY,
        )?.activityInfo?.packageName.orEmpty()
    }.getOrDefault("").also { cachedHome = it }

    /** Blocking Room reads: call off the main thread. */
    fun loadDeck(context: Context, appWidgetId: Int): WidgetDeck {
        val db = context.appContainer.database
        var topic = WidgetInstanceState.topic(context, appWidgetId)
        val facts = runCatching {
            val dao = db.factDao()
            val filtered = when {
                topic.category != null -> dao.getForWidgetByCategorySync(topic.category!!, MAX_CARDS)
                topic.subject != null -> dao.getForWidgetBySubjectSync(topic.subject!!.name, MAX_CARDS)
                else -> null
            }
            // A topic whose facts were pruned falls back to the mix rather than an empty card, and
            // says so: the stored topic becomes "All" so the app's topic row tells the truth.
            filtered?.takeIf { it.isNotEmpty() }
                ?: WikiText.spreadForVariety(dao.getForWidgetSync(MAX_CARDS)).also {
                    if (topic != WidgetTopic.ALL) WidgetInstanceState.setTopic(context, appWidgetId, WidgetTopic.ALL)
                    topic = WidgetTopic.ALL
                }
        }.getOrDefault(emptyList())
        WidgetInstanceState.setCount(context, appWidgetId, facts.size)
        val bookmarked = runCatching { db.bookmarkDao().bookmarkedTitlesSync().toSet() }.getOrDefault(emptySet())
        return WidgetDeck(facts, topic, bookmarked)
    }

    /**
     * The tag shows the active topic; in "All topics" it shows what the fact is about (SPACE,
     * FOOD, …), falling back to its source (DID YOU KNOW, …) when the subject is unknown.
     */
    fun chipFor(topic: WidgetTopic, fact: FactEntity): String = topic.chip
        ?: Subject.entries.firstOrNull { it.name == fact.subject && it != Subject.MISC }?.chip
        ?: fact.sourceCategory

    /** Extras every card action carries to [WidgetActionActivity]. No data URI (see git history). */
    fun actionExtras(action: String, fact: FactEntity, appWidgetId: Int, position: Int = -1) = Intent().apply {
        putExtra(WidgetActionActivity.EXTRA_POSITION, position)
        putExtra(WidgetActionActivity.EXTRA_ACTION, action)
        putExtra(WidgetActionActivity.EXTRA_ARTICLE_TITLE, fact.articleTitle)
        putExtra(WidgetActionActivity.EXTRA_FACT_ID, fact.id)
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
    }

    /**
     * @param click wires one tappable view to an action ([WidgetActionActivity] ACTION_*).
     * @param sizeDp the card's size in dp (null if unknown). Stack pins each card to it, and
     *   drawn-font styles size their text from it.
     */
    fun build(
        context: Context,
        spec: WidgetStyleSpec,
        layout: WidgetLayout,
        deck: WidgetDeck,
        position: Int,
        sizeDp: Pair<Int, Int>?,
        click: RemoteViews.(viewId: Int, action: String) -> Unit,
    ): RemoteViews {
        val fact = deck.facts[position]
        val isSaved = fact.articleTitle in deck.bookmarked
        return RemoteViews(context.packageName, spec.itemLayout).apply {
            WidgetThemeHelper.applyItem(context, this, spec, layout)
            val chip = chipFor(deck.topic, fact)
            val index = "%02d/%02d".format(position + 1, deck.facts.size)
            val title = "→ ${fact.articleTitle}"
            setTextViewText(R.id.widget_item_category, chip)
            setTextViewText(R.id.widget_item_index, index)
            setTextViewText(R.id.widget_item_hook, fact.hookText)
            setTextViewText(R.id.widget_item_title, title)
            // System-font hooks auto-size (12–36sp); if a fact still overflows at 12sp, the hook's
            // fading bottom edge (card XML) softens the cut. No line cap: it depended on size and
            // font-scale maths that differ per phone (HyperOS fits ~7 lines where it allowed 5).
            if (sizeDp != null && WidgetBitmapText.applies(spec.style)) {
                val drawn = runCatching {
                    WidgetBitmapText.apply(context, this, spec, layout, sizeDp, chip, index, fact.hookText, title)
                }.isSuccess
                if (!drawn) {
                    // Fall back to the system-font TextViews rather than an empty card.
                    listOf(R.id.widget_item_category, R.id.widget_item_index, R.id.widget_item_hook, R.id.widget_item_title)
                        .forEach { setViewVisibility(it, View.VISIBLE) }
                    listOf(R.id.widget_item_category_img, R.id.widget_item_index_img, R.id.widget_item_hook_img, R.id.widget_item_title_img)
                        .forEach { setViewVisibility(it, View.GONE) }
                }
                click(R.id.widget_item_category_img, WidgetActionActivity.ACTION_TOPIC)
            }
            setImageViewResource(
                R.id.widget_item_bookmark,
                if (isSaved) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark_outline,
            )
            setContentDescription(
                R.id.widget_item_bookmark,
                context.getString(if (isSaved) R.string.widget_bookmarked else R.string.widget_bookmark),
            )

            // The whole card opens the article — including taps that land on the text itself.
            click(R.id.widget_item_card, WidgetActionActivity.ACTION_OPEN)
            click(R.id.widget_item_hook, WidgetActionActivity.ACTION_OPEN)
            click(R.id.widget_item_title, WidgetActionActivity.ACTION_OPEN)
            click(R.id.widget_item_bookmark, WidgetActionActivity.ACTION_BOOKMARK)
            click(R.id.widget_item_refresh, WidgetActionActivity.ACTION_REFRESH)
            click(R.id.widget_item_category, WidgetActionActivity.ACTION_TOPIC)
            if (layout == WidgetLayout.STACK) {
                // No "›" on Stack cards: the deck flips by swiping, and on a fanned deck the arrow
                // repeated down every card's edge.
                setViewVisibility(R.id.widget_item_next, View.GONE)
                // StackView lays cards out at wrap_content, so each card took its text's height:
                // uneven cards, a stubby front card and sliced text peeking out. Pin every card
                // to the deck's card size so they stack cleanly and only bare edges show behind.
                if (sizeDp != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setViewLayoutWidth(R.id.widget_item_card, sizeDp.first.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
                    setViewLayoutHeight(R.id.widget_item_card, sizeDp.second.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
                }
                // Some launchers (HyperOS) fan the deck far out, so ~30dp strips of each back card
                // show. Keep those strips tidy: the topic chip rides at the very top (reads as a
                // tab, and stays hidden under Pixel's thin edges) and the fact keeps clear of the
                // right edge, so no sliced letters peek out.
                val d = context.resources.displayMetrics.density
                setViewPadding(R.id.widget_item_card, (16 * d).toInt(), (STACK_TOP_DP * d).toInt(), (10 * d).toInt(), (12 * d).toInt())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Only touch views this style's layout has (the "_img" twins exist for drawn fonts).
                    val hooks = if (WidgetBitmapText.applies(spec.style)) listOf(R.id.widget_item_hook, R.id.widget_item_hook_img) else listOf(R.id.widget_item_hook)
                    hooks.forEach {
                        setViewLayoutMargin(it, RemoteViews.MARGIN_END, STACK_HOOK_END_DP, TypedValue.COMPLEX_UNIT_DIP)
                    }
                    // Tighter divider + "→ title" row so the fact gets the room.
                    setViewLayoutMargin(R.id.widget_item_footer, RemoteViews.MARGIN_TOP, 2f, TypedValue.COMPLEX_UNIT_DIP)
                    if (spec.style == WidgetStyle.CLASSIC_EINK || spec.style == WidgetStyle.DOT_MATRIX) {
                        setViewLayoutMargin(R.id.widget_item_rule, RemoteViews.MARGIN_TOP, 3f, TypedValue.COMPLEX_UNIT_DIP)
                    }
                }
            } else {
                // The footer's "›" slot stays empty: the container's "NEXT ›" is drawn over it.
                setViewVisibility(R.id.widget_item_next, View.INVISIBLE)
            }
        }
    }
}
