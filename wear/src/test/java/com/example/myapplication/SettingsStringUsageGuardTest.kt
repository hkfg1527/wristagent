package com.example.myapplication

import com.example.myapplication.readProjectFile
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 死文案守卫：`strings_settings.xml` 里的每个 key 都必须被 Kotlin 代码引用。
 *
 * 为什么值得一条测试：删功能时最容易漏掉的不是代码，而是文案 ——
 * 字符串留在那儿不会报错、不会影响构建，只会让下一个人在"这个名字是干什么的"里迷路。
 * 第 42 轮做设置页复审时一次扫出 6 条死文案（其中 3 条是"滑杆的说明文字"在搬家时丢了）。
 */
class SettingsStringUsageGuardTest {

    @Test
    fun everySettingsStringIsReferencedSomewhereInKotlin() {
        val xml = readProjectFile("src/main/res/values/strings_settings.xml")
        val keys = Regex("<string name=\"([A-Za-z0-9_]+)\">").findAll(xml)
            .map { it.groupValues[1] }.toSet()
        assertEquals("strings_settings.xml 不该是空的", true, keys.isNotEmpty())

        val used = mutableSetOf<String>()
        File("src/main/java").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                Regex("R\\.string\\.([A-Za-z0-9_]+)").findAll(file.readText())
                    .forEach { used += it.groupValues[1] }
            }

        val dead = (keys - used).sorted()
        assertEquals(
            "以下文案没有任何 Kotlin 代码引用：删掉它们，或把它们接到真正的界面上（多半是搬家时丢了）：",
            emptyList<String>(),
            dead
        )
    }
}
