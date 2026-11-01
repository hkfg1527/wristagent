package com.example.myapplication.presentation

import com.example.myapplication.presentation.ui.stripEmphasisMarkers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Markdown 轻量清洗单元测试 (TDD 严格红绿循环)
 * 验证：
 * 1. 成对的 **粗体** / *斜体* / __粗体__ / _斜体_ / `行内代码` 标记被剥离
 * 2. 未配对的标记原样保留（数学乘号、snake_case、行尾半截标记）
 * 3. 换行与列表符号完整保留
 * 4. 流式模式下未闭合标记被暂时隐藏，且逐字增长时可见文本不回退（不闪烁）
 */
class MarkdownLiteTest {

    @Test
    fun stripEmphasisMarkers_pairedBold_removesDoubleAsterisks() {
        val raw = "现在是 **2026年10月31日 星期六 10:38**。"
        assertEquals("现在是 2026年10月31日 星期六 10:38。", stripEmphasisMarkers(raw))
    }

    @Test
    fun stripEmphasisMarkers_pairedDoubleUnderscore_removesMarkers() {
        assertEquals("重要提示：请充电", stripEmphasisMarkers("__重要提示__：请充电"))
    }

    @Test
    fun stripEmphasisMarkers_pairedItalic_removesSingleMarkers() {
        assertEquals("这是 重点 内容", stripEmphasisMarkers("这是 *重点* 内容"))
        assertEquals("这是 重点 内容", stripEmphasisMarkers("这是 _重点_ 内容"))
    }

    @Test
    fun stripEmphasisMarkers_inlineCode_removesBackticks() {
        assertEquals("执行 adb devices 命令", stripEmphasisMarkers("执行 `adb devices` 命令"))
    }

    @Test
    fun stripEmphasisMarkers_unpairedAsterisk_keptAsIs() {
        val raw = "配置 2 * 3 参数"
        assertEquals(raw, stripEmphasisMarkers(raw))
    }

    @Test
    fun stripEmphasisMarkers_unpairedTrailingMarker_keptWhenNotStreaming() {
        assertEquals("这是 *重点", stripEmphasisMarkers("这是 *重点"))
        assertEquals("现在是 **2026", stripEmphasisMarkers("现在是 **2026"))
    }

    @Test
    fun stripEmphasisMarkers_arithmeticAsterisks_neverEaten() {
        assertEquals("面积 = 2*3*4 平方", stripEmphasisMarkers("面积 = 2*3*4 平方"))
        // 带空格的乘号同样不能被当成一对强调标记吃掉
        assertEquals("2 * 3 * 4", stripEmphasisMarkers("2 * 3 * 4"))
        assertEquals("计算 2 * 3 的值", stripEmphasisMarkers("计算 2 * 3 的值"))
    }

    @Test
    fun stripEmphasisMarkers_snakeCaseUnderscores_keptAsIs() {
        val raw = "文件 my_config_file 保持不变"
        assertEquals(raw, stripEmphasisMarkers(raw))
    }

    @Test
    fun stripEmphasisMarkers_multiline_keepsNewlinesAndListMarkers() {
        val raw = "第一行\n\n**第二行**\n- 列表项一\n* 列表项二\n+ 列表项三\n1. 列表项四"
        val expected = "第一行\n\n第二行\n- 列表项一\n* 列表项二\n+ 列表项三\n1. 列表项四"
        assertEquals(expected, stripEmphasisMarkers(raw))
    }

    @Test
    fun stripEmphasisMarkers_emptyString_returnsEmpty() {
        assertEquals("", stripEmphasisMarkers(""))
    }

    @Test
    fun stripEmphasisMarkers_plainText_unchanged() {
        val raw = "今天天气晴朗 22℃，湿度 40%。"
        assertEquals(raw, stripEmphasisMarkers(raw))
    }

    @Test
    fun stripEmphasisMarkers_fencedCodeLines_dropped() {
        val raw = "```kotlin\nval a = 1\n```"
        assertEquals("\nval a = 1\n", stripEmphasisMarkers(raw))
    }

    @Test
    fun stripEmphasisMarkers_streaming_hidesUnclosedMarkers() {
        assertEquals("现在是 2026", stripEmphasisMarkers("现在是 **2026", hideUnclosedTail = true))
        assertEquals("这是 重点", stripEmphasisMarkers("这是 *重点", hideUnclosedTail = true))
        assertEquals("执行 ls", stripEmphasisMarkers("执行 `ls", hideUnclosedTail = true))
    }

    @Test
    fun stripEmphasisMarkers_streaming_visibleTextNeverShrinks() {
        val stages = listOf("*", "**", "**2", "**20", "**2026", "**2026**")
        var previous = ""
        for (stage in stages) {
            val visible = stripEmphasisMarkers(stage, hideUnclosedTail = true)
            assertTrue(
                "流式可见文本出现回退：'$previous' -> '$visible' (输入='$stage')",
                visible.startsWith(previous)
            )
            previous = visible
        }
        assertEquals("2026", previous)
    }

    @Test
    fun stripEmphasisMarkers_alreadyCleanStreamingText_unchanged() {
        val raw = "2026年10月31日 星期六"
        assertEquals(raw, stripEmphasisMarkers(raw, hideUnclosedTail = true))
    }

    // ============ 流式增量清洗器：必须与全量实现逐字一致（性能优化不得改变输出） ============

    @Test
    fun `增量清洗在逐字增长时与全量实现一致`() {
        val full = "**粗体** 与 `code` 以及 *斜体*，换行\n第二行 **未闭合"
        val stripper = com.example.myapplication.presentation.ui.StreamingMarkdownStripper()
        for (end in 1..full.length) {
            val partial = full.substring(0, end)
            assertEquals(
                "第 " + end + " 字符处结果不一致",
                stripEmphasisMarkers(partial, hideUnclosedTail = true),
                stripper.strip(partial, hideUnclosedTail = true)
            )
        }
    }

    @Test
    fun `增量清洗在多行追加时与全量实现一致`() {
        val stripper = com.example.myapplication.presentation.ui.StreamingMarkdownStripper()
        val lines = listOf("# 标题", "- 列表 **项**", "```", "code block", "```", "尾部 `x`")
        var acc = ""
        for (line in lines) {
            acc = if (acc.isEmpty()) line else acc + "\n" + line
            assertEquals(stripEmphasisMarkers(acc, hideUnclosedTail = false), stripper.strip(acc, hideUnclosedTail = false))
            assertEquals(stripEmphasisMarkers(acc, hideUnclosedTail = true), stripper.strip(acc, hideUnclosedTail = true))
        }
    }

    @Test
    fun `增量清洗在输入被替换时回退到全量结果`() {
        val stripper = com.example.myapplication.presentation.ui.StreamingMarkdownStripper()
        stripper.strip("第一版 **内容**", hideUnclosedTail = true)
        // 完全不同的新文本（例如切换会话）：前缀不匹配必须自行回退，而不是拼接旧结果
        val replaced = "完全不同的 *另一段*"
        assertEquals(stripEmphasisMarkers(replaced, hideUnclosedTail = true), stripper.strip(replaced, hideUnclosedTail = true))
        assertEquals(
            stripEmphasisMarkers("", hideUnclosedTail = true),
            stripper.strip("", hideUnclosedTail = true)
        )
    }
}
