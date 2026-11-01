package com.example.myapplication.presentation

import com.example.myapplication.agent.contract.ConversationBlock
import com.example.myapplication.presentation.ui.CameraCurve
import com.example.myapplication.presentation.ui.RenderableConversationItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 镜头曲线契约：死区不动、速度只与剩余距离成正比（无地板）、方向可正可负、
 * 加速有上限、单帧绝不越过目标（不产生回弹）。
 */
class CameraCurveTest {

    @Test
    fun `剩余距离落在死区内时目标速度为零`() {
        assertEquals(0f, CameraCurve.targetSpeed(remainingPx = 12f, deadZonePx = 16f), 0.001f)
        assertEquals(0f, CameraCurve.targetSpeed(remainingPx = 16f, deadZonePx = 16f), 0.001f)
        assertEquals(0f, CameraCurve.targetSpeed(remainingPx = -15f, deadZonePx = 16f), 0.001f)
        assertEquals(8f, CameraCurve.DEAD_ZONE_DP, 0.001f)
    }

    @Test
    fun `死区外速度只与剩余距离成正比_没有速度地板`() {
        assertEquals(24f * CameraCurve.SPEED_PER_PX, CameraCurve.targetSpeed(24f, deadZonePx = 16f), 0.001f)
        assertEquals(200f * CameraCurve.SPEED_PER_PX, CameraCurve.targetSpeed(200f, deadZonePx = 16f), 0.001f)
        // 很近的距离给出很小的速度：旧的 45px/s 地板必须消失
        assertEquals(4f * CameraCurve.SPEED_PER_PX, CameraCurve.targetSpeed(4f, deadZonePx = 0f), 0.001f)
        assertEquals(10f * CameraCurve.SPEED_PER_PX, CameraCurve.targetSpeed(10f, deadZonePx = 0f), 0.001f)
        assertTrue(CameraCurve.targetSpeed(4f, deadZonePx = 0f) < 45f)
    }

    @Test
    fun `目标在视口上方时目标速度为负`() {
        val up = CameraCurve.targetSpeed(remainingPx = -200f, deadZonePx = 16f)
        assertEquals(-200f * CameraCurve.SPEED_PER_PX, up, 0.001f)
        assertTrue("目标在上方必须给负速度", up < 0f)
        assertEquals(-up, CameraCurve.targetSpeed(remainingPx = 200f, deadZonePx = 16f), 0.001f)
    }

    @Test
    fun `远距离目标速度被上下限截断`() {
        assertEquals(CameraCurve.MAX_SPEED, CameraCurve.targetSpeed(5_000f, deadZonePx = 16f), 0.001f)
        assertEquals(-CameraCurve.MAX_SPEED, CameraCurve.targetSpeed(-5_000f, deadZonePx = 16f), 0.001f)
    }

    @Test
    fun `目标速度随距离单调递增`() {
        val samples = listOf(20f, 60f, 200f, 800f, 3000f).map { CameraCurve.targetSpeed(it, deadZonePx = 16f) }
        for (i in 1 until samples.size) {
            assertTrue(
                "距离变大速度不应变小: ${samples[i - 1]} -> ${samples[i]}",
                samples[i] >= samples[i - 1]
            )
        }
    }

    @Test
    fun `加速度上限降到六千且双向对称`() {
        assertEquals(6000f, CameraCurve.ACCEL, 0.001f)
        assertEquals(96f, CameraCurve.nextSpeed(0f, CameraCurve.MAX_SPEED, 0.016f), 0.001f)
        assertEquals(-96f, CameraCurve.nextSpeed(0f, -CameraCurve.MAX_SPEED, 0.016f), 0.001f)
    }

    @Test
    fun `单帧位移不越过正向目标`() {
        assertEquals(30f, CameraCurve.stepPx(speed = 5_000f, dtSeconds = 0.016f, remainingPx = 30f), 0.001f)
    }

    @Test
    fun `单帧位移不越过反向目标_不产生回弹`() {
        assertEquals(-30f, CameraCurve.stepPx(speed = -5_000f, dtSeconds = 0.016f, remainingPx = -30f), 0.001f)
        assertEquals(0f, CameraCurve.stepPx(speed = -5_000f, dtSeconds = 0.016f, remainingPx = 0f), 0.001f)
        assertEquals(0f, CameraCurve.stepPx(speed = 5_000f, dtSeconds = 0.016f, remainingPx = 0f), 0.001f)
    }

    @Test
    fun `定位剩余距离是卡片顶边到可读区顶部的带符号差`() {
        // 视口 384px(192dp@2.0)，卡片中心偏移 +100、高 200 -> 顶边 = 192 + 100 - 100 = 192px
        // 目标顶边 84px(42dp) -> 还要向前（把内容往上推）108px
        assertEquals(108f, CameraCurve.focusRemainingPx(100f, 200f, 384f, 84f), 0.001f)
        // 卡片顶边已经跑到目标上方：中心偏移 -120、高 200 -> 顶边 = 192 - 120 - 100 = -28px
        // 目标是把它拉回来（反向滚动）-112px；负值代表向上/反向，绝不是 0
        assertEquals(-112f, CameraCurve.focusRemainingPx(-120f, 200f, 384f, 84f), 0.001f)
        // 正好对齐 -> 0
        assertEquals(0f, CameraCurve.focusRemainingPx(-8f, 200f, 384f, 84f), 0.001f)
    }

    @Test
    fun `目标项不可见时按所在方向给出搜寻距离`() {
        assertTrue("目标在下方要向前找", CameraCurve.seekRemainingPx(384f, targetBelow = true) > 0f)
        assertTrue("目标在上方要反向找", CameraCurve.seekRemainingPx(384f, targetBelow = false) < 0f)
    }

    @Test
    fun `只有紧跟用户提问且已有正文的助手回合才触发对齐`() {
        fun turn(text: String?): RenderableConversationItem.AssistantTurn {
            val blocks: List<ConversationBlock> = listOfNotNull(
                ConversationBlock.Thought(id = "t1", reasoning = "先想一下"),
                text?.let { ConversationBlock.Text(id = "a1", content = it) }
            )
            return RenderableConversationItem.AssistantTurn(key = "turn_1", blocks = blocks)
        }

        val userPrompt = RenderableConversationItem.UserPrompt(
            ConversationBlock.Text(id = "u1", content = "帮我看下这个报错", isUser = true)
        )

        assertNull(
            "只有欢迎语时不该对齐",
            CameraCurve.replyTurnKey(listOf(turn("主人，今天有什么需要解决的技术或生活问题？")))
        )
        assertNull("还没吐字时不该对齐", CameraCurve.replyTurnKey(listOf(userPrompt)))
        assertNull("回合只有思考胶囊时不该对齐", CameraCurve.replyTurnKey(listOf(userPrompt, turn(null))))
        assertNull("正文还是空白时不该对齐", CameraCurve.replyTurnKey(listOf(userPrompt, turn("   "))))
        assertEquals(
            "turn_1",
            CameraCurve.replyTurnKey(listOf(userPrompt, turn("报错是那个空指针")))
        )
    }
}
