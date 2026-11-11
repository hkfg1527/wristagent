package com.example.myapplication.presentation.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 手表端 10 条新增设置项的清单校验：形态、落位页与偏好键都必须自洽。 */
class WatchSettingsCatalogTest {

    @Test
    fun exactlyTenItemsLand() {
        assertEquals(10, WatchSettingsCatalog.items.size)
        assertEquals(10, WatchSettingsCatalog.items.map { it.id }.toSet().size)
    }

    @Test
    fun everyItemBelongsToAWebPageThatExistsOnTheConsole() {
        val webPages = setOf("model", "provider", "role", "generate", "dialog", "voice", "tools", "interface", "device")
        WatchSettingsCatalog.items.forEach { item ->
            assertTrue(
                "落位页必须存在于网页端: " + item.landingPage,
                item.landingPage in webPages
            )
        }
    }

    @Test
    fun everyItemUsesAWatchHonestForm() {
        // 手表只承载 开关 / 三档枚举 / 滑杆 / 只读状态；长文本与列表编辑一律不留
        val honest = setOf(
            WatchSettingForm.SWITCH,
            WatchSettingForm.TRI_STATE,
            WatchSettingForm.SLIDER,
            WatchSettingForm.READ_ONLY
        )
        WatchSettingsCatalog.items.forEach { item ->
            assertTrue("形态必须可在表端承载: " + item.id + " -> " + item.form, item.form in honest)
        }
    }

    @Test
    fun switchItemsPointAtARealPreferenceKey() {
        WatchSettingsCatalog.items.filter { it.form == WatchSettingForm.SWITCH }.forEach { item ->
            assertNotNull("开关必须有偏好键: " + item.id, item.preferenceKey)
        }
        WatchSettingsCatalog.items.filter { it.form != WatchSettingForm.SWITCH }.forEach { item ->
            assertNull("非开关项不挂在可写偏好键上: " + item.id, item.preferenceKey)
        }
    }

    /** 审计删除的回归阀门：这四条一旦被加回来，说明"假开关"又被画上屏了。 */
    @Test
    fun theFourZeroValueItemsStayDeleted() {
        val removed = listOf(
            "voice.wakeOnScreen",
            "voice.wakeOnLockScreen",
            "interface.usePureBlack",
            "interface.themeMode"
        )
        val present = WatchSettingsCatalog.items.map { it.id }.filter { it in removed }
        assertEquals("这些项没有任何执行路径，不许再出现在清单里：", emptyList<String>(), present)
    }

    @Test
    fun eachDomainOfTheTenIsCounted() {
        assertEquals(
            mapOf(
                WatchSettingsDomain.VOICE to 3,
                WatchSettingsDomain.TOOLS to 1,
                WatchSettingsDomain.MODEL to 1,
                WatchSettingsDomain.MEMORY to 1,
                WatchSettingsDomain.CONTEXT to 2,
                WatchSettingsDomain.INTERFACE to 1,
                WatchSettingsDomain.STATUS to 1
            ),
            WatchSettingsCatalog.items.groupingBy { it.domain }.eachCount()
        )
    }
}
