package com.wikiink.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Users upgrading must keep their cached facts and bookmarks. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), WikiInkDatabase::class.java)

    @Test
    fun v1ToLatestKeepsFactsAndBookmarks() {
        helper.createDatabase(DB, 1).apply {
            execSQL(
                "INSERT INTO facts (id, articleTitle, hookText, sourceCategory, summaryExtract, fullPlainText, thumbnailUrl, fetchedAt, isSeen) " +
                    "VALUES ('f1', 'Alpha', 'Hook', 'DID YOU KNOW', 'Summary', NULL, NULL, 1, 0)",
            )
            execSQL("INSERT INTO folders (id, name, createdAt) VALUES (1, 'General', 1)")
            execSQL("INSERT INTO bookmarks (articleTitle, folderId, hookText, fullPlainText, thumbnailUrl, savedAt) VALUES ('Alpha', 1, 'Hook', 'Text', NULL, 1)")
            close()
        }

        val db = helper.runMigrationsAndValidate(DB, 3, true, WikiInkDatabase.MIGRATION_1_2, WikiInkDatabase.MIGRATION_2_3)

        db.query("SELECT subject FROM facts WHERE id = 'f1'").use {
            assertTrue(it.moveToFirst())
            assertEquals("MISC", it.getString(0))
        }
        db.query("SELECT COUNT(*) FROM bookmarks").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
        db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'index_facts_sourceCategory'").use {
            assertTrue("category index created", it.moveToFirst())
        }
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
