package com.example.myapplication.presentation.ui.settingsv2

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V2 架构与动效接线守卫（源码级，防"删了动效测试还全绿"）。
 *
 * 三条铁律来自目标定义：
 * ①每套风格一个文件，**互不 import**、不 import 旧 `ui/settings`；
 * ②每套都必须有：入场逐行错峰、磁滑吸附（TERMINAL 连滚动）、边缘淡出/缩放（M3_CARD 由官方
 *   `SurfaceTransformation` 承担）；
 * ③字体只用 M3 九档里的少数几档（这里检查是否出现了 `sp` 硬编码字号）。
 *
 * 这些是"接线是否存在"的检查，不能替代真机观感，但能防止接好的线被后续改动悄悄删掉。
 */
class SettingsV2ArchitectureGuardTest {

    private val moduleDir: File = sequenceOf(File("."), File("wear"), File("../wear"))
        .map { it.absoluteFile }
        .first { File(it, "src/main/java/com/example/myapplication/presentation/ui/settingsv2").isDirectory }

    private val v2Dir = File(moduleDir, "src/main/java/com/example/myapplication/presentation/ui/settingsv2")
    private val styleDir = File(v2Dir, "style")

    /** 风格名 → 文件。 */
    private val expectedStyles = mapOf(
        SettingsV2Style.M3_CARD to "M3CardStyle.kt",
        SettingsV2Style.M3_LIST to "M3ListStyle.kt",
        SettingsV2Style.ARC to "ArcStyle.kt",
        SettingsV2Style.SQUARE to "SquareStyle.kt",
        SettingsV2Style.TERMINAL to "TerminalStyle.kt",
    )

    private fun styleFiles(): List<File> = styleDir.listFiles { f -> f.extension == "kt" }!!.sortedBy { it.name }

    private fun text(file: File): String = file.readText()

    @Test
    fun everyStyleHasExactlyOneFileAndNoExtraFiles() {
        assertEquals(
            "风格文件必须与五套一一对应（多出来的文件说明有人偷偷加了第六套）",
            expectedStyles.values.sorted(),
            styleFiles().map { it.name }
        )
    }

    @Test
    fun stylesNeverImportLegacySettingsOrEachOther() {
        styleFiles().forEach { file ->
            val src = text(file)
            assertFalse(
                "${file.name} 不允许 import 旧实现 ui.settings（V2 必须与旧页面完全脱钩）",
                src.contains("presentation.ui.settings.")
            )
            // 同包内不需要 import，所以"互相引用"表现为直接调用别的风格的 Composable
            expectedStyles.values
                .filter { it != file.name }
                .forEach { other ->
                    val otherName = other.removeSuffix(".kt")
                    assertFalse(
                        "${file.name} 不允许调用别的风格（$otherName）：每套必须自洽",
                        src.contains(otherName + "Settings(")
                    )
                }
        }
    }

    @Test
    fun everyStyleKeepsItsMotionWiring() {
        styleFiles().forEach { file ->
            val src = text(file)
            val style = expectedStyles.entries.first { it.value == file.name }.key

            assertTrue(
                if (style == SettingsV2Style.TERMINAL) {
                    // TERMINAL 的例外是有据可依的：真终端的行是被"打印"出来的，不是从下往上淡入。
                    // 所以它不要入场错峰，但必须保留边缘反馈（edgeAppearance）—— 换成这条**更强**的断言，
                    // 而不是把这条守卫删掉。
                    "$style 不做入场淡入，但必须保留边缘反馈（edgeAppearance）"
                } else {
                    "$style 必须保留入场逐行错峰（rememberEnterProgress）"
                },
                if (style == SettingsV2Style.TERMINAL) {
                    !src.contains("rememberEnterProgress") && src.contains("edgeAppearance")
                } else {
                    src.contains("rememberEnterProgress")
                }
            )

            // 只看**调用**，不看注释：注释里提到 snapFlingBehavior 不算接线
            val wiredSnap = src.contains("flingBehavior = TransformingLazyColumnDefaults.snapFlingBehavior")
            if (style == SettingsV2Style.TERMINAL) {
                assertFalse(
                    "TERMINAL 必须保持连续滚动：不允许接 snapFlingBehavior",
                    wiredSnap
                )
            } else {
                assertTrue(
                    "$style 必须保留磁滑吸附（snapFlingBehavior）",
                    wiredSnap
                )
            }

            if (style == SettingsV2Style.M3_CARD) {
                assertTrue(
                    "M3_CARD 的边缘效果必须由官方 SurfaceTransformation 承担",
                    src.contains("SurfaceTransformation")
                )
            } else {
                assertTrue(
                    "$style 必须保留边缘淡出/缩放接线（reportEdgeDistance + edgeAppearance）",
                    src.contains("reportEdgeDistance") && src.contains("edgeAppearance")
                )
            }
        }
    }

    /**
     * 右缘位置指示器（`ScrollIndicator`）：官方 Wear 的第二个"一眼认出"的信号。
     *
     * 四套列表/卡片风格都必须是**真实调用**（`state =` 有传），不是注释里提到；
     * TERMINAL 明确不接 —— 它自己有 `▓/░` ASCII 滚动条，再叠一个官方指示器就是两套刻度打架。
     */
    @Test
    fun everyStyleButTerminalWiresTheOfficialScrollIndicator() {
        styleFiles().forEach { file ->
            val src = text(file)
            val style = expectedStyles.entries.first { it.value == file.name }.key
            val wired = Regex("""ScrollIndicator\(\s*state\s*=""").containsMatchIn(src)
            if (style == SettingsV2Style.TERMINAL) {
                assertFalse(
                    "TERMINAL 已有 ASCII 滚动条，不允许再接官方 ScrollIndicator（两套刻度会打架）",
                    wired
                )
            } else {
                assertTrue("$style 必须接官方 ScrollIndicator(state = …)", wired)
            }
        }
    }

    @Test
    fun stylesUseTypeScaleInsteadOfHardCodedSp() {
        styleFiles().forEach { file ->
            val src = text(file)
            // 终端故意用等宽 + 显式字号（TerminalFontSize），其余四套必须走 M3 类型标尺
            if (file.name == "TerminalStyle.kt") return@forEach
            assertFalse(
                "${file.name} 不应硬编码 sp 字号，必须用 MaterialTheme.typography",
                Regex("""\d+(\.\d+)?\.sp""").containsMatchIn(src)
            )
            assertTrue(
                "${file.name} 必须使用 MaterialTheme.typography 里的类型档",
                src.contains("MaterialTheme.typography.")
            )
        }
    }

    /**
     * 圆屏一行制：四套 Material 风格的行必须把标题与值放**同一行**
     * （`Arrangement.SpaceBetween` + `Alignment.CenterVertically`），不许退回两行堆叠。
     *
     * 两行堆叠在 466px 圆屏上首屏只剩 2~3 项，是"看着像清单、其实没法用"的根源。
     * TERMINAL 是字符网格，不适用这条。
     */
    @Test
    fun materialStylesUseSingleLineRows() {
        listOf("M3CardStyle.kt", "M3ListStyle.kt", "ArcStyle.kt", "SquareStyle.kt")
            .forEach { name ->
                val src = text(File(styleDir, name))
                assertTrue(
                    "$name 的行必须横向排列（SpaceBetween + CenterVertically）",
                    src.contains("horizontalArrangement = Arrangement.SpaceBetween") &&
                        src.contains("verticalAlignment = Alignment.CenterVertically")
                )
                assertFalse(
                    "$name 不许再写两行堆叠的注释（那是旧布局的标记）",
                    src.contains("两行堆叠：")
                )
            }
    }
}
