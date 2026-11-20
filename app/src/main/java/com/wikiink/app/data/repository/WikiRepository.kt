package com.wikiink.app.data.repository

import com.wikiink.app.data.local.BookmarkDao
import com.wikiink.app.data.local.BookmarkEntity
import com.wikiink.app.data.local.FactDao
import com.wikiink.app.data.local.FactEntity
import com.wikiink.app.data.local.FolderEntity
import com.wikiink.app.data.local.FolderWithCount
import com.wikiink.app.data.local.WikiInkDatabase
import com.wikiink.app.data.remote.MediaWikiApiService
import com.wikiink.app.data.quiz.QuizBuilder
import com.wikiink.app.data.quiz.QuizLevel
import com.wikiink.app.data.quiz.QuizSource
import com.wikiink.app.data.quiz.QuizTopic
import com.wikiink.app.data.quiz.QuizTopics
import com.wikiink.app.data.remote.PageSummaryDto
import com.wikiink.app.data.remote.WikipediaApiService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import com.wikiink.app.data.local.FactCategory
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.random.Random
import kotlin.coroutines.cancellation.CancellationException

/** Everything the E-Ink reader needs to render an article. */
data class ReaderArticle(
    val title: String,
    val description: String?,
    val category: String?,
    val hook: String?,
    val imageUrl: String?,
    val sections: List<ArticleSection>,
    val rawPlainText: String,
    val factId: String?,
)

class WikiRepository(
    private val wikiApi: WikipediaApiService,
    private val mediaWikiApi: MediaWikiApiService,
    private val factDao: FactDao,
    private val bookmarkDao: BookmarkDao,
    private val onFactsChanged: () -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default,
    /** Injected so tests can run repository work on a test dispatcher. */
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    // ------------------------------------------------------------------ Facts / sync

    fun observeFacts(): Flow<List<FactEntity>> = factDao.observeAll()

    /** Cached fact counts by source category and by subject, for the topic picker. */
    fun observeTopicCounts(): Flow<Pair<Map<String, Int>, Map<String, Int>>> =
        combine(factDao.observeCategoryCounts(), factDao.observeSubjectCounts()) { c, s ->
            c.associate { it.category to it.count } to s.associate { it.category to it.count }
        }

    suspend fun newestFact(): FactEntity? = factDao.getNewest()

    suspend fun markSeen(factId: String) = factDao.markSeen(factId)

    /**
     * Builds a mixed pool of niche trivia and stores it for the widget:
     *  - DID YOU KNOW hooks from the current and four random monthly `Recent additions` archives,
     *  - ODDITY entries from three random sections of `Wikipedia:Unusual articles`,
     *  - a handful of today's ON THIS DAY events (capped so they no longer dominate),
     *  - RANDOM ARCHIVE summaries to top up.
     * Every fact is tagged with a [Subject] (space, food, myth, …). Categories are interleaved so the deck alternates. @return number of new facts inserted.
     */
    suspend fun syncFacts(): Int = withContext(io) {
        val now = clock()
        val today = LocalDate.now(ZoneOffset.UTC)

        val (feedFacts, dykFacts, oddFacts) = coroutineScope {
            val feed = async {
                runCatchingNonCancel {
                    wikiApi.featuredFeed(today.year.toString(), "%02d".format(today.monthValue), "%02d".format(today.dayOfMonth))
                }.getOrNull()?.let { WikiText.factsFromFeed(it, now) }.orEmpty()
            }
            val dyk = dykSources(today).map { (page, section) ->
                async { parsed(page, section)?.let { WikiText.dykFromParsedHtml(it, now) }.orEmpty() }
            }
            val odd = List(ODDITY_SECTIONS_PER_SYNC) {
                async { parsed(UNUSUAL_ARTICLES, random.nextInt(1, UNUSUAL_MAX_SECTION + 1))?.let { WikiText.odditiesFromParsedHtml(it, now) }.orEmpty() }
            }
            Triple(feed.await(), dyk.awaitAll().flatten(), odd.awaitAll().flatten())
        }

        val trivia = WikiText.dedupe(feedFacts.filter { it.sourceCategory == FactCategory.DID_YOU_KNOW } + dykFacts)
            .shuffled(random).take(MAX_DYK)
        val oddities = WikiText.dedupe(oddFacts).shuffled(random).take(MAX_ODDITIES)
        val onThisDay = feedFacts.filter { it.sourceCategory == FactCategory.ON_THIS_DAY }
            .shuffled(random).take(MAX_ON_THIS_DAY)
        val pool = WikiText.dedupe(trivia + oddities + onThisDay).toMutableList()

        var attempts = 0
        var randomAdded = 0
        while (pool.size < TARGET_POOL && randomAdded < MAX_RANDOM && attempts < MAX_RANDOM_ATTEMPTS) {
            attempts++
            val page = runCatchingNonCancel { wikiApi.randomSummary() }.getOrNull() ?: continue
            val fact = WikiText.factFromRandom(page, now) ?: continue
            if (pool.none { it.articleTitle.equals(fact.articleTitle, ignoreCase = true) }) {
                pool += fact
                randomAdded++
            }
        }

        if (pool.isEmpty() && factDao.count() == 0) {
            throw IllegalStateException("No facts could be fetched")
        }

        val fresh = WikiText.interleave(withSubjects(WikiText.dedupe(pool, factDao.allTitles())), now)
        factDao.insertAll(fresh)
        factDao.pruneTo(MAX_CACHED_FACTS)
        reclassifyCached()
        saveOffline()
        onFactsChanged()
        fresh.size
    }

    /**
     * DYK hooks and oddities arrive without a Wikipedia short description, so fetch those in
     * batches (50 titles per request) and re-classify; facts that already have one keep theirs.
     */
    private suspend fun withSubjects(facts: List<FactEntity>): List<FactEntity> {
        val needs = facts.filter { it.subject == Subject.MISC.name || it.summaryExtract.isBlank() }
        if (needs.isEmpty()) return facts
        val descriptions = fetchDescriptions(needs.map { it.articleTitle })
        return facts.map { f ->
            val d = descriptions[f.articleTitle.lowercase()] ?: return@map f
            f.copy(subject = Subjects.classify(d, f.articleTitle, f.hookText).name)
        }
    }

    private suspend fun fetchDescriptions(titles: List<String>): Map<String, String> = coroutineScope {
        titles.distinct().chunked(DESCRIPTION_BATCH).map { chunk ->
            async {
                runCatchingNonCancel { mediaWikiApi.descriptions(chunk.joinToString("|")) }
                    .getOrNull()?.query?.let { WikiText.descriptionsByRequestedTitle(it) }.orEmpty()
            }
        }.awaitAll().fold(HashMap<String, String>()) { acc, m -> acc.apply { putAll(m) } }
    }

    /**
     * Downloads the full plain text of the next [OFFLINE_ARTICLES] cards so they open without a
     * network. Plain text only (no images), roughly 10–40 KB per article, so this stays around a
     * megabyte of app data and adds nothing to the app download. Bookmarks keep their own copy.
     */
    private suspend fun saveOffline() {
        factDao.trimOfflineText(OFFLINE_ARTICLES)
        val missing = factDao.missingOfflineText(OFFLINE_ARTICLES)
        missing.chunked(OFFLINE_PARALLEL).forEach { batch ->
            coroutineScope {
                batch.map { f -> async { f.articleTitle to fetchPlainText(f.articleTitle) } }.awaitAll()
            }.forEach { (title, text) -> if (text != null) cacheFullText(title, text) }
        }
    }

    /**
     * Rabbit Hole: a random interesting article linked from [title], or null when offline or the
     * page has no usable links.
     */
    suspend fun rabbitHoleFrom(title: String): String? = withContext(io) {
        val links = runCatchingNonCancel { mediaWikiApi.links(title).query?.pages.orEmpty() }
            .getOrNull()?.flatMap { p -> p.links.map { it.title } }.orEmpty()
        NerdGames.rabbitHoleCandidates(title, links).randomOrNull(random)
    }

    /**
     * A batch of on-topic Wikipedia intros for a ranked quiz round. Tries the category tree
     * (`deepcat:`), then the category alone, then a plain keyword search. Empty when offline.
     */
    suspend fun quizPool(topic: QuizTopic): List<QuizSource> = withContext(io) {
        val category = QuizTopics.drawCategory(topic) { random.nextInt(it) }
        // null = random articles (Auto); otherwise widest-to-loosest searches for the category.
        val queries: List<String?> = if (category == null) {
            listOf(null)
        } else {
            listOf("deepcat:\"$category\"", "incategory:\"$category\"", "\"$category\"")
        }
        for (q in queries) {
            val pool = runCatchingNonCancel { if (q == null) mediaWikiApi.randomPool() else mediaWikiApi.searchPool(q) }
                .getOrNull()?.query?.pages.orEmpty()
                .mapNotNull { p -> p.extract?.let { QuizSource(p.title, p.description, it) } }
                .let { QuizBuilder.usable(it) }
            if (pool.size >= QuizLevel.NERD.choices) return@withContext pool
        }
        emptyList()
    }

    /** Number of widget facts readable offline (bookmarks are always offline too). */
    fun observeOfflineCount(): Flow<Int> = factDao.observeOfflineCount()

    /** One-time-ish backfill: facts cached before subjects existed (or still MISC) get another look. */
    private suspend fun reclassifyCached() {
        val misc = factDao.unclassified(MAX_RECLASSIFY)
        if (misc.isEmpty()) return
        val descriptions = fetchDescriptions(misc.map { it.articleTitle })
        misc.forEach { f ->
            val subject = Subjects.classify(descriptions[f.articleTitle.lowercase()], f.articleTitle, f.hookText)
            if (subject != Subject.MISC) factDao.setSubject(f.id, subject.name)
        }
    }

    /** Latest DYK batches plus random monthly archives (2012 → last month) for deep-cut trivia. */
    private fun dykSources(today: LocalDate): List<Pair<String, Int>> {
        val current = listOf(RECENT_ADDITIONS to 2, RECENT_ADDITIONS to 3)
        val lastMonth = YearMonth.from(today).minusMonths(1)
        val first = YearMonth.of(DYK_ARCHIVE_START_YEAR, 1)
        val span = ChronoUnit.MONTHS.between(first, lastMonth).toInt().coerceAtLeast(1)
        val archives = List(DYK_ARCHIVES_PER_SYNC) {
            val ym = first.plusMonths(random.nextInt(span + 1).toLong())
            val month = ym.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
            "$RECENT_ADDITIONS/${ym.year}/$month" to random.nextInt(2, DYK_ARCHIVE_MAX_SECTION + 1)
        }
        return current + archives
    }

    private suspend fun parsed(page: String, section: Int): String? = runCatchingNonCancel {
        mediaWikiApi.parseSection(page, section).parse?.text?.takeIf { it.isNotBlank() }
    }.getOrNull()

    // ------------------------------------------------------------------ Reader

    /** Loads summary + full plain text, preferring cached text; writes fetched text back to the cache. */
    suspend fun loadArticle(title: String, factId: String?): ReaderArticle = withContext(io) {
        val fact = factId?.let { factDao.getById(it) } ?: factDao.getByTitle(title)
        val bookmark = bookmarkDao.getBookmark(title)
        val cachedText = fact?.fullPlainText ?: bookmark?.fullPlainText?.takeIf { it.isNotBlank() }

        coroutineScope {
            val summaryJob = async { runCatchingNonCancel { wikiApi.pageSummary(title.toApiTitle()) }.getOrNull() }
            val textJob = async { cachedText ?: fetchPlainText(title) }

            val summary = summaryJob.await()
            val fetchedText = textJob.await()
            if (fetchedText != null && cachedText == null) cacheFullText(title, fetchedText)

            val text = fetchedText
                ?: summary?.extract
                ?: fact?.summaryExtract?.takeIf { it.isNotBlank() }
                ?: bookmark?.hookText
                ?: throw IllegalStateException("Article unavailable offline")

            fact?.let { factDao.markSeen(it.id) }

            ReaderArticle(
                title = summary?.bestTitle ?: title,
                description = summary?.description,
                category = fact?.sourceCategory,
                hook = fact?.hookText ?: bookmark?.hookText,
                imageUrl = summary?.heroImage() ?: fact?.thumbnailUrl ?: bookmark?.thumbnailUrl,
                sections = WikiText.parseSections(text).ifEmpty { listOf(ArticleSection(null, listOf(text))) },
                rawPlainText = text,
                factId = fact?.id,
            )
        }
    }

    private suspend fun fetchPlainText(title: String): String? = runCatchingNonCancel {
        mediaWikiApi.plainTextExtract(title).query?.pages
            ?.firstOrNull { !it.missing && !it.invalid }
            ?.extract?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private suspend fun cacheFullText(title: String, text: String) {
        factDao.updateFullText(title, text)
    }

    // ------------------------------------------------------------------ Folders & bookmarks

    fun observeFoldersWithCount(): Flow<List<FolderWithCount>> = bookmarkDao.observeFoldersWithCount()

    fun observeFolders(): Flow<List<FolderEntity>> = bookmarkDao.observeFolders()

    fun observeFolder(id: Long): Flow<FolderEntity?> = bookmarkDao.observeFolder(id)

    fun observeBookmarksIn(folderId: Long): Flow<List<BookmarkEntity>> = bookmarkDao.observeBookmarksIn(folderId)

    fun observeIsBookmarked(title: String): Flow<Boolean> = bookmarkDao.observeIsBookmarked(title)

    suspend fun createFolder(name: String): Long =
        bookmarkDao.insertFolder(FolderEntity(name = name.trim(), createdAt = clock()))

    suspend fun deleteFolder(id: Long) {
        bookmarkDao.deleteFolder(id)
        onFactsChanged()
    }

    suspend fun defaultFolderId(): Long = bookmarkDao.defaultFolderId()
        ?: createFolder(WikiInkDatabase.DEFAULT_FOLDER_NAME)

    suspend fun bookmark(article: ReaderArticle, folderId: Long) {
        bookmarkDao.upsert(
            BookmarkEntity(
                articleTitle = article.title,
                folderId = folderId,
                hookText = article.hook ?: WikiText.trimHook(article.sections.firstOrNull()?.paragraphs?.firstOrNull().orEmpty()),
                fullPlainText = article.rawPlainText,
                thumbnailUrl = article.imageUrl,
                savedAt = clock(),
            ),
        )
        onFactsChanged()
    }

    suspend fun removeBookmark(title: String) {
        bookmarkDao.delete(title)
        onFactsChanged()
    }

    /** Outcome of a widget bookmark tap, naming the folder so the widget can say where. */
    data class QuickSaveResult(val saved: Boolean, val folderName: String)

    /**
     * Widget bookmark toggle: saves [title] into the default "General" folder, or — if it's
     * already bookmarked (in any folder) — removes it again.
     */
    suspend fun toggleQuickBookmark(title: String, factId: String?): QuickSaveResult = withContext(io) {
        bookmarkDao.getBookmark(title)?.let { existing ->
            val folder = bookmarkDao.folderName(existing.folderId) ?: WikiInkDatabase.DEFAULT_FOLDER_NAME
            bookmarkDao.delete(title)
            onFactsChanged()
            return@withContext QuickSaveResult(saved = false, folderName = folder)
        }
        val folderId = defaultFolderId()
        val fact = factId?.let { factDao.getById(it) } ?: factDao.getByTitle(title)
        val text = fact?.fullPlainText
            ?: fetchPlainText(title)?.also { cacheFullText(title, it) }
            ?: fact?.summaryExtract.orEmpty()
        bookmarkDao.upsert(
            BookmarkEntity(
                articleTitle = title,
                folderId = folderId,
                hookText = fact?.hookText ?: WikiText.trimHook(text),
                fullPlainText = text,
                thumbnailUrl = fact?.thumbnailUrl,
                savedAt = clock(),
            ),
        )
        onFactsChanged()
        QuickSaveResult(
            saved = true,
            folderName = bookmarkDao.folderName(folderId) ?: WikiInkDatabase.DEFAULT_FOLDER_NAME,
        )
    }

    // ------------------------------------------------------------------ helpers

    private fun String.toApiTitle() = trim().replace(' ', '_')

    private fun PageSummaryDto.heroImage(): String? =
        thumbnail?.source ?: originalimage?.takeIf { (it.width ?: 0) in 1..MAX_HERO_WIDTH }?.source

    private inline fun <T> runCatchingNonCancel(block: () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    companion object {
        const val TARGET_POOL = 56
        const val MAX_DYK = 30
        const val MAX_ODDITIES = 12
        const val MAX_ON_THIS_DAY = 4
        const val MAX_RANDOM = 10
        const val MAX_RANDOM_ATTEMPTS = 18
        const val RECENT_ADDITIONS = "Wikipedia:Recent additions"
        const val UNUSUAL_ARTICLES = "Wikipedia:Unusual articles"
        const val DYK_ARCHIVE_START_YEAR = 2012
        const val DYK_ARCHIVES_PER_SYNC = 4
        const val DYK_ARCHIVE_MAX_SECTION = 25
        const val ODDITY_SECTIONS_PER_SYNC = 3
        const val UNUSUAL_MAX_SECTION = 30
        const val DESCRIPTION_BATCH = 50
        const val MAX_RECLASSIFY = 150
        const val OFFLINE_ARTICLES = 40
        const val OFFLINE_PARALLEL = 4
        const val MAX_CACHED_FACTS = 240
        const val MAX_HERO_WIDTH = 1600
    }
}
