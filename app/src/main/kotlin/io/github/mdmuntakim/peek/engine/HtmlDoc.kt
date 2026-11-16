package io.github.mdmuntakim.peek.engine

/**
 * htmldoc.kt -- PeekBot's HTML reader. Extracts the title, meta description,
 * headings, readable text, links and images out of a fetched page. Pure
 * regex-based extraction, no dependencies: what lib/search.js did for DDG
 * pages, this does for every page PeekBot crawls.
 */
object HtmlDoc {

    class Page(
        val title: String,
        val description: String,
        val headings: List<String>,
        val text: String,
        val links: List<String>,
        val images: List<Triple<String, String, String>>, // url, alt, page title
    )

    private val SCRIPT_STYLE_NOSCRIPT =
        Regex("<(script|style|noscript|template|svg|iframe|canvas|select|button)[\\s\\S]*?</\\1>", RegexOption.IGNORE_CASE)
    private val COMMENT = Regex("<!--[\\s\\S]*?-->")
    private val NAV_BLOCK =
        Regex("<(nav|header|footer|aside|form)[\\s\\S]*?</\\1>", RegexOption.IGNORE_CASE)
    private val ANY_TAG = Regex("<[^>]+>")

    private val TITLE = Regex("<title[^>]*>([\\s\\S]*?)</title>", RegexOption.IGNORE_CASE)
    private val META_DESC = Regex(
        "<meta[^>]+name=[\"'](?:description|og:description|twitter:description)[\"'][^>]*" +
            "content=[\"']([\\s\\S]*?)[\"']",
        RegexOption.IGNORE_CASE,
    )
    private val META_DESC2 = Regex(
        "<meta[^>]+content=[\"']([\\s\\S]*?)[\"'][^>]*name=[\"'](?:description)[\"']",
        RegexOption.IGNORE_CASE,
    )
    private val HEADING = Regex("<h[1-3][^>]*>([\\s\\S]*?)</h[1-3]>", RegexOption.IGNORE_CASE)
    private val ANCHOR = Regex("<a\\s[^>]*href=[\"']([^\"'#]+)[\"']", RegexOption.IGNORE_CASE)
    private val IMG = Regex("<img\\s[^>]*>", RegexOption.IGNORE_CASE)
    private val IMG_SRC = Regex("src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
    private val IMG_ALT = Regex("alt=[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
    private val ROBOTS_NOINDEX = Regex("<meta[^>]+name=[\"']robots[\"'][^>]*content=[\"'][^\"']*noindex", RegexOption.IGNORE_CASE)
    private val CANONICAL = Regex("<link[^>]+rel=[\"']canonical[\"'][^>]*href=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)

    private val SKIP_EXT = Regex(
        "\\.(png|jpe?g|gif|svg|webp|ico|css|js|mjs|json|xml|rss|atom|zip|gz|tar|rar|7z|" +
            "pdf|docx?|xlsx?|pptx?|mp[34]|ogg|wav|webm|mkv|mov|avi|woff2?|ttf|eot|apk|exe|dmg)$",
        RegexOption.IGNORE_CASE,
    )

    /** Parse raw HTML into a structured page. Never throws. */
    fun extract(html0: String, base: String): Page {
        val html = html0
        val title = Text.textOf(TITLE.find(html)?.groupValues?.get(1) ?: "")
        val description = Text.textOf(
            (META_DESC.find(html)?.groupValues?.get(1))
                ?: (META_DESC2.find(html)?.groupValues?.get(1)) ?: "",
        )
        val headings = HEADING.findAll(html).map { Text.textOf(it.groupValues[1]) }.filter { it.isNotEmpty() }.take(12).toList()

        // Readable text: drop non-content regions first, then tags.
        var body = COMMENT.replace(html, " ")
        body = SCRIPT_STYLE_NOSCRIPT.replace(body, " ")
        body = NAV_BLOCK.replace(body, " ")
        val text = Text.textOf(ANY_TAG.replace(body, " "))

        val links = ArrayList<String>()
        val seen = HashSet<String>()
        for (m in ANCHOR.findAll(html)) {
            val abs = absolutize(Text.decodeEntities(m.groupValues[1]).trim(), base) ?: continue
            val u = Text.normUrl(abs)
            if (!u.startsWith("http://") && !u.startsWith("https://")) continue
            if (SKIP_EXT.containsMatchIn(u.substringBefore('?').substringAfterLast('/'))) continue
            if (seen.add(u)) links.add(u)
            if (links.size >= 120) break
        }

        val images = ArrayList<Triple<String, String, String>>()
        val seenImg = HashSet<String>()
        for (tag in IMG.findAll(html).map { it.value }) {
            val raw = IMG_SRC.find(tag)?.groupValues?.get(1) ?: continue
            val abs = absolutize(Text.decodeEntities(raw).trim(), base) ?: continue
            if (!abs.startsWith("http")) continue
            if (abs.substringBefore('?').substringAfterLast('.').lowercase() !in
                listOf("png", "jpg", "jpeg", "gif", "webp")
            ) continue
            if (seenImg.add(abs)) {
                val alt = Text.decodeEntities(IMG_ALT.find(tag)?.groupValues?.get(1) ?: "").trim()
                images.add(Triple(abs, alt.take(120), title))
            }
            if (images.size >= 24) break
        }

        val canonical = CANONICAL.find(html)?.groupValues?.get(1)
        if (canonical != null && title.isEmpty()) {
            links.add(Text.normUrl(absolutize(canonical, base) ?: canonical))
        }
        return Page(title, description, headings, text, links, images)
    }

    /** Is this page worth indexing? (noindex pages and empty bodies are not) */
    fun indexable(html: String, text: String): Boolean =
        !ROBOTS_NOINDEX.containsMatchIn(html) && text.length >= 80

    /** Resolve href/src against the page URL; returns null for non-http targets. */
    fun absolutize(href: String, base: String): String? {
        if (href.isEmpty() || href.startsWith("data:") || href.startsWith("javascript:")) return null
        if (href.startsWith("http://") || href.startsWith("https://")) return href
        if (href.startsWith("//")) return "https:$href"
        return try {
            java.net.URL(java.net.URL(base), href.replace(" ", "%20")).toString()
        } catch (_: Exception) {
            null
        }
    }
}
