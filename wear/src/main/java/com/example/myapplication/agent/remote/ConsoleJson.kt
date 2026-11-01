package com.example.myapplication.agent.remote

/**
 * 极简 JSON 编解码：值域只有 Map/List/String/Int/Double/Boolean/null。
 * 不用 org.json —— 它在本模块的 JVM 单测里被 android.jar 桩替换，方法一律返回默认值，无法断言。
 */
object ConsoleJson {

    private const val MAX_DEPTH = 32

    /** 解析失败或顶层不是对象时返回 null。 */
    fun parseObject(text: String): Map<String, Any?>? {
        @Suppress("UNCHECKED_CAST")
        return Parser(text).parseDocument() as? Map<String, Any?>
    }

    fun write(value: Any?): String {
        val builder = StringBuilder()
        writeValue(builder, value)
        return builder.toString()
    }

    private fun writeValue(builder: StringBuilder, value: Any?) {
        when (value) {
            null -> builder.append("null")
            is String -> writeString(builder, value)
            is Boolean -> builder.append(if (value) "true" else "false")
            is Int, is Long, is Short, is Byte -> builder.append(value.toString())
            is Float -> builder.append(if (value.isNaN() || value.isInfinite()) "null" else writeFloat(value))
            is Double -> builder.append(if (value.isNaN() || value.isInfinite()) "null" else writeDouble(value))
            is Map<*, *> -> {
                builder.append('{')
                var first = true
                for ((key, item) in value) {
                    if (!first) builder.append(',')
                    first = false
                    writeString(builder, key.toString())
                    builder.append(':')
                    writeValue(builder, item)
                }
                builder.append('}')
            }
            is Iterable<*> -> writeIterable(builder, value)
            is Array<*> -> writeIterable(builder, value.toList())
            else -> writeString(builder, value.toString())
        }
    }

    private fun writeIterable(builder: StringBuilder, items: Iterable<*>) {
        builder.append('[')
        var first = true
        for (item in items) {
            if (!first) builder.append(',')
            first = false
            writeValue(builder, item)
        }
        builder.append(']')
    }

    private fun writeFloat(value: Float): String =
        if (value == value.toLong().toFloat() && kotlin.math.abs(value) < 1e7f) {
            value.toLong().toString()
        } else {
            value.toString()
        }

    private fun writeDouble(value: Double): String =
        if (value == value.toLong().toDouble() && kotlin.math.abs(value) < 1e15) {
            value.toLong().toString()
        } else {
            value.toString()
        }

    private fun writeString(builder: StringBuilder, raw: String) {
        builder.append('"')
        for (ch in raw) {
            when {
                ch == '"' -> builder.append("\\\"")
                ch == '\\' -> builder.append("\\\\")
                ch == '\n' -> builder.append("\\n")
                ch == '\r' -> builder.append("\\r")
                ch == '\t' -> builder.append("\\t")
                ch == '\b' -> builder.append("\\b")
                ch == '\u000C' -> builder.append("\\f")
                ch < ' ' || ch == '\u007F' -> builder.append("\\u").append(ch.code.toString(16).padStart(4, '0'))
                else -> builder.append(ch)
            }
        }
        builder.append('"')
    }

    private class InvalidJson : RuntimeException()

    private class Parser(private val text: String) {

        private var pos = 0
        private var depth = 0

        fun parseDocument(): Any? = try {
            skipWhitespace()
            val value = readValue()
            skipWhitespace()
            if (pos == text.length) value else null
        } catch (_: InvalidJson) {
            null
        }

        private fun readValue(): Any? {
            if (pos >= text.length) throw InvalidJson()
            return when (text[pos]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't' -> {
                    expectWord("true")
                    true
                }
                'f' -> {
                    expectWord("false")
                    false
                }
                'n' -> {
                    expectWord("null")
                    null
                }
                else -> if (text[pos] == '-' || text[pos] in '0'..'9') readNumber() else throw InvalidJson()
            }
        }

        private fun readObject(): Map<String, Any?> {
            enter()
            pos++
            val result = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek() == '}') {
                pos++
                leave()
                return result
            }
            while (true) {
                skipWhitespace()
                if (peek() != '"') throw InvalidJson()
                val key = readString()
                skipWhitespace()
                if (peek() != ':') throw InvalidJson()
                pos++
                skipWhitespace()
                result[key] = readValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        leave()
                        return result
                    }
                    else -> throw InvalidJson()
                }
            }
        }

        private fun readArray(): List<Any?> {
            enter()
            pos++
            val result = ArrayList<Any?>()
            skipWhitespace()
            if (peek() == ']') {
                pos++
                leave()
                return result
            }
            while (true) {
                skipWhitespace()
                result.add(readValue())
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        leave()
                        return result
                    }
                    else -> throw InvalidJson()
                }
            }
        }

        private fun readString(): String {
            if (peek() != '"') throw InvalidJson()
            pos++
            val builder = StringBuilder()
            while (true) {
                if (pos >= text.length) throw InvalidJson()
                val ch = text[pos++]
                if (ch == '"') return builder.toString()
                if (ch != '\\') {
                    if (ch < ' ') throw InvalidJson()
                    builder.append(ch)
                    continue
                }
                if (pos >= text.length) throw InvalidJson()
                when (text[pos++]) {
                    '"' -> builder.append('"')
                    '\\' -> builder.append('\\')
                    '/' -> builder.append('/')
                    'b' -> builder.append('\b')
                    'f' -> builder.append('\u000C')
                    'n' -> builder.append('\n')
                    'r' -> builder.append('\r')
                    't' -> builder.append('\t')
                    'u' -> {
                        if (pos + 4 > text.length) throw InvalidJson()
                        val code = text.substring(pos, pos + 4).toIntOrNull(16) ?: throw InvalidJson()
                        builder.append(code.toChar())
                        pos += 4
                    }
                    else -> throw InvalidJson()
                }
            }
        }

        private fun readNumber(): Any {
            val start = pos
            if (peek() == '-') pos++
            while (pos < text.length && text[pos] in '0'..'9') pos++
            var fractional = false
            if (pos < text.length && text[pos] == '.') {
                fractional = true
                pos++
                while (pos < text.length && text[pos] in '0'..'9') pos++
            }
            if (pos < text.length && (text[pos] == 'e' || text[pos] == 'E')) {
                fractional = true
                pos++
                if (pos < text.length && (text[pos] == '+' || text[pos] == '-')) pos++
                while (pos < text.length && text[pos] in '0'..'9') pos++
            }
            val token = text.substring(start, pos)
            if (token.isEmpty() || token == "-") throw InvalidJson()
            if (!fractional) {
                val asInt = token.toIntOrNull()
                if (asInt != null) return asInt
            }
            return token.toDoubleOrNull() ?: throw InvalidJson()
        }

        private fun expectWord(word: String) {
            if (!text.startsWith(word, pos)) throw InvalidJson()
            pos += word.length
        }

        private fun skipWhitespace() {
            while (pos < text.length) {
                val ch = text[pos]
                if (ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r') pos++ else return
            }
        }

        private fun peek(): Char = if (pos < text.length) text[pos] else '\u0000'

        private fun enter() {
            depth++
            if (depth > MAX_DEPTH) throw InvalidJson()
        }

        private fun leave() {
            depth--
        }
    }
}
