package com.example.myapplication.agent.remote

import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.agent.FakeSharedPreferences
import com.example.myapplication.presentation.ui.settings.SettingsCoverage
import com.example.myapplication.presentation.ui.settings.WebSettingsForm
import com.example.myapplication.readProjectFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「网页能改、手表不生效」的守护测试：网页 PAGES 的每个设置键都必须能在 ConsoleBridge 的映射表里落地。
 *
 * 真值来源是 wear/src/main/assets/console/app.js 的 PAGES 与
 * ConsoleFields / AgentPreferencesSettingsPort（POST /api/settings 的真实通路），
 * 不抄一份字段清单：网页新增一行、桥里漏一条映射，这里立刻变红。
 */
class ConsoleSettingsParityGuardTest {

    @Test
    fun pagesParseMatchesTheDocumentedShape() {
        val rows = consoleRows()
        assertEquals("网页 PAGES 页数", 9, rows.map { it.pageId }.toSet().size)
        assertEquals("网页 PAGES 行数", 57, rows.size)
        assertEquals("带设置键的行数", 49, rows.count { it.keys.isNotEmpty() })
        assertEquals(
            "无键的行必须是纯动作 / 只读回显：",
            expectedUnkeyedRows,
            rows.filter { it.keys.isEmpty() }.map { it.pageId + "." + it.id }.toSet()
        )
    }

    @Test
    fun everyWatchWritableWebRowWritesThroughToTheWatch() {
        val request = SettingsRequest(AgentPreferencesSettingsPort(AgentPreferences(FakeSharedPreferences())))
        val violations = mutableListOf<String>()

        for (row in consoleRows()) {
            if (row.keys.isEmpty()) continue
            val rowKey = row.pageId + "." + row.id
            val form = SettingsCoverage.formOf(rowKey)
            if (form == null) {
                violations += rowKey + " 未在 SettingsCoverage 登记（表端是否可达没有声明）"
                continue
            }
            if (form !in WebSettingsForm.WATCH_WRITABLE_FORMS) continue

            for (alias in row.keys) {
                val field = ConsoleFields.field(alias)
                if (field == null) {
                    violations += rowKey + "：网页字段 " + alias +
                        " 在 ConsoleFields / ConsoleBridge 映射表里不存在，POST /api/settings 会把它归入 ignored，手表端永远收不到这次修改"
                    continue
                }
                val coerced = when (val result = ConsoleFields.coerce(field, sampleValue(field, request.read(field.key)))) {
                    is ConsoleValue.Valid -> result.value
                    ConsoleValue.BadType -> {
                        violations += rowKey + "：" + alias + " 的测试样值与字段类型不符"
                        continue
                    }
                    is ConsoleValue.BadValue -> {
                        violations += rowKey + "：" + alias + " 的测试样值不在允许值内"
                        continue
                    }
                }
                if (request.write(field.key, coerced) != ConsoleWriteResult.OK) {
                    violations += rowKey + "：" + alias + " 写回被拒绝（READ_ONLY）"
                    continue
                }
                val landed = request.read(field.key)
                if (landed != coerced) {
                    violations += rowKey + "：" + alias + " 写入后读回不一致（期望 " + coerced + "，实际 " + landed + "）"
                }
            }
        }

        assertEquals(
            "这些网页行在 SettingsCoverage 里声明为手表可改，但控制台桥没有真正落到 AgentPreferences：",
            emptyList<String>(),
            violations
        )
    }

    @Test
    fun noNewWebFieldIsSilentlyDroppedByTheBridge() {
        val rows = consoleRows().filter { it.keys.isNotEmpty() }
        val dropped = rows
            .filter { ConsoleFields.field(it.keys.first()) == null }
            .map { it.pageId + "." + it.id }
            .toSet()
        assertEquals(
            "网页新增字段必须补上 ConsoleBridge / ConsoleFields 映射，否则网页改完手表毫无变化；" +
                "确属网页专属的，登记进 KnownDroppedWebRows：",
            emptySet<String>(),
            dropped - knownDroppedWebRows
        )
        assertEquals(
            "KnownDroppedWebRows 里的行必须仍然存在于网页 PAGES（删行时同步收缩基线，别让基线变成永久豁免）：",
            emptySet<String>(),
            knownDroppedWebRows - rows.map { it.pageId + "." + it.id }.toSet()
        )
    }

    @Test
    fun theParserReportsANewUnmappedRow() {
        // 三角验证：扫描器不是恒返回空表的摆设
        val rows = parseConsoleRows(syntheticPages)
        assertEquals("合成页必须有 2 行", 2, rows.size)
        assertEquals("model", rows[0].pageId)
        assertEquals(listOf("baseUrl"), rows[0].keys)
        assertEquals(listOf("brandNewField"), rows[1].keys)
        assertEquals(
            listOf("brandNewField"),
            rows.filter { ConsoleFields.field(it.keys.first()) == null }.map { it.id }
        )
    }

    @Test
    fun theBridgeRejectsAKeyTheApiLayerDoesNotKnow() {
        // 三角验证：写回通路对未登记键必须真的返回 READ_ONLY，而不是全盘接受
        val port = AgentPreferencesSettingsPort(AgentPreferences(FakeSharedPreferences()))
        assertNull("未登记字段必须被 API 层挡掉（ConsoleApi 会把它归入 ignored）", ConsoleFields.field("totallyUnknownField"))
        assertEquals("写回层必须拒绝未登记字段", ConsoleWriteResult.READ_ONLY, port.write("totallyUnknownField", true))
    }

    @Test
    fun everyBridgeWriteBranchIsDeclaredInTheFieldTable() {
        val source = readProjectFile(consoleBridgePath)
        assertTrue("ConsoleBridge 里找不到 AgentPreferencesSettingsPort.write", source.contains("override fun write(key: String, value: Any): ConsoleWriteResult"))
        val body = source
            .substringAfter("override fun write(key: String, value: Any): ConsoleWriteResult")
            .substringBefore("return ConsoleWriteResult.OK")
        val branches = writeBranchPattern.findAll(body).map { it.groupValues[1] }.toSet()

        assertEquals(
            "ConsoleFields.ALL 里可写的键必须在 write() 里有落点：",
            emptyList<String>(),
            ConsoleFields.ALL.filterNot { it.readOnly }.map { it.key }.filter { it !in branches }
        )
        assertEquals(
            "write() 里的分支必须在 ConsoleFields.ALL 登记，否则 API 层根本不会转发过来（死分支）：",
            emptyList<String>(),
            branches.filter { key -> ConsoleFields.ALL.none { it.key == key } }
        )
    }

    @Test
    fun everyDeclaredFieldIsEchoedBackInTheSnapshot() {
        val source = readProjectFile(consoleBridgePath)
        val body = source
            .substringAfter("override fun snapshot(): Map<String, Any?> = linkedMapOf(")
            .substringBefore("override fun write(")
        val echoed = snapshotEntryPattern.findAll(body).map { it.groupValues[1] }.toSet()
        assertEquals(
            "ConsoleFields.ALL 的每个字段都必须出现在 snapshot() 回显里，否则手表写进去的值网页读不回来：",
            emptyList<String>(),
            ConsoleFields.ALL.map { it.key }.filter { it !in echoed }
        )
    }

    private fun consoleRows(): List<ConsoleRow> = parseConsoleRows(readProjectFile(consolePagesPath))

    private fun parseConsoleRows(pagesText: String): List<ConsoleRow> {
        val block = pagesBlock(pagesText)
        val rows = mutableListOf<ConsoleRow>()
        var pageId = ""
        for (line in block.lines()) {
            val header = pageHeaderPattern.find(line)
            if (header != null) {
                pageId = header.groupValues[1]
                continue
            }
            if (!rowStartPattern.containsMatchIn(line)) continue
            val id = rowIdPattern.find(line)?.groupValues?.get(1) ?: continue
            val type = rowTypePattern.find(line)?.groupValues?.get(1) ?: continue
            val keysGroup = keysPattern.find(line)?.groupValues?.get(1)
            val keys = if (keysGroup == null) {
                emptyList()
            } else {
                keyLiteralPattern.findAll(keysGroup).map { it.groupValues[1] }.toList()
            }
            rows += ConsoleRow(pageId = pageId, id = id, type = type, keys = keys)
        }
        return rows
    }

    private fun pagesBlock(text: String): String {
        val start = text.indexOf("var PAGES = [")
        require(start >= 0) { "app.js 里找不到 var PAGES = [" }
        val end = text.indexOf("\n  ];", start)
        require(end > start) { "PAGES 数组没有按预期用两空格缩进的 ]; 收尾" }
        return text.substring(start, end)
    }

    /** 复刻 ConsoleApi.updateSettings 的真实通路：查表 -> coerce -> write -> snapshot 读回。 */
    private class SettingsRequest(private val port: AgentPreferencesSettingsPort) {
        fun read(key: String): Any? = port.snapshot()[key]
        fun write(key: String, value: Any): ConsoleWriteResult = port.write(key, value)
    }

    /**
     * 造一个与当前值不同、且类型与 JSON 反序列化结果一致的样值。
     *
     * structured 字段（providerProfiles 这类列表）必须造对象数组：它们用 STRING 承载，
     * 落库侧收的却是数组，只按标量类型造样值只会得到 "-guard" 这样的字符串，于是任何
     * **正确**的实现都会被判成 READ_ONLY。这是取样器的缺陷，不是给某个字段开豁免。
     */
    private fun sampleValue(field: ConsoleField, current: Any?): Any = when {
        field.structured -> listOf(
            mapOf("name" to "guard", "baseUrl" to "https://guard.example.com", "model" to "guard-model")
        )
        field.type == ConsoleFieldType.BOOLEAN -> !(current as? Boolean ?: false)
        field.type == ConsoleFieldType.INT -> ((current as? Number)?.toInt() ?: 0) + 7
        field.type == ConsoleFieldType.FLOAT -> ((current as? Number)?.toDouble() ?: 0.0) + 0.25
        field.type == ConsoleFieldType.STRING -> ((current as? String) ?: "") + "-guard"
        else -> field.allowed.firstOrNull { it != current } ?: field.allowed.first()
    }

    private data class ConsoleRow(val pageId: String, val id: String, val type: String, val keys: List<String>)

    private companion object {
        const val consolePagesPath = "src/main/assets/console/app.js"
        const val consoleBridgePath = "src/main/java/com/example/myapplication/agent/remote/ConsoleBridge.kt"

        val pageHeaderPattern = Regex("^\\s*id:\\s*'([^']+)',\\s*title:")
        val rowStartPattern = Regex("^\\s*\\{\\s*id:\\s*'")
        val rowIdPattern = Regex("id:\\s*'([^']+)'")
        val rowTypePattern = Regex("type:\\s*'([^']+)'")
        val keysPattern = Regex("keys:\\s*\\[([^\\]]*)\\]")
        val keyLiteralPattern = Regex("'([^']+)'")
        val writeBranchPattern = Regex("\"([A-Za-z0-9_]+)\"\\s*->")
        val snapshotEntryPattern = Regex("\"([A-Za-z0-9_]+)\" to ")

        val expectedUnkeyedRows = setOf(
            "model.actModels",
            "dialog.actClearMemory",
            "device.infoRoot",
            "device.infoShizuku",
            "device.infoInternet",
            "device.actProbe",
            "device.actAsr",
            "device.actExport"
        )

        /**
         * 网页有设置键、但桥里没有映射的行（HEAD d4defe1 实测）。
         *
         * 只允许收缩：桥补上一条就去掉一条。其中 provider.providerProfiles 与
         * dialog.longTermMemoryEnabled 与 SettingsCoverage 的「表端可改」声明直接冲突，
         * 由 [everyWatchWritableWebRowWritesThroughToTheWatch] 单独钉住。
         */
        val knownDroppedWebRows = setOf(
            "model.topP",
            "model.topK",
            "model.frequencyPenalty",
            "provider.providerProfiles",
            "provider.customHeaders",
            "provider.proxyUrl",
            "role.roleCards",
            "role.systemPromptLibrary",
            "role.openingLine",
            "generate.contextWindow",
            "generate.historyCompressThreshold",
            "generate.truncationStrategy",
            "dialog.longTermMemoryEnabled",
            "dialog.memoryNoteCount",
            "dialog.sandboxLevel",
            "dialog.dangerousCommandBlacklist",
            "tools.toolApprovalPolicy",
            "tools.mcpServers"
        )

        val syntheticPages = "" +
            "var PAGES = [\n" +
            "    {\n" +
            "      id: 'model', title: '合成页',\n" +
            "      sections: [{\n" +
            "        title: '接口', rows: [\n" +
            "          { id: 'baseUrl', type: 'text', keys: ['baseUrl'] },\n" +
            "          { id: 'brandNewField', type: 'switch', keys: ['brandNewField'] }\n" +
            "        ]\n" +
            "      }]\n" +
            "    }\n" +
            "  ];"
    }
}
