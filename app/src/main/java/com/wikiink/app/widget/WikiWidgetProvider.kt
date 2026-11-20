package com.wikiink.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri
import android.util.TypedValue
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import com.wikiink.app.MainActivity
import com.wikiink.app.R
import com.wikiink.app.appContainer
import com.wikiink.app.data.local.FactEntity
import com.wikiink.app.data.prefs.WidgetLayout
import com.wikiink.app.worker.FactSyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Home-screen fact deck. Stack: a swipeable [android.widget.StackView] served by
 * [WikiWidgetService]. One at a time: the current card drawn directly into the widget. Both use
 * [WidgetCards], and every tap is an activity PendingIntent to [WidgetActionActivity] (the launcher
 * reliably delivers those; broadcasts to this receiver were dropped on API 37).
 *
 * Three size presets are separate providers (see [WikiWidgetProviderCompact] and
 * [WikiWidgetProviderLarge]) that share all of this logic; they differ only in their
 * appwidget-provider XML so the picker / pin request can offer a starting size.
 */
open class WikiWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        // Reading the style preference is I/O, so build off the main thread.
        val pending = goAsync()
        context.appContainer.appScope.launch {
            try {
                bindAll(context, manager, appWidgetIds)
                // First time a widget instance appears: flash the gesture hints once.
                val fresh = appWidgetIds.filter { WidgetInstanceState.claimFirstHint(context, it) }
                if (fresh.isNotEmpty()) flashHints(context, manager, fresh)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        // Redraw at the new size (drawn-font styles size their text from it); position is kept.
        val pending = goAsync()
        context.appContainer.appScope.launch {
            try {
                bindAll(context, manager, intArrayOf(appWidgetId))
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        WidgetInstanceState.forget(context, appWidgetIds)
    }

    override fun onEnabled(context: Context) {
        FactSyncWorker.enqueueImmediate(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        when (intent.action) {
            ACTION_REFRESH -> {
                FactSyncWorker.enqueueImmediate(context)
                reloadCards(context)
            }
            ACTION_NEXT -> if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val pending = goAsync()
                context.appContainer.appScope.launch {
                    try {
                        showNext(context, id)
                    } finally {
                        pending.finish()
                    }
                }
            }
            ACTION_TOPIC -> if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val pending = goAsync()
                context.appContainer.appScope.launch {
                    try {
                        cycleTopic(context, id)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.wikiink.app.widget.ACTION_REFRESH"
        private const val EXTRA_CONFIG = "com.wikiink.app.widget.EXTRA_CONFIG"
        const val ACTION_NEXT = "com.wikiink.app.widget.ACTION_NEXT"
        const val ACTION_TOPIC = "com.wikiink.app.widget.ACTION_TOPIC"

        /** Every provider class (size preset) this app ships. */
        val PROVIDERS: List<Class<out WikiWidgetProvider>> = listOf(
            WikiWidgetProvider::class.java,
            WikiWidgetProviderCompact::class.java,
            WikiWidgetProviderLarge::class.java,
        )

        private const val HINT_DELAY_MS = 600L
        private const val HINT_VISIBLE_MS = 900L

        /** All placed InkyNerd widgets, across size presets. */
        fun allWidgetIds(context: Context): IntArray {
            val manager = AppWidgetManager.getInstance(context)
            return PROVIDERS.flatMap { manager.getAppWidgetIds(ComponentName(context, it)).toList() }.toIntArray()
        }

        /**
         * The trampoline activity and the NEXT / TOPIC receivers are reachable from other apps, so
         * an id is only acted on when it is a widget this app actually placed.
         */
        fun isOwnWidget(context: Context, appWidgetId: Int): Boolean =
            appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID && appWidgetId in allWidgetIds(context)

        /**
         * Reloads the cards of every widget without rebuilding it, so each deck stays on the card
         * it's showing (and keeps its topic). Use for new facts, bookmarks and manual refreshes.
         */
        fun reloadCards(context: Context) {
            val ids = allWidgetIds(context)
            if (ids.isEmpty()) return
            val manager = AppWidgetManager.getInstance(context)
            manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_stack)
            // One at a time has no collection to notify: redraw its card (same position).
            context.appContainer.appScope.launch {
                if (context.appContainer.widgetPrefs.current().layout == WidgetLayout.SINGLE) bindAll(context, manager, ids)
            }
        }

        /**
         * Rebuilds every widget (via an explicit ACTION_APPWIDGET_UPDATE broadcast, handled
         * off-main in [onUpdate]). Only for changes to the container itself: style, theme, layout
         * or topic. The stack returns to its first card; single-page mode keeps its position.
         */
        fun rebuildAll(context: Context) {
            val ids = allWidgetIds(context)
            if (ids.isEmpty()) return
            // Same package, so the base provider may update ids owned by any of the presets.
            context.sendBroadcast(
                Intent(context, WikiWidgetProvider::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
            )
        }

        /**
         * Advances one card: the new position is stored and the widget redrawn on it (so later
         * rebuilds keep it). Stack passes [from], the position of the card that was tapped.
         */
        fun showNext(context: Context, appWidgetId: Int, from: Int = -1) {
            if (!isOwnWidget(context, appWidgetId)) return
            val manager = AppWidgetManager.getInstance(context)
            val prefs = context.appContainer.widgetPrefs.currentBlocking()
            // Stack: step from the card whose "›" was tapped (the user may have swiped since the
            // last rebuild). A partial showNext() is ignored by the launcher, so rebuild instead;
            // buildViews() puts the deck on the stored position.
            if (prefs.layout == WidgetLayout.STACK && from >= 0) {
                val count = WidgetInstanceState.count(context, appWidgetId).coerceAtLeast(1)
                WidgetInstanceState.setPosition(context, appWidgetId, (from + 1) % count)
            } else {
                WidgetInstanceState.advance(context, appWidgetId)
            }
            manager.updateAppWidget(
                appWidgetId,
                buildViews(context, appWidgetId, WidgetThemeHelper.resolve(context, prefs), prefs.layout),
            )
        }

        /** Tag tap: move this widget to the next topic that has cached facts, and say which one. */
        suspend fun cycleTopic(context: Context, appWidgetId: Int) {
            if (!isOwnWidget(context, appWidgetId)) return
            val dao = context.appContainer.database.factDao()
            val next = WidgetTopic.next(
                WidgetInstanceState.topic(context, appWidgetId),
                dao.categoryCounts().associate { it.category to it.count },
                dao.subjectCounts().associate { it.category to it.count },
            )
            WidgetInstanceState.setTopic(context, appWidgetId, next)
            withContext(Dispatchers.Main) {
                Toast.makeText(context.applicationContext, context.getString(R.string.widget_topic_toast, next.label), Toast.LENGTH_SHORT).show()
            }
            // Full rebuild resets the deck to the first card of the new topic.
            bindAll(context, AppWidgetManager.getInstance(context), intArrayOf(appWidgetId))
        }

        /** From the app: point every placed widget at [topic] and rebuild them. */
        fun setTopicForAll(context: Context, topic: WidgetTopic) {
            allWidgetIds(context).forEach { WidgetInstanceState.setTopic(context, it, topic) }
            rebuildAll(context)
        }

        /** Topic of the first placed widget, used to show the current choice in the app. */
        fun currentTopic(context: Context): WidgetTopic =
            allWidgetIds(context).firstOrNull()?.let { WidgetInstanceState.topic(context, it) } ?: WidgetTopic.ALL

        /** Briefly shows tiny arrow hints over the controls, then hides them again. */
        private suspend fun flashHints(context: Context, manager: AppWidgetManager, ids: List<Int>) {
            val stack = context.appContainer.widgetPrefs.current().layout == WidgetLayout.STACK
            delay(HINT_DELAY_MS)
            ids.forEach { id ->
                manager.partiallyUpdateAppWidget(
                    id,
                    RemoteViews(context.packageName, R.layout.widget_wiki_container).apply {
                        setViewVisibility(R.id.widget_hint, View.VISIBLE)
                        // Stack: swipe the deck. One at a time: NEXT and the topic tag.
                        setViewVisibility(R.id.widget_hint_swipe, if (stack) View.VISIBLE else View.GONE)
                        setViewVisibility(R.id.widget_hint_next, if (stack) View.GONE else View.VISIBLE)
                        setViewVisibility(R.id.widget_hint_topic, if (stack) View.GONE else View.VISIBLE)
                    },
                )
            }
            delay(HINT_VISIBLE_MS)
            ids.forEach { id ->
                manager.partiallyUpdateAppWidget(
                    id,
                    RemoteViews(context.packageName, R.layout.widget_wiki_container).apply {
                        setViewVisibility(R.id.widget_hint, View.GONE)
                    },
                )
            }
        }

        private fun bindAll(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val prefs = context.appContainer.widgetPrefs.currentBlocking()
            val spec = WidgetThemeHelper.resolve(context, prefs)
            ids.forEach { id -> manager.updateAppWidget(id, buildViews(context, id, spec, prefs.layout)) }
            if (prefs.layout == WidgetLayout.STACK) manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_stack)
        }

        /**
         * Container controls (NEXT ›, topic tag) go through the same trampoline activity as card
         * taps: broadcast PendingIntents from the launcher to this receiver are dropped on recent
         * Android builds (seen on API 37), while activity PendingIntents are always delivered.
         */
        private fun broadcast(context: Context, action: String, appWidgetId: Int, requestBase: Int) =
            PendingIntent.getActivity(
                context,
                requestBase + appWidgetId,
                Intent(context, WidgetActionActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    .putExtra(
                        WidgetActionActivity.EXTRA_ACTION,
                        if (action == ACTION_NEXT) WidgetActionActivity.ACTION_NEXT else WidgetActionActivity.ACTION_TOPIC,
                    )
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        /**
         * One at a time draws the current card straight into the widget with direct
         * PendingIntents. (An AdapterViewFlipper keeps neighbouring cards laid out over the visible
         * one, and its taps opened the wrong article or nothing at all.)
         */
        private fun bindSingleCard(context: Context, views: RemoteViews, appWidgetId: Int, spec: WidgetStyleSpec) {
            val deck = WidgetCards.loadDeck(context, appWidgetId)
            views.removeAllViews(R.id.widget_single)
            if (deck.facts.isEmpty()) {
                views.setViewVisibility(R.id.widget_single, View.GONE)
                views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
                return
            }
            views.setViewVisibility(R.id.widget_empty, View.GONE)
            val position = WidgetInstanceState.position(context, appWidgetId).coerceIn(0, deck.facts.lastIndex)
            val fact = deck.facts[position]
            // Every style needs the size now (line cap); drawn-font styles also draw to it.
            val size = runCatching { WidgetBitmapText.widgetSizeDp(context, appWidgetId) }.getOrNull()
            val card = WidgetCards.build(context, spec, WidgetLayout.SINGLE, deck, position, size) { id, action ->
                setOnClickPendingIntent(id, cardAction(context, appWidgetId, action, fact))
            }
            views.addView(R.id.widget_single, card)
        }

        private val CARD_ACTIONS = listOf(
            WidgetActionActivity.ACTION_OPEN,
            WidgetActionActivity.ACTION_BOOKMARK,
            WidgetActionActivity.ACTION_REFRESH,
            WidgetActionActivity.ACTION_TOPIC,
        )

        private fun cardAction(context: Context, appWidgetId: Int, action: String, fact: FactEntity): PendingIntent =
            PendingIntent.getActivity(
                context,
                REQUEST_CARD + appWidgetId * CARD_ACTIONS.size + CARD_ACTIONS.indexOf(action).coerceAtLeast(0),
                Intent(context, WidgetActionActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    .putExtras(WidgetCards.actionExtras(action, fact, appWidgetId)),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private fun bindStack(context: Context, views: RemoteViews, appWidgetId: Int, spec: WidgetStyleSpec) {
            val serviceIntent = Intent(context, WikiWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                // The look and topic are part of the adapter's identity: a change then binds a new
                // adapter that must reload, instead of a "data changed" signal the launcher can drop
                // while a load is in flight (which left some widgets stuck on an old style).
                putExtra(EXTRA_CONFIG, "${spec.style}|${spec.dark}|${WidgetInstanceState.topic(context, appWidgetId).key}")
                // Unique data URI so the system doesn't share one factory across widget instances.
                data = toUri(Intent.URI_INTENT_SCHEME).toUri()
            }
            views.setRemoteAdapter(R.id.widget_stack, serviceIntent)
            // On fanning launchers the deck is drawn larger than the widget (see deckScale).
            val scale = WidgetCards.deckScale(context)
            if (scale > 1f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching { WidgetBitmapText.widgetSizeDp(context, appWidgetId) }.getOrNull()?.let { (w, h) ->
                    views.setViewLayoutWidth(R.id.widget_stack, w * scale, TypedValue.COMPLEX_UNIT_DIP)
                    views.setViewLayoutHeight(R.id.widget_stack, h * scale, TypedValue.COMPLEX_UNIT_DIP)
                }
            }
            views.setDisplayedChild(R.id.widget_stack, WidgetInstanceState.position(context, appWidgetId))
            views.setEmptyView(R.id.widget_stack, R.id.widget_empty)
            // Card taps: one MUTABLE template; each card's fill-in intent adds its action + fact.
            views.setPendingIntentTemplate(
                R.id.widget_stack,
                PendingIntent.getActivity(
                    context,
                    REQUEST_TEMPLATE + appWidgetId,
                    Intent(context, WidgetActionActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                ),
            )
        }

        private fun buildViews(context: Context, appWidgetId: Int, spec: WidgetStyleSpec, layout: WidgetLayout): RemoteViews {
            return RemoteViews(context.packageName, R.layout.widget_wiki_container).apply {
                WidgetThemeHelper.applyContainer(context, this, spec)
                val single = layout == WidgetLayout.SINGLE
                setViewVisibility(R.id.widget_stack, if (single) View.GONE else View.VISIBLE)
                setViewVisibility(R.id.widget_single, if (single) View.VISIBLE else View.GONE)
                setViewVisibility(R.id.widget_hint, View.GONE)
                // One at a time: "NEXT ›" over the card footer's empty slot. Stack: each card's own "›".
                setViewVisibility(R.id.widget_next, if (single) View.VISIBLE else View.GONE)
                if (single) bindSingleCard(context, this, appWidgetId, spec) else bindStack(context, this, appWidgetId, spec)
                setOnClickPendingIntent(R.id.widget_next, broadcast(context, ACTION_NEXT, appWidgetId, REQUEST_NEXT))

                // Empty state tap opens the app. Refresh lives on each card (fill-in intent).
                val openApp = PendingIntent.getActivity(
                    context,
                    REQUEST_OPEN_APP,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                setOnClickPendingIntent(R.id.widget_empty, openApp)
            }
        }

        private const val REQUEST_OPEN_APP = 1
        private const val REQUEST_NEXT = 100_000
        private const val REQUEST_CARD = 300_000
        private const val REQUEST_TEMPLATE = 1_000
    }
}

/** 3×2 preset — fits beside a row of icons. */
class WikiWidgetProviderCompact : WikiWidgetProvider()

/** 4×3 preset — longer hooks, bigger type. */
class WikiWidgetProviderLarge : WikiWidgetProvider()
