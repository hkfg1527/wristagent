package com.example.myapplication

import com.example.myapplication.readProjectFile
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 上下文文档新鲜度守卫：docs/CONTEXT.md 必须提到当前的 versionName。
 *
 * 为什么需要：那一页是"跨轮恢复的唯一入口"。第 46 轮我翻它时，它还停在 0.0.0.22 / 1266 单测，
 * 而实际已经到 0.0.0.37 / 1283 —— P1 验收矩阵、MotionScheme、Lux 终端三项它都写着"未做"，实际全做完了。
 * 一份会撒谎的记忆源，比没有记忆源更危险。
 *
 * 判据只钉最容易同步的东西：版本号。而且**两个接续段都要写**（0.0.0.100 审查点名）：
 * 以前只在整篇里 `contains`，于是 §0.0 写对了、§0.5 还停在上一个版本时守卫照样绿。
 */
class ContextDocFreshnessGuardTest {

    private fun currentVersionName(): String {
        val gradle = readProjectFile("build.gradle.kts")
        val line = gradle.lines().firstOrNull { it.contains("versionName = ") }
            ?: error("build.gradle.kts 里找不到 versionName")
        // 只留数字与点：把引号、空格、赋值号统统滤掉
        return line.substringAfter("versionName = ").filter { it.isDigit() || it == 46.toChar() }
    }

    @Test
    fun theContextDocMentionsTheCurrentVersionName() {
        val versionName = currentVersionName()
        assertTrue("解析出的版本号不像版本号：" + versionName, versionName.count { it == 46.toChar() } >= 3)
        val doc = readProjectFile("../docs/CONTEXT.md")
        assertTrue(
            "docs/CONTEXT.md 没有提到当前版本 " + versionName + " —— 升版本时把 0.5 运行中状态一起更新",
            doc.contains(versionName)
        )
    }

    @Test
    fun bothResumeSectionsMentionTheCurrentVersionName() {
        val versionName = currentVersionName()
        val doc = readProjectFile("../docs/CONTEXT.md")
        val head = section(doc, "## 0.0 接续卡", "## 0. 恢复步骤")
        val running = section(doc, "## 0.5 运行中状态", "## 0.6 上下文自管理")
        assertTrue("§0.0 接续卡没写当前版本 " + versionName, head.contains(versionName))
        // §0.5 里还有一整段历史条目（每条都带自己的版本号），所以**只认版本行**，
        // 否则「版本行停在旧版本、但历史里提过新版本」这种最常见的不一致会被放过。
        val versionLine = running.lines().firstOrNull { it.trimStart().startsWith("- 内部版") }.orEmpty()
        assertTrue("§0.5 缺少版本行（应以「- 内部版」开头）", versionLine.isNotBlank())
        assertTrue("§0.5 的版本行没写当前版本：$versionLine", versionLine.contains(versionName))
    }

    /**
     * 按两个标题切段。这里**不能**套 [TestSlices] 的"长度 < 全文一半"判据 ——
     * §0.5 本身就占全文三分之二（它是一个版本一段的历史）。锚点存在与顺序才是这条要钉的东西。
     */
    private fun section(doc: String, start: String, end: String): String {
        assertTrue("锚点缺失：$start", doc.contains(start))
        assertTrue("锚点缺失：$end", doc.contains(end))
        val from = doc.indexOf(start)
        val to = doc.indexOf(end, from + start.length)
        assertTrue("锚点顺序异常：$start 应在 $end 之前", to > from)
        return doc.substring(from, to)
    }

    @Test
    fun theContextDocDoesNotClaimUnfinishedWorkThatIsDone() {
        val doc = readProjectFile("../docs/CONTEXT.md")
        val stalePhrases = listOf(
            "MotionScheme 未接入",
            "Lux 终端未做",
            "验收矩阵还没系统做完",
        )
        val stillStale = stalePhrases.filter { doc.contains(it) }
        assertTrue(
            "CONTEXT.md 里还留着已完成事项的旧说法：" + stillStale,
            stillStale.isEmpty()
        )
    }
}
