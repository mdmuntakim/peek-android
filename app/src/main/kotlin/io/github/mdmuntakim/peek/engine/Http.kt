package io.github.mdmuntakim.peek.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Thrown on non-2xx responses, like `httpGet` in lib/util.js. */
class HttpException(val status: Int, url: String) : Exception("HTTP $status for $url")

/** HTTP fetch helpers -- the Kotlin port of `httpGet` / `getJson` in lib/util.js. */
object Http {

    const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0 Safari/537.36"

    /** GET a URL and return the body as text. Throws on network failure or non-2xx. */
    suspend fun get(
        url: String,
        timeoutMs: Int = 8000,
        accept: String = "text/html,application/xhtml+xml",
        headers: Map<String, String> = emptyMap(),
    ): String = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Accept", accept)
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            for ((k, v) in headers) conn.setRequestProperty(k, v)
            val code = conn.responseCode
            if (code !in 200..299) throw HttpException(code, url)
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** GET a URL and parse the body as JSON (any shape). */
    suspend fun getJson(url: String, timeoutMs: Int = 8000): Any? =
        Json.parse(get(url, timeoutMs, "application/json, text/plain"))
}

/**
 * A global scheduler for scraping-sensitive hosts (DuckDuckGo). Requests are
 * serialized and spaced out with a minimum interval, exactly like the node
 * engine's `scheduleDdg`, so a burst of searches does not trip the bot radar.
 */
object DdgScheduler {
    private const val MIN_INTERVAL = 1200L
    private val mutex = Mutex()
    private var last = 0L

    suspend fun <T> schedule(block: suspend () -> T): T = mutex.withLock {
        val wait = last + MIN_INTERVAL - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        last = System.currentTimeMillis()
        block()
    }
}
