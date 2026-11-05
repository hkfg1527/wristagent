package com.example.myapplication.presentation.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 手表端 14 条新增设置项的清单校验：形态、落位页与偏好键都必须自洽。 */
class WatchSettingsCatalogTest {

    @Test
    fun exactlyFourteenItemsLand() {
        assertEquals(14, WatchSettingsCatalog.items.size)
        assertEquals(14, WatchSettingsCatalog.items.map { it.id }.toSet().size)
    }

    @Test
    fun everyItemBelongsToAWebPageThatExistsOnTheConsole() {
        assertEquals(14, WatchSettingsCatalog.items.size)
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
        assertEquals(14, WatchSettingsCatalog.items.size)
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
        assertEquals(14, WatchSettingsCatalog.items.size)
        WatchSettingsCatalog.items.filter { it.form == WatchSettingForm.SWITCH }.forEach { item ->
            assertNotNull("开关必须有偏好键: " + item.id, item.preferenceKey)
        }
        WatchSettingsCatalog.items.filter { it.form != WatchSettingForm.SWITCH }.forEach { item ->
            assertNull("非开关项不挂在可写偏好键上: " + item.id, item.preferenceKey)
        }
    }

    @Test
    fun wakeOnScreenAndLockAreReadOnlyBecauseTheCapabilityIsGone() {
        listOf("voice.wakeOnScreen", "voice.wakeOnLockScreen").forEach { id ->
            val item = WatchSettingsCatalog.items.firstOrNull { it.id == id }
            assertNotNull("缺项: " + id, item)
            assertEquals(WatchSettingForm.READ_ONLY, item?.form)
            assertNull("只读项不得挂在可写偏好键上: " + id, item?.preferenceKey)
        }
    }

    @Test
    fun eachDomainOfTheFourteenIsCounted() {
        assertEquals(
            mapOf(
                WatchSettingsDomain.VOICE to 5,
                WatchSettingsDomain.TOOLS to 1,
                WatchSettingsDomain.MODEL to 1,
                WatchSettingsDomain.MEMORY to 1,
                WatchSettingsDomain.CONTEXT to 2,
                WatchSettingsDomain.INTERFACE to 3,
                WatchSettingsDomain.STATUS to 1
            ),
            WatchSettingsCatalog.items.groupingBy { it.domain }.eachCount()
        )
    }
}
