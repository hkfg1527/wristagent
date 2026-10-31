package com.example.myapplication.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AsrServiceRouterTest {

    private val baiduReady = AsrSettings(
        enabled = true,
        providerId = AsrProviderId.BAIDU,
        baiduApiKey = "AK",
        baiduSecretKey = "SK"
    )

    @Test
    fun disabledSwitchIsUnavailableWithChineseReason() {
        val route = AsrServiceRouter.resolve(baiduReady.copy(enabled = false), systemRecognizerAvailable = true)

        assertTrue(route is AsrRoute.Unavailable)
        val reason = (route as AsrRoute.Unavailable).reason
        assertTrue(reason.isNotBlank())
        assertTrue("应提示已关闭: " + reason, reason.contains("关闭"))
    }

    @Test
    fun configuredBaiduIsSelectedDirectly() {
        val route = AsrServiceRouter.resolve(baiduReady, systemRecognizerAvailable = false)

        assertTrue(route is AsrRoute.UseProvider)
        val use = route as AsrRoute.UseProvider
        assertEquals(AsrProviderId.BAIDU, use.providerId)
        assertNull("无降级时不应有提示", use.note)
    }

    @Test
    fun baiduWithoutKeysFallsBackToSystemWhenAvailable() {
        val route = AsrServiceRouter.resolve(
            baiduReady.copy(baiduApiKey = "", baiduSecretKey = ""),
            systemRecognizerAvailable = true
        )

        val use = route as AsrRoute.UseProvider
        assertEquals(AsrProviderId.SYSTEM, use.providerId)
        assertTrue("必须说明降级原因: " + use.note, use.note.orEmpty().contains("回退"))
    }

    @Test
    fun baiduWithoutKeysAndNoSystemReportsMissingKeys() {
        val route = AsrServiceRouter.resolve(
            baiduReady.copy(baiduApiKey = "  ", baiduSecretKey = " "),
            systemRecognizerAvailable = false
        )

        val unavailable = route as AsrRoute.Unavailable
        assertTrue(unavailable.reason.isNotBlank())
        assertTrue("应提示缺少密钥: " + unavailable.reason, unavailable.reason.contains("密钥"))
    }

    @Test
    fun systemProviderUnavailableReportsMissingEngine() {
        val route = AsrServiceRouter.resolve(
            AsrSettings(enabled = true, providerId = AsrProviderId.SYSTEM),
            systemRecognizerAvailable = false
        )

        val unavailable = route as AsrRoute.Unavailable
        assertTrue("应提示未安装识别服务: " + unavailable.reason, unavailable.reason.contains("未安装"))
        assertTrue(unavailable.reason.isNotBlank())
    }

    @Test
    fun systemProviderSelectedWhenEngineExists() {
        val route = AsrServiceRouter.resolve(
            AsrSettings(enabled = true, providerId = AsrProviderId.SYSTEM),
            systemRecognizerAvailable = true
        )

        assertEquals(AsrProviderId.SYSTEM, (route as AsrRoute.UseProvider).providerId)
    }

    @Test
    fun storedProviderStringParsesCaseInsensitivelyAndFallsBackToSystem() {
        assertEquals(AsrProviderId.BAIDU, AsrProviderId.fromStorage("baidu"))
        assertEquals(AsrProviderId.BAIDU, AsrProviderId.fromStorage("  Baidu "))
        assertEquals(AsrProviderId.SYSTEM, AsrProviderId.fromStorage("system"))
        assertEquals(AsrProviderId.SYSTEM, AsrProviderId.fromStorage(null))
        assertEquals(AsrProviderId.SYSTEM, AsrProviderId.fromStorage("unknown-vendor"))
    }

    @Test
    fun devPidMappingCoversSupportedLanguages() {
        assertEquals(1537, AsrLanguage.MANDARIN.devPid)
        assertEquals(1737, AsrLanguage.ENGLISH.devPid)
        assertEquals(1637, AsrLanguage.CANTONESE.devPid)
        assertEquals(AsrLanguage.CANTONESE, AsrLanguage.fromDevPid(1637))
        assertEquals(AsrLanguage.MANDARIN, AsrLanguage.fromDevPid(999999))
    }
}
