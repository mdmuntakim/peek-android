package io.github.mdmuntakim.peek.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * search.kt -- the Peek search pipeline. Every part of it is Peek's own:
 *
 *   PeekBot crawl  ->  Index (BM25)  ->  Rank  ->  Peek AI answer
 *                  ->  knowledge panel, related searches, suggestions
 *
 * No third-party search engine is contacted, ever. When a query finds
 * nothing, Peek says so honestly and keeps growing its index in the
 * background from the crawl ring.
 */
object Search {

    private const val CACHE_TTL = 120_000L
    private val cache = HashMap<String, Pair<Long, SearchPayload>>()
    private val bg = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    var lastCrawl: CrawlStats = CrawlStats(done = true)
        private set

    // ---------------------------------------------------------------- web results

    /**
     * query the local index and shape the hits into results. This replaces
     * the old remote "web provider": the index is the web provider.
     */
    fun queryIndex(q: Index.Query, limit: Int = 25): List<WebResult> {
        val docs = Index.search(q, limit * 2)
        for (d in docs) d.snippet = Index.snippet(d, q.terms)
        return Rank.rankResults(q.raw, docs, limit)
    }

    // ----------------------------------------------------------------- suggestions

    /** Suggestions come from Peek's own history and indexed page titles. */
    fun suggest(prefix: String, limit: Int = 8): List<Suggest> =
        Index.suggest(prefix, Store.history(), limit)

    // ------------------------------------------------------------------ pipeline

    /**
     * search(query) -> the full result payload for the UI. Never throws.
     */
    suspend fun search(query0: String, limit: Int = 25): SearchPayload {
        val q = query0.trim().take(300)
        if (q.isEmpty()) return emptyResult(q)
        Store.rememberQuery(q)

        val key = "s:" + q.lowercase()
        synchronized(cache) {
            val hit = cache[key]
            if (hit != null && System.currentTimeMillis() - hit.first <= CACHE_TTL) {
                return hit.second.copy(cached = true)
            } else if (hit != null) cache.remove(key)
            if (cache.size > 500) cache.clear()
        }

        val parsed = Index.parseQuery(q)
        val results = queryIndex(parsed, limit)

        // Peek AI answer over the top hits.
        val answer = Answer.synthesizeAnswer(q, results)

        val knowledge = Index.knowledge(parsed)
        val related = Index.related(parsed, results.take(6).map { d ->
            PageDoc(d.url, d.domain, d.title, d.snippet.ifEmpty { d.title })
        })

        val stats = Index.stats()
        val payload = SearchPayload(
            query = q,
            answer = answer,
            knowledge = knowledge,
            results = results,
            related = related,
            providers = stats,
            exploring = results.isEmpty() && Index.state.pages.isNotEmpty(),
        )
        synchronized(cache) { cache[key] = System.currentTimeMillis() to payload }

        // A weak index grows itself: search-empty pages trigger a crawl burst.
        if (results.isEmpty() || Index.state.pages.isEmpty()) growInBackground()
        return payload
    }

    /**
     * Kick off a background PeekBot crawl from the seed ring (never blocks
     * the search). The UI surfaces progress in the status chip.
     */
    fun growInBackground(budget: Int = 24) {
        if (Crawler.running) return
        bg.launch {
            lastCrawl = Crawler.crawl(Store.seedsList(), budget) { lastCrawl = it }
            Store.persist()
        }
    }

    /** Crawl with a caller-supplied budget, awaited (used by the Settings screen). */
    suspend fun crawlNow(budget: Int, progress: (CrawlStats) -> Unit = {}): CrawlStats =
        Crawler.crawl(Store.seedsList(), budget, progress = progress).also { Store.persist() }

    fun emptyResult(q: String) = SearchPayload(
        query = q,
        answer = PeekAnswer("none", "general", "", 0.0, emptyList()),
        knowledge = null,
        results = emptyList(),
        related = emptyList(),
        providers = Index.stats(),
    )
}
