package com.example.myapplication.presentation.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 只读行的两条纯逻辑：文案怎么拆、行到底能不能点。
 *
 * 这两个判定直接决定"看起来能点却没反应"这类问题会不会回来，所以用真实文案做夹具，
 * 不只测函数签名能跑通。
 */
class WebOnlyRowTest {

    private val fallback = "请在手机网页修改"

    @Test
    fun fullWidthColonSplitsLabelAndGuidance() {
        // 中文夹具：全角冒号切一刀
        val parts = webOnlyRowParts("角色卡：请在手机网页修改", fallback)
        assertEquals("角色卡", parts.label)
        assertEquals("请在手机网页修改", parts.guidance)
    }

    @Test
    fun halfWidthColonSplitsLabelAndGuidance() {
        // 三角验证：英文用半角冒号加空格，同一函数必须给出同样的两栏
        val parts = webOnlyRowParts("Role cards: web console", fallback)
        assertEquals("Role cards", parts.label)
        assertEquals("web console", parts.guidance)
    }

    @Test
    fun wholeLineStaysLabelWhenNoSeparator() {
        // 没有分隔符时整句当项名，指引走通用文案 —— 不许把用户看到的字吞掉
        val parts = webOnlyRowParts("角色卡", fallback)
        assertEquals("角色卡", parts.label)
        assertEquals(fallback, parts.guidance)
    }

    @Test
    fun onlyTheFirstSeparatorSplits() {
        // 项名里再出现冒号也只切第一刀，后半段整体是指引
        val parts = webOnlyRowParts("压缩阈值与截断策略：请在手机网页修改：网页端", fallback)
        assertEquals("压缩阈值与截断策略", parts.label)
        assertEquals("请在手机网页修改：网页端", parts.guidance)
    }

    @Test
    fun blankRawKeepsFallbackGuidance() {
        // 边界：空串不该被拆出一对空栏
        val parts = webOnlyRowParts("   ", fallback)
        assertEquals("", parts.label)
        assertEquals(fallback, parts.guidance)
    }

    @Test
    fun realWebOnlyStringsLoseNoCharacter() {
        // 真实夹具：中文五条文案拆开后拼回来与原文逐字一致
        val zh = listOf(
            "角色卡：请在手机网页修改",
            "提示词库：请在手机网页修改",
            "开场白：请在手机网页修改",
            "上下文窗口：请在手机网页修改",
            "压缩阈值与截断策略：请在手机网页修改"
        )
        zh.forEach { raw ->
            val parts = webOnlyRowParts(raw, fallback)
            assertTrue(raw, parts.label.isNotEmpty())
            assertTrue(raw, parts.guidance.isNotEmpty())
            assertEquals(raw, parts.label + "：" + parts.guidance)
        }
        // 英文侧同样逐字不丢
        listOf(
            "Role cards: web console",
            "Opening line: web console"
        ).forEach { raw ->
            val parts = webOnlyRowParts(raw, fallback)
            assertEquals(raw, parts.label + ": " + parts.guidance)
        }
    }

    @Test
    fun webOnlyAndReadOnlyRowsAreNeverInteractive() {
        // 网页专属 18 行 + 只读回显 4 行：表端一律不可点
        val webOnly = SettingsCoverage.byForm(listOf(WebSettingsForm.WEB_ONLY))
        val readOnly = SettingsCoverage.byForm(listOf(WebSettingsForm.READ_ONLY))
        assertEquals(18, webOnly.size)
        assertEquals(4, readOnly.size)
        (webOnly + readOnly).forEach { rowKey ->
            val form = SettingsCoverage.formOf(rowKey)!!
            assertEquals(rowKey, RowInteractivity.READ_ONLY, rowInteractivityOf(form))
        }
    }

    @Test
    fun everyWritableRowIsInteractive() {
        // 补上另一半：26 行可写形态一个都不能落进只读行入口
        val writable = SettingsCoverage.byForm(WebSettingsForm.WATCH_WRITABLE_FORMS)
        assertEquals(26, writable.size)
        writable.forEach { rowKey ->
            val form = SettingsCoverage.formOf(rowKey)!!
            assertEquals(rowKey, RowInteractivity.INTERACTIVE, rowInteractivityOf(form))
        }
        // 48 行全覆盖，没有既不可点也不可写的漏网形态
        assertEquals(48, SettingsCoverage.entries.size)
    }
}
