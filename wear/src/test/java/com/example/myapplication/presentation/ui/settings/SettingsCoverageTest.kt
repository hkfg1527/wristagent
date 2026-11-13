package com.example.myapplication.presentation.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「网页 ⊇ 手表」超集关系的唯一断言处。
 *
 * 网页端 9 页 / 56 行的权威清单来自 wear/src/main/assets/console/app.js 的 PAGES 定义，
 * 这里逐行抄成测试夹具：一旦网页端增删行，本夹具必须先同步，否则下面的覆盖断言会直接失败。
 */
class SettingsCoverageTest {

    @Test
    fun fixtureMatchesWebConsoleShape() {
        // 10 页 / 63 行：与 app.js PAGES 的 sections[].rows[] 数量逐页一致
        assertEquals(10, WebFixture.pages.size)
        assertEquals(63, WebFixture.pages.sumOf { it.rows.size })
        assertEquals(
            listOf(
                "model" to 11,
                "provider" to 3,
                "role" to 3,
                "generate" to 7,
                "dialog" to 7,
                "voice" to 4,
                "tools" to 8,
                "interface" to 7,
                "network" to 7,
                "device" to 6
            ),
            WebFixture.pages.map { it.id to it.rows.size }
        )
    }

    @Test
    fun everyWebRowIsReachableFromWatch() {
        // 超集的第一半：网页有的每一行，手表侧都有可达入口（可写 / 只读回显 / 手机网页指引）
        assertEquals(emptyList<String>(), SettingsCoverage.missingInWatch(WebFixture.pages))
        assertTrue(SettingsCoverage.isSuperset(WebFixture.pages))
    }

    @Test
    fun everyWebPageHasAtLeastOneWatchEntry() {
        val coveredPages = SettingsCoverage.entries.map { it.pageId }.toSet()
        val webPages = WebFixture.pages.map { it.id }.toSet()
        assertEquals(emptySet<String>(), webPages - coveredPages)
    }

    @Test
    fun unknownWebRowIsReportedAsMissing() {
        // 三角验证：这函数不是恒返回空表的摆设
        val grown = WebFixture.pages + WebSettingsPage(
            id = "model",
            rows = listOf(WebSettingsRow(id = "brandNewRow", type = "text"))
        )
        assertEquals(listOf("model.brandNewRow"), SettingsCoverage.missingInWatch(grown))
    }

    @Test
    fun watchCanWriteStrictlyFewerRowsThanWeb() {
        // 超集的第二半：手表可写的行严格少于网页可写的行
        val watchWritable = SettingsCoverage.watchWritable(WebFixture.pages)
        assertTrue(SettingsCoverage.isStrictSuperset(WebFixture.pages))
        // 本轮把 3 行从"仅网页"提升为表端可写（压缩阈值 / 审批策略 / 沙箱等级），
        // 并把网页端新增的 generate.maxContextTurns 一起登记成 SLIDER
        assertEquals(44, watchWritable.size)
        assertEquals(63 - 44, WebFixture.pages.flatMap { p -> p.rows.map { p.id + "." + it.id } }
            .count { it !in watchWritable })
        // 手表可写的每一行都必须真实存在于网页端（不能凭空造出手表独有的可写项）
        val webIds = WebFixture.pages.flatMap { p -> p.rows.map { p.id + "." + it.id } }.toSet()
        assertEquals(emptySet<String>(), watchWritable.toSet() - webIds)
    }

    @Test
    fun watchSideSplitsIntoWritableReadOnlyAndWebOnly() {
        assertEquals(44, SettingsCoverage.byForm(WebSettingsForm.WATCH_WRITABLE_FORMS).size)
        assertEquals(4, SettingsCoverage.byForm(listOf(WebSettingsForm.READ_ONLY)).size)
        assertEquals(15, SettingsCoverage.byForm(listOf(WebSettingsForm.WEB_ONLY)).size)
        assertEquals(63, SettingsCoverage.entries.size)
    }

    @Test
    fun languageEditingStaysOnWebAndTogglesStayOnWatch() {
        // 需要长文本 / 列表编辑的，表端一律「请在手机网页修改」
        assertEquals(WebSettingsForm.TEXT_ENTRY, SettingsCoverage.formOf("model.baseUrl"))
        assertEquals(WebSettingsForm.TEXT_ENTRY, SettingsCoverage.formOf("model.apiKey"))
        assertEquals(WebSettingsForm.WEB_ONLY, SettingsCoverage.formOf("dialog.dangerousCommandBlacklist"))
        assertEquals(WebSettingsForm.WEB_ONLY, SettingsCoverage.formOf("tools.mcpServers"))
        assertEquals(WebSettingsForm.WEB_ONLY, SettingsCoverage.formOf("role.roleCards"))
        // 本轮补全的三档：网页有、表端此前连存储都没有
        assertEquals(WebSettingsForm.SLIDER, SettingsCoverage.formOf("generate.historyCompressThreshold"))
        // 网页端新增的上下文轮数滑杆：表端登记表必须同步，键路径严格是 generate.maxContextTurns
        assertEquals(WebSettingsForm.SLIDER, SettingsCoverage.formOf("generate.maxContextTurns"))
        assertEquals(WebSettingsForm.TRI_STATE, SettingsCoverage.formOf("tools.toolApprovalPolicy"))
        assertEquals(WebSettingsForm.TRI_STATE, SettingsCoverage.formOf("dialog.sandboxLevel"))
        assertEquals(WebSettingsForm.SWITCH, SettingsCoverage.formOf("tools.toolWebSearchEnabled"))
        assertEquals(WebSettingsForm.SWITCH, SettingsCoverage.formOf("dialog.longTermMemoryEnabled"))
        // 网页端一旦多出表端没落位的一页，超集立刻不成立
        val grownConsole = WebFixture.pages +
            WebSettingsPage("brandNewPage", listOf(WebSettingsRow("whatever", "switch")))
        assertFalse(SettingsCoverage.isStrictSuperset(grownConsole))
    }

    /** app.js PAGES 的逐行抄本（id / type 两列足够做超集校验）。 */
    private object WebFixture {
        val pages = listOf(
            WebSettingsPage(
                "model",
                listOf(
                    row("baseUrl", "text"), row("apiKey", "secret"), row("modelName", "select"),
                    row("actModels", "action"), row("streamMode", "switch"), row("topP", "slider"),
                    row("topK", "slider"), row("frequencyPenalty", "slider"),
                    row("defaultThinkingEnabled", "switch"), row("providerManagedSearchEnabled", "switch"),
                    row("autoCompressContext", "switch")
                )
            ),
            WebSettingsPage(
                "provider",
                listOf(row("providerProfiles", "list"), row("customHeaders", "json"), row("proxyUrl", "text"))
            ),
            WebSettingsPage(
                "role",
                listOf(row("roleCards", "list"), row("systemPromptLibrary", "list"), row("openingLine", "textarea"))
            ),
            WebSettingsPage(
                "generate",
                listOf(
                    row("temperature", "slider"), row("maxTokens", "slider"), row("customSystemPrompt", "textarea"),
                    row("contextWindow", "slider"), row("historyCompressThreshold", "slider"),
                    row("maxContextTurns", "slider"), row("truncationStrategy", "segmented")
                )
            ),
            WebSettingsPage(
                "dialog",
                listOf(
                    row("longTermMemoryEnabled", "switch"), row("memoryNoteCount", "slider"),
                    row("actClearMemory", "action"), row("sandboxLevel", "segmented"),
                    row("dangerousCommandBlacklist", "list"), row("securityMode", "segmented"),
                    row("subAgentEnabled", "switch")
                )
            ),
            WebSettingsPage(
                "voice",
                listOf(
                    row("asrEnabled", "switch"), row("asrProvider", "segmented"),
                    row("baiduApiKey", "secret"), row("baiduSecretKey", "secret")
                )
            ),
            WebSettingsPage(
                "tools",
                listOf(
                    row("toolDeviceStatusEnabled", "switch"), row("toolVibrateEnabled", "switch"),
                    row("toolMatrixDensityEnabled", "switch"), row("toolPowerModeEnabled", "switch"),
                    row("toolWeatherEnabled", "switch"), row("toolWebSearchEnabled", "switch"),
                    row("toolApprovalPolicy", "segmented"), row("mcpServers", "list")
                )
            ),
            WebSettingsPage(
                "interface",
                listOf(
                    row("isMcpEnabled", "switch"), row("mcpEndpoint", "text"), row("matrixSpacingDp", "slider"),
                    row("animationsEnabled", "switch"), row("compactMode", "switch"),
                    row("autoSpeakReplies", "switch"), row("asrLiveTranscript", "switch")
                )
            ),
            WebSettingsPage(
                "network",
                listOf(
                    row("requestTimeoutSeconds", "slider"), row("requestRetries", "slider"),
                    row("retryBackoffMs", "slider"), row("wifiOnlyRequests", "switch"),
                    row("settingsStyle", "segmented"), row("replyStyle", "segmented"),
                    row("hapticsEnabled", "switch")
                )
            ),
            WebSettingsPage(
                "device",
                listOf(
                    row("infoRoot", "info"), row("infoShizuku", "info"), row("infoInternet", "info"),
                    row("actProbe", "action"), row("actAsr", "action"), row("actExport", "action")
                )
            )
        )

        private fun row(id: String, type: String) = WebSettingsRow(id = id, type = type)
    }
}
