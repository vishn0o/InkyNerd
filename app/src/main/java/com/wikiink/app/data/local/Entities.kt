package com.wikiink.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "facts",
    indices = [Index("articleTitle"), Index("fetchedAt"), Index("subject"), Index("sourceCategory")],
)
data class FactEntity(
    @PrimaryKey val id: String,
    val articleTitle: String,
    /** ≤ ~180 chars, sized for the widget card. */
    val hookText: String,
    /** One of [FactCategory]. */
    val sourceCategory: String,
    val summaryExtract: String,
    val fullPlainText: String?,
    val thumbnailUrl: String?,
    val fetchedAt: Long,
    val isSeen: Boolean = false,
    /** A [com.wikiink.app.data.repository.Subject] name — what the fact is about. */
    @ColumnInfo(defaultValue = "MISC") val subject: String = "MISC",
)

object FactCategory {
    const val DID_YOU_KNOW = "DID YOU KNOW"
    const val ON_THIS_DAY = "ON THIS DAY"
    const val RANDOM_ARCHIVE = "RANDOM ARCHIVE"
    /** Entries from Wikipedia:Unusual articles — the strange, niche and nerdy. */
    const val ODDITY = "ODDITY"
}

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("folderId")],
)
data class BookmarkEntity(
    @PrimaryKey val articleTitle: String,
    val folderId: Long,
    val hookText: String,
    val fullPlainText: String,
    val thumbnailUrl: String?,
    val savedAt: Long,
)

/** Number of cached facts per [FactCategory]. */
data class CategoryCount(val category: String, val count: Int)

/** Folder row joined with its bookmark count. */
data class FolderWithCount(
    val id: Long,
    val name: String,
    val createdAt: Long,
    @ColumnInfo(name = "bookmarkCount") val bookmarkCount: Int,
)
