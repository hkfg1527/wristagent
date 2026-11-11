package com.example.myapplication.agent.mcp

/**
 * MCP 链路专用的最小 JSON 读写器。
 *
 * 不用 org.json 的原因是可验证性，不是偏好：单测跑在 AGP 的 mockable android.jar 上，
 * org.json 的方法体是 stub（testOptions.unitTests.isReturnDefaultValues 之后一律返回 null/0/false），
 * 协议解析在单测里跑不出任何真实结果。仓库既有的 SessionCodec / SelfDefinedToolCodec 同样手写编解码。
 * 于是 MCP 全链路的 JSON 只经过这里，org.json 只出现在最后一步把远端 inputSchema
 * 搬进 ToolDefinition.parametersSchema（见 [McpJsonBridge]）。
 */
sealed class McpJson {

    fun field(name: String): McpJson? = (this as? Obj)?.fields?.get(name)

    fun stringValue(): String? = (this as? Str)?.value

    fun numberValue(): Double? = when (this) {
        is Num -> value
        is Str -> value.toDoubleOrNull()
        else -> null
    }

    fun intValue(): Int? = numberValue()?.toInt()

    fun boolValue(): Boolean? = when (this) {
        is Bool -> value
        is Str -> value.toBooleanStrictOrNull()
        else -> null
    }

    fun objectFields(): Map<String, McpJson> = (this as? Obj)?.fields ?: emptyMap()

    fun arrayItems(): List<McpJson> = (this as? Arr)?.items ?: emptyList()

    data class Obj(val fields: Map<String, McpJson>) : McpJson()

    data class Arr(val items: List<McpJson>) : McpJson()

    data class Str(val value: String) : McpJson()

    data class Num(val value: Double) : McpJson()

    data class Bool(val value: Boolean) : McpJson()

    object Null : McpJson()

    class McpJsonException(message: String) : Exception(message)

    companion object {

        fun obj(vararg entries: Pair<String, McpJson>): Obj = Obj(linkedMapOf(*entries))

        fun write(value: McpJson): String {
            val builder = StringBuilder()
            writeTo(builder, value)
            return builder.toString()
        }

        fun parse(raw: String): McpJson = Parser(raw).parseDocument()

        private fun writeTo(builder: StringBuilder, value: McpJson) {
            when (value) {
                is Obj -> {
                    builder.append('{')
                    var first = true
                    value.fields.forEach { entry ->
                        if (!first) builder.append(',')
                        first = false
                        writeString(builder, entry.key)
                        builder.append(':')
                        writeTo(builder, entry.value)
                    }
                    builder.append('}')
                }

                is Arr -> {
                    builder.append('[')
                    var first = true
                    value.items.forEach { item ->
                        if (!first) builder.append(',')
                        first = false
                        writeTo(builder, item)
                    }
                    builder.append(']')
                }

                is Str -> writeString(builder, value.value)

                is Num -> {
                    val number = value.value
                    if (number.isFinite() && number == Math.floor(number) && Math.abs(number) < 1e15) {
                        builder.append(number.toLong().toString())
                    } else {
                        builder.append(number.toString())
                    }
                }

                is Bool -> builder.append(if (value.value) "true" else "false")

                Null -> builder.append("null")
            }
        }

        private fun writeString(builder: StringBuilder, text: String) {
            builder.append('"')
            for (char in text) {
                when (char) {
                    '"' -> builder.append("\\\"")
                    '\\' -> builder.append("\\\\")
                    '\n' -> builder.append("\\n")
                    '\r' -> builder.append("\\r")
                    '\t' -> builder.append("\\t")
                    '\b' -> builder.append("\\b")
                    '\u000C' -> builder.append("\\f")
                    else -> {
                        if (char < ' ') {
                            builder.append("\\u")
                            val code = char.code.toString(16)
                            repeat(4 - code.length) { builder.append('0') }
                            builder.append(code)
                        } else {
                            builder.append(char)
                        }
                    }
                }
            }
            builder.append('"')
        }

        private class Parser(private val text: String) {

            private var index = 0

            fun parseDocument(): McpJson {
                skipWhitespace()
                val value = parseValue()
                skipWhitespace()
                if (index != text.length) fail("JSON 末尾存在多余字符")
                return value
            }

            private fun parseValue(): McpJson {
                if (index >= text.length) fail("JSON 意外结束")
                return when (val char = text[index]) {
                    '{' -> parseObject()
                    '[' -> parseArray()
                    '"' -> Str(parseString())
                    't' -> parseLiteral("true", Bool(true))
                    'f' -> parseLiteral("false", Bool(false))
                    'n' -> parseLiteral("null", Null)
                    else -> {
                        if (char == '-' || char in '0'..'9') parseNumber() else fail("无法识别的 JSON 值起始字符 [" + char + "]")
                    }
                }
            }

            private fun parseObject(): Obj {
                index += 1
                skipWhitespace()
                val fields = linkedMapOf<String, McpJson>()
                if (peek() == '}') {
                    index += 1
                    return Obj(fields)
                }
                while (true) {
                    skipWhitespace()
                    if (peek() != '"') fail("JSON 对象的键必须是字符串")
                    val key = parseString()
                    skipWhitespace()
                    if (peek() != ':') fail("JSON 对象的键后面必须跟冒号")
                    index += 1
                    skipWhitespace()
                    fields[key] = parseValue()
                    skipWhitespace()
                    when (peek()) {
                        ',' -> index += 1
                        '}' -> {
                            index += 1
                            return Obj(fields)
                        }

                        else -> fail("JSON 对象缺少逗号或右花括号")
                    }
                }
            }

            private fun parseArray(): Arr {
                index += 1
                skipWhitespace()
                val items = mutableListOf<McpJson>()
                if (peek() == ']') {
                    index += 1
                    return Arr(items)
                }
                while (true) {
                    skipWhitespace()
                    items.add(parseValue())
                    skipWhitespace()
                    when (peek()) {
                        ',' -> index += 1
                        ']' -> {
                            index += 1
                            return Arr(items)
                        }

                        else -> fail("JSON 数组缺少逗号或右方括号")
                    }
                }
            }

            private fun parseString(): String {
                if (peek() != '"') fail("字符串必须以引号开头")
                index += 1
                val builder = StringBuilder()
                while (index < text.length) {
                    val char = text[index]
                    index += 1
                    when (char) {
                        '"' -> return builder.toString()
                        '\\' -> {
                            if (index >= text.length) fail("转义符后缺少字符")
                            val escaped = text[index]
                            index += 1
                            when (escaped) {
                                '"' -> builder.append('"')
                                '\\' -> builder.append('\\')
                                '/' -> builder.append('/')
                                'b' -> builder.append('\b')
                                'f' -> builder.append('\u000C')
                                'n' -> builder.append('\n')
                                'r' -> builder.append('\r')
                                't' -> builder.append('\t')
                                'u' -> {
                                    if (index + 4 > text.length) fail("\\u 转义缺少 4 位十六进制")
                                    val code = text.substring(index, index + 4).toIntOrNull(16)
                                        ?: fail("\\u 转义不是十六进制")
                                    index += 4
                                    builder.append(code.toChar())
                                }

                                else -> fail("不支持的转义字符 [" + escaped + "]")
                            }
                        }

                        else -> builder.append(char)
                    }
                }
                fail("字符串没有闭合")
            }

            private fun parseNumber(): Num {
                val start = index
                while (index < text.length) {
                    val char = text[index]
                    val allowed = char in '0'..'9' || char == '-' || char == '+' || char == '.' || char == 'e' || char == 'E'
                    if (!allowed) break
                    index += 1
                }
                val literal = text.substring(start, index)
                val number = literal.toDoubleOrNull() ?: fail("不是合法数字 [" + literal + "]")
                return Num(number)
            }

            private fun parseLiteral(literal: String, value: McpJson): McpJson {
                if (!text.startsWith(literal, index)) fail("无法识别的字面量")
                index += literal.length
                return value
            }

            private fun skipWhitespace() {
                while (index < text.length) {
                    val char = text[index]
                    if (char == ' ' || char == '\t' || char == '\n' || char == '\r') index += 1 else break
                }
            }

            private fun peek(): Char {
                if (index >= text.length) fail("JSON 意外结束")
                return text[index]
            }

            private fun fail(message: String): Nothing =
                throw McpJsonException(message + "（位置 " + index + "）")
        }
    }
}
