package com.example.myapplication.presentation.ui

import com.example.myapplication.readProjectFile
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 强调色运行时投影的源码守护：除"定义"与"主题投影"外，谁都不许再直接读静态令牌。
 *
 * 强调色可在设置里换 5 档，生效方式是 `Wear.Accent` 这个运行时投影。但直接写
 * `Wear.Palette.Accent` 编译期一样通过 —— 换色后那些元素静止在默认蓝上，
 * 只有真机截图才发现。所以在源码里钉死"还剩哪几处在读静态令牌、分别是谁"。
 *
 * 只数带词边界的 `Wear.Palette.Accent`：AccentDim / AccentTrack 是两枚没有运行时投影的
 * 配套令牌（强调色换成别的时它们该不该跟着算产品决定），不在这条防线的违规面里。
 */
class AccentRuntimeSourceGuardTest {

    @Test
    fun convertedComponentsDoNotReadTheStaticAccentToken() {
        ConvertedPaths.forEach { path ->
            assertEquals(
                path + " 又绕过了运行时强调色：这个文件里只许读 Wear.Accent",
                0,
                staticReadsIn(path)
            )
        }
    }

    @Test
    fun theOnlyStaticReadLeftIsThePureOrbHighlightTone() {
        val code = stripped(BottomInteractiveBlockPath)
        assertEquals(
            "OrbEdgeHighlight.toneOf 是零 Compose 依赖的纯函数（JVM 单测直接覆盖），全文件只许它读令牌",
            1,
            staticAccent.findAll(code).count()
        )
        assertTrue(
            "这一处必须仍是 toneOf 里按 mode 取值的静态映射，不许搬到别的绘制路径上",
            code.contains("mode == BottomBlockMode.RECORDING -> Wear.Palette.Accent")
        )
    }

    @Test
    fun staticAccentSurvivesOnlyWhereItIsDeclaredOrProjected() {
        val actual = mainSources()
            .mapNotNull { file ->
                val reads = staticAccent.findAll(stripComments(file.readText())).count()
                if (reads == 0) null else moduleRelative(file) to reads
            }
            .toMap()
        assertEquals(
            "静态令牌只允许留在这三处：默认档定义、M3 配色投影、toneOf 纯函数；新增一处就会在这里红",
            mapOf(
                "settings/AppearanceSettings.kt" to 1,
                "theme/WearMaterialTheme.kt" to 2,
                BottomInteractiveBlockName to 1
            ),
            actual
        )
    }

    private fun staticReadsIn(path: String): Int = staticAccent.findAll(stripped(path)).count()

    private fun stripped(path: String): String = stripComments(readProjectFile(path))

    /** 注释里的引用不算读令牌：KDoc 讲"默认档等于令牌"不该被当成违规。 */
    private fun stripComments(source: String): String = source
        .replace(Regex("/\\*[\\s\\S]*?\\*/"), " ")
        .replace(Regex("//[^\\n]*"), " ")

    private fun mainSources(): List<File> {
        val root = listOf(File(MainJavaRoot), File("wear", MainJavaRoot))
            .firstOrNull { it.isDirectory }
            ?: error("找不到主源码目录 " + MainJavaRoot + "（cwd=" + File(".").absolutePath + "）")
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    private fun moduleRelative(file: File): String = file.path
        .replace('\\', '/')
        .substringAfter("presentation/ui/")

    private companion object {
        val staticAccent = Regex("Wear\\.Palette\\.Accent\\b")

        const val MainJavaRoot = "src/main/java"
        const val ComponentRoot = "src/main/java/com/example/myapplication/presentation/ui/"
        const val BottomInteractiveBlockName = "BottomInteractiveBlock.kt"

        const val BottomInteractiveBlockPath = ComponentRoot + BottomInteractiveBlockName
        const val OnboardingPath = ComponentRoot + "OnboardingScreen.kt"
        const val FrostedGlassPath = ComponentRoot + "FrostedGlassComponents.kt"
        const val VoiceOverlayPath = ComponentRoot + "VoiceListeningOverlay.kt"
        const val WearComponentsPath = ComponentRoot + "components/WearComponents.kt"

        /** 这次改造的 4 个文件已清零；第 5 个（BottomInteractiveBlock）的唯一残留由上面的用例单独钉住。 */
        val ConvertedPaths = listOf(
            OnboardingPath,
            FrostedGlassPath,
            VoiceOverlayPath,
            WearComponentsPath
        )
    }
}
