package com.wikiink.app

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.wikiink.app.data.local.WikiInkDatabase
import com.wikiink.app.data.prefs.WidgetPreferencesRepository
import com.wikiink.app.data.remote.MediaWikiApiService
import com.wikiink.app.data.remote.UserAgentInterceptor
import com.wikiink.app.data.remote.WikipediaApiService
import com.wikiink.app.data.repository.WikiRepository
import com.wikiink.app.ui.theme.ReaderPrefs
import com.wikiink.app.widget.WikiWidgetProvider
import com.wikiink.app.worker.FactSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

class WikiInkApp : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        FactSyncWorker.enqueuePeriodic(this)
    }

    /** Coil must reuse the UA-tagged client: upload.wikimedia.org rejects generic User-Agents (403). */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { container.okHttp }
        .crossfade(false)
        .build()
}

/**
 * Manual DI container; one instance per process. Everything heavy is lazy so Application.onCreate
 * (which also runs for every widget broadcast) does no disk or network setup on the main thread.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /** Process-lifetime scope for fire-and-forget writes (e.g. widget quick-bookmark). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    val okHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(UserAgentInterceptor())
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
                }
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private fun retrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttp)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    val wikipediaApi: WikipediaApiService by lazy { retrofit(WikipediaApiService.BASE_URL).create(WikipediaApiService::class.java) }
    val mediaWikiApi: MediaWikiApiService by lazy { retrofit(MediaWikiApiService.BASE_URL).create(MediaWikiApiService::class.java) }

    val database: WikiInkDatabase by lazy { WikiInkDatabase.build(appContext) }

    val readerPrefs by lazy { ReaderPrefs(appContext) }

    val widgetPrefs by lazy { WidgetPreferencesRepository(appContext) }

    /** Lifetime ranked-quiz points. */
    val nerdPoints by lazy { com.wikiink.app.data.quiz.NerdPoints(appContext) }

    /** Daily Nerd Challenge (fixed set per day + streak). */
    val dailyChallenge by lazy { com.wikiink.app.data.quiz.DailyChallenge(appContext) }

    val repository: WikiRepository by lazy {
        WikiRepository(
            wikiApi = wikipediaApi,
            mediaWikiApi = mediaWikiApi,
            factDao = database.factDao(),
            bookmarkDao = database.bookmarkDao(),
            onFactsChanged = { WikiWidgetProvider.reloadCards(appContext) },
        )
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as WikiInkApp).container
