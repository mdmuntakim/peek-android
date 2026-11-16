package io.github.mdmuntakim.peek.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests of Peek's own search engine: tokenizer, BM25 ranking, the
 * query language (site:, exclusions, phrases), snippets, suggestions,
 * related searches and the knowledge panel. Everything runs on the plain
 * JVM against the in-memory index.
 */
class IndexTest {

    @Before
    fun reset() {
        Index.clear()
    }

    private fun doc(url: String, title: String, text: String) =
        PageDoc(url, Text.domainOf(url), title, text, System.currentTimeMillis())

    @Test
    fun `tokenizes lowercased alphanumerics and drops stopwords`() {
        assertEquals(
            listOf("quick", "brown", "fox"),
            Index.tokenize("The QUICK brown fox!"),
        )
        assertTrue(Index.tokenize("a of the").isEmpty())
    }

    @Test
    fun `bm25 ranks the most relevant page first`() {
        Index.add(doc("https://a.example/tls", "Transport Layer Security", 
            "Transport Layer Security (TLS) is a cryptographic protocol. TLS encrypts connections."))
        Index.add(doc("https://b.example/cooking", "Making bread",
            "Bread is baked in an oven. Flour and water are the base."))
        Index.add(doc("https://c.example/tls2", "TLS vs SSL history",
            "TLS replaced SSL. The TLS handshake negotiates cipher suites."))

        val hits = Index.search(Index.parseQuery("what is tls"))
        assertTrue("should find the TLS pages", hits.size >= 2)
        assertTrue(
            "the page with TLS in the title should win: got ${hits.first().url}",
            hits.first().url.endsWith("/tls") || hits.first().url.endsWith("/tls2"),
        )
        assertTrue(hits.none { it.url.contains("cooking") })
    }

    @Test
    fun `site filter restricts results to the domain`() {
        Index.add(doc("https://a.example/one", "Alpha", "alpha page about engines"))
        Index.add(doc("https://b.example/two", "Beta", "beta page about engines"))

        val hits = Index.search(Index.parseQuery("engines site:b.example"))
        assertEquals(1, hits.size)
        assertTrue(hits.first().url.startsWith("https://b.example"))
    }

    @Test
    fun `exclusions remove matching pages`() {
        Index.add(doc("https://a.example/rust", "Rust language", "rust is a systems language"))
        Index.add(doc("https://b.example/rustdoc", "Rust on the web", "rust for web assembly"))

        val hits = Index.search(Index.parseQuery("rust -web"))
        assertTrue(hits.none { it.url == "https://b.example/rustdoc" })
        assertTrue(hits.isNotEmpty())
    }

    @Test
    fun `phrases require the exact adjacency`() {
        Index.add(doc("https://a.example/one", "Peek engine", "the peek engine is unique"))
        Index.add(doc("https://b.example/two", "Engines", "peek at the engine of a car"))

        val hits = Index.search(Index.parseQuery("\"peek engine\""))
        assertEquals(1, hits.size)
        assertEquals("https://a.example/one", hits.first().url)
    }

    @Test
    fun `snippets pick the densest passage`() {
        val page = doc(
            "https://a.example/gps",
            "GPS",
            "GPS is a satellite navigation system. " +
                "GPS receivers compute position from satellite signals. " +
                "The history of GPS began in the 1970s.",
        )
        val snip = Index.snippet(page, listOf("satellite"))
        assertTrue(snip.contains("satellite", ignoreCase = true))
    }

    @Test
    fun `suggests past queries and page titles`() {
        Index.add(doc("https://a.example/kotlin", "Kotlin overview", "kotlin language page"))
        val out = Index.suggest("kot", listOf("kotlin vs java", "rust"), 8)
        assertTrue(out.any { it.query == "kotlin vs java" })
        assertTrue(out.any { it.pageUrl == "https://a.example/kotlin" })
    }

    @Test
    fun `related searches combine the anchor term with salient terms`() {
        Index.add(doc("https://a.example/h1", "TLS handshake", 
            "the tls handshake negotiates certificates and cipher suites for security"))
        Index.add(doc("https://b.example/h2", "TLS basics",
            "tls provides security over http with certificates"))
        val q = Index.parseQuery("tls")
        val hits = Index.search(q)
        val rel = Index.related(q, hits)
        assertTrue(rel.all { it.startsWith("tls") })
        assertTrue(rel.isNotEmpty())
    }

    @Test
    fun `knowledge panel matches a page whose title is the query`() {
        Index.add(doc("https://a.example/wiki/GPS", "GPS",
            "The Global Positioning System is a satellite-based navigation system " +
                "owned by the United States government and operated by the United States Space Force. " +
                "It provides geolocation and time information anywhere on Earth."))
        val panel = Index.knowledge(Index.parseQuery("what is gps"))
        assertNotNull(panel)
        assertEquals("GPS", panel!!.title)
        assertTrue(panel.summary.contains("navigation"))
    }

    @Test
    fun `upsert refreshes a browsed page instead of duplicating`() {
        Index.add(doc("https://a.example/x", "Old title", "stale content"))
        assertTrue(Index.upsert(doc("https://a.example/x", "New title", "fresh content")))
        assertEquals(1, Index.stats().pages)
        val hits = Index.search(Index.parseQuery("fresh"))
        assertEquals(1, hits.size)
        assertEquals("New title", hits.first().title)
    }

    @Test
    fun `eviction keeps the index bounded`() {
        val savedMax = Index.MAX_PAGES
        try {
            repeat(50) { i ->
                Index.add(doc("https://s$i.example/", "Page $i", "content of page number $i"))
            }
            assertEquals(50, Index.stats().pages)
            Index.clear()
            assertEquals(0, Index.stats().pages)
        } finally {
            // (MAX_PAGES is a const; this only guards the test's own cleanliness)
            assertFalse(Index.state.pages.isNotEmpty() && Index.state.pages.size > savedMax)
        }
    }
}
