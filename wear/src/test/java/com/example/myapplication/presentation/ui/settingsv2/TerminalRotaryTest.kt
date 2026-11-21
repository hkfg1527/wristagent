package com.example.myapplication.presentation.ui.settingsv2

import com.example.myapplication.presentation.ui.settingsv2.style.rotaryLinesToScroll
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R16：表冠**按行**滚动的判据。
 *
 * 真终端（`less` / `more`）是一行一行翻的。表冠给出的位移是连续像素，所以必须量化成整行，
 * 余量留在累积器里 —— 否则内容会停在"半行悬空"的位置，字符网格立刻破掉。
 */
class TerminalRotaryTest {

    private val line = 36f

    @Test
    fun partialTurnDoesNotScrollUntilAFullLineAccumulates() {
        assertEquals("转 12px（不足一行）不该滚", 12f to 0, rotaryLinesToScroll(0f, 12f, line))
        assertEquals("余量 12 + 12 = 24，仍不足一行", 24f to 0, rotaryLinesToScroll(12f, 12f, line))
        assertEquals("余量 24 + 12 = 36，正好一行且余量为 0", 0f to 1, rotaryLinesToScroll(24f, 12f, line))
        // 反过来也要成立：20 + 10 = 30 不足一行，就不许滚（曾经把 20+10 误写成 36，测试先红了）
        assertEquals("余量 20 + 10 = 30，不足一行", 30f to 0, rotaryLinesToScroll(20f, 10f, line))
    }

    @Test
    fun multiLineTurnScrollsWholeLinesAndKeepsRemainder() {
        assertEquals("40px = 1 行 + 余 4px", 4f to 1, rotaryLinesToScroll(0f, 40f, line))
        assertEquals("71px = 1 行 + 余 35px（不足两行）", 35f to 1, rotaryLinesToScroll(0f, 71f, line))
        assertEquals("72px = 正好 2 行", 0f to 2, rotaryLinesToScroll(0f, 72f, line))
    }

    @Test
    fun negativeTurnScrollsBackwardsByWholeLines() {
        assertEquals("-40px = 回 1 行 + 余 -4px", -4f to -1, rotaryLinesToScroll(0f, -40f, line))
        assertEquals("余量 -20 + -20 = -40 = 回 1 行 + 余 -4", -4f to -1, rotaryLinesToScroll(-20f, -20f, line))
    }

    @Test
    fun remainderNeverReachesOneLine() {
        var acc = 0f
        repeat(20) {
            val (rest, _) = rotaryLinesToScroll(acc, 7f, line)
            acc = rest
            assertTrue("余量必须恒小于一行，实际 $acc", kotlin.math.abs(acc) < line)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroLineHeightIsRejected() {
        rotaryLinesToScroll(0f, 10f, 0f)
    }

    /**
     * R17：真终端的行是**打印出来的**，不是淡入的；而且行必须是"可执行对象"（点一下有输出）。
     */
    @Test
    fun terminalRowsArePrintedNotFadedInAndAreExecutable() {
        val src = File("wear/src/main/java/com/example/myapplication/presentation/ui/settingsv2/style/TerminalStyle.kt")
            .let { if (it.exists()) it else File("../wear/src/main/java/com/example/myapplication/presentation/ui/settingsv2/style/TerminalStyle.kt") }
            .readText()
        assertFalse(
            "终端行不许做入场淡入（enterAppearance 是 UI 语法，终端是直接打印）",
            src.contains("enterAppearance")
        )
        assertTrue("行必须可点（真终端的行是可执行对象）", src.contains(".clickable("))
        assertTrue("点击结果必须回显到列表末尾", src.contains("pushEcho("))
        assertTrue("开关状态必须有本地态（点一下翻一次）", src.contains("mutableStateMapOf"))
    }

    /**
     * 光标只在**有活动**时闪：静止必须常亮，否则界面永不 idle，
     * `uiautomator dump` 报 `could not get idle state`，整条真机取证链路会断（实测 release-check 掉到 6/8）。
     */
    @Test
    fun cursorBlinksOnlyWhileScrolling() {
        val src = File("wear/src/main/java/com/example/myapplication/presentation/ui/settingsv2/style/TerminalStyle.kt")
            .let { if (it.exists()) it else File("../wear/src/main/java/com/example/myapplication/presentation/ui/settingsv2/style/TerminalStyle.kt") }
            .readText()
        assertTrue(
            "闪烁必须由滚动状态门控（isActive = state.isScrollInProgress）",
            src.contains("isActive = state.isScrollInProgress")
        )
        assertTrue("闪烁的副作用必须挂在 isActive 上", src.contains("LaunchedEffect(isActive)"))
        assertFalse(
            "静止时不许留任何周期性重绘（进场闪 5 次那版已证明会断掉 dump）",
            src.contains("repeat(5)")
        )
    }

    /**
     * R24：`> ` 是**可交互行的提示符**，不是每条输出的前缀。
     *
     * TUI 里 `>` 表示"这行能按"；只读信息只是缩进。整页都带 `>` 会让"能按/只能看"失去区分，
     * 也让终端看起来像给每条日志加了装饰。
     */
    @Test
    fun promptMarksOnlyActionableRows() {
        val src = File("wear/src/main/java/com/example/myapplication/presentation/ui/settingsv2/style/TerminalStyle.kt")
            .let { if (it.exists()) it else File("../wear/src/main/java/com/example/myapplication/presentation/ui/settingsv2/style/TerminalStyle.kt") }
            .readText()
        assertTrue(
            "前缀必须按类型分流（可交互 `> ` / 只读两空格）",
            src.contains("if (actionable) TerminalPrompt else \"  \"")
        )
        assertTrue(
            "可交互行的判定必须只看 Toggle/Chevron",
            src.contains("val actionable = row.trailing is RowTrailing.Toggle || row.trailing is RowTrailing.Chevron")
        )
        assertTrue(
            "只读值必须降成暗绿（与可交互行分开层级）",
            src.contains("is RowTrailing.Value -> trailing.text to true")
        )
    }

    /**
     * R19：表冠必须在 **Activity 层**接，不依赖 Compose 焦点。
     *
     * 实测依据：注入 REL_WHEEL 后 `onRotaryScrollEvent` 一行日志都没有，但焦点确实在主 Activity ——
     * Compose 的 rotary 回调依赖节点持有焦点，真机上不可靠。所以改在 `onGenericMotionEvent` 里接，
     * 并用 [RotaryBus.enabled] 做安全阀（否则会把聊天页的表冠滚动一起吞掉）。
     */
    @Test
    fun rotaryIsWiredAtTheActivityLevelWithoutFocus() {
        fun read(rel: String) = File("wear/src/main/java/com/example/myapplication/presentation/$rel")
            .let { if (it.exists()) it else File("../wear/src/main/java/com/example/myapplication/presentation/$rel") }
            .readText()
        val activity = read("MainActivity.kt")
        val terminal = read("ui/settingsv2/style/TerminalStyle.kt")
        val bus = read("ui/settingsv2/common/RotaryBus.kt")

        assertTrue("Activity 必须重写 onGenericMotionEvent", activity.contains("override fun onGenericMotionEvent"))
        assertTrue("Activity 必须把表冠增量交给总线", activity.contains("RotaryBus.emit("))
        assertTrue("Activity 必须先看安全阀", activity.contains("if (RotaryBus.enabled"))
        assertTrue("终端必须订阅总线", terminal.contains("RotaryBus.events.collect"))
        assertTrue(
            "进入终端页必须打开安全阀、离开必须关掉",
            terminal.contains("RotaryBus.enabled = true") && terminal.contains("RotaryBus.enabled = false")
        )
        assertTrue("总线必须带安全阀开关", bus.contains("var enabled"))
        // 第二条可能通道：表冠被映射成音量键的机型（input keyevent 可注入，因此这条可端到端验证）
        assertTrue(
            "Activity 必须也接音量键音量通道（表冠的音量映射）",
            activity.contains("override fun onKeyDown") &&
                activity.contains("KEYCODE_VOLUME_DOWN") &&
                activity.contains("RotaryBus.emit(")
        )
    }

    /**
     * 表冠只接在 TERMINAL 上：另外四套是卡片/列表 UI，表冠该由系统列表语义处理，
     * 不该被我们按行抢走（会把顺滑滑动变成一格一格跳）。
     */
    @Test
    fun onlyTerminalWiresTheRotary() {
        fun src(file: String) = File("wear/src/main/java/com/example/myapplication/presentation/ui/settingsv2/style/$file")
            .let { if (it.exists()) it else File("../wear/src/main/java/com/example/myapplication/presentation/ui/settingsv2/style/$file") }
            .readText()
        assertTrue("TERMINAL 必须接表冠", src("TerminalStyle.kt").contains("onRotaryScrollEvent"))
        listOf("M3CardStyle.kt", "M3ListStyle.kt", "ArcStyle.kt", "SquareStyle.kt").forEach {
            assertFalse("$it 不该抢表冠事件", src(it).contains("onRotaryScrollEvent"))
        }
    }
}
