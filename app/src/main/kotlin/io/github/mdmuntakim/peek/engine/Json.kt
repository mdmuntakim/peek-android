package io.github.mdmuntakim.peek.engine

/**
 * Minimal JSON parser -- mirrors the zero-dependency ethos of the Peek engine.
 * Returns Map<String, Any?>, List<Any?>, String, Double, Boolean or null.
 */
object Json {

    fun parse(text: String): Any? {
        val p = Parser(text)
        val value = p.parseValue()
        p.skipWs()
        return value
    }

    fun obj(value: Any?): Map<String, Any?>? = value as? Map<String, Any?>
    fun arr(value: Any?): List<Any?>? = value as? List<Any?>
    fun str(value: Any?): String? = value as? String
    fun num(value: Any?): Double? = value as? Double ?: (value as? Long)?.toDouble() ?: (value as? Int)?.toDouble()

    /** Minimal JSON string escaper for the snapshot writer in Store. */
    fun escape(s: String): String {
        val out = StringBuilder(s.length + 16)
        for (c in s) {
            when (c) {
                '\\' -> out.append("\\\\")
                '"' -> out.append("\\\"")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\u000c")
                else -> if (c < ' ') out.append("\\u%04x".format(c.code)) else out.append(c)
            }
        }
        return out.toString()
    }

    private class Parser(private val s: String) {
        private var i = 0

        fun skipWs() {
            while (i < s.length && s[i].let { it == ' ' || it == '\t' || it == '\n' || it == '\r' }) i++
        }

        fun parseValue(): Any? {
            skipWs()
            if (i >= s.length) return null
            return when (s[i]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> { expect("true"); true }
                'f' -> { expect("false"); false }
                'n' -> { expect("null"); null }
                else -> parseNumber()
            }
        }

        private fun expect(word: String) {
            if (!s.startsWith(word, i)) throw IllegalArgumentException("Bad JSON at $i")
            i += word.length
        }

        private fun parseObject(): Map<String, Any?> {
            val out = HashMap<String, Any?>()
            i++ // {
            skipWs()
            if (i < s.length && s[i] == '}') { i++; return out }
            while (true) {
                skipWs()
                val key = parseString()
                skipWs()
                if (i >= s.length || s[i] != ':') throw IllegalArgumentException("Expected : at $i")
                i++
                out[key] = parseValue()
                skipWs()
                if (i >= s.length) throw IllegalArgumentException("Unterminated object")
                when (s[i]) {
                    ',' -> i++
                    '}' -> { i++; return out }
                    else -> throw IllegalArgumentException("Expected , or } at $i")
                }
            }
        }

        private fun parseArray(): List<Any?> {
            val out = ArrayList<Any?>()
            i++ // [
            skipWs()
            if (i < s.length && s[i] == ']') { i++; return out }
            while (true) {
                out.add(parseValue())
                skipWs()
                if (i >= s.length) throw IllegalArgumentException("Unterminated array")
                when (s[i]) {
                    ',' -> i++
                    ']' -> { i++; return out }
                    else -> throw IllegalArgumentException("Expected , or ] at $i")
                }
            }
        }

        private fun parseString(): String {
            val sb = StringBuilder()
            i++ // opening quote
            while (true) {
                if (i >= s.length) throw IllegalArgumentException("Unterminated string")
                when (val c = s[i]) {
                    '"' -> { i++; return sb.toString() }
                    '\\' -> {
                        i++
                        if (i >= s.length) throw IllegalArgumentException("Bad escape")
                        when (val e = s[i]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 >= s.length) throw IllegalArgumentException("Bad unicode escape")
                                sb.append(s.substring(i + 1, i + 5).toInt(16).toChar())
                                i += 4
                            }
                            else -> throw IllegalArgumentException("Bad escape \\$e")
                        }
                        i++
                    }
                    else -> { sb.append(c); i++ }
                }
            }
        }

        private fun parseNumber(): Any {
            val start = i
            if (i < s.length && (s[i] == '-' || s[i] == '+')) i++
            var isDouble = false
            while (i < s.length) {
                val c = s[i]
                if (c.isDigit()) i++
                else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    if (c == '.' || c == 'e' || c == 'E') isDouble = true
                    i++
                } else break
            }
            val raw = s.substring(start, i)
            if (raw.isEmpty()) throw IllegalArgumentException("Bad number at $start")
            return if (isDouble) raw.toDouble() else (raw.toLongOrNull() ?: raw.toDouble())
        }
    }
}
