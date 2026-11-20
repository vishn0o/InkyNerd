package com.wikiink.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.util.Consumer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wikiink.app.ui.bookmarks.BookmarksScreen
import com.wikiink.app.ui.onboarding.OnboardingScreen
import com.wikiink.app.ui.quiz.QuizScreen
import com.wikiink.app.ui.reader.ReaderScreen
import com.wikiink.app.ui.reader.ReaderViewModel
import com.wikiink.app.ui.widgetstyle.WidgetStyleScreen
import com.wikiink.app.ui.theme.HairlineWidth
import com.wikiink.app.ui.theme.Ink
import com.wikiink.app.ui.theme.InkRule
import com.wikiink.app.ui.theme.InkyTheme
import com.wikiink.app.ui.theme.AppTheme
import com.wikiink.app.ui.theme.inkClickable
import com.wikiink.app.ui.theme.monoTag
import com.wikiink.app.widget.WikiWidgetProvider
import com.wikiink.app.worker.FactSyncWorker
import androidx.core.content.edit

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            FactSyncWorker.enqueueImmediate(this)
            // After an install the app can sit in Android's "stopped" state, so the system's widget
            // update never reaches us and the launcher keeps the old build's (dead) tap targets.
            // Rebuilding on open makes every widget control live again.
            WikiWidgetProvider.rebuildAll(this)
        }

        // The intent that launched us is consumed once; config changes keep nav state via savedInstanceState.
        val launchIntent = if (savedInstanceState == null) intent else null
        val prefs = appContainer.readerPrefs
        val appPrefs = getSharedPreferences(APP_PREFS, MODE_PRIVATE)
        // Anyone who already has a widget placed (e.g. updating the app) skips setup.
        val alreadySetUp = appPrefs.getBoolean(KEY_ONBOARDED, false) ||
            WikiWidgetProvider.allWidgetIds(this).isNotEmpty()

        setContent {
            val override by prefs.theme.collectAsStateWithLifecycle()
            val theme = override ?: if (isSystemInDarkTheme()) AppTheme.NIGHT else AppTheme.PAPER

            DisposableEffect(theme) {
                val bar = theme.colors.background.toArgb()
                val style = if (theme.colors.isDark) SystemBarStyle.dark(bar) else SystemBarStyle.light(bar, bar)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }

            InkyTheme(theme = theme) {
                var onboarded by rememberSaveable { mutableStateOf(alreadySetUp) }
                if (!onboarded) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Ink.colors.background)
                            .statusBarsPadding()
                            .navigationBarsPadding(),
                    ) {
                        OnboardingScreen(onDone = {
                            appPrefs.edit { putBoolean(KEY_ONBOARDED, true) }
                            onboarded = true
                        })
                    }
                    return@InkyTheme
                }
                val nav = rememberNavController()
                LaunchedEffect(Unit) { launchIntent?.let { routeIntent(nav, it) } }
                DisposableEffect(nav) {
                    val listener = Consumer<Intent> { routeIntent(nav, it) }
                    addOnNewIntentListener(listener)
                    onDispose { removeOnNewIntentListener(listener) }
                }
                AppScaffold(nav, theme = theme, onCycleTheme = { prefs.setTheme(theme.next()) })
            }
        }
    }

    private fun routeIntent(nav: NavHostController, intent: Intent) {
        val title = intent.getStringExtra(EXTRA_ARTICLE_TITLE)?.takeIf { it.isNotBlank() } ?: return
        val factId = intent.getStringExtra(EXTRA_FACT_ID)
        // Consume so a later recreation doesn't re-route.
        intent.removeExtra(EXTRA_ARTICLE_TITLE)
        nav.navigate(Routes.reader(title, factId)) {
            popUpTo(nav.graph.findStartDestination().id) { inclusive = true }
        }
    }

    companion object {
        const val EXTRA_ARTICLE_TITLE = "com.wikiink.app.extra.ARTICLE_TITLE"
        const val EXTRA_FACT_ID = "com.wikiink.app.extra.FACT_ID"
        private const val APP_PREFS = "app_prefs"
        private const val KEY_ONBOARDED = "onboarded"
    }
}

object Routes {
    const val READER = "reader?${ReaderViewModel.ARG_TITLE}={${ReaderViewModel.ARG_TITLE}}" +
        "&${ReaderViewModel.ARG_FACT_ID}={${ReaderViewModel.ARG_FACT_ID}}"
    const val READER_BASE = "reader"
    const val FOLDERS = "folders"
    const val WIDGET = "widget"
    const val QUIZ = "quiz"

    fun reader(title: String, factId: String? = null): String {
        // Uri.encode uses %20 for spaces; URLEncoder's "+" is NOT decoded back by Navigation.
        val t = Uri.encode(title)
        val f = factId?.let { Uri.encode(it) }.orEmpty()
        return "reader?${ReaderViewModel.ARG_TITLE}=$t&${ReaderViewModel.ARG_FACT_ID}=$f"
    }
}

@Composable
private fun AppScaffold(nav: NavHostController, theme: AppTheme, onCycleTheme: () -> Unit) {
    val c = Ink.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        // Section tabs sit at the top; the reader's controls dock at the bottom instead.
        TabBar(nav)
        Box(Modifier.weight(1f)) {
            NavHost(
                navController = nav,
                startDestination = Routes.READER,
                enterTransition = { androidx.compose.animation.EnterTransition.None },
                exitTransition = { androidx.compose.animation.ExitTransition.None },
                popEnterTransition = { androidx.compose.animation.EnterTransition.None },
                popExitTransition = { androidx.compose.animation.ExitTransition.None },
            ) {
                composable(
                    route = Routes.READER,
                    arguments = listOf(
                        navArgument(ReaderViewModel.ARG_TITLE) { type = NavType.StringType; nullable = true; defaultValue = null },
                        navArgument(ReaderViewModel.ARG_FACT_ID) { type = NavType.StringType; nullable = true; defaultValue = null },
                    ),
                ) {
                    ReaderScreen(
                        theme = theme,
                        onCycleTheme = onCycleTheme,
                        onOpenQuiz = { nav.navigate(Routes.QUIZ) { launchSingleTop = true } },
                    )
                }
                composable(Routes.QUIZ) {
                    QuizScreen(onRead = { title -> nav.navigate(Routes.reader(title)) })
                }
                composable(Routes.WIDGET) {
                    WidgetStyleScreen()
                }
                composable(Routes.FOLDERS) {
                    BookmarksScreen(onOpenArticle = { title -> nav.navigate(Routes.reader(title)) })
                }
            }
        }
    }
}

@Composable
private fun TabBar(nav: NavHostController) {
    val c = Ink.colors
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route.orEmpty()
    val onFolders = route == Routes.FOLDERS
    val onWidget = route == Routes.WIDGET
    // The quiz is reached from the reader, so it doesn't light any tab; READER takes you back.
    val onReader = route.startsWith(Routes.READER_BASE)

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            BottomTab("READER", selected = onReader, modifier = Modifier.weight(1f)) {
                if (!onReader) {
                    if (!nav.popBackStack(Routes.READER, inclusive = false)) nav.navigate(Routes.READER_BASE)
                }
            }
            BottomTab("FOLDERS", selected = onFolders, modifier = Modifier.weight(1f)) {
                if (!onFolders) nav.navigate(Routes.FOLDERS) {
                    popUpTo(nav.graph.findStartDestination().id)
                    launchSingleTop = true
                }
            }
            BottomTab("WIDGET", selected = onWidget, modifier = Modifier.weight(1f)) {
                if (!onWidget) nav.navigate(Routes.WIDGET) {
                    popUpTo(nav.graph.findStartDestination().id)
                    launchSingleTop = true
                }
            }
        }
        InkRule(thickness = HairlineWidth)
    }
}

@Composable
private fun BottomTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Ink.colors
    Box(
        modifier
            .background(if (selected) c.ink else c.background)
            .inkClickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = monoTag(12.sp), color = if (selected) c.background else c.ink)
    }
}
