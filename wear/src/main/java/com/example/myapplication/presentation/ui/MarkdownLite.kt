package com.example.myapplication.presentation.ui

/**
 * 极简 Markdown 清洗：
 * 只剥离成对出现的强调标记与反引号，未配对的标记（数学乘号、snake_case、流式半截标记）原样保留。
 * 实现刻意避开"一整条正则删掉所有星号"的做法：先扫描标记游程，再只删除成对的那几个字符。
 */

private val MARKER_CHARS = listOf('*', '_', '`')

/** 整行就是代码围栏（含可选语言标识）时，该行不是正文。 */
private val fenceLineRegex = Regex("^```[A-Za-z0-9_+#.-]*$")

/** 列表符号前缀（- * + 与 1. 这类），必须原样保留，且不参与强调配对。 */
private val listMarkerRegex = Regex("^[ \t]{0,4}([-*+]|[0-9]{1,3}\\.)[ \t]")

/**
 * 清洗一行内的成对强调标记，不会删除任何未配对的标记字符。
 *
 * @param hideUnclosedTail 流式渲染时置 true：末行尚未闭合的标记暂时隐藏，
 *        保证逐字增长时可见文本单调增长（先出现 `**`，再出现正文，而不是闪一下标记）。
 */
fun stripEmphasisMarkers(raw: String, hideUnclosedTail: Boolean = false): String {
    if (raw.isEmpty()) return raw
    val lines = raw.split('\n')
    val lastIndex = lines.lastIndex
    return lines.mapIndexed { index, line ->
        stripLine(line, streaming = hideUnclosedTail && index == lastIndex)
    }.joinToString("\n")
}

/** 一个连续标记游程，例如 `**` 是长度 2 的星号游程。 */
private class MarkerRun(
    val start: Int,
    val length: Int,
    val char: Char,
    val opens: Boolean,
    val closes: Boolean
)

private fun stripLine(line: String, streaming: Boolean): String {
    if (line.isEmpty()) return line
    if (fenceLineRegex.matches(line.trim())) return ""
    // 整行只有标记字符（流式半截标记）时整行先隐藏
    if (streaming && line.none { it.isContentChar() }) return ""

    val prefix = listMarkerRegex.find(line)?.value ?: ""
    return prefix + stripPairedRuns(line.substring(prefix.length), streaming)
}

private fun Char.isContentChar(): Boolean =
    this != '*' && this != '_' && this != '`' && !isWhitespace()

private fun stripPairedRuns(text: String, streaming: Boolean): String {
    val runs = findMarkerRuns(text)
    if (runs.isEmpty()) return text
    val paired = BooleanArray(runs.size)

    // 同字符、同长度的游程按出现顺序两两配对：**a** 与 *b* 互不干扰
    for (marker in MARKER_CHARS) {
        val sameChar = runs.indices.filter { runs[it].char == marker }
        for (group in sameChar.groupBy { runs[it].length }.values) {
            var k = 0
            while (k + 1 < group.size) {
                val open = group[k]
                val close = group[k + 1]
                if (runs[open].opens && runs[close].closes) {
                    paired[open] = true
                    paired[close] = true
                    k += 2
                } else {
                    k += 1
                }
            }
        }
    }

    val builder = StringBuilder(text.length)
    var cursor = 0
    for ((index, run) in runs.withIndex()) {
        val drop = paired[index] || (streaming && !paired[index] && run.isUnclosedMarker())
        if (drop) {
            builder.append(text, cursor, run.start)
            cursor = run.start + run.length
        }
    }
    if (cursor == 0) return text
    builder.append(text, cursor, text.length)
    return builder.toString()
}

/** 未闭合标记：长度 >= 2 的双标记，或"后面还接着正文"的开启标记。 */
private fun MarkerRun.isUnclosedMarker(): Boolean = length >= 2 || opens

private fun findMarkerRuns(text: String): List<MarkerRun> {
    val runs = mutableListOf<MarkerRun>()
    var i = 0
    while (i < text.length) {
        val char = text[i]
        if (char != '*' && char != '_' && char != '`') {
            i++
            continue
        }
        var end = i
        while (end < text.length && text[end] == char) end++
        val prev = text.getOrNull(i - 1)
        val next = text.getOrNull(end)
        if (isMarkerRun(prev, next, char)) {
            runs.add(
                MarkerRun(
                    start = i,
                    length = end - i,
                    char = char,
                    opens = next != null && !next.isWhitespace(),
                    closes = prev != null && !prev.isWhitespace()
                )
            )
        }
        i = end
    }
    return runs
}

/** 判断一个标记游程是否可能承担强调作用，否则它只是正文里的普通字符。 */
private fun isMarkerRun(prev: Char?, next: Char?, char: Char): Boolean {
    // 2*3、2*3*4 这类乘号
    if (prev != null && next != null && prev.isDigit() && next.isDigit()) return false
    // my_config_file 这类词内下划线
    if (char == '_' && prev.isAsciiAlnum() && next.isAsciiAlnum()) return false
    return true
}

private fun Char?.isAsciiAlnum(): Boolean =
    this != null && (this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9')
