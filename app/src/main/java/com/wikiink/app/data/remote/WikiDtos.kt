package com.wikiink.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Response of `feed/featured/{yyyy}/{mm}/{dd}`. Every section is optional; `dyk` is frequently absent. */
@Serializable
data class FeaturedFeedDto(
    val dyk: List<DykItemDto> = emptyList(),
    val onthisday: List<OnThisDayDto> = emptyList(),
)

@Serializable
data class DykItemDto(
    val text: String = "",
    val html: String? = null,
    val articles: List<PageSummaryDto> = emptyList(),
)

@Serializable
data class OnThisDayDto(
    val text: String = "",
    val year: Int? = null,
    val pages: List<PageSummaryDto> = emptyList(),
)

@Serializable
data class TitlesDto(
    val canonical: String? = null,
    val normalized: String? = null,
    val display: String? = null,
)

@Serializable
data class ImageDto(
    val source: String,
    val width: Int? = null,
    val height: Int? = null,
)

/** REST `page/summary` object (also embedded inside feed items). */
@Serializable
data class PageSummaryDto(
    val type: String? = null,
    val title: String? = null,
    val normalizedtitle: String? = null,
    val titles: TitlesDto? = null,
    val description: String? = null,
    val extract: String? = null,
    val thumbnail: ImageDto? = null,
    val originalimage: ImageDto? = null,
) {
    /** Best human-readable title for the article. */
    val bestTitle: String?
        get() = titles?.normalized ?: normalizedtitle ?: title?.replace('_', ' ')
}

/** MediaWiki Action API `prop=extracts` response (formatversion=2). */
@Serializable
data class ExtractQueryDto(
    val query: ExtractQueryBody? = null,
)

@Serializable
data class ExtractQueryBody(
    val pages: List<ExtractPageDto> = emptyList(),
)

@Serializable
data class ExtractPageDto(
    val pageid: Long? = null,
    val title: String = "",
    val extract: String? = null,
    val missing: Boolean = false,
    @SerialName("invalid") val invalid: Boolean = false,
)

/** MediaWiki Action API `action=parse&prop=text` (formatversion=2). `parse` is null when the API returns an error (e.g. no such section). */
@Serializable
data class ParseResponseDto(
    val parse: ParseBodyDto? = null,
)

@Serializable
data class ParseBodyDto(
    val title: String = "",
    val text: String = "",
)

/** `action=query&prop=description` (formatversion=2). */
@Serializable
data class DescriptionQueryDto(
    val query: DescriptionQueryBody? = null,
)

@Serializable
data class DescriptionQueryBody(
    val pages: List<DescriptionPageDto> = emptyList(),
    val normalized: List<TitleMappingDto> = emptyList(),
    val redirects: List<TitleMappingDto> = emptyList(),
)

@Serializable
data class DescriptionPageDto(
    val title: String = "",
    val description: String? = null,
)

@Serializable
data class TitleMappingDto(
    val from: String = "",
    val to: String = "",
)

/** `action=query&prop=links` (formatversion=2). */
@Serializable
data class LinksQueryDto(
    val query: LinksQueryBody? = null,
)

@Serializable
data class LinksQueryBody(
    val pages: List<LinksPageDto> = emptyList(),
)

@Serializable
data class LinksPageDto(
    val title: String = "",
    val links: List<LinkDto> = emptyList(),
)

@Serializable
data class LinkDto(
    val title: String = "",
)

/** Generator query (search / random) with `prop=extracts|description`. */
@Serializable
data class PoolQueryDto(
    val query: PoolQueryBody? = null,
)

@Serializable
data class PoolQueryBody(
    val pages: List<PoolPageDto> = emptyList(),
)

@Serializable
data class PoolPageDto(
    val title: String = "",
    val extract: String? = null,
    val description: String? = null,
)
