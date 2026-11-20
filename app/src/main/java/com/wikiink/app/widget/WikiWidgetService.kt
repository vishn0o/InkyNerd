package com.wikiink.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.wikiink.app.R
import com.wikiink.app.appContainer
import com.wikiink.app.data.prefs.WidgetLayout
import com.wikiink.app.data.prefs.WidgetPreferences
import com.wikiink.app.data.prefs.WidgetStyle

/** Serves the Stack deck's cards (a swipeable StackView needs a collection adapter). */
class WikiWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        WikiWidgetFactory(
            applicationContext,
            intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID),
        )
}

/**
 * One card per cached fact, built by [WidgetCards] exactly like One at a time's card. Runs on a
 * binder thread, so the blocking Room / DataStore reads are allowed here.
 */
class WikiWidgetFactory(
    private val context: Context,
    private val appWidgetId: Int,
) : RemoteViewsService.RemoteViewsFactory {

    private var deck = WidgetDeck(emptyList(), WidgetTopic.ALL, emptySet())
    private var spec: WidgetStyleSpec = WidgetStyles.specFor(WidgetStyle.DEFAULT, dark = false)
    private var cardDp: Pair<Int, Int>? = null

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        val prefs = runCatching { context.appContainer.widgetPrefs.currentBlocking() }.getOrDefault(WidgetPreferences())
        spec = WidgetThemeHelper.resolve(context, prefs)
        deck = WidgetCards.loadDeck(context, appWidgetId)
        cardDp = runCatching {
            val (w, h) = WidgetBitmapText.widgetSizeDp(context, appWidgetId)
            val scale = WidgetCards.deckScale(context)
            WidgetCards.stackCardDp((w * scale).toInt() to (h * scale).toInt())
        }.getOrNull()
    }

    override fun onDestroy() {
        deck = WidgetDeck(emptyList(), WidgetTopic.ALL, emptySet())
    }

    override fun getCount(): Int = deck.facts.size

    override fun getViewAt(position: Int): RemoteViews {
        val fact = deck.facts.getOrNull(position) ?: return loadingView()
        return WidgetCards.build(context, spec, WidgetLayout.STACK, deck, position, cardDp) { id, action ->
            setOnClickFillInIntent(id, WidgetCards.actionExtras(action, fact, appWidgetId, position))
        }
    }

    private fun loadingView() = RemoteViews(context.packageName, R.layout.widget_wiki_loading).apply {
        WidgetThemeHelper.applyLoading(context, this, spec)
    }

    override fun getLoadingView(): RemoteViews = loadingView()

    /** One type per style layout (+ loading) so the host's view recycling never mixes layouts. */
    override fun getViewTypeCount(): Int = WidgetStyle.entries.size + 1

    override fun getItemId(position: Int): Long =
        deck.facts.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}
