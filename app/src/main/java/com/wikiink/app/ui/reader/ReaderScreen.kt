package com.wikiink.app.ui.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wikiink.app.data.local.FolderEntity
import com.wikiink.app.data.repository.ReaderArticle
import com.wikiink.app.data.repository.WikiText
import com.wikiink.app.ui.theme.AppTheme
import com.wikiink.app.ui.theme.HairlineWidth
import com.wikiink.app.ui.theme.Ink
import com.wikiink.app.ui.theme.InkButton
import com.wikiink.app.ui.theme.InkCard
import com.wikiink.app.ui.theme.InkCorner
import com.wikiink.app.ui.theme.InkRule
import com.wikiink.app.ui.theme.InkTag
import com.wikiink.app.ui.theme.MonoFamily
import com.wikiink.app.ui.theme.ReaderFontSize
import com.wikiink.app.ui.theme.ReaderTypeface
import com.wikiink.app.ui.theme.SerifFamily
import com.wikiink.app.ui.theme.inkClickable
import com.wikiink.app.ui.theme.monoTag
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Grayscale (saturation 0) followed by a steep linear contrast curve around mid-gray. */
val EInkColorFilter: ColorFilter = run {
    val contrast = 1.6f
    val offset = 128f * (1f - contrast) // ColorMatrix offsets are in 0..255 space
    val grayscale = ColorMatrix().apply { setToSaturation(0f) }
    val curve = ColorMatrix(
        floatArrayOf(
            contrast, 0f, 0f, 0f, offset,
            0f, contrast, 0f, 0f, offset,
            0f, 0f, contrast, 0f, offset,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
    grayscale.timesAssign(curve)
    ColorFilter.colorMatrix(grayscale)
}


@Composable
fun ReaderScreen(
    theme: AppTheme,
    onCycleTheme: () -> Unit,
    onOpenQuiz: () -> Unit,
    viewModel: ReaderViewModel = viewModel(factory = ReaderViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val content = state as? ReaderUiState.Content
    val fontSize by viewModel.fontSize.collectAsStateWithLifecycle()
    val diving by viewModel.diving.collectAsStateWithLifecycle()
    val typeface by viewModel.typeface.collectAsStateWithLifecycle()
    val bookmarked by viewModel.isBookmarked.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val canStep by viewModel.canStep.collectAsStateWithLifecycle()
    val deckPosition by viewModel.deckPosition.collectAsStateWithLifecycle()
    val refreshFlash by viewModel.refreshFlash.collectAsStateWithLifecycle()
    val openCount by viewModel.openCount.collectAsStateWithLifecycle()
    // Each article opens as a summary (its intro); FULL ARTICLE expands it. Resets per article.
    val articleKey = content?.article?.title
    var fullArticle by rememberSaveable(articleKey) { mutableStateOf(false) }

    // One scroll state for the reader, so progress and per-article positions stay in sync.
    val listState = rememberLazyListState()
    val positions = remember { mutableMapOf<String, Pair<Int, Int>>() }
    var lastKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(articleKey) {
        lastKey?.let { positions[it] = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
        val saved = articleKey?.let { positions[it] }
        if (saved != null) listState.scrollToItem(saved.first, saved.second) else listState.scrollToItem(0)
        lastKey = articleKey
    }
    // Read in leaf composables only, so scrolling doesn't recompose the whole reader.
    val progress = remember(listState) { derivedStateOf { listState.readingProgress() } }
    val scope = rememberCoroutineScope()

    // Word count / reading time are expensive on long articles; compute once per article.
    val articleText = content?.article?.rawPlainText
    val words = remember(articleText) { articleText?.let { WikiText.wordCount(it) } ?: 0 }
    val minutes = remember(articleText) { articleText?.let { WikiText.readingMinutes(it) } ?: 0 }

    // E-Ink refresh flash: a brief full-screen invert when a new article lands.
    var flashing by remember { mutableStateOf(false) }
    LaunchedEffect(openCount) {
        if (refreshFlash && openCount > 0) {
            flashing = true
            delay(120)
            flashing = false
        }
    }

    // An e-reader shouldn't dim mid-sentence.
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // Brief "THEME // AMOLED" popup after each theme change (not on first composition).
    var themePopup by remember { mutableStateOf<String?>(null) }
    var lastTheme by remember { mutableStateOf(theme) }
    LaunchedEffect(theme) {
        if (theme != lastTheme) {
            lastTheme = theme
            themePopup = "THEME // ${theme.label.uppercase()}"
            delay(1_200)
            themePopup = null
        }
    }

    var showFolderPicker by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(message) {
        if (message != null) {
            delay(2_000)
            viewModel.consumeMessage()
        }
    }

    val c = Ink.colors
    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when (val s = state) {
                    ReaderUiState.Loading -> StatusMessage("LOADING ARTICLE…")
                    ReaderUiState.Empty -> StatusMessage(
                        "ARCHIVE EMPTY // WAITING FOR SYNC",
                        action = "SYNC NOW" to viewModel::syncNow,
                    )
                    is ReaderUiState.Error -> StatusMessage(
                        "ERROR // ${s.message.take(140).uppercase()}",
                        action = "RETRY" to viewModel::load,
                    )
                    is ReaderUiState.Content -> ArticleBody(
                        article = s.article,
                        listState = listState,
                        fontSize = fontSize,
                        full = fullArticle,
                        onReadFull = { fullArticle = true },
                    )
                }
                if (content != null) {
                    BackToTop(
                        progress = progress,
                        onClick = { scope.launch { listState.scrollToItem(0) } },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                    )
                }
            }
            if (content != null) {
                ReadingStatus(
                    progress = progress,
                    position = deckPosition,
                    words = words,
                    minutes = minutes,
                )
            }
            // Reading controls dock at the bottom, within thumb reach.
            InkRule()
            ReaderControls(
                fontSize = fontSize,
                onFontSize = viewModel::setFontSize,
                typeface = typeface,
                onTypeface = viewModel::setTypeface,
                theme = theme,
                onCycleTheme = onCycleTheme,
                diving = diving,
                onDive = viewModel::rabbitHole,
                onQuiz = onOpenQuiz,
                bookmarked = bookmarked,
                bookmarkEnabled = content != null,
                onBookmark = { showFolderPicker = true },
                canStep = canStep,
                onPrevious = viewModel::previous,
                onNext = viewModel::next,
                fullArticle = fullArticle,
                canExpand = content?.article?.sections?.size?.let { it > 1 } == true,
                onToggleFull = { fullArticle = !fullArticle },
                refreshFlash = refreshFlash,
                onToggleFlash = viewModel::toggleRefreshFlash,
            )
        }

        (themePopup ?: message)?.let {
            Text(
                text = it,
                style = monoTag(11.sp),
                color = c.background,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = 120.dp)
                    .background(c.ink, RoundedCornerShape(4.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }

        if (flashing) {
            Box(Modifier.fillMaxSize().background(c.ink))
        }
    }

    if (showFolderPicker) {
        FolderPickerDialog(
            folders = folders,
            bookmarked = bookmarked,
            onPick = {
                viewModel.bookmarkTo(it)
                showFolderPicker = false
            },
            onCreate = {
                viewModel.createFolderAndBookmark(it)
                showFolderPicker = false
            },
            onRemove = {
                viewModel.removeBookmark()
                showFolderPicker = false
            },
            onDismiss = { showFolderPicker = false },
        )
    }
}

@Composable
private fun ReaderControls(
    fontSize: ReaderFontSize,
    onFontSize: (ReaderFontSize) -> Unit,
    typeface: ReaderTypeface,
    onTypeface: (ReaderTypeface) -> Unit,
    theme: AppTheme,
    onCycleTheme: () -> Unit,
    diving: Boolean,
    onDive: () -> Unit,
    onQuiz: () -> Unit,
    bookmarked: Boolean,
    bookmarkEnabled: Boolean,
    onBookmark: () -> Unit,
    canStep: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    fullArticle: Boolean,
    canExpand: Boolean,
    onToggleFull: () -> Unit,
    refreshFlash: Boolean,
    onToggleFlash: () -> Unit,
) {
    var showType by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        // The Aa panel opens upward, above the dock.
        if (showType) {
            TypePanel(fontSize, onFontSize, typeface, onTypeface, refreshFlash, onToggleFlash)
        }
        // Row 1: reading through the deck. Row 2: tools. Every control has a weighted slot, so
        // nothing scrolls, wraps or shifts when a label changes.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            InkButton(
                text = "‹ PREV",
                enabled = canStep,
                onClick = onPrevious,
                contentDescription = "Back (or previous fact)",
                modifier = Modifier.weight(1f).height(40.dp),
            )
            InkButton(
                text = if (fullArticle) "SUMMARY" else "FULL ARTICLE",
                selected = fullArticle,
                enabled = canExpand,
                onClick = onToggleFull,
                contentDescription = if (fullArticle) "Show just the summary" else "Show the full article",
                modifier = Modifier.weight(1.3f).height(40.dp),
            )
            InkButton(
                text = "NEXT ›",
                enabled = canStep,
                onClick = onNext,
                contentDescription = "Next fact",
                modifier = Modifier.weight(1f).height(40.dp),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InkButton(
                text = "Aa",
                selected = showType,
                onClick = { showType = !showType },
                contentDescription = "Text size and typeface",
                minWidth = 0.dp,
                textSize = 15.sp,
                modifier = Modifier.weight(0.8f).height(40.dp),
            )
            InkButton(
                text = "◐",
                onClick = onCycleTheme,
                contentDescription = "Theme: ${theme.label}. Tap for the next theme",
                minWidth = 0.dp,
                textSize = 20.sp,
                modifier = Modifier.weight(0.8f).height(40.dp),
            )
            InkButton(
                text = if (diving) "…" else "DIVE",
                enabled = bookmarkEnabled && !diving,
                onClick = onDive,
                contentDescription = "Rabbit Hole: jump to a random article linked from this one",
                minWidth = 0.dp,
                modifier = Modifier.weight(1f).height(40.dp),
            )
            InkButton(
                text = "QUIZ",
                onClick = onQuiz,
                contentDescription = "Quiz: Quick Guess It or Ranked Nerd Points",
                minWidth = 0.dp,
                modifier = Modifier.weight(1f).height(40.dp),
            )
            InkButton(
                text = if (bookmarked) "SAVED" else "SAVE",
                selected = bookmarked,
                enabled = bookmarkEnabled,
                onClick = onBookmark,
                contentDescription = "Bookmark to folder",
                minWidth = 0.dp,
                modifier = Modifier.weight(1.1f).height(40.dp),
            )
        }
    }
}

/** "Aa" panel: text size plus the bundled reading typefaces, each previewed in its own face. */
@Composable
private fun TypePanel(
    fontSize: ReaderFontSize,
    onFontSize: (ReaderFontSize) -> Unit,
    typeface: ReaderTypeface,
    onTypeface: (ReaderTypeface) -> Unit,
    refreshFlash: Boolean,
    onToggleFlash: () -> Unit,
) {
    val c = Ink.colors
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)
            .verticalScroll(rememberScrollState())
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
    ) {
        Text("SIZE", style = monoTag(10.sp), color = c.inkSecondary)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ReaderFontSize.entries.forEach { size ->
                InkButton(
                    text = size.label,
                    selected = size == fontSize,
                    onClick = { onFontSize(size) },
                    contentDescription = "Font size ${size.label}",
                    minWidth = 0.dp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("TYPEFACE", style = monoTag(10.sp), color = c.inkSecondary)
        Spacer(Modifier.height(6.dp))
        ReaderTypeface.entries.forEach { face ->
            val selected = face == typeface
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .border(HairlineWidth, c.border, RoundedCornerShape(4.dp))
                    .background(if (selected) c.ink else c.background, RoundedCornerShape(4.dp))
                    .inkClickable { onTypeface(face) }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        face.label,
                        style = TextStyle(fontFamily = face.family, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
                        color = if (selected) c.background else c.ink,
                    )
                    Text(
                        face.blurb,
                        style = TextStyle(fontFamily = face.family, fontSize = 13.sp, lineHeight = 17.sp),
                        color = if (selected) c.background.copy(alpha = 0.8f) else c.inkSecondary,
                    )
                }
                Text(if (selected) "■" else "□", style = monoTag(13.sp), color = if (selected) c.background else c.ink)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("REFRESH FLASH", style = monoTag(10.sp), color = c.inkSecondary)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            InkButton(
                text = "ON",
                selected = refreshFlash,
                onClick = { if (!refreshFlash) onToggleFlash() },
                contentDescription = "Enable the E-Ink refresh flash",
                minWidth = 0.dp,
                modifier = Modifier.weight(1f),
            )
            InkButton(
                text = "OFF",
                selected = !refreshFlash,
                onClick = { if (refreshFlash) onToggleFlash() },
                contentDescription = "Disable the E-Ink refresh flash",
                minWidth = 0.dp,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "A quick full-screen invert when a new article opens.",
            style = TextStyle(fontFamily = SerifFamily, fontSize = 12.sp, lineHeight = 16.sp),
            color = c.inkSecondary,
        )
    }
}

@Composable
private fun StatusMessage(text: String, action: Pair<String, () -> Unit>? = null) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = monoTag(12.sp), color = Ink.colors.inkSecondary)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            InkButton(text = action.first, onClick = action.second)
        }
    }
}

/** Floating "back to top" button, shown once the reader has scrolled past the first screen. */
@Composable
private fun BackToTop(progress: State<Float>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (progress.value > 0.12f) {
        InkButton(
            text = "↑ TOP",
            onClick = onClick,
            contentDescription = "Back to top",
            minWidth = 0.dp,
            textSize = 11.sp,
            modifier = modifier,
        )
    }
}

/** Slim progress rule plus deck position and reading-time estimate above the control dock. */
@Composable
private fun ReadingStatus(
    progress: State<Float>,
    position: Pair<Int, Int>?,
    words: Int,
    minutes: Int,
) {
    val c = Ink.colors
    val fraction = progress.value.coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(c.surface)) {
            Box(Modifier.fillMaxWidth(fraction).height(2.dp).background(c.ink))
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = position?.let { "FACT ${it.first}/${it.second}" } ?: "ARTICLE",
                style = monoTag(10.sp),
                color = c.inkSecondary,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${(fraction * 100).roundToInt()}% // $words WORDS // ~$minutes MIN",
                style = monoTag(10.sp),
                color = c.inkSecondary,
                maxLines = 1,
            )
        }
    }
}

/** Approximate scroll progress through the current LazyColumn. */
private fun LazyListState.readingProgress(): Float {
    val info = layoutInfo
    val total = info.totalItemsCount
    if (total == 0) return 0f
    val first = info.visibleItemsInfo.firstOrNull() ?: return 0f
    val size = first.size.coerceAtLeast(1)
    val within = (-first.offset).coerceIn(0, size).toFloat() / size
    return ((first.index + within) / total).coerceIn(0f, 1f)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun ArticleBody(
    article: ReaderArticle,
    listState: LazyListState,
    fontSize: ReaderFontSize,
    full: Boolean,
    onReadFull: () -> Unit,
) {
    val c = Ink.colors
    val body = TextStyle(
        fontFamily = SerifFamily,
        fontSize = fontSize.bodySp.sp,
        lineHeight = (fontSize.bodySp * 1.55f).sp,
        color = c.ink,
    )
    val words = remember(article.rawPlainText) { WikiText.wordCount(article.rawPlainText) }
    val minutes = remember(article.rawPlainText) { WikiText.readingMinutes(article.rawPlainText) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            state = listState,
            modifier = Modifier.widthIn(max = 680.dp).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        ) {
            item(key = "header") {
                Column {
                    article.category?.let {
                        InkTag(it)
                        Spacer(Modifier.height(10.dp))
                    }
                    Text(
                        article.title,
                        style = TextStyle(
                            fontFamily = SerifFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = fontSize.titleSp.sp,
                            lineHeight = (fontSize.titleSp * 1.18f).sp,
                        ),
                        color = c.ink,
                        modifier = Modifier.semantics { heading() },
                    )
                    article.description?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            // Wikidata descriptions can carry stray spacing ("Brighton and Hove , UK").
                            WikiText.tidyLine(it),
                            style = TextStyle(
                                fontFamily = SerifFamily,
                                fontSize = (fontSize.bodySp - 1).sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            ),
                            color = c.inkSecondary,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "$words WORDS // ~$minutes MIN READ",
                        style = monoTag(10.sp),
                        color = c.inkSecondary,
                    )
                    Spacer(Modifier.height(14.dp))
                    InkRule(thickness = HairlineWidth)
                    Spacer(Modifier.height(14.dp))
                }
            }

            article.imageUrl?.let { url ->
                item(key = "hero") {
                    HeroImage(url, article.title)
                    Spacer(Modifier.height(18.dp))
                }
            }

            article.hook?.takeIf { it.isNotBlank() }?.let { hook ->
                item(key = "hook") {
                    InkCard(Modifier.fillMaxWidth()) {
                        Text("HOOK", style = monoTag(10.sp), color = c.inkSecondary)
                        Spacer(Modifier.height(6.dp))
                        SelectionContainer {
                            Text(hook, style = body.copy(fontSize = (fontSize.bodySp - 1).sp))
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                }
            }

            // Summary = the article's intro (its first section); the rest appears with FULL ARTICLE.
            val shown = if (full) article.sections else article.sections.take(1)
            shown.forEachIndexed { sIndex, section ->
                section.heading?.let { heading ->
                    item(key = "h$sIndex") {
                        Column(Modifier.padding(top = 18.dp, bottom = 8.dp)) {
                            Text(
                                heading.uppercase(),
                                style = TextStyle(
                                    fontFamily = MonoFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    letterSpacing = 1.4.sp,
                                ),
                                color = c.ink,
                                modifier = Modifier.semantics { heading() },
                            )
                            Spacer(Modifier.height(4.dp))
                            InkRule()
                        }
                    }
                }
                items(section.paragraphs.size, key = { "p$sIndex-$it" }) { pIndex ->
                    SelectionContainer {
                        Text(
                            section.paragraphs[pIndex],
                            style = body,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                }
            }

            if (!full && article.sections.size > 1) {
                item(key = "more") {
                    InkButton(
                        text = "READ FULL ARTICLE ↓ (${article.sections.size - 1} MORE SECTIONS)",
                        onClick = onReadFull,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                }
            }

            item(key = "footer") {
                Column(Modifier.padding(top = 18.dp, bottom = 32.dp)) {
                    InkRule()
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "END // $words WORDS · ~$minutes MIN READ",
                        style = monoTag(10.sp),
                        color = c.inkSecondary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "SOURCE // WIKIPEDIA · CC BY-SA 4.0",
                        style = monoTag(10.sp),
                        color = c.inkSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroImage(url: String, title: String) {
    val c = Ink.colors
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url)
            .crossfade(false)
            .build(),
        contentDescription = title,
        contentScale = ContentScale.Crop,
        colorFilter = EInkColorFilter,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 10f)
            .heightIn(max = 320.dp)
            .border(HairlineWidth, c.border, InkCorner)
            .padding(HairlineWidth)
            .background(c.surface, InkCorner),
    )
}

@Composable
private fun FolderPickerDialog(
    folders: List<FolderEntity>,
    bookmarked: Boolean,
    onPick: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Ink.colors
    var newName by rememberSaveable { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .border(HairlineWidth, c.border, RoundedCornerShape(8.dp))
                .background(c.background, RoundedCornerShape(8.dp))
                .padding(16.dp),
        ) {
            Text("SAVE TO FOLDER", style = monoTag(12.sp), color = c.ink)
            Spacer(Modifier.height(4.dp))
            Text(
                if (bookmarked) "Already saved — pick another folder or remove it." else "Choose where this article should live.",
                style = TextStyle(fontFamily = SerifFamily, fontSize = 13.sp, lineHeight = 17.sp),
                color = c.inkSecondary,
            )
            Spacer(Modifier.height(10.dp))
            InkRule()
            Column(Modifier.heightIn(max = 280.dp).padding(vertical = 6.dp)) {
                LazyColumn {
                    if (folders.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                "NO FOLDERS YET — NAME ONE BELOW.",
                                style = monoTag(10.sp),
                                color = c.inkSecondary,
                                modifier = Modifier.padding(vertical = 14.dp),
                            )
                        }
                    }
                    items(folders, key = { it.id }) { folder ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .inkClickable { onPick(folder.id) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("▸ ", style = monoTag(12.sp), color = c.ink)
                            Text(
                                folder.name,
                                style = TextStyle(fontFamily = SerifFamily, fontSize = 17.sp),
                                color = c.ink,
                                maxLines = 1,
                            )
                        }
                        InkRule(thickness = 0.5.dp)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("+ NEW FOLDER", style = monoTag(10.sp), color = c.inkSecondary)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = newName,
                    onValueChange = { newName = it.take(40) },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = SerifFamily, fontSize = 16.sp, color = c.ink),
                    cursorBrush = SolidColor(c.ink),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (newName.isNotBlank()) onCreate(newName) }),
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, c.border, RoundedCornerShape(4.dp))
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    decorationBox = { inner ->
                        if (newName.isEmpty()) Text("New folder…", style = TextStyle(fontFamily = SerifFamily, fontSize = 16.sp), color = c.inkSecondary)
                        inner()
                    },
                )
                Spacer(Modifier.width(8.dp))
                InkButton(text = "+ ADD", enabled = newName.isNotBlank(), onClick = { onCreate(newName) })
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                if (bookmarked) InkButton(text = "REMOVE", onClick = onRemove)
                InkButton(text = "CANCEL", onClick = onDismiss)
            }
        }
    }
}
