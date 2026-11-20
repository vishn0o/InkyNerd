package com.wikiink.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FactDao {

    @Query("SELECT * FROM facts ORDER BY fetchedAt DESC, id ASC")
    fun observeAll(): Flow<List<FactEntity>>

    /** Blocking read for the widget's RemoteViewsFactory (runs on a binder thread). */
    @Query("SELECT * FROM facts ORDER BY isSeen ASC, fetchedAt DESC, id ASC LIMIT :limit")
    fun getForWidgetSync(limit: Int = 60): List<FactEntity>

    /** Blocking, topic-filtered read for the widget factory. */
    @Query("SELECT * FROM facts WHERE sourceCategory = :category ORDER BY isSeen ASC, fetchedAt DESC, id ASC LIMIT :limit")
    fun getForWidgetByCategorySync(category: String, limit: Int = 60): List<FactEntity>

    @Query("SELECT * FROM facts WHERE subject = :subject ORDER BY isSeen ASC, fetchedAt DESC, id ASC LIMIT :limit")
    fun getForWidgetBySubjectSync(subject: String, limit: Int = 60): List<FactEntity>

    @Query("SELECT sourceCategory AS category, COUNT(*) AS count FROM facts GROUP BY sourceCategory")
    suspend fun categoryCounts(): List<CategoryCount>

    /** Counts per subject; [CategoryCount.category] holds the subject name. */
    @Query("SELECT subject AS category, COUNT(*) AS count FROM facts GROUP BY subject")
    suspend fun subjectCounts(): List<CategoryCount>

    @Query("SELECT sourceCategory AS category, COUNT(*) AS count FROM facts GROUP BY sourceCategory")
    fun observeCategoryCounts(): Flow<List<CategoryCount>>

    @Query("SELECT subject AS category, COUNT(*) AS count FROM facts GROUP BY subject")
    fun observeSubjectCounts(): Flow<List<CategoryCount>>

    @Query("SELECT * FROM facts WHERE subject = 'MISC' LIMIT :limit")
    suspend fun unclassified(limit: Int): List<FactEntity>

    @Query("UPDATE facts SET subject = :subject WHERE id = :id")
    suspend fun setSubject(id: String, subject: String)

    @Query("SELECT * FROM facts WHERE id = :id")
    suspend fun getById(id: String): FactEntity?

    @Query("SELECT * FROM facts WHERE articleTitle = :title ORDER BY fetchedAt DESC LIMIT 1")
    suspend fun getByTitle(title: String): FactEntity?

    @Query("SELECT * FROM facts ORDER BY isSeen ASC, fetchedAt DESC, id ASC LIMIT 1")
    suspend fun getNewest(): FactEntity?

    @Query("SELECT articleTitle FROM facts")
    suspend fun allTitles(): List<String>

    @Query("SELECT COUNT(*) FROM facts")
    suspend fun count(): Int

    /** IGNORE keeps `isSeen` and any cached full text on facts we already have. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(facts: List<FactEntity>)

    /** Facts in the widget's next [window] cards that don't have their article text saved yet. */
    @Query(
        "SELECT * FROM (SELECT * FROM facts ORDER BY isSeen ASC, fetchedAt DESC, id ASC LIMIT :window) " +
            "WHERE fullPlainText IS NULL",
    )
    suspend fun missingOfflineText(window: Int): List<FactEntity>

    /** Drops saved article text outside the next [keep] cards, so offline storage stays bounded. */
    @Query(
        "UPDATE facts SET fullPlainText = NULL WHERE fullPlainText IS NOT NULL AND id NOT IN " +
            "(SELECT id FROM facts ORDER BY isSeen ASC, fetchedAt DESC, id ASC LIMIT :keep)",
    )
    suspend fun trimOfflineText(keep: Int)

    @Query("SELECT COUNT(*) FROM facts WHERE fullPlainText IS NOT NULL")
    fun observeOfflineCount(): Flow<Int>

    @Query("UPDATE facts SET fullPlainText = :text WHERE articleTitle = :title")
    suspend fun updateFullText(title: String, text: String)

    @Query("UPDATE facts SET isSeen = 1 WHERE id = :id")
    suspend fun markSeen(id: String)

    @Query(
        "DELETE FROM facts WHERE id NOT IN " +
            "(SELECT id FROM facts ORDER BY fetchedAt DESC, id ASC LIMIT :keep)",
    )
    suspend fun pruneTo(keep: Int)
}
