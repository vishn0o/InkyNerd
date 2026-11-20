package com.wikiink.app.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.wikiink.app.MainActivity
import com.wikiink.app.R
import com.wikiink.app.appContainer
import com.wikiink.app.worker.FactSyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Invisible trampoline for widget card clicks. Activity PendingIntents are exempt from Android 14+
 * background-activity-launch restrictions, so "open" can always reach [MainActivity], while
 * "bookmark" saves silently and finishes without showing UI.
 */
class WidgetActionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Exported (see the manifest), so treat every extra as untrusted input.
        val title = intent.getStringExtra(EXTRA_ARTICLE_TITLE)?.let(::sanitizeTitle)
        val factId = intent.getStringExtra(EXTRA_FACT_ID)?.takeIf { it.length <= MAX_ID_LENGTH }

        when (intent.getStringExtra(EXTRA_ACTION)) {
            ACTION_OPEN -> if (title != null) {
                startActivity(
                    Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra(MainActivity.EXTRA_ARTICLE_TITLE, title)
                        putExtra(MainActivity.EXTRA_FACT_ID, factId)
                    },
                )
            }
            // Deck controls: run the provider's logic directly (no broadcast hop to drop).
            ACTION_NEXT, ACTION_TOPIC -> {
                val app = applicationContext
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                val next = intent.getStringExtra(EXTRA_ACTION) == ACTION_NEXT
                val from = intent.getIntExtra(EXTRA_POSITION, -1)
                if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    app.appContainer.appScope.launch {
                        if (next) WikiWidgetProvider.showNext(app, id, from) else WikiWidgetProvider.cycleTopic(app, id)
                    }
                }
            }
            ACTION_REFRESH -> {
                FactSyncWorker.enqueueImmediate(applicationContext)
                WikiWidgetProvider.reloadCards(applicationContext)
            }
            ACTION_BOOKMARK -> if (title != null && inFlight.add(title)) {
                val app = applicationContext
                val container = app.appContainer
                container.appScope.launch {
                    // Double-taps on the bookmark icon would save then immediately un-save.
                    val saved = try {
                        runCatching { container.repository.toggleQuickBookmark(title, factId) }
                    } finally {
                        inFlight.remove(title)
                    }
                    withContext(Dispatchers.Main) {
                        val result = saved.getOrNull()
                        val msg = when {
                            result == null -> app.getString(R.string.widget_bookmark_failed)
                            result.saved -> app.getString(R.string.widget_bookmark_saved, result.folderName)
                            else -> app.getString(R.string.widget_bookmark_removed, result.folderName)
                        }
                        Toast.makeText(app, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        /** Wikipedia titles are at most 255 bytes; anything longer did not come from our widget. */
        private const val MAX_TITLE_LENGTH = 255
        private const val MAX_ID_LENGTH = 512

        /** Titles whose widget bookmark toggle is still running. */
        private val inFlight: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

        internal fun sanitizeTitle(raw: String): String? =
            raw.trim().takeIf { it.isNotEmpty() && it.length <= MAX_TITLE_LENGTH && it.none(Char::isISOControl) }

        const val EXTRA_ACTION = "com.wikiink.app.widget.EXTRA_ACTION"
        const val EXTRA_ARTICLE_TITLE = "articleTitle"
        const val EXTRA_FACT_ID = "factId"
        /** Stack only: the deck position of the card whose "›" was tapped (-1 when unknown). */
        const val EXTRA_POSITION = "com.wikiink.app.widget.EXTRA_POSITION"
        const val ACTION_OPEN = "open"
        const val ACTION_BOOKMARK = "bookmark"
        const val ACTION_REFRESH = "refresh"
        const val ACTION_NEXT = "next"
        const val ACTION_TOPIC = "topic"
    }
}
