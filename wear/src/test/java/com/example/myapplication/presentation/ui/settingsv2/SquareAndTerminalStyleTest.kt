package com.example.myapplication.presentation.ui.settingsv2

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.wear.compose.material3.MaterialTheme
import com.example.myapplication.presentation.ui.settingsv2.style.SquareHairlineHeight
import com.example.myapplication.presentation.ui.settingsv2.style.SquareRowMinHeight
import com.example.myapplication.presentation.ui.settingsv2.style.TerminalBackground
import com.example.myapplication.presentation.ui.settingsv2.style.TerminalForeground
import com.example.myapplication.presentation.ui.settingsv2.style.TerminalLineHeight
import com.example.myapplication.presentation.ui.settingsv2.style.TerminalPrompt
import com.example.myapplication.presentation.ui.settingsv2.style.TerminalPromptTag
import com.example.myapplication.presentation.ui.settingsv2.style.TerminalRowTag
import com.example.myapplication.presentation.ui.settingsv2.style.TerminalScrollBarTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * R4：`SQUARE` 与 `TERMINAL` 两套的判据。
 *
 * 方角：角半径 0（身份参数）、发丝线 **0.5dp = 1px@2.0**、行高 ≥48dp；
 * 终端：`> ` 前缀真的在文本里、前景 `#3BFF9A`、背景 `#0B0F0C`、且**不做磁滑吸附**。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w200dp-h200dp")
class SquareAndTerminalStyleTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun squareIsZeroCornerWithOnePixelHairline() {
        assertEquals(
            "方角档圆角必须是 0",
            0f,
            SettingsV2Style.SQUARE.identity.cornerRadius.value,
            0.0001f
        )
        assertEquals(
            "发丝线必须是 0.5dp（density 2.0 下 = 1 物理像素）",
            0.5f,
            SquareHairlineHeight.value,
            0.0001f
        )
        assertTrue("方角行高必须 ≥48dp", SquareRowMinHeight.value >= 48f)

        compose.setContent { MaterialTheme { SettingsV2Screen(style = SettingsV2Style.SQUARE) } }
        compose.waitForIdle()
        compose.onNodeWithText("连得上").assertIsDisplayed()
        assertTrue(
            "方角档必须画出带 testTag 的行",
            compose.onAllNodesWithTag("square-row").fetchSemanticsNodes().isNotEmpty()
        )
    }

    /**
     * R13：SQUARE 与 M3_LIST 必须在**行距**上就能分开，不能只靠对齐差异。
     *
     * 真机曾实测两套行距 112px(56.0dp) vs 114px(57.0dp) —— 差 2px，同屏无法区分。
     * 判据：行高差 ≥12dp（真机行距差 ≈24px），且方角档不得低于 48dp 触控下限。
     */
    @Test
    fun squareIsLooserThanM3ListByAtLeastTwelveDp() {
        val square = SettingsV2Style.SQUARE.identity.rowMinHeight.value
        val list = SettingsV2Style.M3_LIST.identity.rowMinHeight.value
        assertTrue(
            "方角行高必须仍是触控下限以上：$square",
            square >= 48f
        )
        assertTrue(
            "方角(${square}dp) 与官方列表(${list}dp) 的行高差必须 ≥12dp，否则一屏看不出区别",
            square - list >= 12f
        )
    }

    /**
     * R13：分隔线的**策略**必须不同 —— 官方列表通栏，方角内缩。
     *
     * 这条用源码守卫锁住：两套的线都通栏时，同屏只剩"标题居中"一处差异（真机实测两套行距
     * 差 2px 时就是这样）。守卫读源码，防止以后有人"统一一下"把它们改成一样。
     */
    @Test
    fun squareHairlineIsInsetWhileTheOfficialListIsFullBleed() {
        fun src(rel: String): String = sequenceOf(File("."), File("wear"), File("../wear"))
            .map { it.absoluteFile }
            .map { File(it, "src/main/java/com/example/myapplication/presentation/ui/settingsv2/style/$rel") }
            .first { it.exists() }
            .readText()
        val square = src("SquareStyle.kt")
        val list = src("M3ListStyle.kt")
        assertTrue(
            "方角发丝线必须内缩（padding(horizontal = SquareHairlineInset)）",
            square.contains("padding(horizontal = SquareHairlineInset)")
        )
        assertTrue(
            "官方列表的发丝线必须保持通栏，不能也内缩",
            !list.contains("SquareHairlineInset")
        )
    }

    @Test
    fun terminalRowsCarryPromptPrefixAndTerminalColours() {
        assertEquals("终端提示符必须是 '> '", "> ", TerminalPrompt)
        assertEquals(
            "终端背景必须是 #0B0F0C",
            androidx.compose.ui.graphics.Color(0xFF0B0F0C),
            TerminalBackground
        )
        assertEquals(
            "终端前景必须是 #3BFF9A",
            androidx.compose.ui.graphics.Color(0xFF3BFF9A),
            TerminalForeground
        )
        assertFalse("终端必须关闭磁滑吸附（连续滚动）", SettingsV2Style.TERMINAL.identity.snapScroll)
        // 判据换了：48dp 是"触控目标"的下限，而终端行不是触控目标（点击由滚动面上的其它入口负责）。
        // 真终端是字符网格：行高 18dp（36px），一屏 12 行。旧口径让它退化成"一屏 4 行的列表"。
        assertEquals(
            "终端行高必须落在字符网格上（18dp = 36px）",
            18f,
            TerminalLineHeight.value,
            0.01f
        )
        assertTrue(
            "一屏必须能放 ≥10 行（高度 466px / 36px ≈ 12 行），否则又变回列表",
            466f / (TerminalLineHeight.value * 2f) >= 10f
        )
        assertTrue(
            "终端行高不得低于 16dp（再低等宽字会挤在一起）",
            TerminalLineHeight.value >= 16f
        )

        compose.setContent { MaterialTheme { SettingsV2Screen(style = SettingsV2Style.TERMINAL) } }
        compose.waitForIdle()
        assertTrue(
            "终端行必须以 '> ' 开头",
            compose.onAllNodesWithText(">", substring = true).fetchSemanticsNodes().isNotEmpty()
        )
        assertTrue(
            "终端必须画出带 testTag 的行",
            compose.onAllNodesWithTag(TerminalRowTag).fetchSemanticsNodes().isNotEmpty()
        )
        assertTrue(
            "终端必须画出 ASCII 滚动条",
            compose.onAllNodesWithTag(TerminalScrollBarTag).fetchSemanticsNodes().isNotEmpty()
        )
        assertTrue(
            "提示符必须钉在底部（独立节点，不随内容滚走）",
            compose.onAllNodesWithTag(TerminalPromptTag).fetchSemanticsNodes().isNotEmpty()
        )
        assertTrue(
            "终端的换风格入口必须存在且是同一个 tag",
            compose.onAllNodesWithTag(StyleEntryTag).fetchSemanticsNodes().isNotEmpty()
        )
    }

    /**
     * R37：装饰字符不能进无障碍树。
     *
     * 规则线是 30 个 `─`、滚动条是 12 个 `▓/░`。读屏会把它们当内容念出来（"横线横线…"、
     * "方块方块浅色方块…"），对盲用用户是纯噪声。所以这两处必须 `clearAndSetSemantics { }`，
     * 而内容行（`# 连得上`、`> 工具与 MCP`）仍要可读。
     */
    @Test
    fun decorativeGlyphsStayOutOfTheSemanticsTree() {
        compose.setContent { MaterialTheme { SettingsV2Screen(style = SettingsV2Style.TERMINAL) } }
        compose.waitForIdle()

        val allTexts = compose.onAllNodes(hasText("", substring = true))
            .fetchSemanticsNodes()
            .map { it.config.toString() }
        assertTrue("内容行必须仍可读（# 分区头）", allTexts.any { it.contains("连得上") })
        assertTrue(
            "装饰字符不许进语义树，实际节点=${allTexts.joinToString(" | ")}",
            allTexts.none { it.contains("─") || it.contains("░") || it.contains("▓") }
        )

        val src = File("wear/src/main/java/com/example/myapplication/presentation/ui/settingsv2/style/TerminalStyle.kt")
            .let { if (it.exists()) it else File("../wear/src/main/java/com/example/myapplication/presentation/ui/settingsv2/style/TerminalStyle.kt") }
            .readText()
        assertTrue(
            "两处装饰都必须 clearAndSetSemantics",
            src.contains("text = TerminalRule.repeat(TerminalRuleCells),") && src.contains("clearAndSetSemantics")
        )
    }

    /**
     * R31：终端里的点击**不能有 Material 涟漪**。
     *
     * `Modifier.clickable` 默认用 `LocalIndication`（M3 是涟漪）。真终端点一行不会荡出一圈光晕 ——
     * 这是"看起来像终端"和"真的是终端"之间很小但很明显的一处。所以终端侧一律 `indication = null`。
     */
    /**
     * R55：读屏要能说清"这行能干什么、现在什么状态"。
     *
     * 终端是一屏字符：`> 工具与 MCP   /open`、`[ OFF ]` 对 TalkBack 只是乱码。
     * 所以可操作行要带 contentDescription —— 跳转说"打开"，开关说"开/关"；
     * 只读行（模型/温度）**不加**描述，它本来就只是信息。
     */
    @Test
    fun actionableRowsDescribeTheirActionAndState() {
        // 自定义最小数据：可操作行必须**在视口内**（用全量示例数据时它们在首屏之外，
        // 没被组合就查不到语义 —— 这是本轮踩过的坑，真机 dump 里其实是有 content-desc 的）
        val sections = listOf(
            SettingsV2Section(
                title = "区",
                rows = listOf(
                    SettingsV2Row("jump", "跳转项", trailing = RowTrailing.Chevron),
                    SettingsV2Row("switch", "开关项", trailing = RowTrailing.Toggle(false) {}),
                    SettingsV2Row("readonly", "只读项", trailing = RowTrailing.Value("v")),
                ),
            )
        )
        compose.setContent {
            MaterialTheme { SettingsV2Screen(style = SettingsV2Style.TERMINAL, sections = sections) }
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("跳转项，打开", substring = true, useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("开关项，关", substring = true, useUnmergedTree = true).assertExists()
        assertTrue(
            "只读行不该有描述（它只是信息）",
            compose.onAllNodesWithContentDescription("只读项", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun terminalClicksHaveNoRipple() {
        listOf(
            "wear/src/main/java/com/example/myapplication/presentation/ui/settingsv2/style/TerminalStyle.kt",
            "wear/src/main/java/com/example/myapplication/presentation/ui/settingsv2/TerminalStylePicker.kt",
        ).forEach { rel ->
            val src = File(rel).let { if (it.exists()) it else File("../$rel") }.readText()
            assertFalse(
                "$rel 里不许有裸 `.clickable {`（会用默认涟漪）",
                src.contains(".clickable {") || src.contains(".clickable(onClick")
            )
            assertTrue("$rel 必须显式 indication = null", src.contains("indication = null"))
        }
    }

    /**
     * R30：选择器开着时，返回键只能关选择器。
     *
     * 真机实测（0.0.0.215）：菜单打开时按返回，整个设置页被关掉、直接回到聊天页 —— 一次返回丢了上下文。
     * 守卫：SettingsV2Screen 必须用 `BackHandler(enabled = pickerOpen)` 拦截。
     */
    @Test
    fun backKeyClosesThePickerInsteadOfThePage() {
        val src = File("wear/src/main/java/com/example/myapplication/presentation/ui/settingsv2/SettingsV2Screen.kt")
            .let { if (it.exists()) it else File("../wear/src/main/java/com/example/myapplication/presentation/ui/settingsv2/SettingsV2Screen.kt") }
            .readText()
        assertTrue("必须用 BackHandler 拦截返回", src.contains("BackHandler(enabled = pickerOpen)"))
        assertTrue("拦截后只关选择器", src.contains("BackHandler(enabled = pickerOpen) { pickerOpen = false }"))
    }

    /**
     * R29：TERMINAL 的风格选择器必须是**字符菜单**，不是 M3 卡片面板。
     *
     * 点 `[ 风格 ]` 弹出卡片面板时，满屏等宽绿字突然变成 UI —— 这是整套里最破坏沉浸感的一处。
     * 守卫两条：①该文件不许出现 M3 控件调用；②必须仍是字符语法（`# 页面风格`、`> ` 标记、规则线）。
     */
    @Test
    fun terminalPickerIsACharacterMenu() {
        // 文件在 settingsv2/ 根下，不在 style/：style/ 里的每个文件都被守卫当成"风格渲染器"
        // （要求入场错峰、禁止硬编码 sp），选择器不属于那一类。
        val src = File("wear/src/main/java/com/example/myapplication/presentation/ui/settingsv2/TerminalStylePicker.kt")
            .let { if (it.exists()) it else File("../wear/src/main/java/com/example/myapplication/presentation/ui/settingsv2/TerminalStylePicker.kt") }
            .readText()
        listOf("Surface(", "Card(", "Button(", "MaterialTheme.typography", "SwitchButton(").forEach { forbidden ->
            assertFalse("终端选择器里不允许调用 $forbidden", src.contains(forbidden))
        }
        assertTrue("必须有字符头 `# 页面风格`", src.contains("\"# 页面风格\""))
        assertTrue("当前项必须用 `> ` 标记", src.contains("if (style == current) \"> \" else \"  \""))
        assertTrue("必须有规则线", src.contains("\"─\".repeat("))
        assertTrue("必须保留既有 testTag（工具与测试不用改）", src.contains("StylePickerTag") && src.contains("StylePickerItemTagPrefix"))
    }

    /**
     * R15/R26：TERMINAL 的换风格入口必须是**字符命令**，不是胶囊按钮，而且长在终端的底栏里。
     *
     * 第一版它浮在屏幕右下角当独立控件（真机截图里一眼出戏）；R26 起收进底栏，与 `[n/4] 12L`
     * 同一行 —— 真终端的底栏就是一行。所以断言跟着挪位置，但"必须不是胶囊"这条没变。
     */
    @Test
    fun terminalStyleEntryIsACharacterCommand() {
        fun src(rel: String) = File("wear/src/main/java/com/example/myapplication/presentation/ui/settingsv2/$rel")
            .let { if (it.exists()) it else File("../wear/src/main/java/com/example/myapplication/presentation/ui/settingsv2/$rel") }
            .readText()
        val terminal = src("style/TerminalStyle.kt")
        val screen = src("SettingsV2Screen.kt")
        assertTrue("终端入口必须是 `[ 风格 ]` 字符命令（在终端自己的底栏里）", terminal.contains("[ 风格 ]"))
        assertTrue("终端入口由 onStylePicker 回调驱动", terminal.contains("onStylePicker"))
        assertTrue(
            "四套的胶囊按钮必须排除 TERMINAL（style != TERMINAL 才画）",
            screen.contains("if (style != SettingsV2Style.TERMINAL)")
        )
    }

    /**
     * R15：TERMINAL 必须**零 M3 控件**。
     *
     * "像终端"不是换个等宽字体就完了：胶囊开关、圆角卡片、按钮的呼吸感一出现，立刻退回"像终端的 UI"。
     * 这条用源码守卫把它们挡在外面（真机 dump 里开关显示为 `[ ON ]` 也由 [terminalTogglesAreCharacters] 钉住）。
     */
    @Test
    fun terminalUsesNoMaterialComponents() {
        val src = File("wear/src/main/java/com/example/myapplication/presentation/ui/settingsv2/style/TerminalStyle.kt")
            .let { if (it.exists()) it else File("../wear/src/main/java/com/example/myapplication/presentation/ui/settingsv2/style/TerminalStyle.kt") }
            .readText()
        // 只认"调用语法"（带左括号）：否则会误伤解释性注释
        listOf("SwitchButton(", "CompactButton(", "Card(", "Surface(", "SurfaceTransformation(").forEach { forbidden ->
            assertFalse(
                "终端里不允许调用 $forbidden —— 那是 UI 的语法，不是终端的语法",
                src.contains(forbidden)
            )
        }
        assertTrue("开关必须渲染成字符 [ ON  ] / [ OFF ]", src.contains("[ ON  ]") && src.contains("[ OFF ]"))
        assertTrue("末行必须是块光标 █", src.contains("TerminalCursorBlock"))
        assertTrue("滚动条必须是 ASCII ▓/░", src.contains("TerminalThumbCell") && src.contains("TerminalTrackCell"))
    }
}
