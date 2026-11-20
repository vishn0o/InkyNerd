package com.wikiink.app.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

/** MediaWiki Action API — base URL `https://en.wikipedia.org/w/`. */
interface MediaWikiApiService {

    @GET("api.php")
    suspend fun plainTextExtract(
        @Query("titles") title: String,
        @Query("action") action: String = "query",
        @Query("prop") prop: String = "extracts",
        @Query("explaintext") explainText: Int = 1,
        @Query("exsectionformat") sectionFormat: String = "plain",
        @Query("redirects") redirects: Int = 1,
        @Query("format") format: String = "json",
        @Query("formatversion") formatVersion: Int = 2,
    ): ExtractQueryDto

    /** Random articles matching a CirrusSearch query (e.g. `deepcat:"Formula One"`), with intros. */
    @GET("api.php")
    suspend fun searchPool(
        @Query("gsrsearch") search: String,
        @Query("action") action: String = "query",
        @Query("generator") generator: String = "search",
        @Query("gsrnamespace") namespace: Int = 0,
        @Query("gsrlimit") limit: Int = 20,
        @Query("gsrsort") sort: String = "random",
        @Query("prop") prop: String = "extracts|description",
        @Query("exintro") intro: Int = 1,
        @Query("explaintext") plain: Int = 1,
        @Query("exlimit") exLimit: Int = 20,
        @Query("redirects") redirects: Int = 1,
        @Query("format") format: String = "json",
        @Query("formatversion") formatVersion: Int = 2,
    ): PoolQueryDto

    /** Random articles of any subject, with intros (Auto topic). */
    @GET("api.php")
    suspend fun randomPool(
        @Query("action") action: String = "query",
        @Query("generator") generator: String = "random",
        @Query("grnnamespace") namespace: Int = 0,
        @Query("grnlimit") limit: Int = 20,
        @Query("prop") prop: String = "extracts|description",
        @Query("exintro") intro: Int = 1,
        @Query("explaintext") plain: Int = 1,
        @Query("exlimit") exLimit: Int = 20,
        @Query("format") format: String = "json",
        @Query("formatversion") formatVersion: Int = 2,
    ): PoolQueryDto

    /** Article-namespace links out of [title] (for Rabbit Hole). */
    @GET("api.php")
    suspend fun links(
        @Query("titles") title: String,
        @Query("action") action: String = "query",
        @Query("prop") prop: String = "links",
        @Query("plnamespace") namespace: Int = 0,
        @Query("pllimit") limit: String = "max",
        @Query("redirects") redirects: Int = 1,
        @Query("format") format: String = "json",
        @Query("formatversion") formatVersion: Int = 2,
    ): LinksQueryDto

    /** Wikidata short descriptions for up to 50 `|`-separated titles. */
    @GET("api.php")
    suspend fun descriptions(
        @Query("titles") titles: String,
        @Query("action") action: String = "query",
        @Query("prop") prop: String = "description",
        @Query("redirects") redirects: Int = 1,
        @Query("format") format: String = "json",
        @Query("formatversion") formatVersion: Int = 2,
    ): DescriptionQueryDto

    /** Rendered HTML of one section of a page (used for DYK archives and Unusual articles). */
    @GET("api.php")
    suspend fun parseSection(
        @Query("page") page: String,
        @Query("section") section: Int,
        @Query("action") action: String = "parse",
        @Query("prop") prop: String = "text",
        @Query("redirects") redirects: Int = 1,
        @Query("disabletoc") disableToc: Int = 1,
        @Query("format") format: String = "json",
        @Query("formatversion") formatVersion: Int = 2,
    ): ParseResponseDto

    companion object {
        const val BASE_URL = "https://en.wikipedia.org/w/"
    }
}
