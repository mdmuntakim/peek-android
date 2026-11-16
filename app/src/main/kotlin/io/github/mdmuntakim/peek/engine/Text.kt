package io.github.mdmuntakim.peek.engine

import java.net.URI

/** Text helpers -- the Kotlin port of lib/util.js. */
object Text {

    val UA = Http.UA

    private val NAMED = mapOf(
        "&amp;" to "&", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&apos;" to "'",
        "&#39;" to "'", "&#x27;" to "'", "&#x2F;" to "/", "&nbsp;" to " ",
        "&hellip;" to "…", "&mdash;" to "—", "&ndash;" to "–",
        "&ldquo;" to "“", "&rdquo;" to "”", "&lsquo;" to "‘", "&rsquo;" to "’",
        "&copy;" to "©", "&reg;" to "®", "&trade;" to "™", "&lrm;" to "", "&rlm;" to "",
    )

    private val HEX_ENTITY = Regex("&#x([0-9a-fA-F]+);")
    private val DEC_ENTITY = Regex("&#(\\d+);")
    private val NAMED_ENTITY = Regex("&(amp|lt|gt|quot|apos|nbsp|hellip|mdash|ndash|ldquo|rdquo|lsquo|rsquo|copy|reg|trade|lrm|rlm);")

    /** Decode HTML entities including numeric references. */
    fun decodeEntities(s0: String?): String {
        if (s0.isNullOrEmpty()) return ""
        var s = s0
        s = HEX_ENTITY.replace(s) { m -> Character.toChars(m.groupValues[1].toInt(16)).concatToString() }
        s = DEC_ENTITY.replace(s) { m -> Character.toChars(m.groupValues[1].toInt()).concatToString() }
        s = NAMED_ENTITY.replace(s) { m -> NAMED["&${m.groupValues[1]};"] ?: m.value }
        return s
    }

    private val SCRIPT_STYLE = Regex("<(script|style)[\\s\\S]*?</\\1>", RegexOption.IGNORE_CASE)
    private val ANY_TAG = Regex("<[^>]+>")

    /** Remove HTML tags (script/style bodies included). */
    fun stripTags(s: String): String =
        ANY_TAG.replace(SCRIPT_STYLE.replace(s, " "), " ")

    /** Tags + entities + whitespace -> plain text. */
    fun textOf(s: String?): String = decodeEntities(stripTags(s ?: "")).replace(Regex("\\s+"), " ").trim()

    /** Hostname without a leading "www.". */
    fun domainOf(url: String): String = try {
        URI(url).host?.lowercase()?.removePrefix("www.") ?: ""
    } catch (_: Exception) {
        try {
            // tolerate scheme-relative and malformed URLs
            val host = Regex("^(?:https?:)?//([^/?#]+)").find(url)?.groupValues?.get(1) ?: ""
            host.substringBefore(':').lowercase().removePrefix("www.")
        } catch (_: Exception) { "" }
    }

    /** Strip tracking params and fragments so duplicate URLs collapse. */
    fun normUrl(u: String): String = try {
        val uri = URI(u)
        val host = uri.host?.lowercase()
        val path = uri.path?.trimEnd('/')
        val rebuilt = StringBuilder()
        rebuilt.append(uri.scheme?.lowercase() ?: "https").append("://")
        if (host != null) {
            val userInfo = uri.userInfo
            if (userInfo != null) rebuilt.append(userInfo).append('@')
            rebuilt.append(host)
            if (uri.port != -1 && uri.port != defaultPort(uri.scheme)) rebuilt.append(':').append(uri.port)
        }
        if (path != null) rebuilt.append(path)
        val out = rebuilt.toString()
        if (out.startsWith("http")) out else u
    } catch (_: Exception) {
        u
    }

    private fun defaultPort(scheme: String?) = when (scheme?.lowercase()) {
        "http" -> 80
        "https" -> 443
        else -> -1
    }

    val STOPWORDS: Set<String> = setOf(
        "a", "an", "and", "are", "as", "at", "be", "but", "by", "can", "could", "did", "do", "does", "for",
        "from", "had", "has", "have", "how", "i", "in", "into", "is", "it", "its", "like", "me", "more",
        "most", "my", "no", "not", "of", "on", "or", "our", "should", "so", "than", "that", "the", "then",
        "there", "these", "this", "to", "under", "up", "was", "we", "were", "what", "when", "where", "which",
        "who", "why", "will", "with", "would", "you", "your", "vs", "versus", "about",
    )

    /** Content words of a query, lowercased, stop words removed. */
    fun keyTerms(q: String): List<String> =
        q.lowercase()
            .replace(Regex("[^a-z0-9\\s'-]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() && it !in STOPWORDS && it.length > 1 }

    private val SENTENCE_BOUNDARY = Regex("([.!?])\\s+(?=[A-Z0-9\"“'(])")

    /** Split text into sentences on ., !, ? boundaries. */
    fun splitSentences(text0: String): List<String> {
        val t = text0.replace(Regex("\\s+"), " ").trim()
        if (t.isEmpty()) return emptyList()
        val marked = SENTENCE_BOUNDARY.replace(t, "$1\u0001")
        return marked.split("\u0001").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun escapeRegExp(s: String): String = Regex.escape(s)
}
