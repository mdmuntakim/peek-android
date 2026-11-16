package io.github.mdmuntakim.peek.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DdgParserTest {

    private fun htmlPage() = """
        <html><body>
        <div class="result results_links">
          <h2><a rel="nofollow" class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fen.wikipedia.org%2Fwiki%2FTLS&amp;rut=abc">Transport Layer <b>Security</b></a></h2>
          <a class="result__snippet" href="#">Transport Layer Security (TLS) encrypts connections between clients and servers.</a>
        </div>
        <div class="result results_links">
          <h2><a rel="nofollow" class="result__a" href="https://example.org/guide">A plain https link</a></h2>
          <a class="result__snippet" href="#">This is the snippet text of the second result.</a>
        </div>
        <a class="result__a" href="/ad">Blocked internal link</a>
        </body></html>
    """.trimIndent()

    @Test
    fun `parseDdgHtml extracts uddg targets snippets and drops ads`() {
        val out = Search.parseDdgHtml(htmlPage())
        assertEquals(2, out.size)
        assertEquals("https://en.wikipedia.org/wiki/TLS", out[0].url)
        assertEquals("Transport Layer Security", out[0].title)
        assertEquals(
            "Transport Layer Security (TLS) encrypts connections between clients and servers.",
            out[0].snippet,
        )
        assertEquals("https://example.org/guide", out[1].url)
        assertEquals(0, out[0].position)
        assertEquals(1, out[1].position)
    }

    @Test
    fun `parseDdgLite extracts lite layout`() {
        val lite = """
            <table><tr>
            <td><a rel="nofollow" class="result-link" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Fone">Example One</a></td>
            <td><a class="result-snippet" href="#">Snippet number one here.</a></td>
            </tr></table>
        """.trimIndent()
        val out = Search.parseDdgLite(lite)
        assertEquals(1, out.size)
        assertEquals("https://example.com/one", out[0].url)
        assertEquals("Example One", out[0].title)
        assertEquals("Snippet number one here.", out[0].snippet)
    }
}

class RankTest {

    private fun r(title: String, url: String, snippet: String = "", source: String = "web", position: Int = 0) =
        WebResult(title, url, snippet, source, position)

    @Test
    fun `ranks by term hits and de-duplicates urls`() {
        val results = listOf(
            r("Quantum computing basics", "https://a.com/q?utm=1", "Quantum computing explained"),
            r("Quantum computing basics", "https://a.com/q", "duplicate"),
            r("Unrelated story", "https://b.com/x", "nothing here"),
            r("Quantum computing deep dive", "https://en.wikipedia.org/wiki/Quantum_computing", "quantum computing"),
        )
        val ranked = Rank.rankResults("quantum computing", results)
        assertEquals(3, ranked.size)
        // the authority domain with URL term hits leads the ranking
        assertEquals("https://en.wikipedia.org/wiki/Quantum_computing", ranked[0].url)
        // the duplicate collapsed to one entry
        assertEquals(1, ranked.count { it.url.startsWith("https://a.com/q") })
        assertEquals(0, ranked[0].rank)
        assertEquals(1, ranked[1].rank)
    }

    @Test
    fun `authority domains get a boost`() {
        val wiki = Rank.rankResults("tls", listOf(r("TLS", "https://en.wikipedia.org/wiki/TLS", "tls")))
        val blog = Rank.rankResults("tls", listOf(r("TLS", "https://randomblog.net/tls", "tls")))
        assertTrue(wiki[0].score > blog[0].score)
    }

    @Test
    fun `position prior favors earlier provider results`() {
        val early = Rank.rankResults("tls", listOf(r("TLS", "https://a.com/1", "", position = 0)))
        val late = Rank.rankResults("tls", listOf(r("TLS", "https://a.com/2", "", position = 9)))
        assertTrue(early[0].score > late[0].score)
    }
}
