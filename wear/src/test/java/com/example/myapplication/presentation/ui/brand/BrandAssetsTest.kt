package com.example.myapplication.presentation.ui.brand

import com.example.myapplication.readProjectFile
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 品牌资产的守护测试。
 *
 * 两条产品约束在这里被钉死：
 * 1. 每家供应商都必须有标识与角色，且**没有两家共用同一张图**；
 * 2. 标识与角色是**两套独立资产** —— 不同目录、不同格式、不同命名前缀，谁也不能顶替谁。
 */
class BrandAssetsTest {

    private val markNames = listOf(
        "deepseek", "openai", "anthropic", "gemini", "kimi", "qwen",
        "ollama", "mistral", "grok", "llama", "unknown"
    )

    @Test
    fun everyBrandHasItsOwnMarkVector() {
        val ids = BrandId.entries.map { it.markRes }
        assertEquals("11 家必须有 11 张不同的标识资源", ids.size, ids.toSet().size)
        assertEquals("标识数量应与枚举一致", BrandId.entries.size, 11)
        for (name in markNames) {
            val xml = readProjectFile("src/main/res/drawable/brand_" + name + "_mark.xml")
            assertTrue("brand_" + name + "_mark 必须是 24dp 向量", xml.contains("android:viewportWidth=\"24\""))
            assertTrue("brand_" + name + "_mark 必须是单色白路径，用 tint 上色", xml.contains("#FFFFFFFF"))
        }
    }

    @Test
    fun everyBrandHasACharacterAndItIsA12x12DotBitmap() {
        assertEquals("角色表必须覆盖全部枚举", BrandId.entries.toSet(), BrandCharacters.keys.toSet())
        for ((brand, art) in BrandCharacters) {
            val body = art.body.split("/")
            val eyes = art.eyes.split("/")
            assertEquals(brand.name + " 的主形必须是 12 行", 12, body.size)
            assertEquals(brand.name + " 的眼睛必须是 12 行", 12, eyes.size)
            for (row in body + eyes) {
                assertEquals(brand.name + " 的每一行必须是 12 格", 12, row.length)
                assertTrue(brand.name + " 只允许 '#' 与 '.'", row.all { it == '#' || it == '.' })
            }
            assertTrue(brand.name + " 必须有主形", body.any { it.contains('#') })
        }
    }

    @Test
    fun theTwoAssetFamiliesStayApart() {
        // 角色源图在 docs/brand/characters，标识源图在 docs/brand/marks —— 目录不同、前缀不同、格式不同
        // 单测的 CWD 是模块目录（wear/），仓库根在上一层；两种布局都试
        val characters = listOf("../docs/brand/characters", "docs/brand/characters", "wear/docs/brand/characters")
            .map { File(it) }
            .firstOrNull { it.isDirectory }
        assertNotNull("角色源目录必须存在", characters)
        val charFiles = characters!!.listFiles().orEmpty().filter { it.name.endsWith(".svg") }.map { it.name }
        assertEquals("角色必须 11 个", 11, charFiles.size)
        val markFiles = markNames.map { "brand_" + it + "_mark.xml" }.toSet()
        assertTrue(
            "两套资产不许有同名文件",
            charFiles.none { it in markFiles }
        )
        assertTrue("角色的命名前缀必须是品牌名，不能带 brand_", charFiles.none { it.startsWith("brand_") })
    }

    @Test
    fun endpointsAreRecognisedByHostFirstAndModelSecond() {
        assertEquals(BrandId.DEEPSEEK, BrandId.fromEndpoint("https://api.deepseek.com"))
        assertEquals(BrandId.GEMINI, BrandId.fromEndpoint("https://generativelanguage.googleapis.com"))
        assertEquals(BrandId.ANTHROPIC, BrandId.fromEndpoint("https://api.anthropic.com/v1"))
        assertEquals(BrandId.OLLAMA, BrandId.fromEndpoint("http://127.0.0.1:11434"))
        // host 优先于模型名：自建网关挂着 gpt 的名字，但域名是 moonshot
        assertEquals(BrandId.KIMI, BrandId.fromEndpoint("https://api.moonshot.cn", "gpt-4o"))
        // host 认不出时用模型名兜底
        assertEquals(BrandId.DEEPSEEK, BrandId.fromEndpoint("https://my-gw.internal/v1", "deepseek-v4-pro"))
        // 两者都不中 = 自建端点，走统一兜底
        assertEquals(BrandId.UNKNOWN, BrandId.fromEndpoint("https://acme.internal/v1", "acme-2-turbo"))
        assertEquals(BrandId.UNKNOWN, BrandId.fromEndpoint("", ""))
    }
}
