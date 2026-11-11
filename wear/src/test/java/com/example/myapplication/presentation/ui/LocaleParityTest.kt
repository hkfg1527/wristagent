package com.example.myapplication.presentation.ui

import com.example.myapplication.readProjectFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 中英资源对齐的可执行规格。
 *
 * 为什么需要它：想加一条中文文案却忘了加英文（或反过来），编译不会报错、测试不会红、
 * lint 的 MissingTranslation 在 CI 里被 `abortOnError = false` 吞掉——线上表现是英文用户看到中文，
 * 或中文用户看到键名。这类问题只能靠资源文件之间的集合相等断言来守。
 */
class LocaleParityTest {

    private val stringFiles = listOf(
        "strings.xml",
        "strings_ui.xml",
        "strings_chat.xml",
        "strings_settings.xml",
        "strings_tools.xml",
        "strings_agent_prompt.xml",
    )

    private fun keysOf(locale: String): Map<String, String> {
        val pairs = linkedMapOf<String, String>()
        for (name in stringFiles) {
            val text = readProjectFile("src/main/res/$locale/$name")
            for (match in Regex("<string name=\"([^\"]+)\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL).findAll(text)) {
                val key = match.groupValues[1]
                val value = match.groupValues[2]
                assertTrue(
                    locale + "/" + name + " 里 " + key + " 重复定义了两遍",
                    pairs.put(key, value) == null
                )
            }
        }
        return pairs
    }

    @Test
    fun everyEnglishKeyHasAChineseCounterpartAndViceVersa() {
        val en = keysOf("values")
        val zh = keysOf("values-zh")
        assertTrue("英文资源为空，扫描正则可能已失效", en.isNotEmpty())
        assertEquals(
            "两侧键集必须完全一致；只在一侧存在的键会让某个语言显示成键名或漏文案",
            en.keys.sorted(),
            zh.keys.sorted()
        )
    }

    @Test
    fun placeholdersMatchOnBothSides() {
        val en = keysOf("values")
        val zh = keysOf("values-zh")
        val mismatched = mutableListOf<String>()
        for ((key, enValue) in en) {
            val zhValue = zh[key] ?: continue
            val enSlots = Regex("%(\\d+)\\$[a-zA-Z]").findAll(enValue).map { it.value }.sorted().toList()
            val zhSlots = Regex("%(\\d+)\\$[a-zA-Z]").findAll(zhValue).map { it.value }.sorted().toList()
            if (enSlots != zhSlots) mismatched += key + " en=" + enSlots + " zh=" + zhSlots
        }
        assertEquals(
            "占位符不一致会导致运行时抛 IllegalFormatException 或参数串位：",
            emptyList<String>(),
            mismatched
        )
    }

    @Test
    fun noValueIsBlankOnEitherSide() {
        for (locale in listOf("values", "values-zh")) {
            val blank = keysOf(locale).filterValues { it.isBlank() }.keys.toList()
            assertEquals(locale + " 里有空文案，界面会出现没有文字的可点区域：", emptyList<String>(), blank)
        }
    }
}