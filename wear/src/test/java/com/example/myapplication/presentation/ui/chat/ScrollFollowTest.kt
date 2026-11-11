package com.example.myapplication.presentation.ui.chat

import com.example.myapplication.presentation.ui.CameraCurve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 跟随滚动的策略契约（纯逻辑）。
 *
 * 真机证据（59fps 录制逐帧解出的 Δcy）：一次 243px 的自动滚动里
 * 9.5 / 16.5 / 22.5 / 31 / 36.5 / 40.5 / **19.5 / 32 / 12.5 / 22** —— 减速不单调。
 * 峰值 40.5px/帧 ≈ MAX_SPEED(2600px/s) × 16.9ms(43.9px)，说明速度已经打满，
 * 于是**每帧位移恰好等于当帧的剩余距离**：跳动的是目标，不是速度。
 */
class ScrollFollowTest {

    /** 真机观测到的目标（剩余距离）序列，单位 px。 */
    private val fieldTargets = listOf(9.5f, 16.5f, 22.5f, 31f, 36.5f, 40.5f, 19.5f, 32f, 12.5f, 22f)

    private val frameSeconds = 0.0169f

    // ------------------------------------------------------------------
    // 1. 合并：同一帧内的追加只产生一条滚动目标更新（跨帧不合并）
    // ------------------------------------------------------------------

    @Test
    fun `连续五十次追加只产生一条滚动目标更新`() {
        val requests = ScrollFollow.FollowRequests()
        repeat(50) { requests.request() }
        assertTrue("置位期间必须一直挂着请求", requests.hasPending)

        assertTrue("五十次追加只能被取走一次", requests.take())
        assertFalse("取走之后再来一次就是空", requests.take())
        assertFalse(requests.hasPending)
    }

    @Test
    fun `每帧两条追加就每帧各一条更新`() {
        val requests = ScrollFollow.FollowRequests()
        var updates = 0
        repeat(3) {
            requests.request()
            requests.request()
            if (requests.take()) updates++
        }
        assertEquals("每帧两条追加 = 每帧一条更新", 3, updates)
    }

    @Test
    fun `回合切换丢弃未取走的追加且不补滚`() {
        val requests = ScrollFollow.FollowRequests()
        requests.request()
        requests.clear()
        assertFalse("上一回合的追加不许在新回合补滚一次", requests.take())
        requests.request()
        assertTrue(requests.take())
    }

    @Test
    fun `同一帧内追加五十次只产生一条更新`() {
        // 合并的粒度是"一帧"：置位期间的追加全落成同一条，[ScrollFollow.FollowRequests.take] 一帧取一次
        val requests = ScrollFollow.FollowRequests()
        var updates = 0

        repeat(50) { requests.request() }          // 同一帧：五十次 token 追加
        if (requests.take()) updates++             // 镜头这一帧只取一次
        assertEquals("同一帧内五十次追加必须合并成恰好一条更新", 1, updates)

        if (requests.take()) updates++             // 紧接着的下一帧没有任何追加
        assertEquals("合并的那一条被取走之后不许残留", 1, updates)

        requests.request()                         // 再下一帧内容又长了
        if (requests.take()) updates++
        assertEquals("新一帧的追加必须自己再产生一条，不许被上一帧吃掉", 2, updates)
    }

    @Test
    fun `每帧至多一条更新所以十帧就是十条`() {
        // 合并只在**同一帧内**发生：驱动拿到的是"目标过期了"这一个布尔，每帧取一次 = 每帧一条。
        // 十帧各追加五次，结论必须是十条而不是一条 —— 跨帧合并会让跟随整整漏掉后九帧的增长。
        val requests = ScrollFollow.FollowRequests()
        val taken = mutableListOf<Boolean>()
        repeat(10) {
            repeat(5) { requests.request() }   // 每帧五次 token 追加
            taken += requests.take()
        }

        assertEquals("每帧恰好一条更新，十帧就是十条", 10, taken.count { it })
        assertEquals("每一帧都必须取到，不许被跨帧合并掉", List(10) { true }, taken)
    }

    // ------------------------------------------------------------------
    // 2. 目标限速：只限收缩方向
    // ------------------------------------------------------------------

    @Test
    fun `目标收缩每帧最多走一个预算`() {
        val budget = ScrollFollow.maxShrinkPx(frameSeconds)
        assertEquals(340f * 0.0169f, budget, 1e-3f)
        assertEquals("收缩不许一步到底", 40.5f - budget, ScrollFollow.limitTarget(40.5f, 19.5f, budget), 1e-3f)
        assertEquals("收缩很小时按真实值走", 39f, ScrollFollow.limitTarget(40.5f, 39f, budget), 1e-3f)
    }

    @Test
    fun `目标变大立刻承认不许滞后`() {
        val budget = ScrollFollow.maxShrinkPx(frameSeconds)
        assertEquals("新内容出现要马上追，不能被限速拖延", 80f, ScrollFollow.limitTarget(12f, 80f, budget), 1e-4f)
        assertEquals("相等也原样返回", 12f, ScrollFollow.limitTarget(12f, 12f, budget), 1e-4f)
    }

    @Test
    fun `退化输入不会把目标锁死`() {
        assertEquals("dt 为 0 时不许动目标", 40f, ScrollFollow.limitTarget(40f, 5f, ScrollFollow.maxShrinkPx(0f)), 1e-4f)
        assertEquals("负预算按 0 处理", 40f, ScrollFollow.limitTarget(40f, 5f, -7f), 1e-4f)
    }

    // ------------------------------------------------------------------
    // 3. 该不该跟随：用户手势与沉浸态优先
    // ------------------------------------------------------------------

    @Test
    fun `四个条件同时成立才允许跟随`() {
        assertTrue(
            "全部条件成立必须跟随",
            ScrollFollow.shouldFollow(true, userScrolling = false, nowMs = 1_000L, yieldUntilMs = 900L, immersive = false)
        )
        assertFalse(
            "用户正在拖/转表冠时一帧都不许抢",
            ScrollFollow.shouldFollow(true, userScrolling = true, nowMs = 1_000L, yieldUntilMs = 900L, immersive = false)
        )
        assertFalse(
            "让路窗口内不许抢",
            ScrollFollow.shouldFollow(true, userScrolling = false, nowMs = 800L, yieldUntilMs = 900L, immersive = false)
        )
        assertFalse(
            "沉浸态（审批/录音/输入/抽屉）不许抢",
            ScrollFollow.shouldFollow(true, userScrolling = false, nowMs = 1_000L, yieldUntilMs = 900L, immersive = true)
        )
        assertFalse(
            "未授权时其它条件再好也不许动",
            ScrollFollow.shouldFollow(false, userScrolling = false, nowMs = 1_000L, yieldUntilMs = 900L, immersive = false)
        )
        assertTrue(
            "让路窗口到期的下一帧立刻接回来",
            ScrollFollow.shouldFollow(true, userScrolling = false, nowMs = 900L, yieldUntilMs = 900L, immersive = false)
        )
    }

    // ------------------------------------------------------------------
    // 4. 真机序列回放：限速之后减速必须单调
    // ------------------------------------------------------------------

    /**
     * 按 ChatScreen 里的驱动结构回放：目标先过限速器，再进速度限速器与单帧夹取。
     *
     * 起始速度取 [CameraCurve.MAX_SPEED]：真机峰值 40.5px/帧 已经贴着 MAX_SPEED 的 43.9px 上限，
     * 说明这一段里每帧位移等于当帧剩余距离（被目标夹取，不是被速度夹取）。
     */
    private fun simulate(targets: List<Float>, limitTarget: Boolean): List<Float> {
        var speed = CameraCurve.MAX_SPEED
        var previousAim = targets.first()
        var hasAim = false
        return targets.map { raw ->
            val aim = if (limitTarget && hasAim) {
                ScrollFollow.limitTarget(previousAim, raw, ScrollFollow.maxShrinkPx(frameSeconds))
            } else {
                raw
            }
            hasAim = true
            previousAim = aim
            speed = CameraCurve.nextSpeed(speed, CameraCurve.targetSpeed(aim), frameSeconds)
            CameraCurve.stepPx(speed, frameSeconds, aim)
        }
    }

    private fun maxFrameDrop(steps: List<Float>): Float =
        steps.zipWithNext().maxOf { (a, b) -> a - b }

    private fun peakIndex(steps: List<Float>): Int = steps.indices.maxByOrNull { steps[it] } ?: 0

    @Test
    fun `真机序列不限速时必须复现减速不单调`() {
        val steps = simulate(fieldTargets, limitTarget = false)
        val tail = steps.drop(peakIndex(steps) + 1)
        assertTrue(
            "未限速的驱动必须能还原用户看到的来回跳，实际 " + steps,
            tail.zipWithNext().any { (a, b) -> b > a + 1e-3f }
        )
        assertTrue("单帧跌幅明显超过任何限速预算，实测 " + maxFrameDrop(steps), maxFrameDrop(steps) > 10f)
    }

    @Test
    fun `限速之后同一段目标的减速单调且单帧跌幅不超预算`() {
        val steps = simulate(fieldTargets, limitTarget = true)
        val tail = steps.drop(peakIndex(steps) + 1)
        assertTrue(
            "峰值之后必须单调不增（减速单调），实际 " + steps,
            tail.zipWithNext().all { (a, b) -> b <= a + 1e-3f }
        )
        val budget = ScrollFollow.maxShrinkPx(frameSeconds)
        assertTrue(
            "单帧跌幅不许超过收缩预算 " + budget + "，实测 " + maxFrameDrop(steps),
            maxFrameDrop(steps) <= budget + 1e-3f
        )
    }

    @Test
    fun `限速不会让整段滚动偏离原距离`() {
        val raw = simulate(fieldTargets, limitTarget = false).sum()
        val limited = simulate(fieldTargets, limitTarget = true).sum()
        assertTrue("限速后总位移不能被吃掉：" + limited + " vs " + raw, limited >= raw * 0.9f)
        assertTrue("也不许凭空多滚一大段：" + limited + " vs " + raw, limited <= raw * 1.3f)
    }
}
