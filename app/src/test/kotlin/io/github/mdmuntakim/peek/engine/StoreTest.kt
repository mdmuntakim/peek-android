package io.github.mdmuntakim.peek.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The Store layer: JSON snapshot round-trips, history, bookmarks, seeds and
 * image collection. Runs on the plain JVM with a temp directory.
 */
class StoreTest {

    private val tmp = File(System.getProperty("java.io.tmpdir"), "peek-store-test-${System.nanoTime()}")

    @Test
    fun `snapshot round trips pages history bookmarks seeds and images`() {
        tmp.mkdirs()
        try {
            // Build engine state, persist.
            Store.initForTests(tmp)
            Index.add(PageDoc("https://a.example/page", "a.example", "A page title",
                "Some longer body text of the page that carries words."))
            Store.rememberQuery("test query")
            Store.addBookmark("A page title", "https://a.example/page")
            Store.addImage(ImageResult("alt words", "https://a.example/page", "https://a.example/i.png", 10, 10))
            Store.addSeed("https://custom.example/seed")
            Store.persist()
            assertTrue(Store.fileForTests()!!.exists())

            // Wipe the in-memory engine, restore from disk.
            Index.clear()
            Store.initForTests(tmp)
            assertEquals(1, Index.stats().pages)
            assertEquals(listOf("test query"), Store.history())
            assertTrue(Store.isBookmarked("https://a.example/page"))
            assertEquals(listOf("https://custom.example/seed", *Store.DEFAULT_SEEDS.toTypedArray())
                .filter { it == "https://custom.example/seed" }, Store.seedsList().filter { it == "https://custom.example/seed" })
            assertTrue(Store.seedsList().contains("https://custom.example/seed"))
            assertEquals(1, Store.imageSearch("alt").size)
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun `search over collected images matches alt text`() {
        tmp.mkdirs()
        try {
            Store.initForTests(tmp)
            Store.addImage(ImageResult("sunset over mountains", "https://a.example/p", "https://a.example/1.jpg", 0, 0))
            Store.addImage(ImageResult("a keyboard", "https://b.example/p", "https://b.example/2.jpg", 0, 0))
            val out = Store.imageSearch("mountains")
            assertEquals(1, out.size)
            assertEquals("https://a.example/1.jpg", out.first().image)
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun `history is deduplicated newest first`() {
        tmp.mkdirs()
        try {
            Store.initForTests(tmp)
            Store.rememberQuery("one")
            Store.rememberQuery("two")
            Store.rememberQuery("one")
            assertEquals(listOf("one", "two"), Store.history())
            Store.clearHistory()
            assertTrue(Store.history().isEmpty())
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun `seeds default to the built-in crawl ring`() {
        tmp.mkdirs()
        try {
            Store.initForTests(tmp)
            assertTrue(Store.seedsList().containsAll(Store.DEFAULT_SEEDS))
            assertFalse(Store.seedsList().any { it.contains("duckduckgo") || it.contains("google") || it.contains("bing") })
        } finally {
            tmp.deleteRecursively()
        }
    }
}
