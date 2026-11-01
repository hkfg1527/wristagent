package com.example.myapplication.presentation

import com.example.myapplication.presentation.ui.ReadingCamera
import com.example.myapplication.presentation.ui.ReadingCameraEvent
import com.example.myapplication.presentation.ui.ReadingCameraPhase
import com.example.myapplication.presentation.ui.ReadingPace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 提词器阅读节奏：慢速下滚速度由"人类默读下限"反推，
 * 500 字/分钟 × 0.7 ≈ 5.8 字/秒，14 字一行 ⇒ 每行 2.41s ⇒ 16dp / 2.41s ≈ 6.6dp/s。
 */
class ReadingPaceTest {

    @Test
    fun `每秒可读字数等于基准五百字乘零点七`() {
        assertEquals(500f, ReadingPace.MAX_READ_CHARS_PER_MIN, 0.001f)
        assertEquals(0.7f, ReadingPace.PACE_FACTOR, 0.001f)
        assertEquals(14, ReadingPace.CHARS_PER_LINE)
        assertEquals(16f, ReadingPace.LINE_HEIGHT_DP, 0.001f)
        assertEquals(350f / 60f, ReadingPace.charsPerSec(), 0.01f)
    }

    @Test
    fun `每行耗时由每行字数除以每秒字数推导`() {
        assertEquals(14f / (350f / 60f), ReadingPace.secondsPerLine(), 0.01f)
        assertEquals(2.4f, ReadingPace.secondsPerLine(), 0.02f)
    }

    @Test
    fun `下滚速度等于行高除以每行耗时`() {
        assertEquals(16f / (14f / (350f / 60f)), ReadingPace.panSpeedDpPerSec(), 0.01f)
        assertEquals(6.6f, ReadingPace.panSpeedDpPerSec(), 0.1f)
    }

    @Test
    fun `px 换算随屏幕密度线性`() {
        assertEquals(13.3f, ReadingPace.panSpeedPxPerSec(density = 2f), 0.2f)
        assertEquals(ReadingPace.panSpeedDpPerSec() * 1.5f, ReadingPace.panSpeedPxPerSec(density = 1.5f), 0.01f)
        assertEquals(ReadingPace.panSpeedDpPerSec() * 3f, ReadingPace.panSpeedPxPerSec(density = 3f), 0.01f)
    }

    @Test
    fun `阅读系数越大下滚越快且成正比`() {
        assertTrue(
            "系数大必须滚得更快",
            ReadingPace.panSpeedDpPerSec(0.7f) > ReadingPace.panSpeedDpPerSec(0.35f)
        )
        assertEquals(
            2f * ReadingPace.panSpeedDpPerSec(0.35f),
            ReadingPace.panSpeedDpPerSec(0.7f),
            0.01f
        )
    }
}

/** 提词器镜头状态机：对齐一次 -> 恒定慢速下滚 -> 遇到末尾/用户/强制态就停，绝不回弹也不自启。 */
class ReadingCameraTest {

    private fun aligned(): ReadingCamera = ReadingCamera().apply {
        onEvent(ReadingCameraEvent.TurnBodyAppeared)
        onEvent(ReadingCameraEvent.Aligned)
    }

    @Test
    fun `初始为空闲`() {
        assertEquals(ReadingCameraPhase.Idle, ReadingCamera().phase)
    }

    @Test
    fun `回合首次出现正文时进入对齐`() {
        val camera = ReadingCamera()
        assertEquals(ReadingCameraPhase.AlignToTurnStart, camera.onEvent(ReadingCameraEvent.TurnBodyAppeared))
    }

    @Test
    fun `对齐完成后进入匀速自动下滚`() {
        val camera = ReadingCamera()
        camera.onEvent(ReadingCameraEvent.TurnBodyAppeared)
        assertEquals(ReadingCameraPhase.AutoPan, camera.onEvent(ReadingCameraEvent.Aligned))
    }

    @Test
    fun `到达内容末尾后停止`() {
        val camera = aligned()
        assertEquals(ReadingCameraPhase.Idle, camera.onEvent(ReadingCameraEvent.ReachedEnd))
    }

    @Test
    fun `用户手动滚动立即停止并锁住本轮的自动下滚`() {
        val camera = aligned()
        assertEquals(ReadingCameraPhase.Idle, camera.onEvent(ReadingCameraEvent.UserScrolled))
        assertTrue(camera.isStoppedForTurn)
        // 对齐事件随后到达也不会把镜头重新推起来
        assertEquals(ReadingCameraPhase.Idle, camera.onEvent(ReadingCameraEvent.Aligned))
        assertEquals(ReadingCameraPhase.Idle, camera.phase)
    }

    @Test
    fun `审批录音等强制态立即停止且本轮不自启`() {
        val camera = aligned()
        assertEquals(ReadingCameraPhase.Idle, camera.onEvent(ReadingCameraEvent.Interrupted))
        assertTrue(camera.isStoppedForTurn)
        assertEquals(ReadingCameraPhase.Idle, camera.onEvent(ReadingCameraEvent.Aligned))
    }

    @Test
    fun `对齐期间被强制态打断也不会进入自动下滚`() {
        val camera = ReadingCamera()
        camera.onEvent(ReadingCameraEvent.TurnBodyAppeared)
        assertEquals(ReadingCameraPhase.Idle, camera.onEvent(ReadingCameraEvent.Interrupted))
        assertEquals(ReadingCameraPhase.Idle, camera.onEvent(ReadingCameraEvent.Aligned))
    }

    @Test
    fun `新一轮开始重新对齐并解除上一轮的停止`() {
        val camera = aligned()
        camera.onEvent(ReadingCameraEvent.UserScrolled)
        assertTrue(camera.isStoppedForTurn)

        assertEquals(ReadingCameraPhase.AlignToTurnStart, camera.onEvent(ReadingCameraEvent.TurnBodyAppeared))
        assertFalse(camera.isStoppedForTurn)
        assertEquals(ReadingCameraPhase.AutoPan, camera.onEvent(ReadingCameraEvent.Aligned))
    }

    @Test
    fun `自动下滚中被打断同样停止`() {
        val camera = aligned()
        assertEquals(ReadingCameraPhase.AutoPan, camera.phase)
        assertEquals(ReadingCameraPhase.Idle, camera.onEvent(ReadingCameraEvent.Interrupted))
        assertEquals(ReadingCameraPhase.Idle, camera.onEvent(ReadingCameraEvent.ReachedEnd))
    }

    @Test
    fun `空闲态收到对齐完成事件不会自己启动下滚`() {
        val camera = ReadingCamera()
        assertEquals(ReadingCameraPhase.Idle, camera.onEvent(ReadingCameraEvent.Aligned))
        assertEquals(ReadingCameraPhase.Idle, camera.onEvent(ReadingCameraEvent.ReachedEnd))
    }

    /** 用户要求提词器再快一倍：倍率只作用于界面速度，不改变人类读速模型（PACE_FACTOR 保持 0.7）。 */
    @Test
    fun `用户倍率提快下滚且仍属极慢`() {
        val base = ReadingPace.panSpeedDpPerSec()
        val boosted = ReadingPace.panSpeedDpPerSec(speedMultiplier = ReadingPace.USER_SPEED_MULTIPLIER)
        // 倍率必须 >1（真机实测 0.7 读速太慢），但不写死具体倍数，方便继续调参
        assertTrue("界面倍率必须大于 1", ReadingPace.USER_SPEED_MULTIPLIER > 1f)
        assertEquals(base * ReadingPace.USER_SPEED_MULTIPLIER, boosted, 0.001f)
        assertEquals("人类读速模型本身不随产品提速改变", 0.7f, ReadingPace.PACE_FACTOR, 0.0001f)
        // 上限校验：即便提速，一屏 94dp 也要 2.5 秒以上才走完，不能变成"刷屏"
        assertTrue("提速后仍须是慢速阅读", 94f / boosted >= 2.5f)
    }
}
