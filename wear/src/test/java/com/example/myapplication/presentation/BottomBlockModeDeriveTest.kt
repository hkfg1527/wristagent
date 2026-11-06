package com.example.myapplication.presentation

import com.example.myapplication.presentation.ui.BottomBlockMode
import com.example.myapplication.presentation.ui.MainInputType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 底栏形态的派生契约。
 *
 * 真机现象：点过按钮之后底栏形态开始互相覆盖 —— 录音中被"思考停止"抢走、回答一结束
 * 停止胶囊又消失、审批放行后按钮回不来。根因是四处写者各自 copy 同一个字段。
 * 这里把形态收敛成一个纯函数：审批 > 录音 > 执行中 > 空闲。
 */
class BottomBlockModeDeriveTest {

    private fun idle() = ChatUiState.initial(MainInputType.VOICE)

    @Test
    fun `全空闲时是闲置主键`() {
        assertEquals(BottomBlockMode.IDLE, idle().deriveBottomBlockMode())
    }

    @Test
    fun `执行中给停止胶囊`() {
        assertEquals(
            BottomBlockMode.THINKING_STOP,
            idle().copy(isExecuting = true, isThinkingActive = true).deriveBottomBlockMode()
        )
    }

    @Test
    fun `正文流式阶段也是停止胶囊_不能被写回闲置`() {
        // 引擎在吐正文时会顺手把 bottomBlockMode 写回 IDLE，此时用户必须还能看见"停止"
        val streaming = idle().copy(
            isExecuting = true,
            isThinkingActive = false,
            bottomBlockMode = BottomBlockMode.IDLE
        )
        assertEquals(BottomBlockMode.THINKING_STOP, streaming.deriveBottomBlockMode())
    }

    @Test
    fun `录音优先于执行中`() {
        assertEquals(
            BottomBlockMode.RECORDING,
            idle().copy(isExecuting = true, isRecordingVoice = true).deriveBottomBlockMode()
        )
        assertEquals(
            BottomBlockMode.RECORDING,
            idle().copy(isRecordingVoice = true).deriveBottomBlockMode()
        )
    }

    @Test
    fun `审批优先于录音与执行中`() {
        val approval = idle().copy(
            isExecuting = true,
            isRecordingVoice = true,
            bottomBlockMode = BottomBlockMode.ACTION_ALLOW
        )
        assertEquals(BottomBlockMode.ACTION_ALLOW, approval.deriveBottomBlockMode())
    }

    @Test
    fun `同一帧里多个写者互相覆盖后派生结果只认最高优先级`() {
        // 延迟 300ms 的"拉长成停止胶囊"写入把录音态覆盖了：派生层必须无视这次覆盖
        val raced = idle().copy(
            isExecuting = true,
            isRecordingVoice = true,
            bottomBlockMode = BottomBlockMode.THINKING_STOP
        )
        assertEquals(BottomBlockMode.RECORDING, raced.deriveBottomBlockMode())
        // 同一个快照反复派生必须稳定（不依赖调用次数、不累积内部状态）
        assertEquals(raced.deriveBottomBlockMode(), raced.deriveBottomBlockMode())
        assertEquals(raced.deriveBottomBlockMode(), raced.copy().deriveBottomBlockMode())
    }

    @Test
    fun `录音结束后立刻回到执行中的停止胶囊`() {
        val released = idle().copy(
            isExecuting = true,
            isRecordingVoice = false,
            bottomBlockMode = BottomBlockMode.IDLE
        )
        assertEquals(BottomBlockMode.THINKING_STOP, released.deriveBottomBlockMode())
    }
}
