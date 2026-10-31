package com.example.myapplication.presentation

import com.example.myapplication.presentation.ui.CameraCurve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 镜头曲线契约：远快近慢、加速有上限、接近末端自然减速、绝不越过剩余距离。 */
class CameraCurveTest {

    @Test
    fun `剩余距离很远时目标速度达到上限`() {
        assertEquals(CameraCurve.MAX_SPEED, CameraCurve.targetSpeed(remainingPx = 5_000f))
    }

    @Test
    fun `剩余距离很近时目标速度放慢但不为零`() {
        val slow = CameraCurve.targetSpeed(remainingPx = 4f)
        assertEquals(CameraCurve.MIN_SPEED, slow)
        assertTrue("近距离必须明显慢于远距离", slow < CameraCurve.targetSpeed(remainingPx = 500f))
    }

    @Test
    fun `目标速度随距离单调递增`() {
        val samples = listOf(10f, 60f, 200f, 800f, 3000f).map { CameraCurve.targetSpeed(it) }
        for (i in 1 until samples.size) {
            assertTrue("距离变大速度不应变小: ${samples[i - 1]} -> ${samples[i]}", samples[i] >= samples[i - 1])
        }
    }

    @Test
    fun `起步时每帧速度增量被加速度限制`() {
        // 一帧(16ms)内的最大速度增量 = ACCEL * dt，与具体加速度值解耦
        val next = CameraCurve.nextSpeed(currentSpeed = 0f, targetSpeed = CameraCurve.MAX_SPEED, dtSeconds = 0.016f)
        assertEquals(CameraCurve.ACCEL * 0.016f, next, 0.001f)
    }

    @Test
    fun `接近末端时速度会下降_形成减速过程`() {
        // 同一起始速度，剩余距离从 800 -> 40，目标速度应显著下降
        val far = CameraCurve.targetSpeed(800f)
        val near = CameraCurve.targetSpeed(40f)
        assertTrue("接近末端必须减速", near < far / 2f)
    }

    @Test
    fun `单帧位移不会越过剩余距离`() {
        assertEquals(30f, CameraCurve.stepPx(speed = 5_000f, dtSeconds = 0.016f, remainingPx = 30f), 0.001f)
    }

    @Test
    fun `剩余距离计算把最后一项底部对齐到留白之上`() {
        // 视口 384px，底部留白 112px，最后一项底部在 384px -> 还要往上走 112px
        assertEquals(112f, CameraCurve.remainingPx(300f, 84f, 384f, 112f), 0.001f)
        // 已经到达目标位置 -> 剩余 0
        assertEquals(0f, CameraCurve.remainingPx(200f, 72f, 384f, 112f), 0.001f)
    }
}
