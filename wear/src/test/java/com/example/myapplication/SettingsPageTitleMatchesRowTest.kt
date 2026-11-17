package com.example.myapplication

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「页面标题 = 根页行标题」守卫（`docs/SETTINGS-IA.md` §4）。
 *
 * 规则的理由很朴素：用户在根页点的是「模型参数」，进去看到「推理参数」，会怀疑自己点错了。
 * 这条规矩一直写在 IA 文档里，但**从来没有任何测试钉住它** —— 所以 2026-11-17 的
 * 15 页巡检一次性抓出 4 页违反（模型与服务→模型与端点、模型参数→推理参数、
 * 语音与唤醒→语音输入、底栏副键→手势与侧键）。
 *
 * 钉法：子页标题键与根页行标题键**取值必须相等**，中英两份都要。
 * 只列"两个键不同"的那几对；同键的（如 `settings_about`）天然一致，不用重复钉。
 */
class SettingsPageTitleMatchesRowTest {

    /** 子页标题键 → 根页行标题键。 */
    private val pairs = listOf(
        "settings_sub_model_config" to "settings_model_and_service",
        "settings_sub_hyper_params" to "settings_model_params",
        "settings_sub_voice_entry" to "settings_voice_entry",
        "settings_sub_gesture" to "settings_side_key",
    )

    private fun valueOf(xml: String, key: String): String? =
        Regex("<string name=\"" + key + "\">([^<]*)</string>").find(xml)?.groupValues?.get(1)

    private fun check(file: String) {
        val xml = readProjectFile(file)
        val mismatched = mutableListOf<String>()
        for ((titleKey, rowKey) in pairs) {
            val title = valueOf(xml, titleKey)
            val row = valueOf(xml, rowKey)
            if (title.isNullOrBlank() || row.isNullOrBlank()) {
                mismatched += "$titleKey / $rowKey（字符串缺失）"
                continue
            }
            if (title != row) mismatched += "子页「$title」≠ 根页行「$row」"
        }
        assertTrue(
            "$file 里子页标题与根页行名不一致（用户点了 A 进去看到 B）：" + mismatched,
            mismatched.isEmpty()
        )
    }

    @Test
    fun chinesePageTitlesEqualTheirRootRows() = check("src/main/res/values-zh/strings_settings.xml")

    @Test
    fun englishPageTitlesEqualTheirRootRows() = check("src/main/res/values/strings_settings.xml")
}
