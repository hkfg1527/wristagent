package com.example.myapplication.presentation.ui

import com.example.myapplication.readProjectFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 纯图标按钮的无障碍名称守护：5 处只有图形的可点区域必须给出文案，且文案必须来自字符串资源。
 *
 * 这两条编译期都不会报错 —— Canvas 叉号少了 contentDescription，屏幕阅读器只会念"按钮"；
 * 把名称写成中文字面量，等到要出英文版时也没人会想起它。所以在这里扫真实源文件钉死。
 */
class IconButtonAccessibilityGuardTest {

    @Test
    fun everyIconOnlyButtonTakesItsNameFromAStringResource() {
        for (site in describedSites) {
            val code = cleanCode(site.path)
            val binding = stringResourceBinding(code, site.key)
            assertTrue(
                site.label + " 的文案必须走 stringResource(R.string." + site.key + ")，不许写字面量",
                binding != null
            )
            assertTrue(
                site.label + " 必须用 contentDescription 把 " + binding + " 交给语义树，否则屏幕阅读器只念得出\"按钮\"",
                Regex(Regex.escape("contentDescription = " + binding) + "\\b").containsMatchIn(code)
            )
        }
    }

    @Test
    fun theFullScreenCancelLayerGetsAnOnClickLabel() {
        val code = cleanCode(VoicePath)
        val binding = stringResourceBinding(code, "ui_listening_cancel")
        assertTrue("整层取消区的动作名必须走 stringResource(R.string.ui_listening_cancel)", binding != null)
        assertTrue(
            "取消区整层可点却没有任何属于自己的文字，动作名必须挂在 clickable 的 onClickLabel 上",
            Regex("onClickLabel\\s*=\\s*" + binding + "\\b").containsMatchIn(code)
        )
    }

    @Test
    fun eachWingAndCrossDeclaresTheButtonRole() {
        // 左翼 = 键盘、右翼 = 会话抽屉：整个底栏文件里恰好两处 Role.Button，
        // 少一个就说明有一侧又退回了"只有图标的匿名 Box"。
        assertEquals(
            "底栏两翼都必须声明 Role.Button：",
            2,
            Regex("role = Role\\.Button").findAll(cleanCode(BottomPath)).count()
        )
        // 抽屉的三个 44dp 图标框（删除 / 确认 / 关闭）逐个查函数体：
        // 按文件计数会被会话卡、新开对话那些带文字的整行点击混进来，拉平了也没有意义。
        val drawer = cleanCode(DrawerPath)
        for (name in listOf("DeleteButton", "ConfirmDeleteButton", "CloseButton")) {
            assertTrue(name + " 必须存在", drawer.contains("fun " + name + "("))
            assertTrue(
                name + " 必须声明 Role.Button，否则读屏只念出一个没有角色的可点框",
                bodyOf(drawer, name).contains("role = Role.Button")
            )
        }
    }

    @Test
    fun noAccessibilityNameIsAHardcodedLiteral() {
        for (path in listOf(BottomPath, DrawerPath, VoicePath)) {
            val literals = Regex("(contentDescription|onClickLabel)\\s*=\\s*\"").findAll(cleanCode(path))
                .map { it.value }
                .toList()
            assertEquals(
                path + " 的无障碍名称必须走 stringResource，不许硬编码字面量（中英文都不行）：",
                emptyList<String>(),
                literals
            )
        }
    }

    @Test
    fun everyAccessibilityNameExistsInBothLocales() {
        val en = stringsOf("values")
        val zh = stringsOf("values-zh")
        assertEquals("中英键名必须一一对应（缺的那侧会静默回落到英文）：", en.keys, zh.keys)
        for (site in describedSites) {
            assertTrue("values/strings_ui.xml 缺少 " + site.key, en.containsKey(site.key))
            assertTrue("values-zh/strings_ui.xml 缺少 " + site.key, zh.containsKey(site.key))
            assertTrue(site.key + " 的英文不能为空", en.getValue(site.key).isNotBlank())
            assertTrue(site.key + " 的中文不能为空", zh.getValue(site.key).isNotBlank())
            assertNotEquals(site.key + " 的中英是同一串，说明中文侧忘了翻译", en.getValue(site.key), zh.getValue(site.key))
        }
        val cancelEn = en.getValue("ui_listening_cancel")
        val cancelZh = zh.getValue("ui_listening_cancel")
        assertTrue("取消录音的英文不能为空", cancelEn.isNotBlank())
        assertTrue("取消录音的中文不能为空", cancelZh.isNotBlank())
        assertNotEquals("取消录音的中英是同一串，说明中文侧忘了翻译", cancelEn, cancelZh)
    }

    @Test
    fun theTouchTokenStillExplainsWhyFortyFourIsDeliberate() {
        val code = readProjectFile(TokensPath)
        assertTrue("Wear.Size.Touch 必须保持 44dp（抬到 48dp 会把引导页页脚撑出圆屏）", code.contains("val Touch = 44.dp"))
        val kdoc = code.substringBefore("val Touch = 44.dp").takeLast(1200)
        assertTrue("KDoc 必须写明 48dp 才是无障碍建议值，否则下一个人会当成漏改", kdoc.contains("48dp"))
        assertTrue("KDoc 必须写明页脚到圆周只剩 2.10dp 余量", kdoc.contains("2.10dp"))
    }

    /** 去掉注释：KDoc 里提到 contentDescription 不算实现，注释里的示例字面量也不该触发守护。 */
    private fun cleanCode(path: String): String =
        readProjectFile(path)
            .replace(Regex("/\\*[\\s\\S]*?\\*/"), " ")
            .replace(Regex("//[^\\n]*"), " ")

    /** `val x = stringResource(R.string.key)` 里的 x：文案先在组合里取到，才能交给非 @Composable 的 semantics。 */
    private fun stringResourceBinding(code: String, key: String): String? =
        Regex("val\\s+(\\w+)\\s*=\\s*stringResource\\(R\\.string\\." + key + "\\)")
            .find(code)
            ?.groupValues
            ?.get(1)

    /** 顶层 Composable 的函数体：本文件的这些函数都以列 0 的 `}` 收尾。 */
    private fun bodyOf(code: String, name: String): String =
        code.substringAfter("fun " + name + "(").substringBefore("\n}")

    private fun stringsOf(locale: String): Map<String, String> {
        val xml = readProjectFile("src/main/res/" + locale + "/strings_ui.xml")
        return Regex("<string name=\"([a-z_]+)\">([\\s\\S]*?)</string>")
            .findAll(xml)
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    private data class Site(val path: String, val key: String, val label: String)

    private companion object {
        const val BottomPath = "src/main/java/com/example/myapplication/presentation/ui/BottomInteractiveBlock.kt"
        const val DrawerPath = "src/main/java/com/example/myapplication/presentation/ui/SessionDrawer.kt"
        const val VoicePath = "src/main/java/com/example/myapplication/presentation/ui/VoiceListeningOverlay.kt"
        const val TokensPath = "src/main/java/com/example/myapplication/presentation/ui/theme/WearTokens.kt"

        /** 4 个用 contentDescription 的纯图标按钮（第 5 处整层取消区走 onClickLabel）。 */
        val describedSites = listOf(
            Site(BottomPath, "ui_bottom_open_keyboard", "底栏左翼（键盘图标）"),
            Site(BottomPath, "ui_bottom_open_sessions", "底栏右翼（图层图标）"),
            Site(DrawerPath, "ui_drawer_delete_session", "抽屉删除叉号"),
            Site(DrawerPath, "ui_drawer_close", "抽屉关闭叉号")
        )
    }
}
