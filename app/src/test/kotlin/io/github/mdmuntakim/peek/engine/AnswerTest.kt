package io.github.mdmuntakim.peek.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerTest {

    private fun r(title: String, snippet: String, rank: Int = 0, domain: String = "example.com") =
        WebResult(title, "https://$domain/x", snippet, "web", 0).also { it.rank = rank; it.domain = domain }

    @Test
    fun `classifies intents`() {
        assertEquals("compare", Answer.classifyIntent("nginx vs apache"))
        assertEquals("howto", Answer.classifyIntent("how to make bread"))
        assertEquals("define", Answer.classifyIntent("what is quantum entanglement"))
        assertEquals("list", Answer.classifyIntent("best hiking trails"))
        assertEquals("general", Answer.classifyIntent("solar flare news"))
    }

    @Test
    fun `instant abstract wins outright`() {
        val ia = InstantAnswer(
            abstract = "Transport Layer Security is a cryptographic protocol designed to provide " +
                "communications security over a network, widely used on the web today.",
            abstractSource = "Wikipedia",
            abstractURL = "https://en.wikipedia.org/wiki/TLS",
            heading = "TLS",
            image = "",
            related = emptyList(),
        )
        val answer = Answer.synthesizeAnswer("what is tls", listOf(r("TLS", "snippet")), ia)
        assertEquals("instant", answer.type)
        assertEquals(0.95, answer.confidence, 1e-9)
        assertEquals(1, answer.sources.size)
        assertEquals("Wikipedia", answer.sources[0].title)
    }

    @Test
    fun `extracts a composed answer from snippets`() {
        val results = listOf(
            r(
                "TLS overview",
                "Transport Layer Security encrypts data between a client and a server. " +
                    "It replaced the older SSL protocol in modern deployments everywhere.",
                rank = 0,
            ),
            r(
                "TLS history",
                "TLS 1.3 was published by the IETF and removed legacy cipher suites completely.",
                rank = 1,
                domain = "ietf.org",
            ),
        )
        val answer = Answer.synthesizeAnswer("what is tls", results, null)
        assertEquals("ai", answer.type)
        assertTrue(answer.text.contains("Transport Layer Security"))
        assertTrue(answer.sources.any { it.domain == "ietf.org" })
        assertTrue(answer.confidence > 0.0)
    }

    @Test
    fun `drops boilerplate and near duplicates`() {
        val results = listOf(
            r("Click page", "Click here to sign up and subscribe to our newsletter right now!! " +
                "Click here to sign up and subscribe to our newsletter right now again."),
        )
        val answer = Answer.synthesizeAnswer("some topic", results, null)
        assertEquals("none", answer.type)
    }

    @Test
    fun `trigram similarity detects duplicates`() {
        assertTrue(Answer.tooSimilar("The quick brown fox jumps over the lazy dog",
            "The quick brown fox jumps over the lazy dog!"))
    }
}

class JsonTest {

    @Test
    fun `parses nested structures`() {
        val v = Json.parse("""{"a":[1,2,{"b":true}],"c":"x\ny","d":null,"e":-2.5}""")
        val obj = Json.obj(v)!!
        val a = Json.arr(obj["a"])!!
        assertEquals(1.0, Json.num(a[0]))
        val inner = Json.obj(a[2])!!
        assertEquals(true, inner["b"])
        assertEquals("x\ny", Json.str(obj["c"]))
        assertEquals(null, obj["d"])
        assertEquals(-2.5, Json.num(obj["e"]))
    }

    @Test
    fun `parses arrays and unicode escapes`() {
        val v = Json.parse("""["a","A\u0041"]""")
        assertEquals(listOf("a", "AA"), Json.arr(v))
    }
}

class SearchPipelineTest {

    @Test
    fun `empty query yields empty payload`() {
        val payload = Search.emptyResult("")
        assertEquals(0, payload.results.size)
        assertEquals("none", payload.answer.type)
    }

    @Test
    fun `related builder trims and caps at eight`() {
        val suggestions = (1..12).map { "suggestion $it" }
        val related = Search.buildRelated("query", null, emptyList(), suggestions)
        assertEquals(8, related.size)
    }
}
