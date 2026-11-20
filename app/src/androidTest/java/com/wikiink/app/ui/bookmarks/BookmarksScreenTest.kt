package com.wikiink.app.ui.bookmarks

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wikiink.app.data.local.BookmarkEntity
import com.wikiink.app.data.local.FolderEntity
import com.wikiink.app.data.local.WikiInkDatabase
import com.wikiink.app.data.repository.WikiRepository
import com.wikiink.app.testing.offlineApi
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Folders screen against a real (in-memory) Room database, offline. */
@RunWith(AndroidJUnit4::class)
class BookmarksScreenTest {

    @get:Rule val compose = createComposeRule()

    private lateinit var db: WikiInkDatabase
    private lateinit var repository: WikiRepository
    private var opened: String? = null

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, WikiInkDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = WikiRepository(offlineApi(), offlineApi(), db.factDao(), db.bookmarkDao(), onFactsChanged = {})
        runBlocking {
            db.bookmarkDao().insertFolder(FolderEntity(name = "General", createdAt = 0))
            val space = db.bookmarkDao().insertFolder(FolderEntity(name = "Space", createdAt = 1))
            db.bookmarkDao().upsert(BookmarkEntity("Voyager 1", space, "Farthest probe", "Text", null, 0))
        }
        compose.setContent { BookmarksScreen(onOpenArticle = { opened = it }, viewModel = BookmarksViewModel(repository)) }
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun foldersListShowsCountsAndTheProtectedDefault() {
        compose.onNodeWithText("General").assertExists()
        compose.onNodeWithText("DEFAULT").assertExists()
        compose.onNodeWithText("1 ITEM").assertExists()
        compose.onNodeWithText("0 ITEMS").assertExists()
    }

    @Test
    fun openingAFolderAndABookmark() {
        compose.onNodeWithText("Space").performClick()
        compose.onNodeWithText("Voyager 1", substring = true).performClick()
        compose.runOnIdle { assertEquals("Voyager 1", opened) }
        compose.onNodeWithText("◂ BACK").performClick()
        compose.onNodeWithText("DEFAULT").assertExists()
    }

    @Test
    fun createIsDisabledUntilANameIsTyped() {
        compose.onNodeWithText("+ NEW").performClick()
        compose.onNodeWithText("CREATE").assertIsNotEnabled()
    }

    @Test
    fun creatingAFolderAddsExactlyOne() {
        compose.onNodeWithText("+ NEW").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Maps")
        compose.onNodeWithText("CREATE").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTextCount("Maps") == 1 }
        val count = db.query("SELECT COUNT(*) FROM folders WHERE name = 'Maps'", null).use { it.moveToFirst(); it.getInt(0) }
        assertEquals(1, count)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String) =
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size
}
