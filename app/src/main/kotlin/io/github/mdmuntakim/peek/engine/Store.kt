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
        "https://developer.mozilla.org/en-US/docs/Web/JavaScript",
        "https://developer.mozilla.org/en-US/docs/Web/HTML",
        "https://developer.mozilla.org/en-US/docs/Web/CSS",
        "https://docs.python.org/3/tutorial/",
        "https://doc.rust-lang.org/book/",
        "https://www.gutenberg.org",
        "https://www.gutenberg.org/ebooks/1342",
        "https://www.gutenberg.org/ebooks/11",
        "https://www.gutenberg.org/ebooks/84",
        "https://openlibrary.org",
        "https://arxiv.org",
        "https://www.python.org",
        "https://www.rust-lang.org",
        "https://nodejs.org/en/docs",
        "https://go.dev/doc/",
        "https://kotlinlang.org/docs/home.html",
        "https://developer.android.com",
        "https://www.w3.org",
        "https://whatwg.org",
        "https://www.ietf.org",
        "https://en.wikipedia.org/wiki/Science",
        "https://en.wikipedia.org/wiki/Technology",
        "https://en.wikipedia.org/wiki/Mathematics",
        "https://en.wikipedia.org/wiki/Physics",
        "https://en.wikipedia.org/wiki/Chemistry",
        "https://en.wikipedia.org/wiki/Biology",
        "https://en.wikipedia.org/wiki/Computer_science",
        "https://en.wikipedia.org/wiki/Artificial_intelligence",
        "https://en.wikipedia.org/wiki/Machine_learning",
        "https://en.wikipedia.org/wiki/Internet",
        "https://en.wikipedia.org/wiki/World_Wide_Web",
        "https://en.wikipedia.org/wiki/Search_engine",
        "https://en.wikipedia.org/wiki/Database",
        "https://en.wikipedia.org/wiki/Operating_system",
        "https://en.wikipedia.org/wiki/Linux",
        "https://en.wikipedia.org/wiki/Android_(operating_system)",
        "https://en.wikipedia.org/wiki/Programming_language",
        "https://en.wikipedia.org/wiki/Java_(programming_language)",
        "https://en.wikipedia.org/wiki/Kotlin_(programming_language)",
        "https://en.wikipedia.org/wiki/Python_(programming_language)",
        "https://en.wikipedia.org/wiki/C_(programming_language)",
        "https://en.wikipedia.org/wiki/C++",
        "https://en.wikipedia.org/wiki/Rust_(programming_language)",
        "https://en.wikipedia.org/wiki/Go_(programming_language)",
        "https://en.wikipedia.org/wiki/JavaScript",
        "https://en.wikipedia.org/wiki/TypeScript",
        "https://en.wikipedia.org/wiki/HTML",
        "https://en.wikipedia.org/wiki/CSS",
        "https://en.wikipedia.org/wiki/HTTP",
        "https://en.wikipedia.org/wiki/HTTPS",
        "https://en.wikipedia.org/wiki/TLS",
        "https://en.wikipedia.org/wiki/Cryptography",
        "https://en.wikipedia.org/wiki/Network_protocol",
        "https://en.wikipedia.org/wiki/TCP/IP",
        "https://en.wikipedia.org/wiki/DNS",
        "https://en.wikipedia.org/wiki/IP_address",
        "https://en.wikipedia.org/wiki/IPv4",
        "https://en.wikipedia.org/wiki/IPv6",
        "https://en.wikipedia.org/wiki/Router_(computing)",
        "https://en.wikipedia.org/wiki/Web_browser",
        "https://en.wikipedia.org/wiki/Web_server",
        "https://en.wikipedia.org/wiki/Cloud_computing",
        "https://en.wikipedia.org/wiki/Virtual_machine",
        "https://en.wikipedia.org/wiki/Container_(virtualization)",
        "https://en.wikipedia.org/wiki/Docker_(software)",
        "https://en.wikipedia.org/wiki/Kubernetes",
        "https://en.wikipedia.org/wiki/Git",
        "https://en.wikipedia.org/wiki/GitHub",
        "https://en.wikipedia.org/wiki/Open_source",
        "https://en.wikipedia.org/wiki/Free_software",
        "https://en.wikipedia.org/wiki/GNU",
        "https://en.wikipedia.org/wiki/Linux_kernel",
        "https://en.wikipedia.org/wiki/Unix",
        "https://en.wikipedia.org/wiki/Shell_(computing)",
        "https://en.wikipedia.org/wiki/Command-line_interface",
        "https://en.wikipedia.org/wiki/Text_editor",
        "https://en.wikipedia.org/wiki/Compiler",
        "https://en.wikipedia.org/wiki/Interpreter_(computing)",
        "https://en.wikipedia.org/wiki/Algorithm",
        "https://en.wikipedia.org/wiki/Data_structure",
        "https://en.wikipedia.org/wiki/Sorting_algorithm",
        "https://en.wikipedia.org/wiki/Big_O_notation",
        "https://en.wikipedia.org/wiki/Recursion",
        "https://en.wikipedia.org/wiki/Object-oriented_programming",
        "https://en.wikipedia.org/wiki/Functional_programming",
        "https://en.wikipedia.org/wiki/Concurrency_(computer_science)",
        "https://en.wikipedia.org/wiki/Parallel_computing",
        "https://en.wikipedia.org/wiki/Distributed_computing",
        "https://en.wikipedia.org/wiki/Computer_network",
        "https://en.wikipedia.org/wiki/Cybersecurity",
        "https://en.wikipedia.org/wiki/Malware",
        "https://en.wikipedia.org/wiki/Phishing",
        "https://en.wikipedia.org/wiki/Encryption",
        "https://en.wikipedia.org/wiki/Hash_function",
        "https://en.wikipedia.org/wiki/Public-key_cryptography",
        "https://en.wikipedia.org/wiki/Blockchain",
        "https://en.wikipedia.org/wiki/Bitcoin",
        "https://en.wikipedia.org/wiki/Ethereum",
        "https://en.wikipedia.org/wiki/Quantum_computing",
        "https://en.wikipedia.org/wiki/Quantum_mechanics",
        "https://en.wikipedia.org/wiki/Relativity",
        "https://en.wikipedia.org/wiki/Thermodynamics",
        "https://en.wikipedia.org/wiki/Electromagnetism",
        "https://en.wikipedia.org/wiki/Optics",
        "https://en.wikipedia.org/wiki/Astronomy",
        "https://en.wikipedia.org/wiki/Solar_System",
        "https://en.wikipedia.org/wiki/Earth",
        "https://en.wikipedia.org/wiki/Moon",
        "https://en.wikipedia.org/wiki/Mars",
        "https://en.wikipedia.org/wiki/Jupiter",
        "https://en.wikipedia.org/wiki/Saturn",
        "https://en.wikipedia.org/wiki/Sun",
        "https://en.wikipedia.org/wiki/Star",
        "https://en.wikipedia.org/wiki/Galaxy",
        "https://en.wikipedia.org/wiki/Black_hole",
        "https://en.wikipedia.org/wiki/Big_Bang",
        "https://en.wikipedia.org/wiki/Evolution",
        "https://en.wikipedia.org/wiki/Natural_selection",
        "https://en.wikipedia.org/wiki/Genetics",
        "https://en.wikipedia.org/wiki/DNA",
        "https://en.wikipedia.org/wiki/RNA",
        "https://en.wikipedia.org/wiki/Protein",
        "https://en.wikipedia.org/wiki/Cell_(biology)",
        "https://en.wikipedia.org/wiki/Human_body",
        "https://en.wikipedia.org/wiki/Brain",
        "https://en.wikipedia.org/wiki/Heart",
        "https://en.wikipedia.org/wiki/Immune_system",
        "https://en.wikipedia.org/wiki/Medicine",
        "https://en.wikipedia.org/wiki/Vaccine",
        "https://en.wikipedia.org/wiki/Antibiotic",
        "https://en.wikipedia.org/wiki/Disease",
        "https://en.wikipedia.org/wiki/Cancer",
        "https://en.wikipedia.org/wiki/Diabetes",
        "https://en.wikipedia.org/wiki/COVID-19",
        "https://en.wikipedia.org/wiki/History",
        "https://en.wikipedia.org/wiki/Ancient_history",
        "https://en.wikipedia.org/wiki/Middle_Ages",
        "https://en.wikipedia.org/wiki/Renaissance",
        "https://en.wikipedia.org/wiki/Industrial_Revolution",
        "https://en.wikipedia.org/wiki/World_War_I",
        "https://en.wikipedia.org/wiki/World_War_II",
        "https://en.wikipedia.org/wiki/Cold_War",
        "https://en.wikipedia.org/wiki/French_Revolution",
        "https://en.wikipedia.org/wiki/American_Revolution",
        "https://en.wikipedia.org/wiki/Roman_Empire",
        "https://en.wikipedia.org/wiki/Ancient_Greece",
        "https://en.wikipedia.org/wiki/Ancient_Egypt",
        "https://en.wikipedia.org/wiki/Ancient_China",
        "https://en.wikipedia.org/wiki/Medieval_Europe",
        "https://en.wikipedia.org/wiki/Geography",
        "https://en.wikipedia.org/wiki/Continent",
        "https://en.wikipedia.org/wiki/Ocean",
        "https://en.wikipedia.org/wiki/Mountain",
        "https://en.wikipedia.org/wiki/River",
        "https://en.wikipedia.org/wiki/Climate",
        "https://en.wikipedia.org/wiki/Weather",
        "https://en.wikipedia.org/wiki/Ecosystem",
        "https://en.wikipedia.org/wiki/Biodiversity",
        "https://en.wikipedia.org/wiki/Environment",
        "https://en.wikipedia.org/wiki/Pollution",
        "https://en.wikipedia.org/wiki/Climate_change",
        "https://en.wikipedia.org/wiki/Renewable_energy",
        "https://en.wikipedia.org/wiki/Solar_energy",
        "https://en.wikipedia.org/wiki/Wind_power",
        "https://en.wikipedia.org/wiki/Nuclear_power",
        "https://en.wikipedia.org/wiki/Fossil_fuel",
        "https://en.wikipedia.org/wiki/Economics",
        "https://en.wikipedia.org/wiki/Supply_and_demand",
        "https://en.wikipedia.org/wiki/Inflation",
        "https://en.wikipedia.org/wiki/Gross_domestic_product",
        "https://en.wikipedia.org/wiki/Stock_market",
        "https://en.wikipedia.org/wiki/Bank",
        "https://en.wikipedia.org/wiki/Money",
        "https://en.wikipedia.org/wiki/Currency",
        "https://en.wikipedia.org/wiki/Trade",
        "https://en.wikipedia.org/wiki/Globalization",
        "https://en.wikipedia.org/wiki/Politics",
        "https://en.wikipedia.org/wiki/Democracy",
        "https://en.wikipedia.org/wiki/Republic",
        "https://en.wikipedia.org/wiki/Parliament",
        "https://en.wikipedia.org/wiki/Election",
        "https://en.wikipedia.org/wiki/Human_rights",
        "https://en.wikipedia.org/wiki/Law",
        "https://en.wikipedia.org/wiki/Constitution",
        "https://en.wikipedia.org/wiki/Education",
        "https://en.wikipedia.org/wiki/Literacy",
        "https://en.wikipedia.org/wiki/University",
        "https://en.wikipedia.org/wiki/School",
        "https://en.wikipedia.org/wiki/Mathematics_(continued)",
        "https://en.wikipedia.org/wiki/Algebra",
        "https://en.wikipedia.org/wiki/Geometry",
        "https://en.wikipedia.org/wiki/Calculus",
        "https://en.wikipedia.org/wiki/Statistics",
        "https://en.wikipedia.org/wiki/Probability",
        "https://en.wikipedia.org/wiki/Number_theory",
        "https://en.wikipedia.org/wiki/Prime_number",
        "https://en.wikipedia.org/wiki/Fibonacci_number",
        "https://en.wikipedia.org/wiki/Pi",
        "https://en.wikipedia.org/wiki/E_(mathematical_constant)",
        "https://en.wikipedia.org/wiki/Golden_ratio",
        "https://en.wikipedia.org/wiki/Literature",
        "https://en.wikipedia.org/wiki/Novel",
        "https://en.wikipedia.org/wiki/Poetry",
        "https://en.wikipedia.org/wiki/Shakespeare",
        "https://en.wikipedia.org/wiki/Homer",
        "https://en.wikipedia.org/wiki/Dante_Alighieri",
        "https://en.wikipedia.org/wiki/Philosophy",
        "https://en.wikipedia.org/wiki/Ethics",
        "https://en.wikipedia.org/wiki/Logic",
        "https://en.wikipedia.org/wiki/Metaphysics",
        "https://en.wikipedia.org/wiki/Epistemology",
        "https://en.wikipedia.org/wiki/Plato",
        "https://en.wikipedia.org/wiki/Aristotle",
        "https://en.wikipedia.org/wiki/Kant",
        "https://en.wikipedia.org/wiki/Nietzsche",
        "https://en.wikipedia.org/wiki/Art",
        "https://en.wikipedia.org/wiki/Painting",
        "https://en.wikipedia.org/wiki/Sculpture",
        "https://en.wikipedia.org/wiki/Music",
        "https://en.wikipedia.org/wiki/Architecture",
        "https://en.wikipedia.org/wiki/Photography",
        "https://en.wikipedia.org/wiki/Film",
        "https://en.wikipedia.org/wiki/Cinema",
        "https://en.wikipedia.org/wiki/Theatre",
        "https://en.wikipedia.org/wiki/Dance",
        "https://en.wikipedia.org/wiki/Sports",
        "https://en.wikipedia.org/wiki/Football",
        "https://en.wikipedia.org/wiki/Basketball",
        "https://en.wikipedia.org/wiki/Cricket",
        "https://en.wikipedia.org/wiki/Tennis",
        "https://en.wikipedia.org/wiki/Olympic_Games",
        "https://en.wikipedia.org/wiki/Chess",
        "https://en.wikipedia.org/wiki/Psychology",
        "https://en.wikipedia.org/wiki/Cognition",
        "https://en.wikipedia.org/wiki/Memory",
        "https://en.wikipedia.org/wiki/Learning",
        "https://en.wikipedia.org/wiki/Behavior",
        "https://en.wikipedia.org/wiki/Sociology",
        "https://en.wikipedia.org/wiki/Culture",
        "https://en.wikipedia.org/wiki/Religion",
        "https://en.wikipedia.org/wiki/Mythology",
        "https://en.wikipedia.org/wiki/Language",
        "https://en.wikipedia.org/wiki/Linguistics",
        "https://en.wikipedia.org/wiki/Grammar",
        "https://en.wikipedia.org/wiki/Writing",
        "https://en.wikipedia.org/wiki/Alphabet",

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
