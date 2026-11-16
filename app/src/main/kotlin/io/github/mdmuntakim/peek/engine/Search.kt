package io.github.mdmuntakim.peek.engine

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

/**
 * search.kt -- the Peek search pipeline, ported from lib/search.js.
 *
 * Providers (all key-free):
 *   * DuckDuckGo HTML results        -> the web index
 *   * DuckDuckGo Instant Answer API  -> abstracts + related topics
 *   * Wikipedia search + summary API -> knowledge panel
 *
 * Everything runs in parallel with per-provider timeouts; a failing
 * provider degrades the page gracefully instead of breaking the search.
 */
object Search {

    private const val PROVIDER_TIMEOUT = 7000
    private const val CACHE_TTL = 120_000L

    private val cache = HashMap<String, Pair<Long, SearchPayload>>()

    // ---------------------------------------------------------------- web results

    /** Parse a DuckDuckGo HTML results page into result objects. */
    fun parseDdgHtml(html: String, limit: Int = 20): List<WebResult> {
        val out = ArrayList<WebResult>()
        val re = Regex(
            "<a[^>]+class=\"result__a\"[^>]*href=\"([^\"]+)\"[^>]*>([\\s\\S]*?)</a>([\\s\\S]*?)" +
                "(?:<a[^>]+class=\"result__snippet\"[^>]*>([\\s\\S]*?)</a>)"
        )
        for (m in re.findAll(html)) {
            if (out.size >= limit) break
            var href = Text.decodeEntities(m.groupValues[1])
            val uddg = extractUddg(href)
            if (uddg != null) href = uddg
            if (!href.startsWith("http://") && !href.startsWith("https://")) continue // drops ad blocks
            val title = Text.textOf(m.groupValues[2])
            if (title.isEmpty()) continue
            val snippet = Text.textOf(m.groupValues[4])
            out.add(WebResult(title, href, snippet, "web", out.size))
        }
        return out
    }

    /** Parse the lite (table-based) DuckDuckGo results page. */
    fun parseDdgLite(html: String, limit: Int = 20): List<WebResult> {
        val out = ArrayList<WebResult>()
        val re = Regex(
            "<a[^>]+class=\"result-link\"[^>]*href=\"([^\"]+)\"[^>]*>([\\s\\S]*?)</a>[\\s\\S]*?" +
                "(?:<td[^>]*>[\\s\\S]*?<a[^>]*class=\"result-snippet\"[^>]*>([\\s\\S]*?)</a>)"
        )
        for (m in re.findAll(html)) {
            if (out.size >= limit) break
            var href = Text.decodeEntities(m.groupValues[1])
            val uddg = extractUddg(href)
            if (uddg != null) href = uddg
            if (!href.startsWith("http://") && !href.startsWith("https://")) continue
            val title = Text.textOf(m.groupValues[2])
            if (title.isEmpty()) continue
            val snippet = Text.textOf(m.groupValues[3])
            out.add(WebResult(title, href, snippet, "web", out.size))
        }
        return out
    }

    private fun extractUddg(href: String): String? {
        val m = Regex("[?&]uddg=([^&]+)").find(href) ?: return null
        return java.net.URLDecoder.decode(m.groupValues[1], "UTF-8")
    }

    /**
     * ddgWeb -- fetch and parse web results from DuckDuckGo. All requests go
     * through the scheduler and each endpoint is retried once after a backoff,
     * because the endpoints serve an anti-bot page instead of results when
     * they throttle.
     */
    suspend fun ddgWeb(query: String, limit: Int = 20): List<WebResult> {
        val enc = java.net.URLEncoder.encode(query, "UTF-8")
        val attempts = listOf(
            "https://html.duckduckgo.com/html/?q=$enc&kl=us-en" to "html",
            "https://lite.duckduckgo.com/lite/?q=$enc&kl=us-en" to "lite",
        )
        for ((url, kind) in attempts) {
            for (tryNo in 0 until 2) {
                try {
                    val text = DdgScheduler.schedule { Http.get(url, PROVIDER_TIMEOUT) }
                    val out = if (kind == "lite") parseDdgLite(text, limit) else parseDdgHtml(text, limit)
                    if (out.isNotEmpty()) return out
                } catch (_: Exception) { /* fall through to retry */ }
                if (tryNo == 0) delay(1500L + (0..700).random())
            }
        }
        return emptyList()
    }

    // ------------------------------------------------------------ instant answers

    suspend fun ddgInstant(query: String): InstantAnswer? {
        val data = Json.obj(
            Http.getJson(
                "https://api.duckduckgo.com/?q=${java.net.URLEncoder.encode(query, "UTF-8")}" +
                    "&format=json&no_redirect=1&no_html=1&t=peek",
                PROVIDER_TIMEOUT,
            )
        ) ?: return null

        val related = (Json.arr(data["RelatedTopics"]) ?: emptyList())
            .mapNotNull { t -> Json.obj(t) }
            .filter { o -> !Json.str(o["Text"]).isNullOrEmpty() && !Json.str(o["FirstURL"]).isNullOrEmpty() }
            .take(8)
            .mapIndexed { i, o ->
                WebResult(Json.str(o["Text"])!!.trim(), Json.str(o["FirstURL"])!!, "", "instant", i)
            }

        return InstantAnswer(
            abstract = Json.str(data["Abstract"])?.trim() ?: "",
            abstractSource = Json.str(data["AbstractSource"]) ?: "",
            abstractURL = Json.str(data["AbstractURL"]) ?: "",
            heading = Json.str(data["Heading"]) ?: "",
            image = Json.str(data["ImageAbsoluteURL"]) ?: "",
            answer = Json.str(data["Answer"])?.trim() ?: "",
            related = related,
        )
    }

    // ----------------------------------------------------------------- wikipedia

    /**
     * Wikipedia search + summary for the best-matching article. Returns the
     * knowledge panel and supplementary web results that keep the page
     * populated when the web index is throttled.
     */
    suspend fun wikipedia(query: String): Pair<KnowledgePanel?, List<WebResult>> {
        val terms = Text.keyTerms(query)
        if (terms.isEmpty()) return null to emptyList()

        val s = Json.obj(
            Http.getJson(
                "https://en.wikipedia.org/w/api.php?action=query&format=json&list=search" +
                    "&srsearch=${java.net.URLEncoder.encode(query, "UTF-8")}&srlimit=5&origin=*",
                PROVIDER_TIMEOUT,
            )
        ) ?: return null to emptyList()

        val hits = (Json.obj(s["query"])?.let { Json.arr(it["search"]) } ?: emptyList())
            .mapNotNull { Json.obj(it) }
        if (hits.isEmpty()) return null to emptyList()

        // Only surface the panel when the encyclopedia article actually matches
        // the query's key terms, otherwise it is noise.
        val matching = hits.mapNotNull { h ->
            val title = Json.str(h["title"]) ?: return@mapNotNull null
            val plain = Text.textOf(Json.str(h["snippet"]))
            title to plain
        }.filter { (_, title) -> terms.any { w -> title.lowercase().contains(w) } }

        val panel: KnowledgePanel? = try {
            val bestTitle = matching.firstOrNull()?.first ?: hits.firstNotNullOf { Json.str(it["title"]) }
            val sum = Json.obj(
                Http.getJson(
                    "https://en.wikipedia.org/api/rest_v1/page/summary/" +
                        java.net.URLEncoder.encode(bestTitle, "UTF-8"),
                    PROVIDER_TIMEOUT,
                )
            )
            val extract = Json.str(sum?.get("extract")) ?: ""
            if (extract.isNotEmpty()) {
                val thumb = Json.obj(sum?.get("thumbnail"))?.let { Json.str(it["source"]) } ?: ""
                val urls = Json.obj(sum?.get("content_urls"))
                val page = urls?.let { Json.obj(it["desktop"]) }?.let { Json.str(it["page"]) } ?: ""
                KnowledgePanel(
                    title = Json.str(sum?.get("title")) ?: bestTitle,
                    summary = extract,
                    image = thumb,
                    url = page.ifEmpty { "https://en.wikipedia.org/wiki/" + bestTitle.replace(" ", "_") },
                )
            } else null
        } catch (_: Exception) { null }

        val results = matching.take(3).mapIndexed { i, (title, plain) ->
            WebResult(
                title,
                "https://en.wikipedia.org/wiki/" + title.replace(" ", "_"),
                plain,
                "wikipedia",
                i,
            ).apply { sourceBonus = 0.4 }
        }

        return panel to results
    }

    // ----------------------------------------------------------------- suggestions

    suspend fun suggest(q0: String, limit: Int = 10): List<String> {
        val query = q0.trim()
        if (query.isEmpty()) return emptyList()
        val data = try {
            Http.getJson(
                "https://duckduckgo.com/ac/?q=${java.net.URLEncoder.encode(query, "UTF-8")}&type=list",
                4000,
            )
        } catch (_: Exception) {
            return emptyList()
        }
        return when {
            Json.arr(data)?.let { Json.arr(it.getOrNull(1)) } != null ->
                (Json.arr(Json.arr(data)!![1]) ?: emptyList()).mapNotNull { Json.str(it) }.take(limit)
            Json.arr(data) != null ->
                (Json.arr(data)!!).mapNotNull { x -> Json.str(Json.obj(x)?.get("phrase")) }.take(limit)
            else -> emptyList()
        }
    }

    // ------------------------------------------------------------------ pipeline

    internal fun buildRelated(
        query: String,
        instant: InstantAnswer?,
        ranked: List<WebResult>,
        suggestions: List<String>,
    ): List<String> {
        val seen = HashSet<String>()
        seen.add(query.lowercase())
        val out = ArrayList<String>()
        fun push(raw: String) {
            val t = raw.replace(Regex("\\s+"), " ").trim()
            if (t.length < 3 || t.length > 80) return
            val k = t.lowercase()
            if (seen.contains(k)) return
            seen.add(k)
            out.add(t)
        }
        for (s in suggestions) push(s)
        for (t in instant?.related ?: emptyList()) {
            // Related-topic texts read like "Topic – description"; keep the head.
            push(t.title.split(Regex("[–—-]\\s"))[0].trim())
        }
        // Fall back to key phrases of the top result titles.
        for (r in ranked.take(5)) {
            if (out.size >= 8) break
            val words = r.title.replace(Regex("[|–—-].*$"), "").trim()
                .split(Regex("\\s+")).take(6).joinToString(" ")
            push(words)
        }
        return out.take(8)
    }

    /**
     * search(query) -> the full result payload for the UI. Never throws:
     * provider failures become empty sections.
     */
    suspend fun search(query0: String, limit: Int = 25): SearchPayload {
        val q = query0.trim().take(300)
        if (q.isEmpty()) return emptyResult(q)

        val key = "s:" + q.lowercase()
        synchronized(cache) {
            val hit = cache[key]
            if (hit != null && System.currentTimeMillis() - hit.first <= CACHE_TTL) {
                return hit.second.copy(cached = true)
            } else if (hit != null) cache.remove(key)
            if (cache.size > 500) cache.clear()
        }

        val (web, instant, wikiOut, suggestions) = coroutineScope {
            val w = async { runCatching { ddgWeb(q) }.getOrDefault(emptyList()) }
            val i = async { runCatching { ddgInstant(q) }.getOrNull() }
            val emptyWiki: Pair<KnowledgePanel?, List<WebResult>> = null to emptyList()
            val wiki = async { runCatching { wikipedia(q) }.getOrDefault(emptyWiki) }
            val s = async { runCatching { suggest(q, 8) }.getOrDefault(emptyList()) }
            Providers4(w.await(), i.await(), wiki.await(), s.await())
        }

        val ia = instant
        val merged = buildList {
            addAll(web)
            ia?.related?.forEach { r -> add(r.also { it.sourceBonus = 0.5 }) }
            addAll(wikiOut.second)
        }
        val ranked = Rank.rankResults(q, merged, limit)
        val answer = Answer.synthesizeAnswer(q, ranked, ia)
        val related = buildRelated(q, ia, ranked, suggestions)

        val payload = SearchPayload(
            query = q,
            answer = answer,
            knowledge = wikiOut.first,
            instant = ia?.takeIf { it.answer.isNotEmpty() }?.let {
                InstantCard(it.answer, it.image)
            },
            results = ranked,
            related = related,
            providers = ProviderStats(
                web = web.size,
                instant = if (ia != null && (ia.abstract.isNotEmpty() || ia.related.isNotEmpty())) 1 else 0,
                wikipedia = wikiOut.second.size,
            ),
        )
        synchronized(cache) { cache[key] = System.currentTimeMillis() to payload }
        return payload
    }

    private data class Providers4(
        val web: List<WebResult>,
        val instant: InstantAnswer?,
        val wiki: Pair<KnowledgePanel?, List<WebResult>>,
        val suggestions: List<String>,
    )

    fun emptyResult(q: String) = SearchPayload(
        query = q,
        answer = PeekAnswer("none", "general", "", 0.0, emptyList()),
        knowledge = null,
        instant = null,
        results = emptyList(),
        related = emptyList(),
        providers = ProviderStats(0, 0, 0),
    )
}
