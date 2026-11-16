package io.github.mdmuntakim.peek.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerTest {

    private fun r(title: String, snippet: String, rank: Int = 0, domain: String = "example.com") =
        WebResult(title, "https://$domain/x", snippet, "index", 0).also { it.rank = rank; it.domain = domain }

    @Test
    fun `classifies intents`() {
        assertEquals("compare", Answer.classifyIntent("nginx vs apache"))
        assertEquals("howto", Answer.classifyIntent("how to make bread"))
        assertEquals("define", Answer.classifyIntent("what is quantum entanglement"))
        assertEquals("list", Answer.classifyIntent("best hiking trails"))
        assertEquals("general", Answer.classifyIntent("solar flare news"))
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
        val answer = Answer.synthesizeAnswer("what is tls", results)
        assertEquals("ai", answer.type)
        assertTrue(answer.text.contains("Transport Layer Security"))
        assertTrue(answer.sources.any { it.domain == "ietf.org" })
        assertTrue(answer.confidence > 0.0)
    }

    @Test
    fun `no results give a none answer`() {
        val answer = Answer.synthesizeAnswer("anything at all", emptyList())
        assertEquals("none", answer.type)
        assertEquals("", answer.text)
    }
}
