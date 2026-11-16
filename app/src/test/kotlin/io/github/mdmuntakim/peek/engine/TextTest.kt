package io.github.mdmuntakim.peek.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextTest {

    @Test
    fun `decode entities handles named and numeric forms`() {
        assertEquals("a & b", Text.decodeEntities("a &amp; b"))
        assertEquals("…", Text.decodeEntities("&hellip;"))
        assertEquals("<x>", Text.decodeEntities("&#x3C;x&#62;"))
        assertEquals("'", Text.decodeEntities("&#39;"))
        assertEquals("", Text.decodeEntities(""))
        assertEquals("", Text.decodeEntities(null))
    }

    @Test
    fun `stripTags removes script bodies and tags`() {
        assertEquals(
            "hello world",
            Text.textOf("<p>hello<script>bad()</script></p><b> world</b>")
        )
    }

    @Test
    fun `keyTerms drops stopwords and punctuation`() {
        assertEquals(listOf("tls", "works"), Text.keyTerms("What is TLS, and how it works?"))
        assertTrue(Text.keyTerms("how to").isEmpty())
    }

    @Test
    fun `splitSentences splits on boundaries`() {
        val s = Text.splitSentences("First sentence. Second one! Third? Trailing")
        assertEquals(listOf("First sentence.", "Second one!", "Third?", "Trailing"), s)
    }

    @Test
    fun `domainOf handles normal and www hosts`() {
        assertEquals("example.com", Text.domainOf("https://www.example.com/x/y?a=1"))
        assertEquals("", Text.domainOf(":://bad"))
    }

    @Test
    fun `normUrl strips query and fragment consistently`() {
        val a = Text.normUrl("https://www.example.com/a/?utm_source=x#frag")
        val b = Text.normUrl("https://WWW.EXAMPLE.COM/a")
        assertEquals("https://www.example.com/a", a)
        assertEquals(a, b)
    }
}
