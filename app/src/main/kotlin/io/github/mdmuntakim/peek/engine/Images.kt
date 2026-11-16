package io.github.mdmuntakim.peek.engine

import kotlinx.coroutines.delay

/**
 * images.kt -- image search, ported from lib/images.js with a hardened
 * provider chain. Primary provider is the DuckDuckGo images endpoint (needs
 * a per-query "vqd" token extracted from the HTML page first); when the
 * endpoint serves its anti-bot challenge, the key-free Openverse API is
 * used instead. Any failure returns an empty list, the Images tab then
 * simply stays empty.
 */
object Images {

    private const val CACHE_TTL = 300_000L
    private val cache = HashMap<String, Pair<Long, List<ImageResult>>>()

    suspend fun imageSearch(query0: String, limit: Int = 40): List<ImageResult> {
        val q = query0.trim().take(300)
        if (q.isEmpty()) return emptyList()
        val key = "i:" + q.lowercase()

        synchronized(cache) {
            val hit = cache[key]
            if (hit != null) {
                if (System.currentTimeMillis() - hit.first <= CACHE_TTL) return hit.second
                cache.remove(key)
            }
            if (cache.size > 300) cache.clear()
        }

        val ddg = runCatching { ddgImages(q, limit) }.getOrDefault(emptyList())
        val out = ddg.ifEmpty { runCatching { openverseImages(q, limit) }.getOrDefault(emptyList()) }

        synchronized(cache) { cache[key] = System.currentTimeMillis() to out }
        return out
    }

    /** DuckDuckGo image results through the vqd-tokenized i.js endpoint. */
    internal suspend fun ddgImages(q: String, limit: Int): List<ImageResult> {
        var vqd: String? = null
        for (attempt in 0 until 2) {
            if (attempt > 0) delay(1600)
            val page = DdgScheduler.schedule {
                Http.get("https://duckduckgo.com/?q=${java.net.URLEncoder.encode(q, "UTF-8")}&iax=images&ia=images", 8000)
            }
            val m = Regex("vqd=[\"']?([\\d-]{6,})[\"']?").find(page)
            if (m != null) { vqd = m.groupValues[1]; break }
        }
        if (vqd == null) return emptyList()
        val data = Json.obj(
            Http.getJson(
                "https://duckduckgo.com/i.js?l=us-en&o=json&q=${java.net.URLEncoder.encode(q, "UTF-8")}" +
                    "&vqd=$vqd&f=,,,&p=1",
                8000,
            )
        )
        return (Json.arr(data?.get("results")) ?: emptyList())
            .mapNotNull { r -> Json.obj(r) }
            .filter { !Json.str(it["image"]).isNullOrEmpty() }
            .take(limit)
            .map {
                ImageResult(
                    title = (Json.str(it["title"]) ?: "").trim(),
                    url = Json.str(it["url"]) ?: "",
                    image = Json.str(it["image"])!!,
                    width = (Json.num(it["width"]) ?: 0.0).toInt(),
                    height = (Json.num(it["height"]) ?: 0.0).toInt(),
                )
            }
    }

    /** Openverse (openly licensed images) -- key-free fallback provider. */
    internal suspend fun openverseImages(q: String, limit: Int): List<ImageResult> {
        val pageSize = limit.coerceAtMost(20) // Openverse caps page_size at 20
        val data = Json.obj(
            Http.getJson(
                "https://api.openverse.org/v1/images/?q=${java.net.URLEncoder.encode(q, "UTF-8")}" +
                    "&page_size=$pageSize&license_type=all",
                8000,
            )
        )
        return (Json.arr(data?.get("results")) ?: emptyList())
            .mapNotNull { r -> Json.obj(r) }
            .filter { !Json.str(it["url"]).isNullOrEmpty() }
            .take(limit)
            .map {
                ImageResult(
                    title = (Json.str(it["title"]) ?: "").trim(),
                    url = (Json.str(it["foreign_landing_url"]) ?: Json.str(it["url"]) ?: "").trim(),
                    image = Json.str(it["url"])!!,
                    width = (Json.num(it["width"]) ?: 0.0).toInt(),
                    height = (Json.num(it["height"]) ?: 0.0).toInt(),
                )
            }
    }
}
