package io.github.mdmuntakim.peek.engine

import java.io.File

/**
 * store.kt -- Peek's persistence layer, deliberately dependency-free: the
 * whole engine state (index, images, history, bookmarks, seeds) serializes
 * to one JSON snapshot file with an atomic rename, so no platform storage
 * API is needed and the engine stays testable on the plain JVM.
 */
object Store {

    private const val MAX_HISTORY = 200
    private const val MAX_IMAGES = 1200

    private lateinit var file: File
    private val lock = Any()

    private val historyQ = ArrayDeque<String>()
    private val bookmarks = ArrayList<Pair<String, String>>() // title to url
    private val images = ArrayList<ImageResult>()
    private val seeds = ArrayList<String>()
    private var autoIndexBrowse = true
    private var dirty = false

    /** Default crawl ring — open, index-friendly sites PeekBot starts from. */
    val DEFAULT_SEEDS = listOf(
        "https://en.wikipedia.org/wiki/Search_engine",
        "https://www.gutenberg.org",
        "https://developer.mozilla.org/en-US/docs/Web",
        "https://www.python.org",
        "https://www.rust-lang.org",
        "https://arxiv.org",
        "https://www.gutenberg.org/collection/finished",
        "https://openlibrary.org",
    )

    // ------------------------------------------------------------------ setup

    fun init(dir: File) {
        file = File(dir, "peek-index.json")
        synchronized(lock) {
            seeds.clear(); seeds.addAll(DEFAULT_SEEDS)
            try {
                if (file.exists()) restore(file.readText())
            } catch (_: Exception) {
                // A corrupt snapshot is simply dropped; PeekBot re-crawls.
            }
        }
    }

    /** Test entry point: reset every in-memory list, then load from dir. */
    fun initForTests(dir: File) {
        synchronized(lock) {
            historyQ.clear(); bookmarks.clear(); images.clear(); dirty = false
            Index.clear()
        }
        init(dir)
    }

    fun fileForTests(): File? = if (::file.isInitialized) file else null

    private fun restore(text: String) {
        val root = Json.obj(Json.parse(text)) ?: return
        for (p in Json.arr(root["pages"]) ?: emptyList()) {
            Json.obj(p)?.let { o ->
                Index.add(
                    PageDoc(
                        url = Json.str(o["url"]) ?: return@let,
                        domain = Json.str(o["domain"]) ?: "",
                        title = Json.str(o["title"]) ?: "",
                        text = Json.str(o["text"]) ?: "",
                        fetchedAt = (Json.num(o["fetchedAt"]) ?: 0.0).toLong(),
                    )
                )
            }
        }
        for (i in Json.arr(root["images"]) ?: emptyList()) {
            Json.obj(i)?.let { o ->
                ImageResult(
                    title = Json.str(o["title"]) ?: "",
                    url = Json.str(o["url"]) ?: "",
                    image = Json.str(o["image"]) ?: "",
                    width = (Json.num(o["width"]) ?: 0.0).toInt(),
                    height = (Json.num(o["height"]) ?: 0.0).toInt(),
                )
            }?.let { images.add(it) }
        }
        for (q in Json.arr(root["history"]) ?: emptyList()) (q as? String)?.let { historyQ.addLast(it) }
        for (b in Json.arr(root["bookmarks"]) ?: emptyList()) {
            Json.obj(b)?.let { o ->
                Pair(Json.str(o["title"]) ?: "", Json.str(o["url"]) ?: "")
            }?.takeIf { it.second.isNotEmpty() }?.let { bookmarks.add(it) }
        }
        (Json.arr(root["seeds"]) ?: emptyList()).mapNotNull { it as? String }.let {
            if (it.isNotEmpty()) { seeds.clear(); seeds.addAll(it) }
        }
        autoIndexBrowse = root["autoIndexBrowse"] as? Boolean ?: true
    }

    // -------------------------------------------------------------- serialize

    private fun snapshotJson(): String {
        val out = StringBuilder()
        fun esc(s: String): String = Json.escape(s)
        out.append('{')
        // pages
        out.append("\"pages\":[")
        Index.state.pages.forEachIndexed { i, p ->
            if (i > 0) out.append(',')
            out.append("{\"url\":\"").append(esc(p.url))
                .append("\",\"domain\":\"").append(esc(p.domain))
                .append("\",\"title\":\"").append(esc(p.title))
                .append("\",\"text\":\"").append(esc(p.text))
                .append("\",\"fetchedAt\":").append(p.fetchedAt).append('}')
        }
        // images
        out.append("],\"images\":[")
        images.forEachIndexed { i, m ->
            if (i > 0) out.append(',')
            out.append("{\"title\":\"").append(esc(m.title))
                .append("\",\"url\":\"").append(esc(m.url))
                .append("\",\"image\":\"").append(esc(m.image))
                .append("\",\"width\":").append(m.width)
                .append(",\"height\":").append(m.height).append('}')
        }
        // history
        out.append("],\"history\":[")
        historyQ.forEachIndexed { i, q -> if (i > 0) out.append(','); out.append('"').append(esc(q)).append('"') }
        // bookmarks
        out.append("],\"bookmarks\":[")
        bookmarks.forEachIndexed { i, (t, u) ->
            if (i > 0) out.append(',')
            out.append("{\"title\":\"").append(esc(t)).append("\",\"url\":\"").append(esc(u)).append("\"}")
        }
        // seeds
        out.append("],\"seeds\":[")
        seeds.forEachIndexed { i, s -> if (i > 0) out.append(','); out.append('"').append(esc(s)).append('"') }
        out.append("],\"autoIndexBrowse\":").append(autoIndexBrowse)
        out.append('}')
        return out.toString()
    }

    /** Write the snapshot if anything changed. Debounced by the caller. */
    fun persist() {
        if (!::file.isInitialized) return
        synchronized(lock) {
            if (!dirty) return
            try {
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(snapshotJson())
                if (!tmp.renameTo(file)) {
                    file.delete()
                    tmp.renameTo(file)
                }
                dirty = false
            } catch (_: Exception) {
            }
        }
    }

    private fun markDirty() {
        dirty = true
    }

    // ---------------------------------------------------------------- history

    fun rememberQuery(q: String) {
        synchronized(lock) {
            historyQ.remove(q)
            historyQ.addLast(q)
            while (historyQ.size > MAX_HISTORY) historyQ.removeFirst()
            markDirty()
        }
    }

    fun history(): List<String> = synchronized(lock) { historyQ.reversed() }

    fun clearHistory() {
        synchronized(lock) { historyQ.clear(); markDirty(); persist() }
    }

    // -------------------------------------------------------------- bookmarks

    fun addBookmark(title: String, url: String): Boolean = synchronized(lock) {
        if (bookmarks.any { it.second == url }) return false
        bookmarks.add(0, title to url)
        markDirty(); true
    }

    fun removeBookmark(url: String) = synchronized(lock) {
        bookmarks.removeAll { it.second == url }
        markDirty()
    }

    fun isBookmarked(url: String): Boolean = synchronized(lock) { bookmarks.any { it.second == url } }

    fun bookmarks(): List<Pair<String, String>> = synchronized(lock) { bookmarks.toList() }

    // ----------------------------------------------------------------- images

    fun addImage(img: ImageResult) {
        synchronized(lock) {
            if (images.any { it.image == img.image }) return
            images.add(0, img)
            while (images.size > MAX_IMAGES) images.removeAt(images.size - 1)
            markDirty()
        }
    }

    /** Images from pages matching the query terms. */
    fun imageSearch(query: String, limit: Int = 60): List<ImageResult> {
        val terms = Index.tokenize(query)
        if (terms.isEmpty()) return emptyList()
        val pageScores = HashMap<String, Int>()
        for (p in Index.search(Index.parseQuery(query), limit = 50)) {
            pageScores[p.url] = (p.score * 10).toInt()
        }
        return synchronized(lock) {
            images.map { img ->
                val alt = img.title.lowercase()
                img to (terms.count { alt.contains(it) || img.url.lowercase().contains(it) } * 4 +
                    (pageScores[img.url] ?: 0))
            }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }
                .take(limit)
                .map { it.first }
        }
    }

    // ------------------------------------------------------------------ seeds

    fun seedsList(): List<String> = synchronized(lock) { seeds.toList() }

    fun addSeed(url: String): Boolean = synchronized(lock) {
        val u = Text.normUrl(url.trim())
        if (!u.startsWith("http") || seeds.contains(u)) return false
        seeds.add(u); markDirty(); true
    }

    fun removeSeed(url: String) = synchronized(lock) { seeds.remove(url); markDirty() }

    fun resetSeeds() = synchronized(lock) {
        seeds.clear(); seeds.addAll(DEFAULT_SEEDS); markDirty()
    }

    // --------------------------------------------------------------- settings

    fun autoIndex(): Boolean = synchronized(lock) { autoIndexBrowse }

    fun setAutoIndex(v: Boolean) = synchronized(lock) { autoIndexBrowse = v; markDirty() }

    // ------------------------------------------------------------------ reset

    fun clearIndex() {
        Index.clear()
        synchronized(lock) { images.clear(); markDirty(); persist() }
    }
}
