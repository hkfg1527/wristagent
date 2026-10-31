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
}
