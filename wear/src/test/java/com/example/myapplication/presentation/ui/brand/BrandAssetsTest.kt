package com.example.myapplication.presentation.ui.brand

import com.example.myapplication.readProjectFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 品牌资产的守护测试。
 *
 * 用户 02:05 拍板：自绘点阵角色全部撤掉，卡片上放**官方品牌标识**。所以这里钉两件事：
 * 1. 每家都有官方标识（自建端点例外，它就该用通用兜底）；
 * 2. 官方标识是矢量、且**自带品牌色**（不再靠 tint 上色）。
 */
class BrandAssetsTest {

    private val officialNames = listOf(
        "deepseek", "openai", "anthropic", "gemini", "kimi", "qwen",
        "ollama", "mistral", "grok", "llama",
    )

    @Test
    fun everyRealBrandHasAnOfficialMark() {
        val withOfficial = BrandId.entries.filter { it.officialRes != 0 }
        assertEquals("除了自建端点，每家都要有官方标识", 10, withOfficial.size)
        assertEquals("官方标识不许两家共用同一份资源", 10, withOfficial.map { it.officialRes }.toSet().size)
        assertEquals(
            "只有 UNKNOWN 允许没有官方标识",
            BrandId.UNKNOWN,
            BrandId.entries.single { it.officialRes == 0 },
        )
    }

    @Test
    fun theOfficialVectorsAreValidAndCarryTheirOwnColour() {
        for (name in officialNames) {
            val xml = readProjectFile("src/main/res/drawable/brand_official_" + name + ".xml")
            assertTrue(name + " 必须是 24dp 矢量", xml.contains("android:width=\"24dp\""))
            assertTrue(name + " 必须有 viewport", xml.contains("android:viewportWidth="))
            // ChatGPT 的官方单色标识在深色底上本来就是白色，这是唯一允许纯白的一家；
            // 其余必须自带品牌色（说明官方图没被我们改成 tint 的白图）。
            if (name == "openai") {
                assertTrue("openai 的官方单色标识应为白色", xml.contains("#FFFFFFFF"))
            } else {
                assertTrue(name + " 必须自带品牌色", !xml.contains("#FFFFFFFF"))
            }
            assertTrue(name + " 必须有 path", xml.contains("android:pathData="))
        }
    }

    @Test
    fun theSelfDrawnDotCharactersAreGone() {
        val source = runCatching {
            readProjectFile("src/main/java/com/example/myapplication/presentation/ui/brand/BrandCharacterArt.kt")
        }.getOrNull()
        assertTrue("点阵角色资产必须已经删掉（用户明确说它丑）", source == null || source.isEmpty())
    }

    @Test
    fun endpointsAreRecognisedByHostFirstAndModelSecond() {
        assertEquals(BrandId.DEEPSEEK, BrandId.fromEndpoint("https://api.deepseek.com"))
        assertEquals(BrandId.GEMINI, BrandId.fromEndpoint("https://generativelanguage.googleapis.com"))
        assertEquals(BrandId.ANTHROPIC, BrandId.fromEndpoint("https://api.anthropic.com/v1"))
        assertEquals(BrandId.OLLAMA, BrandId.fromEndpoint("http://127.0.0.1:11434"))
        assertEquals(BrandId.KIMI, BrandId.fromEndpoint("https://api.moonshot.cn", "gpt-4o"))
        assertEquals(BrandId.DEEPSEEK, BrandId.fromEndpoint("https://my-gw.internal/v1", "deepseek-v4-pro"))
        assertEquals(BrandId.UNKNOWN, BrandId.fromEndpoint("https://acme.internal/v1", "acme-2-turbo"))
        assertEquals(BrandId.UNKNOWN, BrandId.fromEndpoint("", ""))
    }
}
