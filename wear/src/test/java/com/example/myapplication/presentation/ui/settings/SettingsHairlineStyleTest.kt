package com.example.myapplication.presentation.ui.settings

import com.example.myapplication.TestSlices
import com.example.myapplication.readProjectFile
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 发丝线的**逐风格差异**（方角 vs 终端）。
 *
 * 背景（S2）：方角档与终端档共用 `HAIRLINE_FLAT` 这一支，以前分隔线都是"通栏 1dp"，
 * 两套因此看不出区别。现在方角走"缩进 + 半像素（与文字左缘对齐）"，终端保持通栏 1dp。
 *
 * 这条守卫钉住分支本身：一旦有人把差异删掉（又变回一个通栏常量），这里立刻红。
 */
class SettingsHairlineStyleTest {

    private val shared = readProjectFile(
        "src/main/java/com/example/myapplication/presentation/ui/settings/SettingsShared.kt"
    )

    @Test
    fun theHairlineBranchStillDifferentiatesSquareFromTerminal() {
        val branch = TestSlices.slice(
            shared,
            "if (SettingsStylePolicy.current().rowShell == SettingsRowShell.HAIRLINE_FLAT) {",
            "if (groupChrome) {",
            "HAIRLINE_FLAT 分支"
        )
        assertTrue(
            "发丝线必须按 usesMonospace 分流（终端通栏 / 方角缩进）",
            branch.contains("SettingsStylePolicy.usesMonospace")
        )
        assertTrue("方角档的分隔线要缩进到文字左缘", branch.contains("SettingsRowSidePadding"))
        assertTrue("方角档的分隔线要比终端更细（半像素）", branch.contains("0.5.dp"))
        assertTrue("终端档仍是通栏 1dp", branch.contains("1.dp"))
    }
}
