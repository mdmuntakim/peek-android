package io.github.mdmuntakim.peek.engine

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Live end-to-end smoke test. Runs only when PEEK_LIVE=1 is set, so the
 * normal CI run stays offline-safe:
 *
 *     PEEK_LIVE=1 gradle :app:testDebugUnitTest --tests "*LiveSmokeTest*"
 */
class LiveSmokeTest {

    @Test
    fun `live search returns results and answer`() {
        assumeTrue("PEEK_LIVE=1 not set; skipping live test", System.getenv("PEEK_LIVE") == "1")
        val payload = kotlinx.coroutines.runBlocking { Search.search("what is TLS") }
        println("answer: ${payload.answer.type} -> ${payload.answer.text.take(120)}")
        println("results: ${payload.results.size}, providers: $payload.providers")
        assertTrue("web results should not be empty", payload.results.isNotEmpty())
        assertTrue(
            "at least one provider must contribute",
            payload.providers.web + payload.providers.instant + payload.providers.wikipedia > 0,
        )
    }

    @Test
    fun `live image search returns images`() {
        assumeTrue("PEEK_LIVE=1 not set; skipping live test", System.getenv("PEEK_LIVE") == "1")
        val images = kotlinx.coroutines.runBlocking { Images.imageSearch("aurora borealis") }
        println("images: ${images.size}, first: ${images.firstOrNull()?.image?.take(80)}")
        assertTrue("image results should not be empty", images.isNotEmpty())
    }
}
