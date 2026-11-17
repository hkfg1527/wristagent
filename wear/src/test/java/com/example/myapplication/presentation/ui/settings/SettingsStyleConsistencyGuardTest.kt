package com.example.myapplication.presentation.ui.settings

import com.example.myapplication.appearance.SettingsStyle
import com.example.myapplication.readProjectFile
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 「风格必须内部自洽」的源码守卫。
 *
 * [SettingsStylePolicyTest] 只钉住「每套风格都声明了 8 个槽位」，管不住**声明归声明、渲染归渲染**：
 * 渲染侧完全可以继续自己写 `if (WearAppearance.style == ...)`、继续手搓开关、
 * 继续让两个返回控件同时受一个条件控制 —— 编译器一个字都不会说，真机上就是「大杂烩」。
 *
 * 做法同 [SettingsVisualGuardTest]：读真实源文件 + 先 [codeOnly] 去掉注释再扫字符串，
 * 且**先断言锚点存在**再取片段（不用 `substringAfter` —— 找不到锚点时它会返回整个文件，
 * 那种断言永远是绿的）。四件事各一条，另加一条检测器自检（共 5 个用例）：
 *
 * 1. 风格判断只许活在槽位表里：全 `src/main` 的 `WearAppearance.style ==` 有硬上限；
 * 2. 声明了官方控件的风格，渲染文件里不许再有手搓件（当前 4 套都还是手搓 → 按 TODO 跳过，接线后自动启用）；
 * 3. 贴底返回键与列表首项返回行不许受同一个风格条件控制（否则一套风格里长出两个返回入口）；
 * 4. 每套风格的枚举名都必须在 `docs/UI-STYLES.md` 里有说明（缺文档即红）。
 */
class SettingsStyleConsistencyGuardTest {

    /**
     * 风格判断不许散落。
     *
     * 上限是 2026-11-18 现场数出来的实数，不是拍的：全 `src/main` 只剩 **1 处** `WearAppearance.style ==`，
     * 就在 `SettingsStylePolicy.isActive` 自己身上（外观页那排单选问「哪套在生效」）—— 它本身就是「读表」的入口。
     * 渲染侧原本那 11 处（SettingsScreen.kt / SettingsShared.kt / WatchSettingsSections.kt，10 行）已全部
     * 搬进槽位表，源码里留下的是「原来是 …，现在读表」的注释。注释里的提及先被 [codeOnly] 去掉，
     * 否则 SettingsStylePolicy.kt 的 KDoc 会把它数成 2。
     *
     * 这是**单向棘轮**：只许往下调小，不许往上抬。想加一处新的 `style ==`，先问「能不能进槽位表」；
     * 真搬不进去才调高上限，并在这一行写清为什么。
     */
    @Test
    fun styleComparisonsStayInsideThePolicyTable() {
        val sources = mainSources()
        // 防空转：扫描路径坏掉时是 0 处，下面那条上限就成了恒真断言
        assertTrue(
            "只扫到 " + sources.size + " 个 Kotlin 文件 —— 扫描路径坏了（cwd=" +
                File(".").absolutePath + "），src/main 下有一百多个 .kt，上限断言会变成恒真",
            sources.size >= 100
        )
        assertTrue(
            "扫描没读到 settings/SettingsStylePolicy.kt，扫的不是设置页源码树：" + sources.keys.take(3),
            sources.keys.any { it.endsWith("settings/SettingsStylePolicy.kt") }
        )
        val byFile = sources
            .mapValues { (_, text) -> StyleComparison.findAll(codeOnly(text)).count() }
            .filterValues { it > 0 }
        val total = byFile.values.sum()
        assertTrue(
            "全 src/main 扫到 0 处 WearAppearance.style == —— 连 SettingsStylePolicy.isActive 那一处都没了：" +
                "要么扫描或去注释坏了（先确认它能数出 1 处），要么真的把最后一处也删了（那就把上限改成 0）",
            total > 0
        )
        assertTrue(
            "WearAppearance.style == 现在 " + total + " 处，超过上限 " + StyleComparisonLimit + " 处，分布 " + byFile +
                "。这个上限只许往下调；**每新增一处都要搬进 SettingsStylePolicy（槽位表）**，" +
                "别在渲染代码里再写 if (style == ...) —— 那正是一页里「一会儿 M3E、一会儿基础」的来源",
            total <= StyleComparisonLimit
        )
    }

    /**
     * M3 官方档不许出现手搓控件：某套风格把 toggle / slider / radio 声明成官方控件后，
     * 它的渲染文件里就不许再有 `drawCircle(` / `SettingsCompactSwitch(` / `SettingsThinSlider(`。
     *
     * 现状：四套风格的三个槽位全是 COMPACT_SWITCH / THIN_TRACK / DOT，没有「官方控件档」可查，
     * 所以用 [assumeTrue] 明确跳过（JUnit 报告成 skipped，而不是悄悄通过）。
     * 谁先把某套风格改成 OFFICIAL_*，这条会自己醒来按 [renderFilesFor] 去查那套风格的渲染文件，
     * 失败信息直接指出「哪套风格 / 哪个槽位 / 哪个文件 / 还有哪些手搓件」。
     */
    @Test
    fun officialSlotStylesCarryNoHandRolledControls() {
        // 0.0.0.58 起 M3_LIST / M3_TRANSFORM 已把槽位声明成官方件，但**渲染还没接线**
        // （官方列表实现在 SettingsStyleM3List.kt，尚未被 SettingsSubPage 调用）。
        // 所以这里显式列出"声明了官方件但还没接线"的风格；接线后必须把它从这里删掉，
        // 让守卫自动生效 —— 这份清单只许变短，不许变长。
        val notYetWired = setOf(SettingsStyle.M3_LIST, SettingsStyle.M3_TRANSFORM)
        val official = officialSlotStyles().filterNot { it in notYetWired }
        assumeTrue(
            "TODO(等官方控件接线后再启用)：四套风格的 toggle/slider/radio 当前都是手搓件" +
                "（COMPACT_SWITCH / THIN_TRACK / DOT），还没有官方控件档可查，这条守卫无事可做。" +
                "某套风格的槽位改成 OFFICIAL_SWITCH_BUTTON / OFFICIAL_SEGMENTED / OFFICIAL_RADIO_BUTTON 之后，" +
                "它会自动开始检查该风格的渲染文件里是否还留着 drawCircle( / SettingsCompactSwitch( / SettingsThinSlider(",
            official.isNotEmpty()
        )
        val offenders = mutableListOf<String>()
        for (style in official) {
            val spec = SettingsStylePolicy.specOf(style)
            val officialSlots = mutableListOf<String>()
            if (spec.toggle == SettingsToggleControl.OFFICIAL_SWITCH_BUTTON) officialSlots += "toggle"
            if (spec.slider == SettingsSliderControl.OFFICIAL_SEGMENTED) officialSlots += "slider"
            if (spec.radio == SettingsRadioControl.OFFICIAL_RADIO_BUTTON) officialSlots += "radio"
            for (slot in officialSlots) {
                for (fileName in renderFilesFor(slot)) {
                    val hits = handRolledSymbols(codeOnly(readSettings(fileName)))
                    if (hits.isNotEmpty()) {
                        offenders += style.name + " 的 " + slot + " 槽位已声明官方控件，但 " + fileName +
                            " 里还有 " + hits
                    }
                }
            }
        }
        assertTrue(
            "官方控件档不许和手搓件共存（声明是官方、画的还是手搓 = 声明变成了摆设）：" +
                offenders.joinToString("；"),
            offenders.isEmpty()
        )
    }

    /**
     * 上一条现在是 skipped，所以这里单独钉住「检测器真的会报」——否则那条守卫哪天醒过来也可能是个空壳。
     *
     * 两个方向都验证：真实代码里的三个手搓签名必须被认出来，注释里的同名提及必须不算。
     */
    @Test
    fun handRolledDetectorReportsRealCodeAndIgnoresComments() {
        val real = "Canvas { drawCircle(color = tint) }\n" +
            "SettingsCompactSwitch(checked = checked)\n" +
            "SettingsThinSlider(value = value)"
        assertEquals(
            "三个手搓签名必须全部被认出来（顺序即 HandRolledSymbols 的顺序）",
            HandRolledSymbols,
            handRolledSymbols(codeOnly(real))
        )
        val commented = "// 以前这里是 SettingsThinSlider( 与 drawCircle(\n/* SettingsCompactSwitch( */"
        assertEquals(
            "注释里提到手搓件不算「还在用它」",
            emptyList<String>(),
            handRolledSymbols(codeOnly(commented))
        )
    }

    /**
     * 返回控件唯一。
     *
     * 现状（SettingsShared.kt）：贴底键在 `if (!backInList) { SettingsEdgeBackButton(...) }` 里，
     * 列表首项在 `if (backInList) { ... SettingsBackCardRow(...) }` 里，两个条件互为取反 —— 一屏只出一个。
     * 谁把两个调用塞进同一个风格条件，屏幕底部就会同时长出两个返回入口。
     */
    @Test
    fun theTwoBackControlsNeverShareOneStyleCondition() {
        val shared = codeOnly(readSettings("SettingsShared.kt"))
        val edgeAt = callSiteIndex(shared, "SettingsEdgeBackButton(")
        val cardAt = callSiteIndex(shared, "SettingsBackCardRow(")
        assertTrue(
            "SettingsShared.kt 里找不到 SettingsEdgeBackButton( 的调用点（只剩声明？）—— 返回控件的接线被搬走了，先更新这条守卫",
            edgeAt >= 0
        )
        assertTrue(
            "SettingsShared.kt 里找不到 SettingsBackCardRow( 的调用点（只剩声明？）—— 同上",
            cardAt >= 0
        )
        val edgeGuard = styleGuardBefore(shared, edgeAt)
        val cardGuard = styleGuardBefore(shared, cardAt)
        assertTrue(
            "SettingsEdgeBackButton 的调用点前面找不到风格条件（窗口里的 if 都没有 " + StyleTokens +
                " 这些令牌）—— 返回控件必须仍由风格决定，不能写死在某种风格上",
            edgeGuard != null
        )
        assertTrue(
            "SettingsBackCardRow 的调用点前面找不到风格条件 —— 同上",
            cardGuard != null
        )
        assertNotEquals(
            "SettingsEdgeBackButton 与 SettingsBackCardRow 受同一个风格条件控制：「" + edgeGuard +
                "」。两个返回控件会在同一套风格里一起生效，屏幕底部同时出现两个返回入口",
            edgeGuard,
            cardGuard
        )
    }

    /**
     * 每套风格都有对应说明。
     *
     * 风格只活在代码里，下一个人就会照着别的风格改 —— 所以文档里必须出现每套风格的**枚举名**
     * （写 `GOOGLE_CARD`，不写「大卡片」这种别名：别名没有唯一性，改不动代码）。
     * 文档还不存在时，失败信息直接说「缺文档」，而不是抛一个看不懂的 FileNotFoundException。
     */
    @Test
    fun everyStyleIsDocumentedInTheUiStylesDoc() {
        val file = firstExistingFile(*DocCandidates.toTypedArray())
        assertTrue(
            "缺文档：docs/UI-STYLES.md 不存在（已试过 " + DocCandidates + "，cwd=" +
                File(".").absolutePath + "）。四套风格（" + styleNames() + "）每套都要有自己的小节：" +
                "走什么骨架、组标题怎么排、行外壳是什么、开关/滑杆/单选是官方件还是手搓件、返回控件在哪",
            file != null
        )
        val doc = file?.readText().orEmpty()
        assertTrue("docs/UI-STYLES.md 是空文件", doc.isNotBlank())
        val missing = styleNames().filter { name -> !doc.contains(name) }
        assertTrue(
            "docs/UI-STYLES.md 没提到这些风格枚举名：" + missing +
                " —— 每套风格至少要有自己的小节标题，且写枚举名而不是中文别名",
            missing.isEmpty()
        )
    }

    // ---------------------------------------------------------------- 源码扫描

    /** 去掉注释再扫：文档/注释里提到某个符号不算「代码在用它」。 */
    private fun codeOnly(text: String): String =
        text.replace(Regex("/\\*[\\s\\S]*?\\*/"), " ").replace(Regex("//[^\n]*"), " ")

    /** 全 `src/main` 的 Kotlin 源码：相对 `src/main` 的路径 → 文本。两种 cwd 布局都试。 */
    private fun mainSources(): Map<String, String> {
        val root = listOf(MainDir, "wear/" + MainDir)
            .map { File(it) }
            .firstOrNull { it.isDirectory }
            ?: return emptyMap()
        return root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .associateBy(
                { it.relativeTo(root).path.replace(File.separatorChar, '/') },
                { it.readText() }
            )
    }

    private fun readSettings(name: String): String = readProjectFile(SettingsDir + name)

    private fun firstExistingFile(vararg candidates: String): File? =
        candidates.map { File(it) }.firstOrNull { it.isFile }

    private fun styleNames(): List<String> = SettingsStyle.values().map { it.name }

    // ---------------------------------------------------------------- 断言 2 的检测器

    /** 声明了任一「官方控件」槽位的风格。全都手搓时返回空列表（断言 2 因此跳过）。 */
    private fun officialSlotStyles(): List<SettingsStyle> = SettingsStyle.values().filter { style ->
        val spec = SettingsStylePolicy.specOf(style)
        spec.toggle == SettingsToggleControl.OFFICIAL_SWITCH_BUTTON ||
            spec.slider == SettingsSliderControl.OFFICIAL_SEGMENTED ||
            spec.radio == SettingsRadioControl.OFFICIAL_RADIO_BUTTON
    }

    /**
     * 槽位 → 真正画这个槽位的源文件。
     *
     * 2026-11-18 现状：四套风格共用这几个行组件（开关 / 滑杆 / 单选各一个文件），
     * 所以「某套风格对应的渲染文件」就是这三个文件本身。某套风格哪天拆出自己的渲染文件，加进对应那行即可。
     */
    private fun renderFilesFor(slot: String): List<String> = when (slot) {
        "toggle" -> listOf("SettingsSwitchRow.kt")
        "slider" -> listOf("SettingsSliderRow.kt")
        "radio" -> listOf("SettingsRadioRow.kt")
        else -> error("未登记的槽位 " + slot + "：先把它对应的渲染文件写进 renderFilesFor")
    }

    /** 文本里出现了哪些手搓件签名（顺序固定，便于断言信息可读）。 */
    private fun handRolledSymbols(code: String): List<String> = HandRolledSymbols.filter { code.contains(it) }

    // ---------------------------------------------------------------- 断言 3 的定位器

    /**
     * 找某个名字的**调用点**下标（跳过 `fun ` 声明本身）。
     *
     * 找不到返回 -1，交给调用方断言 —— 绝不返回「整段文本」这种永远非空的兜底值。
     */
    private fun callSiteIndex(code: String, symbol: String): Int {
        var from = 0
        while (true) {
            val i = code.indexOf(symbol, from)
            if (i < 0) return -1
            val before = code.substring(maxOf(0, i - 60), i)
            if (!before.contains("fun ")) return i
            from = i + symbol.length
        }
    }

    /**
     * 取调用点之前**最近一条**风格条件（`if (...)` 的条件原文），没有则返回 null。
     *
     * - 只看前面 [GuardWindow] 个字符：够覆盖 `if (...) {` 与一两层缩进，又不至于把上一个函数的条件算进来；
     * - 条件里必须出现 [StyleTokens] 里的令牌，否则那是一条业务条件（如 `if (isEnabled)`），不算风格条件；
     * - 括号做配平扫描，`SettingsStylePolicy.current().back == ...` 这种带嵌套括号的条件不会被截断。
     */
    private fun styleGuardBefore(code: String, callIndex: Int): String? {
        val window = code.substring(maxOf(0, callIndex - GuardWindow), callIndex)
        return ifConditions(window).lastOrNull { condition ->
            StyleTokens.any { token -> condition.contains(token) }
        }
    }

    /** 把一段文本里所有 `if (` 的条件按括号配平切出来（按出现顺序）。 */
    private fun ifConditions(window: String): List<String> {
        val conditions = mutableListOf<String>()
        for (match in IfHead.findAll(window)) {
            val start = match.range.first + match.value.length
            var depth = 1
            var i = start
            while (i < window.length && depth > 0) {
                when (window[i]) {
                    '(' -> depth++
                    ')' -> depth--
                }
                i++
            }
            if (depth == 0) conditions += window.substring(start, i - 1).trim()
        }
        return conditions
    }

    private companion object {
        /** 2026-11-18 实测：全 `src/main` 只剩 1 处，在 `SettingsStylePolicy.isActive` 里。只许往下调。 */
        const val StyleComparisonLimit = 1

        const val MainDir = "src/main"
        const val SettingsDir = "src/main/java/com/example/myapplication/presentation/ui/settings/"

        /** 风格条件窗口：只看调用点前面这么多字符。 */
        const val GuardWindow = 600

        /** 文档在两种 cwd（`wear/` 与仓库根）下的相对路径。 */
        val DocCandidates = listOf("../docs/UI-STYLES.md", "docs/UI-STYLES.md", "wear/docs/UI-STYLES.md")

        /** 手搓件的源码签名：官方控件档的渲染文件里一个都不许有。 */
        val HandRolledSymbols = listOf("drawCircle(", "SettingsCompactSwitch(", "SettingsThinSlider(")

        /** 认得出「这是风格条件」的令牌。 */
        val StyleTokens = listOf("backInList", "WearAppearance.style", "SettingsStylePolicy", "SettingsStyle.")

        val StyleComparison = Regex("""WearAppearance\.style\s*==""")

        val IfHead = Regex("""\bif\s*\(""")
    }
}
