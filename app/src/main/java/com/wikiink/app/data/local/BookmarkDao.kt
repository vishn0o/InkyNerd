package com.wikiink.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {

    // ---- Folders ----

    @Query(
        "SELECT f.id, f.name, f.createdAt, COUNT(b.articleTitle) AS bookmarkCount " +
            "FROM folders f LEFT JOIN bookmarks b ON b.folderId = f.id " +
            "GROUP BY f.id ORDER BY f.createdAt ASC, f.id ASC",
    )
    fun observeFoldersWithCount(): Flow<List<FolderWithCount>>

    @Query("SELECT * FROM folders ORDER BY createdAt ASC, id ASC")
    fun observeFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE id = :id")
    fun observeFolder(id: Long): Flow<FolderEntity?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFolder(folder: FolderEntity): Long

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteFolder(id: Long)

    @Query("SELECT name FROM folders WHERE id = :id")
    suspend fun folderName(id: Long): String?

    @Query("SELECT id FROM folders ORDER BY id ASC LIMIT 1")
    suspend fun defaultFolderId(): Long?

    // ---- Bookmarks ----

    @Query("SELECT * FROM bookmarks WHERE folderId = :folderId ORDER BY savedAt DESC")
    fun observeBookmarksIn(folderId: Long): Flow<List<BookmarkEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE articleTitle = :title)")
    fun observeIsBookmarked(title: String): Flow<Boolean>

    @Query("SELECT * FROM bookmarks WHERE articleTitle = :title")
    suspend fun getBookmark(title: String): BookmarkEntity?

    /** Blocking read for the widget factory. */
    @Query("SELECT articleTitle FROM bookmarks")
    fun bookmarkedTitlesSync(): List<String>

    @Upsert
    suspend fun upsert(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE articleTitle = :title")
    suspend fun delete(title: String)
}
