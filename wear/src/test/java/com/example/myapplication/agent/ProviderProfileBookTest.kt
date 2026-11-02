package com.example.myapplication.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 多供应商 Profile 的纯逻辑与序列化：不依赖 Context、不依赖 Compose，直接把
 * 「上限 5 套」「删除当前项后 activeProfileId 回退」「改名不动选中项」「往返不丢字符」钉死。
 */
class ProviderProfileBookTest {

    private fun profile(id: String, name: String = id, model: String = "deepseek-chat") =
        ProviderProfile(
            id = id,
            name = name,
            baseUrl = "https://api." + id + ".com",
            apiKey = "sk-" + id,
            modelName = model
        )

    private fun book(vararg ids: String) =
        ids.fold(ProviderProfileBook()) { acc, id -> acc.add(profile(id)) }

    @Test
    fun newProfileFromCurrentBecomesActiveAndCopiesSingleValueFields() {
        val endpoint = ProviderEndpoint(
            baseUrl = "https://api.moonshot.cn",
            apiKey = "sk-moon",
            modelName = "kimi-k2"
        )

        val next = ProviderProfileBook()
            .addFromCurrent(name = "月之暗面", endpoint = endpoint, id = "p1")

        val active = next.active()
        assertEquals("p1", next.activeProfileId)
        assertEquals("月之暗面", active?.name)
        // 套用即写入现有单值字段的三项，一个字段都不能错位
        assertEquals(endpoint, active?.endpoint())
    }

    @Test
    fun atMostFiveProfilesAreKept() {
        val full = book("p1", "p2", "p3", "p4", "p5")
        assertEquals(ProviderProfileBook.MaxProfiles, full.profiles.size)

        val overflow = full.add(profile("p6"))

        assertEquals(5, overflow.profiles.size)
        assertTrue(overflow.profiles.none { it.id == "p6" })
        // 越界写入被拒时返回的是原实例，调用方据此提示"已达上限"
        assertSame(full, overflow)
    }

    @Test
    fun removingActiveProfileFallsBackToFirstRemaining() {
        val removed = book("p1", "p2", "p3").select("p2").remove("p2")

        assertEquals(listOf("p1", "p3"), removed.profiles.map { it.id })
        assertEquals("p1", removed.activeProfileId)
    }

    @Test
    fun removingNonActiveProfileKeepsActiveSelection() {
        val removed = book("p1", "p2", "p3").select("p3").remove("p1")

        assertEquals("p3", removed.activeProfileId)
        assertEquals("p3", removed.active()?.id)
    }

    @Test
    fun removingLastProfileClearsActiveSelection() {
        val removed = book("p1").select("p1").remove("p1")

        assertTrue(removed.profiles.isEmpty())
        assertNull(removed.activeProfileId)
        assertNull(removed.active())
    }

    @Test
    fun renameOnlyTouchesNameAndKeepsSelection() {
        val renamed = book("p1", "p2").select("p2").rename("p2", "  备用线路  ")

        assertEquals("备用线路", renamed.profiles.first { it.id == "p2" }.name)
        assertEquals("p2", renamed.activeProfileId)
        // 空名称被忽略，不会把名字改没
        assertEquals(renamed, renamed.rename("p2", "   "))
        // 目标不存在时原样返回
        assertSame(renamed, renamed.rename("nope", "x"))
    }

    @Test
    fun selectIgnoresUnknownId() {
        val selected = book("p1").select("p1")

        assertSame(selected, selected.select("missing"))
        assertEquals("p1", selected.activeProfileId)
    }

    @Test
    fun codecRoundTripsSeparatorsTildeAndUnicode() {
        val nasty = ProviderProfile(
            id = "p1",
            // 分隔符与波浪号真的要能往返：baseUrl 里带查询串时常见 ~ 与 &，
            // 而 UNIT/RECORD SEPARATOR 是内部转义字母 "~f" "~r" 的来源，必须不被当成分隔符
            name = "线路 ~f ~r ~\u001E\u001F 一号",
            baseUrl = "https://api.example.com/v1?x=~f&y=1",
            apiKey = "sk-a\u001Fb\u001Ec",
            modelName = "deepseek-reasoner"
        )
        val original = ProviderProfileBook(profiles = listOf(nasty), activeProfileId = "p1")

        val decoded = ProviderProfileCodec.decode(ProviderProfileCodec.encode(original))

        assertEquals(original, decoded)
        assertEquals(nasty.name, decoded.profiles.single().name)
        assertEquals(nasty.apiKey, decoded.profiles.single().apiKey)
    }

    @Test
    fun codecKeepsMultipleRecordsAndActiveId() {
        val original = book("p1", "p2", "p3").select("p3")

        val decoded = ProviderProfileCodec.decode(ProviderProfileCodec.encode(original))

        assertEquals(listOf("p1", "p2", "p3"), decoded.profiles.map { it.id })
        assertEquals("p3", decoded.activeProfileId)
    }

    @Test
    fun codecReturnsEmptyBookForMissingOrCorruptData() {
        assertEquals(ProviderProfileBook(), ProviderProfileCodec.decode(null))
        assertEquals(ProviderProfileBook(), ProviderProfileCodec.decode(""))
        assertEquals(ProviderProfileBook(), ProviderProfileCodec.decode("garbage"))
        // 版本不符：宁可当作没有配置，也不要把旧格式猜成新格式
        assertEquals(ProviderProfileBook(), ProviderProfileCodec.decode("v0\u001Ep1"))
        // active 指向已被删掉的 id 时回退为 null，而不是留一个悬空指针
        val dangling = ProviderProfileCodec.decode("v1\u001Ep9\u001Ep1\u001F名称\u001Fhttps://a\u001Fsk\u001Fm")
        assertEquals("p1", dangling.profiles.single().id)
        assertNull(dangling.activeProfileId)
    }

    @Test
    fun bookIsImmutableSoScreensCannotDriftFromPersistedState() {
        val original = book("p1")
        val added = original.add(profile("p2"))

        assertEquals(1, original.profiles.size)
        assertEquals(2, added.profiles.size)
        assertFalse(original.profiles.any { it.id == "p2" })
    }
}
