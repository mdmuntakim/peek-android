package io.github.mdmuntakim.peek.engine

/**
 * answer.kt -- the Peek AI answer synthesizer, ported from lib/answer.js.
 *
 * It builds one professional, self-contained answer paragraph out of the
 * top-ranked results:
 *   1. instant answers (abstract of a knowledge source) win outright;
 *   2. otherwise sentences are extracted from the snippets of the best
 *      results, scored against the query (intent-aware), and the most
 *      informative, non-redundant sentences are composed into an answer.
 * The engine is fully deterministic and needs no API key.
 */
object Answer {

    private val BOILERPLATE = Regex(
        "\\b(click here|sign up|sign in|subscribe|log in|privacy policy|cookie policy|" +
            "all rights reserved|terms of service|advertisement|sponsored|share this|" +
            "follow us|newsletter|download now|contact us|read more)\\b", RegexOption.IGNORE_CASE)

    private val PRONOUN_START = Regex("^(it|they|he|she|this|these|those|there)\\s", RegexOption.IGNORE_CASE)

    private val INTENT_HINTS: Map<String, Regex> = mapOf(
        "define" to Regex(
            "\\b(is|are|was|were|refers? to|means|denotes|is defined as|is known as|is a|is an|is the)\\b",
            RegexOption.IGNORE_CASE),
        "howto" to Regex(
            "\\b(step|first|then|next|finally|open|click|select|install|run|use|choose|press|tap|enable|set up|type|enter)\\b",
            RegexOption.IGNORE_CASE),
        "compare" to Regex(
            "\\b(vs\\.?|versus|compared?|difference|while|whereas|however|better|than|both|advantage|instead)\\b",
            RegexOption.IGNORE_CASE),
        "list" to Regex(
            "\\b(best|top|recommended|popular|leading|alternatives|examples include|such as|including)\\b",
            RegexOption.IGNORE_CASE),
        "general" to Regex("\\b\\w"),
    )

    private class Candidate(val sentence: String, var score: Double, val result: WebResult, val index: Int)

    /** what-is / how-to / compare / list / general */
    fun classifyIntent(query0: String): String {
        val q = query0.lowercase()
        if (Regex("\\bvs\\.?\\b|\\bversus\\b|\\bdifference between\\b").containsMatchIn(q)) return "compare"
        if (Regex("^how\\s+(to|do|can|does|would)|\\bsteps to\\b|\\bhow do i\\b").containsMatchIn(q)) return "howto"
        if (Regex("^(what|who) (is|are|was|were)|\\bmeaning of\\b|\\bdefinition of\\b|\\bwhat's\\b").containsMatchIn(q)) return "define"
        if (Regex("^(best|top|recommended|cheapest|fastest)\\b").containsMatchIn(q)) return "list"
        return "general"
    }

    /** Weighted term overlap between a sentence and the query's key terms. */
    fun overlapScore(sentence: String, terms: List<String>): Double {
        if (terms.isEmpty()) return 0.5
        val low = sentence.lowercase()
        var hits = 0
        for (t in terms) {
            val re = Regex("(^|[^a-z0-9'-])${Regex.escape(t)}([^a-z0-9'-]|$)")
            if (re.containsMatchIn(low)) hits += 1
        }
        return hits.toDouble() / terms.size
    }

    private fun trigrams(s0: String): Set<String> {
        val s = s0.lowercase().replace(Regex("\\s+"), " ")
        val out = HashSet<String>()
        for (i in 0 until maxOf(0, s.length - 2)) out.add(s.substring(i, i + 3))
        return out
    }

    /** Trigram-Jaccard similarity, used to drop near-duplicate sentences. */
    fun tooSimilar(a: String, b: String): Boolean {
        val A = trigrams(a)
        val B = trigrams(b)
        if (A.isEmpty() || B.isEmpty()) return false
        var inter = 0
        for (g in A) if (B.contains(g)) inter++
        return inter.toDouble() / minOf(A.size, B.size) > 0.6
    }

    /**
     * synthesizeAnswer(query, rankedResults, instant) -> [PeekAnswer]
     */
    fun synthesizeAnswer(query: String, rankedResults: List<WebResult>, instant: InstantAnswer?): PeekAnswer {
        val intent = classifyIntent(query)
        val terms = Text.keyTerms(query)

        // 1) A real abstract from the instant-answer provider beats everything.
        if (instant != null && instant.abstract.length >= 80) {
            val src = if (instant.abstractURL.isNotEmpty()) AnswerSource(
                title = if (instant.abstractSource.isNotEmpty()) instant.abstractSource
                else if (instant.heading.isNotEmpty()) instant.heading else "Knowledge source",
                url = instant.abstractURL,
                domain = Text.domainOf(instant.abstractURL),
            ) else null
            return PeekAnswer("instant", intent, instant.abstract, 0.95, listOfNotNull(src))
        }

        // 2) Extract candidate sentences from the top results' snippets.
        val cands = ArrayList<Candidate>()
        for (r in rankedResults.take(12)) {
            if (r.snippet.isEmpty()) continue
            val sentences = Text.splitSentences(r.snippet)
            sentences.forEachIndexed { i, s ->
                if (s.length < 40 || s.length > 340) return@forEachIndexed
                if (BOILERPLATE.containsMatchIn(s)) return@forEachIndexed
                var score = overlapScore(s, terms) * 6
                score += r.score * 0.35
                score += if (i == 0) 1.0 else 0.0
                INTENT_HINTS[intent]?.let { hint -> if (hint.containsMatchIn(s)) score += 2.0 }
                if (PRONOUN_START.containsMatchIn(s)) score -= 1.5
                if (Regex("[|•]").containsMatchIn(s) || Regex("\\.\\.\\.$").containsMatchIn(s)) score -= 1.0
                cands.add(Candidate(s, score, r, i))
            }
        }
        if (cands.isEmpty()) return PeekAnswer("none", intent, "", 0.0, emptyList())

        cands.sortByDescending { it.score }

        val chosen = ArrayList<Candidate>()
        for (c in cands) {
            if (chosen.size >= 3) break
            if (chosen.any { tooSimilar(it.sentence, c.sentence) }) continue
            chosen.add(c)
        }
        if (chosen.isEmpty()) return PeekAnswer("none", intent, "", 0.0, emptyList())

        // Present the sentences in the order of their sources for coherence.
        chosen.sortWith(compareBy({ if (it.result.rank == 99) 99 else it.result.rank }, { it.index }))

        val text = chosen.joinToString(" ") { it.sentence }
        val sources = ArrayList<AnswerSource>()
        val seenDomain = HashSet<String>()
        for (c in chosen) {
            if (c.result.domain.isEmpty() || seenDomain.contains(c.result.domain)) continue
            seenDomain.add(c.result.domain)
            sources.add(AnswerSource(c.result.title, c.result.url, c.result.domain))
        }
        val confidence = (0.35 + chosen.sumOf { it.score } / 22.0).coerceAtMost(0.9)

        return PeekAnswer("ai", intent, text, confidence, sources)
    }
}
