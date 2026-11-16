package io.github.mdmuntakim.peek.engine

/**
 * rank.kt -- turn BM25 hits from Peek's index into ranked, de-duplicated
 * results for the UI. The score is Peek's own BM25 output; a small authority
 * nudge (domains usually written by domain experts) breaks ties toward
 * quality sources. No third-party ranking is involved anywhere.
 */
object Rank {

    /** Domains whose pages are usually written by editors or domain experts. */
    val AUTHORITY: Set<String> = setOf(
        "wikipedia.org", "github.com", "stackoverflow.com", "mozilla.org", "developer.mozilla.org",
        "python.org", "nodejs.org", "nasa.gov", "nih.gov", "nist.gov", "britannica.com", "nature.com",
        "science.org", "mit.edu", "stanford.edu", "harvard.edu", "reuters.com", "apnews.com", "bbc.com",
        "economist.com", "theguardian.com", "arstechnica.com", "ieee.org", "acm.org",
        "w3.org", "ietf.org", "arxiv.org", "springer.com", "cambridge.org", "oup.com",
        "gutenberg.org", "openlibrary.org", "npr.org", "pbs.org", "nationalgeographic.com",
    )

    /** Score, de-duplicate and rank indexed pages for a query. */
    fun rankResults(query: String, docs: List<PageDoc>, limit: Int = 40): List<WebResult> {
        val seen = HashSet<String>()
        val out = ArrayList<WebResult>()
        for (d in docs) {
            if (!seen.add(Text.normUrl(d.url))) continue
            val r = WebResult(
                title = d.title,
                url = d.url,
                snippet = d.snippet,
                source = "index",
                position = out.size,
            )
            r.domain = d.domain
            r.score = d.score + (if (AUTHORITY.contains(d.domain) || AUTHORITY.any { d.domain.endsWith(".$it") }) 0.8 else 0.0)
            out.add(r)
        }
        out.sortByDescending { it.score }
        return out.take(limit).also { list -> list.forEachIndexed { i, r -> r.rank = i } }
    }
}
