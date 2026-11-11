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
 * 两类令牌各有一条防线：主色 `Wear.Palette.Accent` 由前两个用例钉住，
 * 配套的 AccentDim（页面读它的投影 Wear.AccentSurface / Wear.AccentRail）由
 * [companionTokensAreOnlyReadWhereTheChangeBoundaryEnds] 钉住。
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
    fun theOrbHighlightToneTakesTheAccentAsAParameter() {
        val code = stripped(BottomInteractiveBlockPath)
        assertEquals(
            "OrbEdgeHighlight.toneOf 是零 Compose 依赖的纯函数（JVM 单测直接覆盖），读不了 @Composable 的投影：强调色必须由调用方传入",
            0,
            staticAccent.findAll(code).count()
        )
        assertTrue(
            "toneOf 必须收强调色参数，静息档复用页面那一份轨道位算法",
            code.contains("fun toneOf(mode: BottomBlockMode, isPressed: Boolean, accent: Color)") &&
                code.contains("else -> accentRailOf(accent)")
        )
    }

    @Test
    fun companionTokensAreOnlyReadWhereTheChangeBoundaryEnds() {
        val actual = mainSources()
            .mapNotNull { file ->
                val reads = companionToken.findAll(stripComments(file.readText())).count()
                if (reads == 0) null else moduleRelative(file) to reads
            }
            .toMap()
        assertEquals(
            "配套派生色已有运行时投影（Wear.AccentSurface / Wear.AccentRail），页面代码只许读投影：" +
                "这里每多一处，那枚配套色换色时就静止在默认蓝上。只允许保留默认档定义本身 ——" +
                "WearMaterialTheme 的两个槽位，它同时是 AccentDim「有人在读」的证据。",
            mapOf("theme/WearMaterialTheme.kt" to 2),
            actual
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
            "静态令牌只允许留在这两处：默认档定义、M3 配色投影（toneOf 已改成把强调色当参数收）；新增一处就会在这里红",
            mapOf(
                "settings/AppearanceSettings.kt" to 1,
                "theme/WearMaterialTheme.kt" to 2
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

        /** 配套令牌：词边界写法，配色投影里的 `Wear.Palette.AccentDim` 才会命中。 */
        val companionToken = Regex("Palette\\.Accent(?:Dim|Track)\\b")

        const val MainJavaRoot = "src/main/java"
        const val ComponentRoot = "src/main/java/com/example/myapplication/presentation/ui/"
        const val BottomInteractiveBlockName = "BottomInteractiveBlock.kt"

        const val BottomInteractiveBlockPath = ComponentRoot + BottomInteractiveBlockName
        const val OnboardingPath = ComponentRoot + "OnboardingScreen.kt"
        const val FrostedGlassPath = ComponentRoot + "FrostedGlassComponents.kt"
        const val VoiceOverlayPath = ComponentRoot + "VoiceListeningOverlay.kt"
        const val WearComponentsPath = ComponentRoot + "components/WearComponents.kt"

        /** 这次改造的 5 个文件已全部清零：任何一处再读静态令牌，换色后它就静止在默认蓝上。 */
        val ConvertedPaths = listOf(
            OnboardingPath,
            FrostedGlassPath,
            VoiceOverlayPath,
            WearComponentsPath,
            BottomInteractiveBlockPath
        )
    }
}
