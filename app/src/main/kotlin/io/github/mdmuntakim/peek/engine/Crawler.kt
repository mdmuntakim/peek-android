package io.github.mdmuntakim.peek.engine

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * crawler.kt -- PeekBot, Peek's own web crawler. Breadth-first from seed
 * URLs, one request per host every 1.2s (or the host's Crawl-delay), robots.txt
 * respected, page size capped. Every fetched page feeds [Index], so the
 * search engine's universe is exactly what PeekBot has read and what the
 * user browsed — no other search engine is ever consulted.
 */
object Crawler {

    const val MAX_PAGE_BYTES = 512 * 1024
    private const val HOST_DELAY_MS = 1200L
    private const val MAX_HOSTS = 40

    class Stats(
        var fetched: Int = 0,
        var indexed: Int = 0,
        var failed: Int = 0,
        var skipped: Int = 0,
    ) {
        fun snapshot(done: Boolean) = CrawlStats(fetched, indexed, failed, skipped, done)
    }

    @Volatile
    var running: Boolean = false
        private set

    @Volatile
    var last: CrawlStats = CrawlStats(done = true)
        private set

    private val hostLast = HashMap<String, Long>()

    @Volatile
    private var cancelFlag = false

    fun cancel() {
        cancelFlag = true
    }

    /**
     * Crawl from [seeds] breadth-first. Cross-host links are followed so the
     * index can leave the seed ring, but the number of hosts per run is
     * capped to keep the crawl focused and polite.
     */
    suspend fun crawl(
        seeds: List<String>,
        budget: Int,
        maxDepth: Int = 3,
        progress: (CrawlStats) -> Unit = {},
    ): CrawlStats {
        if (running) return last
        running = true
        cancelFlag = false
        val stats = Stats()
        try {
            val queue = ArrayDeque<Pair<String, Int>>()
            val queued = HashSet<String>()
            fun push(url: String, depth: Int) {
                val u = Text.normUrl(url)
                if (queued.add(u)) queue.addLast(u to depth)
            }
            seeds.forEach { push(it, 0) }
            val visited = HashSet<String>(Index.state.byUrl.keys)
            val knownHosts = Index.state.domains()
            val newHosts = HashSet<String>()

            while (queue.isNotEmpty() && stats.fetched < budget && !cancelFlag) {
                val (url, depth) = queue.removeFirst()
                if (url in visited) continue
                val host = Text.domainOf(url)
                if (host.isEmpty()) continue

                // Politeness: never hammer a host.
                val lastHit = synchronized(hostLast) { hostLast[host] }
                lastHit?.let {
                    val wait = it + HOST_DELAY_MS - System.currentTimeMillis()
                    if (wait > 0) kotlinx.coroutines.delay(wait)
                }
                synchronized(hostLast) { hostLast[host] = System.currentTimeMillis() }

                val robots = Robots.rulesFor(host)
                val path = try {
                    java.net.URI(url).let { (it.rawPath ?: "/") + (it.rawQuery ?: "") }
                } catch (_: Exception) { "/" }
                if (!robots.allowed(path)) {
                    stats.skipped++; continue
                }
                val delay = robots.crawlDelayMs.coerceAtLeast(0)

                stats.fetched++
                progress(stats.snapshot(false))
                val html = try {
                    fetchPage(url, 10_000)
                } catch (_: Exception) {
                    stats.failed++; continue
                }
                visited.add(url)
                if (delay > HOST_DELAY_MS) kotlinx.coroutines.delay(delay - HOST_DELAY_MS)

                val doc = HtmlDoc.extract(html, url)
                if (!HtmlDoc.indexable(html, doc.text)) {
                    stats.skipped++; continue
                }
                if (Index.add(PageDoc(url, host, doc.title.ifEmpty { doc.description }, doc.text))) {
                    stats.indexed++
                    for (img in doc.images) {
                        Store.addImage(ImageResult(img.second, url, img.first, 0, 0))
                    }
                    progress(stats.snapshot(false))
                }

                if (depth >= maxDepth) continue
                for (link in doc.links) {
                    val linkHost = Text.domainOf(link)
                    if (linkHost == host) {
                        push(link, depth + 1)
                    } else if (linkHost !in knownHosts &&
                        newHosts.size < MAX_HOSTS &&
                        newHosts.add(linkHost)
                    ) {
                        // First page of an unseen host: enters the frontier once.
                        push(link, depth + 1)
                    }
                }
            }
            return stats.snapshot(done = true).also { last = it }
        } finally {
            running = false
            last = stats.snapshot(done = true)
            progress(last)
        }
    }

    /** Fetch one page with the PeekBot identity and a hard size cap. */
    private suspend fun fetchPage(url: String, timeoutMs: Int): String =
        Http.getLimited(url, timeoutMs, "text/html,application/xhtml+xml", Robots.robotHeaders())
}
