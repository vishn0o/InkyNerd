package com.wikiink.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [FactEntity::class, FolderEntity::class, BookmarkEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class WikiInkDatabase : RoomDatabase() {
    abstract fun factDao(): FactDao
    abstract fun bookmarkDao(): BookmarkDao

    companion object {
        const val DEFAULT_FOLDER_NAME = "General"

        fun build(context: Context): WikiInkDatabase =
            Room.databaseBuilder(context.applicationContext, WikiInkDatabase::class.java, "wikiink.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .addCallback(SeedCallback)
                .build()

        /** v2: facts gain a `subject` (what they're about) for the widget's topic switcher. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE facts ADD COLUMN subject TEXT NOT NULL DEFAULT 'MISC'")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_facts_subject ON facts(subject)")
            }
        }

        /** v3: index the widget's per-category deck query and the topic-count GROUP BY. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_facts_sourceCategory ON facts(sourceCategory)")
            }
        }

        private object SeedCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "INSERT INTO folders (name, createdAt) VALUES (?, ?)",
                    arrayOf<Any>(DEFAULT_FOLDER_NAME, System.currentTimeMillis()),
                )
            }
        }
    }
}
