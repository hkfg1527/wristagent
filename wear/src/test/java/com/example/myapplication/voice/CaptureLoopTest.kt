package com.example.myapplication.voice

import com.example.myapplication.voice.CaptureLoop.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureLoopTest {

    @Test
    fun activeReadsWithDataContinue() {
        assertEquals("读到 512 字节应继续", Decision.CONTINUE, CaptureLoop.decide(active = true, read = 512))
        assertEquals("读满缓冲应继续", Decision.CONTINUE, CaptureLoop.decide(active = true, read = 4096))
    }

    @Test
    fun activeReadOfZeroContinues() {
        assertEquals("读 0 字节不是错误，由循环自身决定退出", Decision.CONTINUE, CaptureLoop.decide(active = true, read = 0))
    }

    @Test
    fun cancellationWinsOverReadError() {
        assertEquals(
            "已取消时即使返回错误码也必须按取消处理，不能报录音失败",
            Decision.CANCELLED,
            CaptureLoop.decide(active = false, read = -3)
        )
    }

    @Test
    fun negativeReadWithoutCancellationIsReadError() {
        assertEquals(
            "未取消时的负读数属于真实录音错误",
            Decision.READ_ERROR,
            CaptureLoop.decide(active = true, read = -3)
        )
    }
}
