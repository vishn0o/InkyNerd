package com.wikiink.app.testing

import com.wikiink.app.data.local.FactCategory
import com.wikiink.app.data.local.FactEntity
import com.wikiink.app.data.repository.ReaderArticle

fun fact(title: String, id: String = "id-$title", fullText: String? = null) = FactEntity(
    id = id,
    articleTitle = title,
    hookText = "Hook for $title",
    sourceCategory = FactCategory.DID_YOU_KNOW,
    summaryExtract = "Summary of $title",
    fullPlainText = fullText,
    thumbnailUrl = null,
    fetchedAt = 0L,
)

fun article(title: String, factId: String? = "id-$title") = ReaderArticle(
    title = title,
    description = null,
    category = null,
    hook = "Hook for $title",
    imageUrl = null,
    sections = emptyList(),
    rawPlainText = "Text of $title",
    factId = factId,
)
