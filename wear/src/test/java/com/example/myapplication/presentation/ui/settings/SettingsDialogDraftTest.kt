package com.example.myapplication.presentation.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 文本对话框草稿：编辑不落盘、确认才提交、取消整段丢弃。
 *
 * 三条路径都是纯逻辑，因此不需要 Robolectric 或 Compose 测试环境。
 */
class SettingsDialogDraftTest {

    @Test
    fun freshDraftCommitsNothing() {
        // 打开后直接确认：草稿等于初值，不产生任何写入
        val draft = SettingsDialogDraft("https://api.deepseek.com")
        assertFalse(draft.isDirty)
        assertNull(draft.commitValue())
    }

    @Test
    fun editedDraftCommitsTheDraft() {
        val draft = SettingsDialogDraft("deepseek-chat").edit("deepseek-reasoner")
        assertTrue(draft.isDirty)
        assertEquals("deepseek-reasoner", draft.commitValue())
    }

    @Test
    fun draftKeepsCjkAndWhitespaceVerbatim() {
        // 第二组差异化数据：中文 + 空格 + 换行，提交时不做 trim
        val text = "系统提示词 第一行\n第二行"
        assertEquals(text, SettingsDialogDraft("").edit(text).commitValue())
    }

    @Test
    fun clearingTheValueIsARealCommit() {
        // 清空是合法提交：空串必须能落盘，不能被当成"没改"吞掉
        val draft = SettingsDialogDraft("sk-1234").edit("")
        assertTrue(draft.isDirty)
        assertEquals("", draft.commitValue())
    }

    @Test
    fun editingBackToTheOpenedValueCommitsNothing() {
        val draft = SettingsDialogDraft("sk-1234").edit("sk-9999").edit("sk-1234")
        assertFalse(draft.isDirty)
        assertNull(draft.commitValue())
    }

    @Test
    fun cancellingDropsTheDraftWithoutTouchingTheOpenedValue() {
        // 取消 = 丢掉编辑后的草稿：打开时那份既没被修改，也不会产生写入
        val opened = SettingsDialogDraft("sk-1234")
        val edited = opened.edit("sk-9999")
        assertNull(opened.commitValue())
        assertEquals("sk-1234", opened.text)
        // 编辑后的草稿只在确认时才可能落盘，取消路径永远读不到它
        assertEquals("sk-9999", edited.commitValue())
        assertNull(opened.edit("sk-1234").commitValue())
    }
}

/** 行取值可见规则：200 / 201 的边界必须写对，写反就是窄屏多出一条被截断的取值。 */
class SettingsRowValueVisibilityTest {

    @Test
    fun narrowScreensAndTheBoundaryItselfHideTheValue() {
        assertFalse(settingsRowShowsValue(192))
        assertFalse(settingsRowShowsValue(SettingsRowValueMinScreenWidthDp))
    }

    @Test
    fun oneDpAboveTheBoundaryAndRealWatchesShowTheValue() {
        // +1 是真机侧唯一的开关点；233dp 是 TicWatch Pro 5 的实测宽度
        assertTrue(settingsRowShowsValue(SettingsRowValueMinScreenWidthDp + 1))
        assertTrue(settingsRowShowsValue(233))
    }
}
