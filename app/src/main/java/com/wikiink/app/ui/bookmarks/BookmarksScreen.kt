package com.wikiink.app.ui.bookmarks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wikiink.app.data.local.BookmarkEntity
import com.wikiink.app.data.local.FolderWithCount
import com.wikiink.app.ui.theme.HairlineWidth
import com.wikiink.app.ui.theme.Ink
import com.wikiink.app.ui.theme.InkButton
import com.wikiink.app.ui.theme.InkCard
import com.wikiink.app.ui.theme.InkRule
import com.wikiink.app.ui.theme.InkTopBar
import com.wikiink.app.ui.theme.SerifFamily
import com.wikiink.app.ui.theme.monoTag
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BookmarksScreen(
    onOpenArticle: (title: String) -> Unit,
    viewModel: BookmarksViewModel = viewModel(factory = BookmarksViewModel.Factory),
) {
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val openId by viewModel.openFolderId.collectAsStateWithLifecycle()
    val openFolder by viewModel.openFolder.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()

    var showCreate by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by rememberSaveable { mutableStateOf<Long?>(null) }

    BackHandler(enabled = openId != null) { viewModel.openFolder(null) }

    Column(Modifier.fillMaxSize().background(Ink.colors.background)) {
        if (openId == null) {
            InkTopBar("INKYNERD // FOLDERS") {
                InkButton(text = "+ NEW", onClick = { showCreate = true })
            }
            FolderList(
                folders = folders,
                defaultId = viewModel.defaultFolderId,
                onOpen = viewModel::openFolder,
                onDelete = { pendingDelete = it },
            )
        } else {
            InkTopBar("INKYNERD // ${openFolder?.name?.uppercase() ?: "FOLDER"}") {
                InkButton(text = "◂ BACK", onClick = { viewModel.openFolder(null) })
            }
            BookmarkList(
                bookmarks = bookmarks,
                onOpen = onOpenArticle,
                onRemove = viewModel::removeBookmark,
            )
        }
    }

    if (showCreate) {
        CreateFolderDialog(
            onCreate = {
                viewModel.createFolder(it)
                showCreate = false
            },
            onDismiss = { showCreate = false },
        )
    }

    pendingDelete?.let { id ->
        val folder = folders.firstOrNull { it.id == id }
        ConfirmDialog(
            title = "DELETE FOLDER?",
            body = "\"${folder?.name.orEmpty()}\" and its ${folder?.bookmarkCount ?: 0} bookmark(s) will be removed.",
            confirm = "DELETE",
            onConfirm = {
                viewModel.deleteFolder(id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun FolderList(
    folders: List<FolderWithCount>,
    defaultId: Long?,
    onOpen: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val c = Ink.colors
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(folders, key = { it.id }) { folder ->
            InkCard(Modifier.fillMaxWidth(), onClick = { onOpen(folder.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            folder.name,
                            style = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.SemiBold, fontSize = 19.sp),
                            color = c.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (folder.bookmarkCount == 1) "1 ITEM" else "${folder.bookmarkCount} ITEMS",
                            style = monoTag(10.sp),
                            color = c.inkSecondary,
                        )
                    }
                    if (folder.id != defaultId) {
                        InkButton(text = "DEL", onClick = { onDelete(folder.id) })
                    } else {
                        Text("DEFAULT", style = monoTag(10.sp), color = c.inkSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun BookmarkList(
    bookmarks: List<BookmarkEntity>,
    onOpen: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    val c = Ink.colors
    if (bookmarks.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("FOLDER EMPTY", style = monoTag(12.sp), color = c.inkSecondary)
        }
        return
    }
    val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(bookmarks, key = { it.articleTitle }) { bm ->
            InkCard(Modifier.fillMaxWidth(), onClick = { onOpen(bm.articleTitle) }) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "SAVED ${dateFmt.format(Date(bm.savedAt))}",
                            style = monoTag(10.sp),
                            color = c.inkSecondary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            bm.articleTitle,
                            style = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp),
                            color = c.ink,
                        )
                        if (bm.hookText.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                bm.hookText,
                                style = TextStyle(fontFamily = SerifFamily, fontSize = 14.sp, lineHeight = 20.sp),
                                color = c.inkSecondary,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    InkButton(text = "✕", onClick = { onRemove(bm.articleTitle) }, contentDescription = "Remove bookmark")
                }
            }
        }
    }
}

@Composable
private fun CreateFolderDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    val c = Ink.colors
    var name by rememberSaveable { mutableStateOf("") }
    InkDialog(onDismiss) {
        Text("NEW FOLDER", style = monoTag(12.sp), color = c.ink)
        Spacer(Modifier.height(10.dp))
        InkRule()
        Spacer(Modifier.height(12.dp))
        BasicTextField(
            value = name,
            onValueChange = { name = it.take(40) },
            singleLine = true,
            textStyle = TextStyle(fontFamily = SerifFamily, fontSize = 17.sp, color = c.ink),
            cursorBrush = SolidColor(c.ink),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onCreate(name) }),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, c.border, RoundedCornerShape(4.dp))
                .padding(horizontal = 10.dp, vertical = 10.dp),
            decorationBox = { inner ->
                if (name.isEmpty()) Text("Folder name…", style = TextStyle(fontFamily = SerifFamily, fontSize = 17.sp), color = c.inkSecondary)
                inner()
            },
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            InkButton(text = "CANCEL", onClick = onDismiss)
            InkButton(text = "CREATE", enabled = name.isNotBlank(), onClick = { onCreate(name) })
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Ink.colors
    InkDialog(onDismiss) {
        Text(title, style = monoTag(12.sp), color = c.ink)
        Spacer(Modifier.height(10.dp))
        InkRule()
        Spacer(Modifier.height(12.dp))
        Text(body, style = TextStyle(fontFamily = SerifFamily, fontSize = 16.sp, lineHeight = 22.sp), color = c.ink)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            InkButton(text = "CANCEL", onClick = onDismiss)
            InkButton(text = confirm, selected = true, onClick = onConfirm)
        }
    }
}

@Composable
private fun InkDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val c = Ink.colors
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .border(HairlineWidth, c.border, RoundedCornerShape(8.dp))
                .background(c.background, RoundedCornerShape(8.dp))
                .padding(16.dp),
        ) { content() }
    }
}
