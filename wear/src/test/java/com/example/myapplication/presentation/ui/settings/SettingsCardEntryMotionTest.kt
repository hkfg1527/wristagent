package com.example.myapplication.presentation.ui.settings

import com.example.myapplication.readProjectFile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 卡片档入场动效的守护：**只淡入、不位移**。
 *
 * 实机连拍证据：入场时每行先下移 24dp 再上浮，位移期间相邻两张卡会贴在一起 ——
 * 扫描 x=233 那一列，入场帧是连成一整段 h≈293（=146×2），静止帧才是分开的两段（间隙 16px）。
 * 卡片之间的 8dp 间隙是硬约束，所以卡片档的入场位移必须恒为 0。
 */
class SettingsCardEntryMotionTest {

    private val source: String = readProjectFile(
        "src/main/java/com/example/myapplication/presentation/ui/settings/SettingsRowStagger.kt"
    )

    @Test
    fun theCardStyleEntersWithoutSliding() {
        assertTrue(
            "必须按风格区分入场位移",
            source.contains("val slideOnEnter = WearAppearance.style != SettingsStyle.GOOGLE_CARD")
        )
        assertTrue(
            "位移必须被 slideOnEnter 门控",
            source.contains("if (slideOnEnter) (1f - enterValue) * enterOffsetPx else 0f")
        )
    }

    @Test
    fun theExitSlideSurvivesBecauseItHappensOffScreen() {
        assertTrue("退场仍要下滑淡出", source.contains("exitValue * exitOffsetPx"))
    }

    @Test
    fun theWholeRowNeverDisappearsForTheCardStyle() {
        assertFalse(
            "淡入不能被误删：入场仍要由 enterValue 驱动 alpha",
            source.contains("alpha = enterValue * (1f - exitValue)").not()
        )
    }
}
