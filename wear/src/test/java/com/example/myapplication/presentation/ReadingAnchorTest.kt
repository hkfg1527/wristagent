package com.example.myapplication.presentation

import com.example.myapplication.presentation.ui.CameraCurve
import com.example.myapplication.presentation.ui.ReadingAnchor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 镜头跟随的判定契约（纯逻辑）。
 *
 * 真机现象：回答开始 / 思考 / 工具胶囊出现时镜头都不跟随，正在读的那一行不在"看的区域"。
 * 旧实现只对齐一次再恒速下滚，任何布局变化（胶囊插入、胶囊折叠、底栏形变、逐字吐字）
 * 都会让锚点离开原位且不再被纠正。这里把判定收敛成"锚点 vs 阅读带"一个纯函数。
 */
class ReadingAnchorTest {

    // 210dp 高、2.0 密度的视口：420px；带顶 = 34dp 时钟 + 8dp 余量 = 42dp = 84px；
    // 底栏占 140px、与正文留 8dp(16px) 缝 → 带底 = 420 - 140 - 16 = 264px。
    private val viewport = 420f
    private val bandTop = 84f
    private val bandBottom = 264f
    private val deadZone = 16f

    @Test
    fun `锚点落在阅读带内时不滚`() {
        val plan = ReadingAnchor.plan(200f, bandTop, bandBottom, deadZone, true, true)
        assertEquals(ReadingAnchor.Move.Hold, plan.move)
        assertTrue(plan.isHold)
        assertEquals(0f, plan.deltaPx, 1e-4f)
    }

    @Test
    fun `锚点低于带下沿时正文上移且位移恰好等于超出量`() {
        val plan = ReadingAnchor.plan(300f, bandTop, bandBottom, deadZone, true, true)
        assertEquals(ReadingAnchor.Move.ContentUp, plan.move)
        assertEquals(36f, plan.deltaPx, 1e-4f)
    }

    @Test
    fun `锚点高于带上沿时正文下移且位移为负`() {
        val plan = ReadingAnchor.plan(40f, bandTop, bandBottom, deadZone, true, true)
        assertEquals(ReadingAnchor.Move.ContentDown, plan.move)
        assertEquals(-44f, plan.deltaPx, 1e-4f)
    }

    @Test
    fun `死区两侧都不滚_刚跨出死区才滚`() {
        // 下沿：带底 + 半个死区 → 不动（layoutInfo 反推的位置永远有几像素抖动）
        assertTrue(ReadingAnchor.plan(bandBottom + deadZone * 0.5f, bandTop, bandBottom, deadZone, true, true).isHold)
        val below = ReadingAnchor.plan(bandBottom + deadZone + 0.5f, bandTop, bandBottom, deadZone, true, true)
        assertEquals(ReadingAnchor.Move.ContentUp, below.move)
        assertEquals(deadZone + 0.5f, below.deltaPx, 1e-4f)
        // 上沿：同上，方向相反
        assertTrue(ReadingAnchor.plan(bandTop - deadZone * 0.5f, bandTop, bandBottom, deadZone, true, true).isHold)
        val above = ReadingAnchor.plan(bandTop - deadZone - 0.5f, bandTop, bandBottom, deadZone, true, true)
        assertEquals(ReadingAnchor.Move.ContentDown, above.move)
        assertEquals(-(deadZone + 0.5f), above.deltaPx, 1e-4f)
    }

    @Test
    fun `锚点已经高于首项且无法往回滚时不滚`() {
        val plan = ReadingAnchor.plan(40f, bandTop, bandBottom, deadZone, canScrollForward = true, canScrollBackward = false)
        assertEquals(ReadingAnchor.Move.Hold, plan.move)
        assertEquals(0f, plan.deltaPx, 1e-4f)
    }

    @Test
    fun `已经滚到内容末尾时不再假装能拉回`() {
        val plan = ReadingAnchor.plan(400f, bandTop, bandBottom, deadZone, canScrollForward = false, canScrollBackward = true)
        assertEquals(ReadingAnchor.Move.Hold, plan.move)
    }

    @Test
    fun `阅读带上下沿由留白与底栏实际占用算出`() {
        assertEquals(84f, ReadingAnchor.bandTopPx(topInsetPx = 68f, marginPx = 16f), 1e-4f)
        assertEquals(264f, ReadingAnchor.bandBottomPx(viewport, barFootprintPx = 140f, gapPx = 16f), 1e-4f)
        // 底栏占得越多（主键展开 / 审批扁条），阅读带越矮
        assertTrue(
            ReadingAnchor.bandBottomPx(viewport, 200f, 16f) <
                ReadingAnchor.bandBottomPx(viewport, 140f, 16f)
        )
        // 底栏完全收起（贴底弧条）→ 阅读带跟着往下长
        assertTrue(
            ReadingAnchor.bandBottomPx(viewport, 40f, 16f) >
                ReadingAnchor.bandBottomPx(viewport, 140f, 16f)
        )
        assertEquals(8f, ReadingAnchor.BAND_BOTTOM_GAP_DP, 1e-4f)
    }

    @Test
    fun `可见项算出最后一条正文的底边`() {
        // Wear 的 offset 是相对视口中线的中心偏移：底边 = 视口高/2 + offset + size/2
        assertEquals(310f, CameraCurve.itemBottomPx(0f, 200f, viewport), 1e-4f)
        assertEquals(410f, CameraCurve.itemBottomPx(150f, 100f, viewport), 1e-4f)
        // 整张卡片都跑到上方 → 底边为负，说明锚点该被拉回来
        assertEquals(-10f, CameraCurve.itemBottomPx(-260f, 80f, viewport), 1e-4f)
    }

    @Test
    fun `给定可见项与锚点位置时返回对应的滚动决策`() {
        // 回合卡在带下沿之外：底边 350px > 264px → 正文上移 86px
        val bodyBottom = CameraCurve.itemBottomPx(itemCenterOffsetPx = 40f, itemSizePx = 200f, viewportHeightPx = viewport)
        val below = ReadingAnchor.plan(bodyBottom, bandTop, bandBottom, deadZone, true, true)
        assertEquals(ReadingAnchor.Move.ContentUp, below.move)
        assertEquals(86f, below.deltaPx, 1e-4f)

        // 同一张卡往上挪一段，底边落进带内 → 不滚
        val inBandBottom = CameraCurve.itemBottomPx(itemCenterOffsetPx = -110f, itemSizePx = 200f, viewportHeightPx = viewport)
        assertTrue(ReadingAnchor.plan(inBandBottom, bandTop, bandBottom, deadZone, true, true).isHold)
    }

    @Test
    fun `用户滚动中一律让路`() {
        assertTrue(ReadingAnchor.shouldYield(isScrollInProgress = true, nowMs = 1_000L, pauseUntilMs = 0L))
    }

    @Test
    fun `用户滚动后的暂停窗口内让路_窗口过期后恢复跟随`() {
        assertTrue(ReadingAnchor.shouldYield(isScrollInProgress = false, nowMs = 1_000L, pauseUntilMs = 2_200L))
        assertFalse("窗口边界之后就必须让镜头接回来", ReadingAnchor.shouldYield(false, nowMs = 2_200L, pauseUntilMs = 2_200L))
        assertFalse(ReadingAnchor.shouldYield(false, nowMs = 2_500L, pauseUntilMs = 2_200L))
    }
}
