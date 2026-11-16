package io.github.mdmuntakim.peek.engine

/**
 * robots.kt -- PeekBot's robots.txt client. Per-host cache of the rules for
 * both `*` and `PeekBot` user agents. A missing robots.txt means "allowed";
 * a network failure while fetching it also means "allowed" (the crawl of
 * that host is simply best-effort).
 */
object Robots {

    class Rules(private val disallow: List<String>, private val allow: List<String>, val crawlDelayMs: Long) {
        fun allowed(path: String): Boolean {
            for (a in allow) if (path.startsWith(a)) return true
            for (d in disallow) if (d.isEmpty() || path.startsWith(d)) return false
            return true
        }
    }

    val ALLOW_ALL = Rules(emptyList(), emptyList(), 0)

    private val cache = HashMap<String, Pair<Long, Rules>>()
    private const val TTL = 30 * 60_000L

    /** Parse a robots.txt body into rules for `*` and PeekBot. */
    fun parse(body: String): Rules {
        // Group-based collection: consecutive user-agent lines share a group.
        val groups = LinkedHashMap<String, Pair<MutableList<String>, MutableList<String>>>()
        val delays = HashMap<String, Long>()
        var currentAgents = listOf<String>()
        var lastWasAgent = false

        for (rawLine in body.lineSequence()) {
            val line = rawLine.substringBefore('#').trim()
            if (line.isEmpty()) continue
            val idx = line.indexOf(':')
            if (idx < 0) continue
            val key = line.substring(0, idx).trim().lowercase()
            val value = line.substring(idx + 1).trim()
            when (key) {
                "user-agent" -> {
                    if (!lastWasAgent) currentAgents = listOf()
                    val agent = value.lowercase()
                    currentAgents = currentAgents + agent
                    groups.getOrPut(agent) { mutableListOf<String>() to mutableListOf<String>() }
                    lastWasAgent = true
                }
                "crawl-delay" -> {
                    val d = (value.toLongOrNull() ?: 0L) * 1000L
                    currentAgents.forEach { delays[it] = d }
                    lastWasAgent = false
                }
                "disallow", "allow" -> {
                    for (agent in currentAgents) {
                        val g = groups.getOrPut(agent) { mutableListOf<String>() to mutableListOf<String>() }
                        if (key == "disallow") g.first.add(value) else g.second.add(value)
                    }
                    lastWasAgent = false
                }
                else -> lastWasAgent = false
            }
        }

        // PeekBot's own group wins; otherwise the `*` group applies.
        val chosen = groups["peekbot"]?.takeIf { it.first.isNotEmpty() || it.second.isNotEmpty() }
            ?: groups["*"]
            ?: return ALLOW_ALL
        return Rules(chosen.first, chosen.second, delays["peekbot"] ?: delays["*"] ?: 0L)
    }

    /** Rules for a host, fetched once per TTL. `fetcher` injectable for tests. */
    suspend fun rulesFor(host: String, fetcher: suspend (String) -> String? = ::fetchBody): Rules {
        val now = System.currentTimeMillis()
        synchronized(cache) {
            val hit = cache[host]
            if (hit != null && now - hit.first <= TTL) return hit.second
        }
        val rules = try {
            fetcher("https://$host/robots.txt")?.let { parse(it) } ?: ALLOW_ALL
        } catch (_: Exception) {
            ALLOW_ALL
        }
        synchronized(cache) {
            if (cache.size > 200) cache.clear()
            cache[host] = now to rules
        }
        return rules
    }

    private suspend fun fetchBody(url: String): String? = try {
        Http.get(url, 6000, "text/plain", robotHeaders())
    } catch (e: HttpException) {
        if (e.status in 400..499) null else throw e // 4xx = no robots.txt
    }

    fun robotHeaders(): Map<String, String> = mapOf("User-Agent" to Http.CRAWLER_UA)
}
