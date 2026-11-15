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
 * 判据只钉最容易同步的东西：版本号。升版本就必须顺手更新这一页。
 */
class ContextDocFreshnessGuardTest {

    @Test
    fun theContextDocMentionsTheCurrentVersionName() {
        val gradle = readProjectFile("build.gradle.kts")
        val line = gradle.lines().firstOrNull { it.contains("versionName = ") }
            ?: error("build.gradle.kts 里找不到 versionName")
        // 只留数字与点：把引号、空格、赋值号统统滤掉
        val versionName = line.substringAfter("versionName = ").filter { it.isDigit() || it == 46.toChar() }
        assertTrue("解析出的版本号不像版本号：" + versionName, versionName.count { it == 46.toChar() } >= 3)
        val doc = readProjectFile("../docs/CONTEXT.md")
        assertTrue(
            "docs/CONTEXT.md 没有提到当前版本 " + versionName + " —— 升版本时把 0.5 运行中状态一起更新",
            doc.contains(versionName)
        )
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
