package com.example.myapplication.presentation.ui

import com.example.myapplication.readProjectFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 会话抽屉视觉约束的源码守护：强调色只标选中态、卡片描边中性、点阵签名与设置页共用一份。
 *
 * 这三条编译期都不报错 —— 抽屉退回"选中即实底"或"强调色当描边"没人会发现，
 * 直到下一张真机截图把它们当成设计稿。所以在这里扫真实源文件钉死。
 */
class SessionDrawerVisualGuardTest {

    @Test
    fun accentNeverPaintsAnAreaInTheDrawer() {
        val code = drawerCode()
        assertEquals(
            "抽屉里的强调色只允许两处：选中胶囊的文字 + 当前会话的 6dp 点",
            2,
            Regex("Wear\\.Accent\\b").findAll(code).count()
        )
        assertFalse(
            "强调色不许再涂成实底",
            Regex("background\\(Wear\\.(Palette\\.)?Accent\\b").containsMatchIn(code)
        )
        assertFalse(
            "强调色不许再当容器底色",
            Regex("containerColor = Wear\\.(Palette\\.)?Accent\\b").containsMatchIn(code)
        )
        assertTrue(
            "选中胶囊只留 1dp 的强调色轨道位描边",
            code.contains("if (selected) Wear.AccentRail else Wear.Palette.Border")
        )
        assertTrue(
            "选中胶囊必须透明填充",
            code.contains("if (selected) Color.Transparent else Wear.Palette.SurfaceAlt")
        )
    }

    @Test
    fun sessionCardStrokeIsNeutralAndTheCurrentSessionIsADot() {
        val code = drawerCode()
        assertFalse("会话卡不许再用强调色描边", code.contains("isSelected -> Wear.Palette.Accent"))
        assertTrue("当前会话改为 6dp 点", code.contains(".size(Wear.Size.StatusBlock)"))
        assertFalse("旧的 8dp 点必须已撤掉", code.contains(".size(Wear.Space.Sm)"))
    }

    @Test
    fun drawerDoesNotDependOnSettingsInternalsForDecoration() {
        // 抽屉标题在 ScalingLazyColumn 的 item 里，会被裁到节点边界。曾试过用它画设置页那套
        // 三点点阵签名，真机像素扫描确认标题左侧 x<35 区域一个非背景像素都没有 —— 一个点都没渲染出来。
        // 与其为了装饰去动列表内边距，不如这一页不放签名。所以这里钉住"不许再引进来"。
        val code = drawerCode()
        assertFalse("抽屉不许再依赖设置页的签名实现", code.contains("headerDotSignature"))
        assertFalse("抽屉不许再依赖设置页的签名整宽常量", code.contains("HeaderDotSignatureWidth"))
        assertFalse("不许在抽屉里另写一份点阵", code.contains("HeaderDotDiameter"))
    }

    private fun drawerCode(): String =
        readProjectFile(DrawerPath)
            .replace(Regex("/\\*[\\s\\S]*?\\*/"), " ")
            .replace(Regex("//[^\n]*"), " ")

    private companion object {
        const val DrawerPath =
            "src/main/java/com/example/myapplication/presentation/ui/SessionDrawer.kt"
        const val SettingsPath =
            "src/main/java/com/example/myapplication/presentation/ui/settings/SettingsShared.kt"
    }
}
