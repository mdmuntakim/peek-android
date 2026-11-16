package io.github.mdmuntakim.peek.engine

/** One ranked result (web, instant-topic or wikipedia article). */
class WebResult(
    val title: String,
    url: String,
    snippet: String,
    val source: String,
    val position: Int,
    var sourceBonus: Double = 0.0,
) {
    var url: String = url
    var snippet: String = snippet
    var score: Double = 0.0
    var domain: String = ""
    var rank: Int = 99
}

/** Abstract from a knowledge source (the "instant" provider). */
data class InstantAnswer(
    val abstract: String,
    val abstractSource: String,
    val abstractURL: String,
    val heading: String,
    val image: String,
    val answer: String = "",
    val related: List<WebResult>,
)

/** Wikipedia knowledge panel. */
data class KnowledgePanel(
    val title: String,
    val summary: String,
    val image: String,
    val url: String,
)

/** A source cited by the Peek AI answer. */
data class AnswerSource(val title: String, val url: String, val domain: String)

/** The Peek AI synthesized answer. */
data class PeekAnswer(
    val type: String, // "instant" | "ai" | "none"
    val intent: String, // "define" | "howto" | "compare" | "list" | "general"
    val text: String,
    val confidence: Double,
    val sources: List<AnswerSource>,
)

/** The instant-answer card shown above results. */
data class InstantCard(val text: String, val image: String)

/** Provider hit counts, used by the status row in the UI. */
data class ProviderStats(val web: Int, val instant: Int, val wikipedia: Int)

/** The full search payload the UI renders. */
data class SearchPayload(
    val query: String,
    val answer: PeekAnswer,
    val knowledge: KnowledgePanel?,
    val instant: InstantCard?,
    val results: List<WebResult>,
    val related: List<String>,
    val providers: ProviderStats,
    val cached: Boolean = false,
)

/** One image result. */
data class ImageResult(
    val title: String,
    val url: String,
    val image: String,
    val width: Int,
    val height: Int,
)
