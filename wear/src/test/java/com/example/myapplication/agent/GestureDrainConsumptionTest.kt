package com.example.myapplication.agent

import com.example.myapplication.readProjectFile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 缺口 1 的闭环测试：drain 出来的整批动作必须真的被执行。
 *
 * 上一版的漏洞是"按键收到了、队列里也有货，但唯一的消费点只认 STOP_TURN"，
 * 于是 SWITCH_MODEL / FORGET_REMEMBERED 被 drain 出来后直接扔掉 —— 设置里是开关，按下去等于没按。
 * 因此这里的断言全部落在可观测状态上（模型名 / 放行清单 / 队列是否被取走），
 * 而不是"某个函数被调用过"。
 */
class GestureDrainConsumptionTest {

    private lateinit var prefs: AgentPreferences

    @Before
    fun setUp() {
        GestureRequestQueue.clear()
        prefs = AgentPreferences(FakeSharedPreferences())
        prefs.cachedRemoteModels = "deepseek-chat,deepseek-reasoner,deepseek-coder"
        prefs.modelName = "deepseek-chat"
    }

    @After
    fun tearDown() {
        GestureRequestQueue.clear()
    }

    /** 与 AgentDispatcher 完全一致的消费方式：一批 drain 出来的动作整体结算。 */
    private fun consumeDrained(): GestureDrainOutcome =
        GestureRuntime(prefs).applyDrained(GestureRequestQueue.drain())

    // ------------------------------------------------------------ 切模型

    @Test
    fun `切模型之后下一轮请求的 model 参数就是新模型`() {
        assertEquals("前置条件：请求体取的是当前模型", "deepseek-chat", requestModelName(prefs))

        GestureRequestQueue.post(GestureAction.SWITCH_MODEL)
        val drained = consumeDrained()

        assertFalse("切模型不是终止，不该顺手打死当前 turn", drained.stopTurn)
        assertEquals(1, drained.outcomes.size)
        assertEquals(GestureAction.SWITCH_MODEL, drained.outcomes.single().action)
        assertEquals("deepseek-reasoner", drained.outcomes.single().modelName)
        assertEquals("下一轮请求的 model 参数必须是新模型", "deepseek-reasoner", requestModelName(prefs))
        assertEquals("drain 是取走语义：这一批已经没了", emptyList<GestureAction>(), GestureRequestQueue.drain())
    }

    @Test
    fun `连续两次切模型按清单顺序前进不会被写死`() {
        GestureRequestQueue.post(GestureAction.SWITCH_MODEL)
        consumeDrained()
        assertEquals("deepseek-reasoner", requestModelName(prefs))

        GestureRequestQueue.post(GestureAction.SWITCH_MODEL)
        consumeDrained()
        assertEquals("再切一次要落到第三个，而不是停在同一个值", "deepseek-coder", requestModelName(prefs))
    }

    // ------------------------------------------------------------ 忘掉记住的命令

    @Test
    fun `忘掉记住的命令之后放行清单为空`() {
        assertTrue(prefs.rememberShellCommand("pm list packages"))
        assertTrue(prefs.rememberShellCommand("dumpsys battery"))
        assertEquals(2, prefs.rememberedShellCommandList().size)
        assertTrue(
            "前置条件：清单非空时这条命令确实被放行",
            ShellPolicy.isRemembered("pm list packages", prefs.rememberedShellCommandList())
        )

        GestureRequestQueue.post(GestureAction.FORGET_REMEMBERED)
        val drained = consumeDrained()

        assertFalse(drained.stopTurn)
        assertEquals(GestureAction.FORGET_REMEMBERED, drained.outcomes.single().action)
        assertEquals("放行清单必须真的清空", emptyList<String>(), prefs.rememberedShellCommandList())
        assertEquals(0, prefs.rememberedShellCommandCount)
        assertFalse(
            "清空之后同一条命令不再被放行",
            ShellPolicy.isRemembered("pm list packages", prefs.rememberedShellCommandList())
        )
    }

    // ------------------------------------------------------------ 终止 + 其余动作同一批

    @Test
    fun `终止与切模型同时入队时两者都生效且终止仍然终止`() {
        GestureRequestQueue.post(GestureAction.STOP_TURN)
        GestureRequestQueue.post(GestureAction.SWITCH_MODEL)

        val drained = consumeDrained()

        assertTrue("STOP_TURN 必须仍然终止当前 turn", drained.stopTurn)
        assertEquals("同一批里排在后面的动作不能被终止吞掉", "deepseek-reasoner", prefs.modelName)
        assertEquals("消费侧不许把 STOP_TURN 再投回队列", emptyList<GestureAction>(), GestureRequestQueue.drain())
        assertFalse("下一次消费不能再拿到终止，否则会打断下一个任务", consumeDrained().stopTurn)
    }

    @Test
    fun `同一批里动作先后顺序不影响结算结果`() {
        listOf(
            listOf(GestureAction.STOP_TURN, GestureAction.SWITCH_MODEL),
            listOf(GestureAction.SWITCH_MODEL, GestureAction.STOP_TURN)
        ).forEach { batch ->
            GestureRequestQueue.clear()
            prefs.modelName = "deepseek-chat"
            assertTrue(prefs.rememberShellCommand("pm list packages"))
            batch.forEach { GestureRequestQueue.post(it) }
            GestureRequestQueue.post(GestureAction.FORGET_REMEMBERED)

            val drained = consumeDrained()

            assertTrue(batch.toString(), drained.stopTurn)
            assertEquals(batch.toString(), "deepseek-reasoner", prefs.modelName)
            assertEquals(batch.toString(), 0, prefs.rememberedShellCommandCount)
        }
    }

    // ------------------------------------------------------------ 边界

    @Test
    fun `NONE 不产生任何副作用`() {
        assertTrue(prefs.rememberShellCommand("pm list packages"))
        GestureRequestQueue.clear()

        val drained = GestureRuntime(prefs).applyDrained(listOf(GestureAction.NONE))

        assertFalse(drained.stopTurn)
        assertFalse("NONE 不算执行成功", drained.appliedOthers)
        assertEquals("deepseek-chat", prefs.modelName)
        assertEquals(1, prefs.rememberedShellCommandCount)
        assertFalse(GestureRequestQueue.consumeStopTurn())
    }

    @Test
    fun `同一个动作只执行一次`() {
        GestureRequestQueue.post(GestureAction.SWITCH_MODEL)
        consumeDrained()
        assertEquals("deepseek-reasoner", prefs.modelName)

        val second = consumeDrained()

        assertFalse(second.stopTurn)
        assertEquals("队列已取走，不能留着再执行一次", emptyList<GestureOutcome>(), second.outcomes)
        assertEquals("模型不许被同一批按键连切两下", "deepseek-reasoner", prefs.modelName)
    }

    @Test
    fun `输入侧只投递不执行消费侧才落地`() {
        // 输入侧（按键层）就是 MainActivity 的那一句：GestureRequestQueue.post(action)
        GestureRequestQueue.post(GestureAction.FORGET_REMEMBERED)
        assertTrue(prefs.rememberShellCommand("pm list packages"))
        assertEquals("只入队还没人执行", 1, prefs.rememberedShellCommandCount)

        consumeDrained()

        assertEquals("消费侧执行之后才清空", 0, prefs.rememberedShellCommandCount)
    }

    // ------------------------------------------------------------ 接线守卫

    @Test
    fun `调度方的消费点已经换成整批执行`() {
        val dispatcher = readProjectFile(
            "src/main/java/com/example/myapplication/agent/AgentDispatcher.kt"
        )
        assertTrue(
            "工具循环必须把整批动作交给执行器",
            dispatcher.contains("gestureRuntime.applyDrained(GestureRequestQueue.drain())")
        )
        assertFalse(
            "只认 STOP_TURN 的旧消费点必须消失：它会把切模型 / 忘掉清单丢掉",
            dispatcher.contains("consumeStopTurn()")
        )
        assertTrue(
            "手势执行器必须是调度方持久的成员，而不是每次临时 new 出来的",
            dispatcher.contains("private val gestureRuntime = GestureRuntime(preferences)")
        )
        assertTrue(
            "停止的判据仍然来自执行器的 stopTurn",
            dispatcher.contains("if (isCancelled || gestures.stopTurn)")
        )
    }
}
