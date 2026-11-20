package com.wikiink.app.data.remote

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Shared networking setup for both Wikipedia endpoints.
 *
 * Wikimedia's REST API policy requires a descriptive User-Agent on every
 * request; the interceptor below guarantees it for both services.
 */
object ApiProvider {

    private const val USER_AGENT = "InkyNerdAndroid/1.0 (contact@inkynerd.local)"
    private const val REST_BASE_URL = "https://en.wikipedia.org/api/rest_v1/"
    private const val ACTION_BASE_URL = "https://en.wikipedia.org/"

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    private val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val contentType = "application/json".toMediaType()

    private fun retrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory(contentType))
        .build()

    val wikipedia: WikipediaApiService by lazy {
        retrofit(REST_BASE_URL).create(WikipediaApiService::class.java)
    }

    val mediaWiki: MediaWikiApiService by lazy {
        retrofit(ACTION_BASE_URL).create(MediaWikiApiService::class.java)
    }
}
