package com.example.myapplication

import com.example.myapplication.readProjectFile
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * IA 文档与界面文案的一致性守卫。
 *
 * 起因：我三次按文档里的名字去真机上找入口，三次都判定"没出现"——
 * 「远程控制台」其实叫「网页控制台」、「外观与点阵」叫「外观与动效」、
 * 「手势与侧键」叫「底栏副键」、「权限」叫「特权执行」。
 * 文档是我写的、界面也是我写的，但我写文档时用的是"我想要的名字"，不是"用户看到的名字"。
 *
 * 这条测试把两边钉在一起：**IA 文档必须提到每个页面与分区的界面真实文案**。
 * 改名时要么同步改文档，要么这条会红。
 */
class SettingsIaDocNamingGuardTest {

    /** 页面 / 分区 → 承载它显示名的字符串键。 */
    private val requiredKeys = listOf(
        "settings_zone_answer",
        "settings_zone_voice",
        "settings_zone_act",
        "settings_zone_connect",
        "settings_model_and_service",
        "settings_provider_profiles",
        "settings_model_params",
        "settings_prompt_context",
        "settings_voice_entry",
        "settings_side_key",
        "settings_appearance",
        "settings_tools_and_mcp",
        "settings_security_policy",
        "settings_privilege_title",
        "settings_lux_terminal",
        "settings_sub_network_request",
        "settings_remote_console_title",
        "settings_sub_transfer",
        "settings_about",
    )

    @Test
    fun theIaDocUsesTheSameWordsTheUserSees() {
        val zh = readProjectFile("src/main/res/values-zh/strings_settings.xml")
        val doc = readProjectFile("../docs/SETTINGS-IA.md")

        val missing = mutableListOf<String>()
        for (key in requiredKeys) {
            val pattern = Regex("<string name=\"" + key + "\">([^<]*)</string>")
            val text = pattern.find(zh)?.groupValues?.get(1)
            if (text.isNullOrBlank()) {
                missing += key + "（字符串缺失）"
                continue
            }
            if (!doc.contains(text)) missing += text + "（来自 " + key + "）"
        }
        assertTrue(
            "IA 文档里缺少这些「界面真实文案」——文档改名或界面改名时必须同步：" + missing,
            missing.isEmpty()
        )
    }
}
