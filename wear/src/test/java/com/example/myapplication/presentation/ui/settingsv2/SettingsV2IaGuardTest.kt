package com.example.myapplication.presentation.ui.settingsv2

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V2 的信息架构守卫（替代 0.0.0.167 删掉的旧 IA 守卫）。
 *
 * 旧实现时代有两条守卫按 `strings.xml` 校验"子页标题与根页行名一致"。V2 的分区是代码常量
 * （[SettingsV2SampleData.zoneTitles]）而不是字符串资源，所以判据换成：
 * ①四个分区的**名字与顺序**必须固定；
 * ②这四个名字必须同时出现在**文档**里（IA 文档与 V2 计划各一处），
 *   防止代码改了名字而文档没跟上——这正是旧实现踩过的坑。
 */
class SettingsV2IaGuardTest {

    private val expectedZones = listOf("连得上", "让它能做事", "让它听懂我", "让它答得好")

    @Test
    fun theFourZonesKeepTheirNamesAndOrder() {
        assertEquals(
            "V2 的四个分区必须与 IA 一致且顺序固定",
            expectedZones,
            SettingsV2SampleData.zoneTitles
        )
        assertEquals(
            "分区数量必须恰好四个（多一个少一个都要改 IA 文档）",
            4,
            SettingsV2SampleData.root().size
        )
        assertEquals(
            "每个分区都必须至少有一行设置",
            true,
            SettingsV2SampleData.root().all { it.rows.isNotEmpty() }
        )
    }

    @Test
    fun everyZoneIsMentionedInTheDocs() {
        // 测试的工作目录是 wear/，项目根在上一级；两种写法都试一遍
        val candidates = listOf(
            "docs/SETTINGS-REBUILD-V2.md",
            "docs/CONTEXT.md",
            "../docs/SETTINGS-REBUILD-V2.md",
            "../docs/CONTEXT.md"
        )
        val docs = candidates.map { File(it) }.filter { it.exists() }

        assertTrue("至少要能找到一份文档来校验 IA", docs.isNotEmpty())

        expectedZones.forEach { zone ->
            val mentioned = docs.any { it.readText().contains(zone) }
            assertTrue("分区「$zone」在文档里找不到 —— 代码改了名字就必须同步文档", mentioned)
        }
    }
}
