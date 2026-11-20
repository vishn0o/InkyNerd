package com.wikiink.app.data.remote

import retrofit2.http.GET
import retrofit2.http.Path

/** Wikimedia REST API — base URL `https://en.wikipedia.org/api/rest_v1/`. */
interface WikipediaApiService {

    @GET("feed/featured/{yyyy}/{mm}/{dd}")
    suspend fun featuredFeed(
        @Path("yyyy") year: String,
        @Path("mm") month: String,
        @Path("dd") day: String,
    ): FeaturedFeedDto

    @GET("page/random/summary")
    suspend fun randomSummary(): PageSummaryDto

    /** [title] must use underscores instead of spaces; Retrofit percent-encodes the rest. */
    @GET("page/summary/{title}")
    suspend fun pageSummary(@Path("title") title: String): PageSummaryDto

    companion object {
        const val BASE_URL = "https://en.wikipedia.org/api/rest_v1/"
    }
}
