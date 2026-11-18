package com.example.myapplication.agent.remote

import com.example.myapplication.agent.ProviderProfile
import com.example.myapplication.agent.ProviderProfileBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 网页提交供应商档案的落库规则。
 *
 * 回归背景（红队 0.0.0.100 实证）：`ProviderProfileBook.add()` 满 5 套或 id 重复时**原样返回**，
 * 旧实现当成功处理 —— 加第 6 套回 200 + `ok:true`，账本里却只有 5 套，第 6 套静默消失；
 * 两行同名也同理。0.0.0.95 只改了返回码、没改这里，等于没修。
 */
class ConsoleProviderProfilesTest {

    private var nextId = 0
    private fun id() = "p" + (++nextId)

    private fun row(name: String, model: String = "m", baseUrl: String = "https://x") =
        mapOf("name" to name, "baseUrl" to baseUrl, "model" to model)

    private fun existing(count: Int): ProviderProfileBook =
        (1..count).fold(ProviderProfileBook()) { book, index ->
            book.add(ProviderProfile(id = "old$index", name = "档案$index", baseUrl = "u", apiKey = "k", modelName = "m"))
        }

    @Test
    fun sixProfilesCannotSilentlyBecomeFive() {
        val book = rebuildProviderBookFromWebRows(
            entries = (1..6).map { row("新$it") },
            existing = ProviderProfileBook(),
            newId = { id() },
            fallbackApiKey = "k"
        )
        assertNull("第 6 套落不下去时必须整体拒绝，而不是静默丢一条", book)
    }

    @Test
    fun duplicateNamesAreRejectedInsteadOfCollapsingIntoOne() {
        val book = rebuildProviderBookFromWebRows(
            entries = listOf(row("同一个名字"), row("同一个名字")),
            existing = ProviderProfileBook(),
            newId = { id() },
            fallbackApiKey = "k"
        )
        assertNull("两行同名会拿到同一个 id，第二行会被 add 丢掉 —— 必须拒绝", book)
    }

    @Test
    fun aNormalPayloadStillLandsAndKeepsExistingIds() {
        val before = existing(2)
        val book = rebuildProviderBookFromWebRows(
            entries = listOf(
                row("档案1", model = "改过的模型"),
                row("档案2"),
                row("全新的")
            ),
            existing = before,
            newId = { id() },
            fallbackApiKey = "k"
        )
        assertNotNull(book)
        assertEquals(3, book!!.profiles.size)
        assertEquals("同名行必须沿用原 id（否则 apiKey 会丢）", "old1", book.profiles[0].id)
        assertEquals("改过的模型", book.profiles[0].modelName)
        assertEquals("新行的 apiKey 用当前单值兜底", "k", book.profiles[2].apiKey)
    }

    @Test
    fun emptyNameOrNonObjectRowIsRejected() {
        assertNull(
            rebuildProviderBookFromWebRows(listOf(row("")), ProviderProfileBook(), { id() }, "k")
        )
        assertNull(
            rebuildProviderBookFromWebRows(listOf("不是对象"), ProviderProfileBook(), { id() }, "k")
        )
    }
}
