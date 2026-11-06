package com.example.myapplication

import java.io.File

/**
 * 单测读取仓库内真实文件的唯一入口。
 *
 * Gradle 单测的 CWD 是模块目录（wear/），从仓库根目录跑时又会多一层 wear/，
 * 两种布局都试一次，避免守护测试因为"找不到文件"而不是"断言失败"。
 */
internal fun readProjectFile(relative: String): String {
    val file = listOf(File(relative), File("wear", relative)).firstOrNull { it.exists() }
        ?: error("找不到项目文件 " + relative + "（cwd=" + File(".").absolutePath + "）")
    return file.readText()
}
