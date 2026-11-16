package io.github.mdmuntakim.peek.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Thrown on non-2xx responses. */
class HttpException(val status: Int, url: String) : Exception("HTTP $status for $url")

/**
 * HTTP fetch helpers. Two identities:
 *  - [UA]      : pages the user opens through the in-app browser layer
 *  - [CRAWLER_UA]: PeekBot, the app's own crawler, honestly identified
 */
object Http {

    const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0 Safari/537.36"

    const val CRAWLER_UA =
        "PeekBot/0.2 (+https://github.com/mdmuntakim/peek-android; personal search crawler)"

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
            conn.setRequestProperty("User-Agent", headers["User-Agent"] ?: UA)
            conn.setRequestProperty("Accept", accept)
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            for ((k, v) in headers) if (k != "User-Agent") conn.setRequestProperty(k, v)
            val code = conn.responseCode
            if (code !in 200..299) throw HttpException(code, url)
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * GET with a byte cap: PeekBot never reads more than [maxBytes] of any
     * page, no matter what the server sends.
     */
    suspend fun getLimited(
        url: String,
        timeoutMs: Int = 8000,
        accept: String = "text/html,application/xhtml+xml",
        headers: Map<String, String> = emptyMap(),
        maxBytes: Int = Crawler.MAX_PAGE_BYTES,
    ): String = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", headers["User-Agent"] ?: Http.CRAWLER_UA)
            conn.setRequestProperty("Accept", accept)
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            for ((k, v) in headers) if (k != "User-Agent") conn.setRequestProperty(k, v)
            val code = conn.responseCode
            if (code !in 200..299) throw HttpException(code, url)
            val type = conn.contentType ?: ""
            if (!type.contains("html") && !type.contains("text")) {
                throw HttpException(415, url)
            }
            val out = StringBuilder()
            val reader = conn.inputStream.bufferedReader(Charsets.UTF_8)
            val buf = CharArray(4096)
            var total = 0
            while (true) {
                val n = reader.read(buf)
                if (n < 0) break
                total += n
                if (total > maxBytes) break
                out.append(buf, 0, n)
            }
            out.toString()
        } finally {
            conn.disconnect()
        }
    }
}
