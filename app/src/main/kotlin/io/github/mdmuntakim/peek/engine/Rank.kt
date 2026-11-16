package io.github.mdmuntakim.peek.engine

/**
 * rank.kt -- merge, de-duplicate and score web results for a query.
 * A BM25-flavoured scorer ported from lib/rank.js: term hits in
 * title / snippet / URL are weighted, authoritative domains get a boost,
 * and each provider's own ordering contributes a small position prior.
 */
object Rank {

    /** Domains whose pages are usually written by editors or domain experts. */
    val AUTHORITY: Set<String> = setOf(
        "wikipedia.org", "github.com", "stackoverflow.com", "mozilla.org", "developer.mozilla.org",
        "python.org", "nodejs.org", "nasa.gov", "nih.gov", "nist.gov", "britannica.com", "nature.com",
        "science.org", "mit.edu", "stanford.edu", "harvard.edu", "reuters.com", "apnews.com", "bbc.com",
        "economist.com", "theguardian.com", "nytimes.com", "arstechnica.com", "ieee.org", "acm.org",
        "w3.org", "ietf.org", "arxiv.org", "springer.com", "elsevier.com", "cambridge.org", "oup.com",
        "msn.com", "npr.org", "pbs.org", "smithsonianmag.com", "nationalgeographic.com",
    )

    /** Score, de-duplicate and rank raw results from every provider. */
    fun rankResults(query: String, results: List<WebResult>, limit: Int = 40): List<WebResult> {
        val terms = Text.keyTerms(query)
        val patterns = terms.map { t ->
            Regex("(^|[^a-z0-9'-])${Regex.escape(t)}([^a-z0-9'-]|$)", RegexOption.IGNORE_CASE)
        }
        val seen = HashMap<String, WebResult>()
        val out = ArrayList<WebResult>()

        for (r in results) {
            if (r.url.isEmpty() || r.title.isEmpty()) continue
            val url = Text.normUrl(r.url)
            if (!url.startsWith("http://") && !url.startsWith("https://")) continue

            val domain = if (r.domain.isNotEmpty()) r.domain else Text.domainOf(url)
            var score = 0.0
            for (i in terms.indices) {
                val re = patterns[i]
                if (re.containsMatchIn(r.title)) score += 4.0
                if (re.containsMatchIn(r.snippet)) score += 2.0
                if (re.containsMatchIn(url)) score += 1.5
            }
            if (terms.isEmpty()) score += 1.0

            if (AUTHORITY.contains(domain) || AUTHORITY.any { d ->
                    domain.endsWith(".$d") || domain == d
                }
            ) score += 1.2
            else if (Regex("^(docs?|api|developer)\\.").containsMatchIn(domain) ||
                Regex("\\.(edu|gov)$").containsMatchIn(domain)
            ) score += 0.8

            // Position prior: providers already rank their own results.
            score += (1.5 - r.position * 0.15).coerceAtLeast(0.0)
            score += r.sourceBonus

            val prev = seen[url]
            if (prev != null) {
                prev.score = maxOf(prev.score, score)
                if (r.snippet.length > prev.snippet.length) prev.snippet = r.snippet
                continue
            }
            r.url = url
            r.domain = domain
            r.score = score
            seen[url] = r
            out.add(r)
        }

        out.sortByDescending { it.score }
        return out.take(limit).also { list -> list.forEachIndexed { i, r -> r.rank = i } }
    }
}
