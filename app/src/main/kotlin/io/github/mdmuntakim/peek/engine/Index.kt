package io.github.mdmuntakim.peek.engine

/**
 * index.kt -- Peek's own search index. A positional-free inverted index with
 * field-weighted term frequencies and a BM25 scorer, written from scratch:
 *
 *   pages      : every crawled document (url, domain, title, text)
 *   postings   : term -> [pageId, weightedTf, pageId, weightedTf, ...]
 *   titleHits  : term -> [pageId, ...] (the term occurs in the title)
 *   docLen     : token count of each page's body
 *
 * The index lives in memory and is persisted by [Store] as a JSON snapshot,
 * so it survives restarts without any platform storage dependency. Nothing
 * about it is derived from another search engine: PeekBot's crawl corpus is
 * the entire universe Peek searches.
 */
object Index {

    // ------------------------------------------------------------- tokenizing

    private val WORD = Regex("[a-z0-9][a-z0-9'\\-]*")

    /** Lowercase content words of a text: tokenized, stop words dropped. */
    fun tokenize(text: String): List<String> =
        WORD.findAll(text.lowercase())
            .map { it.value.trim('\'', '-') }
            .filter { it.length > 1 && it !in Text.STOPWORDS }
            .toList()

    // ------------------------------------------------------------------- model

    class TermData {
        /** term -> flattened [pageId, tf, pageId, tf, ...] */
        val postings = HashMap<String, IntArray>()
        /** term -> page ids where the term occurs in the title. */
        val titles = HashMap<String, IntArray>()
        /** term -> pages where the term occurs, used for related-search terms. */
        val df = HashMap<String, Int>()
    }

    class State {
        val pages = ArrayList<PageDoc>()
        val byUrl = HashMap<String, Int>()
        val terms = TermData()
        val docLen = ArrayList<Int>()
        var totalTokens = 0L

        fun avgLen(): Double = if (pages.isEmpty()) 1.0 else totalTokens.toDouble() / pages.size

        fun domains(): Set<String> = pages.map { it.domain }.toSet()
    }

    @Volatile
    var state = State()
        private set

    // ---------------------------------------------------------------- indexing

    const val MAX_PAGES = 4000
    private const val MAX_TEXT = 24_000 // characters of body text kept per page

    /** Index a crawled page. Returns false when the page was a duplicate. */
    @Synchronized
    fun add(doc: PageDoc): Boolean {
        val s = state
        val url = Text.normUrl(doc.url)
        if (s.byUrl.containsKey(url)) return false
        if (s.pages.size >= MAX_PAGES) {
            // Evict the oldest page so fresh crawls keep the index current.
            val oldest = s.pages.indices.minByOrNull { s.pages[it].fetchedAt } ?: return false
            removeAt(oldest)
        }
        val id = s.pages.size
        val clean = PageDoc(
            url = url,
            domain = Text.domainOf(url),
            title = doc.title.ifEmpty { url.substringAfter("://").substringBefore('/') },
            text = doc.text.take(MAX_TEXT),
            fetchedAt = if (doc.fetchedAt > 0) doc.fetchedAt else System.currentTimeMillis(),
        )
        s.pages.add(clean)
        s.byUrl[url] = id

        val bodyTokens = tokenize(clean.text)
        s.docLen.add(bodyTokens.size.coerceAtLeast(1))
        s.totalTokens += bodyTokens.size

        // Field-weighted term frequencies: title x4, headings x2, body x1.
        val tf = HashMap<String, Int>()
        for (t in tokenize(clean.title)) tf.merge(t, 4) { a, b -> a + b }
        for (t in bodyTokens) tf.merge(t, 1) { a, b -> a + b }
        val titleTerms = HashSet(tokenize(clean.title))

        for ((term, count) in tf) {
            val td = s.terms
            td.postings.merge(term, intArrayOf(id, count)) { a, b -> a + b }
            if (titleTerms.contains(term)) {
                td.titles.merge(term, intArrayOf(id)) { a, b -> a + b }
            }
            td.df.merge(term, 1) { x, y -> x + y }
        }
        return true
    }

    /** A page the user browsed: index it even if it was seen, refresh content. */
    @Synchronized
    fun upsert(doc: PageDoc): Boolean {
        val url = Text.normUrl(doc.url)
        if (!state.byUrl.containsKey(url)) return add(doc)
        remove(url)
        return add(doc)
    }

    @Synchronized
    private fun remove(url: String) {
        val s = state
        val id = s.byUrl.remove(url) ?: return
        val doc = s.pages.removeAt(id)
        // Rebuild: page ids shifted; correctness first, speed second at personal scale.
        s.byUrl.clear()
        s.docLen.removeAt(id)
        s.totalTokens = s.docLen.sumOf { it.toLong() }
        val newTerms = TermData()
        s.pages.forEachIndexed { newId, p ->
            s.byUrl[p.url] = newId
            val tf = HashMap<String, Int>()
            for (t in tokenize(p.title)) tf.merge(t, 4) { a, b -> a + b }
            for (t in tokenize(p.text)) tf.merge(t, 1) { a, b -> a + b }
            val titleTerms = HashSet(tokenize(p.title))
            for ((term, count) in tf) {
                newTerms.postings.merge(term, intArrayOf(newId, count)) { a, b -> a + b }
                if (term in titleTerms) newTerms.titles.merge(term, intArrayOf(newId)) { a, b -> a + b }
                newTerms.df.merge(term, 1) { x, y -> x + y }
            }
        }
        s.terms.postings.clear(); s.terms.postings.putAll(newTerms.postings)
        s.terms.titles.clear(); s.terms.titles.putAll(newTerms.titles)
        s.terms.df.clear(); s.terms.df.putAll(newTerms.df)
    }

    @Synchronized
    private fun removeAt(id: Int) {
        val s = state
        remove(s.pages[id].url)
    }

    @Synchronized
    fun clear() {
        state = State()
    }

    @Synchronized
    fun replaceAll(newState: State) {
        state = newState
    }

    // ------------------------------------------------------------------ query

    /** Parsed query: content terms, quoted phrases, site: filter, -exclusions. */
    class Query(
        val terms: List<String>,
        val phrases: List<String>,
        val sites: List<String>,
        val excludes: List<String>,
        val raw: String,
    )

    fun parseQuery(raw: String): Query {
        val terms = ArrayList<String>()
        val phrases = ArrayList<String>()
        val sites = ArrayList<String>()
        val excludes = ArrayList<String>()

        var rest = raw
        // quoted phrases
        Regex("\"([^\"]+)\"").findAll(rest).forEach { m ->
            phrases.add(Text.textOf(m.groupValues[1]).lowercase())
        }
        rest = rest.replace(Regex("\"[^\"]+\""), " ")
        for (tok in rest.split(Regex("\\s+"))) {
            val t = tok.trim()
            when {
                t.isEmpty() -> {}
                t.startsWith("site:") -> sites.add(t.removePrefix("site:").lowercase().removePrefix("www."))
                t.startsWith("-") && t.length > 1 ->
                    tokenize(t.substring(1)).forEach { excludes.add(it) }
                else -> terms.addAll(tokenize(t))
            }
        }
        if (terms.isEmpty() && phrases.isEmpty()) {
            // fall back to raw tokens so the user always sees something
            terms.addAll(WORD.findAll(raw.lowercase()).map { it.value }.filter { it.length > 1 })
        }
        return Query(terms.distinct(), phrases, sites, excludes, raw)
    }

    // ------------------------------------------------------------------ BM25

    private const val K1 = 1.2
    private const val B = 0.72
    private const val TITLE_BOOST = 2.2

    private fun idf(df: Int, n: Int): Double =
        kotlin.math.ln(1.0 + (n - df + 0.5).coerceAtLeast(0.0) / (df + 0.5))

    /**
     * BM25 over the weighted postings. Every operator in the query language
     * (site:, -, "phrase") is applied here, on data Peek itself collected.
     */
    @Synchronized
    fun search(query: Query, limit: Int = 30): List<PageDoc> {
        val s = state
        if (s.pages.isEmpty()) return emptyList()

        // Candidate set: union of postings for query terms (all terms when
        // possible, otherwise any term to keep recall).
        var candidates: MutableSet<Int>? = null
        for (t in query.terms) {
            val arr = s.terms.postings[t] ?: continue
            val set = HashSet<Int>(arr.size / 2)
            var i = 0
            while (i < arr.size) { set.add(arr[i]); i += 2 }
            candidates = if (candidates == null) set
            else if (candidates.size > 40) candidates // wide enough; skip intersect for speed
            else candidates.intersect(set.toSet()).toMutableSet()
        }
        if (candidates == null && query.phrases.isEmpty()) return emptyList()
        if (candidates == null) candidates = (0 until s.pages.size).toMutableSet()

        val avg = s.avgLen()
        val scored = ArrayList<Pair<Int, Double>>()
        outer@ for (id in candidates) {
            val page = s.pages[id]
            if (query.sites.isNotEmpty() &&
                query.sites.none { site -> page.domain == site || page.domain.endsWith(".$site") }
            ) continue
            if (query.excludes.any { ex -> pageContains(s, id, page, ex) }) continue
            if (query.phrases.any { ph ->
                    val low = (page.title + " \n" + page.text).lowercase()
                    !low.contains(ph)
                }
            ) continue

            var score = 0.0
            val hits = ArrayList<String>()
            for (t in query.terms) {
                val arr = s.terms.postings[t] ?: continue
                var tf = 0
                var i = 0
                while (i < arr.size) {
                    if (arr[i] == id) { tf = arr[i + 1]; break }
                    i += 2
                }
                if (tf == 0) continue
                val dl = s.docLen[id].toDouble().coerceAtLeast(1.0)
                score += idf(s.terms.df[t] ?: 1, s.pages.size) *
                    tf * (K1 + 1) / (tf + K1 * (1 - B + B * dl / avg))
                if (s.terms.titles[t]?.contains(id) == true) {
                    score += TITLE_BOOST * (idf(s.terms.df[t] ?: 1, s.pages.size))
                    hits.add(t)
                }
            }
            if (query.terms.isNotEmpty() && score <= 0.0) continue@outer
            // Recency nudge: fresher crawls rank slightly higher.
            val ageDays = ((System.currentTimeMillis() - page.fetchedAt) / 86_400_000L).coerceAtLeast(0)
            score += (1.0 - (ageDays * 0.02)).coerceAtLeast(0.0)

            page.score = score
            page.hits = hits.ifEmpty { query.terms }
            scored.add(id to score)
        }

        scored.sortByDescending { it.second }
        return scored.take(limit).map { s.pages[it.first] }
    }

    private fun pageContains(s: State, id: Int, page: PageDoc, term: String): Boolean {
        if (page.text.lowercase().contains(term)) return true
        val arr = s.terms.postings[term] ?: return false
        var i = 0
        while (i < arr.size) { if (arr[i] == id) return true; i += 2 }
        return false
    }

    // --------------------------------------------------------------- snippets

    /**
     * Build a result snippet: the passage of the page with the densest
     * concentration of query terms, cut to ~2 sentences around it.
     */
    fun snippet(page: PageDoc, terms: List<String>): String {
        if (terms.isEmpty()) return page.text.take(220)
        val sentences = Text.splitSentences(page.text)
        if (sentences.isEmpty()) return page.text.take(220)
        var best = -1
        var bestScore = -1
        for ((i, sen) in sentences.withIndex()) {
            val low = sen.lowercase()
            var hits = terms.count { low.contains(it) }
            if (i + 1 < sentences.size) {
                val nxt = sentences[i + 1].lowercase()
                hits += terms.count { nxt.contains(it) }
            }
            if (hits > bestScore) { bestScore = hits; best = i }
        }
        if (bestScore <= 0) {
            // fall back to the meta-description-like opening
            return sentences.take(2).joinToString(" ").take(260)
        }
        val out = buildList {
            if (best > 0 && bestScore < 3) add(sentences[best - 1])
            add(sentences[best])
            if (best + 1 < sentences.size) add(sentences[best + 1])
        }
        return out.joinToString(" ").take(320)
    }

    // ------------------------------------------------------------ suggestions

    /** Prefix suggestions from Peek's own data: past queries and page titles. */
    @Synchronized
    fun suggest(prefix: String, pastQueries: List<String>, limit: Int = 8): List<Suggest> {
        val p = prefix.trim().lowercase()
        if (p.isEmpty()) return emptyList()
        val out = ArrayList<Suggest>()
        val seen = HashSet<String>()
        for (q in pastQueries) {
            if (out.size >= limit / 2) break
            if (q.lowercase().startsWith(p) && q.lowercase() != p && seen.add(q.lowercase())) {
                out.add(Suggest(q, "", ""))
            }
        }
        for (page in state.pages.asReversed()) {
            if (out.size >= limit) break
            if (page.title.lowercase().startsWith(p) && seen.add("p:" + page.url)) {
                out.add(Suggest("", page.url, page.title))
            }
        }
        return out
    }

    // ------------------------------------------------------- related searches

    /** Related searches: salient terms of the top pages joined to the query. */
    @Synchronized
    fun related(query: Query, top: List<PageDoc>, limit: Int = 8): List<String> {
        if (top.isEmpty()) return emptyList()
        val n = state.pages.size
        val salient = HashMap<String, Double>()
        for (page in top.take(8)) {
            val seenInPage = HashSet<String>()
            for (t in tokenize(page.title) + tokenize(page.text.take(600))) {
                if (t in query.terms || t in query.excludes || !seenInPage.add(t)) continue
                val df = state.terms.df[t] ?: continue
                salient.merge(t, idf(df, n)) { a, b -> a + b }
            }
        }
        val anchor = query.terms.lastOrNull() ?: query.raw.trim().split(" ").last()
        return salient.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { "$anchor ${it.key}" }
    }

    // ------------------------------------------------------- knowledge panel

    /**
     * Entity panel: when some indexed page's title IS the query, the panel
     * shows its opening sentences — Peek's own "encyclopedia card", built
     * only from crawled pages.
     */
    @Synchronized
    fun knowledge(query: Query): KnowledgePanel? {
        if (query.phrases.isEmpty() && query.terms.size > 4) return null
        val want = query.raw.lowercase()
            .removePrefix("what is").removePrefix("who is").removePrefix("what are")
            .replace(Regex("[?.!]$"), "").trim()
        if (want.isEmpty()) return null
        for (page in state.pages.asReversed()) {
            val t = page.title.lowercase()
                .replace(Regex("\\s*[|–\\-].*$"), "").trim()
            if (t == want || t == want.removePrefix("the ")) {
                val summary = Text.splitSentences(page.text).take(3).joinToString(" ").take(420)
                if (summary.length < 60) return null
                return KnowledgePanel(
                    title = page.title.replace(Regex("\\s*[|–\\-].*$"), "").trim(),
                    summary = summary,
                    image = "",
                    url = page.url,
                    domain = page.domain,
                )
            }
        }
        return null
    }

    // ----------------------------------------------------------------- stats

    @Synchronized
    fun stats(): ProviderStats = ProviderStats(
        pages = state.pages.size,
        terms = state.terms.postings.size,
        sites = state.pages.map { it.domain }.distinct().size,
        crawled = state.pages.size,
    )
}
