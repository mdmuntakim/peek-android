package io.github.mdmuntakim.peek.engine

/**
 * One page PeekBot has crawled and indexed. Peek's only source of truth:
 * the engine never asks another search engine for anything.
 */
class PageDoc(
    val url: String,
    val domain: String,
    val title: String,
    val text: String,
    val fetchedAt: Long = 0L,
) {
    var score: Double = 0.0
    var snippet: String = ""
    var rank: Int = 0
    /** Terms of the query found in the title/snippet, used for highlighting. */
    var hits: List<String> = emptyList()
}

/**
 * One ranked result handed to the UI. A thin view over [PageDoc] so the
 * ranker, answer synthesizer and UI all speak the same language.
 */
class WebResult(
    val title: String,
    url: String,
    snippet: String,
    val source: String,
    val position: Int,
) {
    var url: String = url
    var snippet: String = snippet
    var score: Double = 0.0
    var domain: String = ""
    var rank: Int = 99
}

/** The knowledge panel, built from the best entity match in Peek's own index. */
data class KnowledgePanel(
    val title: String,
    val summary: String,
    val image: String,
    val url: String,
    val domain: String,
)

/** A source cited by the Peek AI answer. */
data class AnswerSource(val title: String, val url: String, val domain: String)

/** The Peek AI synthesized answer (deterministic extractive synthesis). */
data class PeekAnswer(
    val type: String, // "ai" | "none"
    val intent: String, // "define" | "howto" | "compare" | "list" | "general"
    val text: String,
    val confidence: Double,
    val sources: List<AnswerSource>,
)

/** Hit counts across Peek's own index, used by the status row in the UI. */
data class ProviderStats(
    val pages: Int,
    val terms: Int,
    val sites: Int,
    val crawled: Int,
)

/** The full search payload the UI renders. */
data class SearchPayload(
    val query: String,
    val answer: PeekAnswer,
    val knowledge: KnowledgePanel?,
    val results: List<WebResult>,
    val related: List<String>,
    val providers: ProviderStats,
    val cached: Boolean = false,
    /** True when the index has no matching page and PeekBot is growing the index. */
    val exploring: Boolean = false,
)

/** One image captured by PeekBot while crawling. */
data class ImageResult(
    val title: String,
    /** The page that carries the image. */
    val url: String,
    /** The image URL itself. */
    val image: String,
    val width: Int,
    val height: Int,
)

/** A single suggestion: either a previous query or a page title from the index. */
data class Suggest(val query: String, val pageUrl: String, val pageTitle: String)

/** Progress report from a PeekBot crawl run. */
data class CrawlStats(
    val fetched: Int = 0,
    val indexed: Int = 0,
    val failed: Int = 0,
    val skipped: Int = 0,
    val done: Boolean = false,
)
