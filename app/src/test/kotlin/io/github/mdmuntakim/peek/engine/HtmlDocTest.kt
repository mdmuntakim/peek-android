package io.github.mdmuntakim.peek.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** PeekBot's HTML reader: extraction of text, links and images. */
class HtmlDocTest {

    private fun page() = """
        <html><head>
          <title>Transport Layer Security</title>
          <meta name="description" content="TLS is a cryptographic protocol.">
        </head><body>
          <nav><a href="/menu">Menu</a></nav>
          <h1>Overview</h1>
          <p>TLS encrypts connections between clients and servers.</p>
          <a href="https://example.org/next">Next page</a>
          <a href="/relative/page">Relative</a>
          <a href="mailto:someone@example.org">Mail</a>
          <a href="#anchor">Anchor</a>
          <img src="/img/diagram.png" alt="A TLS diagram">
          <img src="https://cdn.example.org/pic.jpg" alt="Photo">
          <img src="data:image/gif;base64,AAAA" alt="inline">
        </body></html>
    """.trimIndent()

    @Test
    fun `extracts title description headings text links images`() {
        val p = HtmlDoc.extract(page(), "https://a.example/wiki/tls")
        assertEquals("Transport Layer Security", p.title)
        assertEquals("TLS is a cryptographic protocol.", p.description)
        assertTrue(p.headings.contains("Overview"))
        assertTrue(p.text.contains("TLS encrypts connections"))
        // nav/mail/anchor links are dropped, http(s) links kept and absolutized
        assertTrue(p.links.contains("https://example.org/next"))
        assertTrue(p.links.contains("https://a.example/relative/page"))
        assertTrue(p.links.none { it.startsWith("mailto:") })
        assertTrue(p.links.none { it.contains("#") })
        // images are absolutized, data URIs dropped
        assertEquals(2, p.images.size)
        assertTrue(p.images.any { it.first == "https://a.example/img/diagram.png" })
        assertTrue(p.images.any { it.first == "https://cdn.example.org/pic.jpg" })
    }

    @Test
    fun `indexable rejects noindex and empty pages`() {
        val noindex = "<html><head><meta name=\"robots\" content=\"noindex,follow\"></head>" +
            "<body>${"word ".repeat(40)}</body></html>"
        assertFalse(HtmlDoc.indexable(noindex, "some text"))
        assertTrue(HtmlDoc.indexable("<html><body>text</body></html>", "x".repeat(200)))
        assertFalse(HtmlDoc.indexable("<html></html>", "short"))
    }

    @Test
    fun `absolutize handles protocol relative and bare paths`() {
        assertEquals("https://x.org/a", HtmlDoc.absolutize("//x.org/a", "https://b.org/"))
        assertEquals("https://b.org/sub/c", HtmlDoc.absolutize("c", "https://b.org/sub/"))
        assertNull(HtmlDoc.absolutize("javascript:void(0)", "https://b.org/"))
        assertNull(HtmlDoc.absolutize("data:text/plain,x", "https://b.org/"))
    }

    @Test
    fun `scripts and styles never leak into the text`() {
        val html = "<html><body><script>var x = 'should not appear';</script>" +
            "<style>.x{color:red}</style><p>Visible words live here.</p></body></html>"
        val p = HtmlDoc.extract(html, "https://a.example/")
        assertFalse(p.text.contains("should not appear"))
        assertTrue(p.text.contains("Visible words live here."))
    }
}

/** PeekBot's robots.txt rules. */
class RobotsTest {

    @Test
    fun `parses the star group and crawl delay`() {
        val r = Robots.parse(
            """
            User-agent: *
            Disallow: /private/
            Allow: /private/public
            Crawl-delay: 2

            User-agent: Googlebot
            Disallow:
            """.trimIndent()
        )
        assertFalse(r.allowed("/private/secret"))
        assertTrue(r.allowed("/private/public/page"))
        assertTrue(r.allowed("/open"))
        assertEquals(2000, r.crawlDelayMs)
    }

    @Test
    fun `a peekbot group wins over star`() {
        val r = Robots.parse(
            """
            User-agent: *
            Disallow: /

            User-agent: PeekBot
            Disallow: /tmp
            """.trimIndent()
        )
        assertTrue("the PeekBot group must win for its own rules", r.allowed("/"))
        assertFalse(r.allowed("/tmp/x"))
    }

    @Test
    fun `missing rules allow everything`() {
        val r = Robots.parse("")
        assertTrue(r.allowed("/anything"))
    }

    @Test
    fun `rulesFor caches per host`() = kotlinx.coroutines.runBlocking {
        var fetches = 0
        val rules1 = Robots.rulesFor("cached.example") { fetches++; "User-agent: *\nDisallow: /x" }
        val rules2 = Robots.rulesFor("cached.example") { fetches++; "" }
        assertEquals(1, fetches)
        assertFalse(rules1.allowed("/x"))
        assertFalse("second call must be served from the cache", rules2.allowed("/x"))
    }
}
