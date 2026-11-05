package com.example.myapplication.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 执行型 agent 的系统提示词：装配顺序、customSystemPrompt 覆盖，
 * 以及资源里的中英两份提示词各自都带齐四段硬约束（身份 / 能力 / 纪律 / 边界）。
 */
class AgentSystemPromptTest {

    private val sections = listOf("SEC-IDENTITY", "SEC-CAPABILITY", "SEC-DISCIPLINE", "SEC-BOUNDARY")

    @Test
    fun composeKeepsMemoryAndTelemetryAheadOfTheFourSections() {
        val prompt = AgentSystemPrompt.compose(sections, "MEMORY-BLOCK", "TELEMETRY-BLOCK", "")

        for (section in sections) {
            assertTrue("提示词缺少 $section", prompt.contains(section))
        }
        assertTrue(prompt.contains("MEMORY-BLOCK"))
        assertTrue(prompt.contains("TELEMETRY-BLOCK"))
        assertTrue("长记忆必须排在能力段之前", prompt.indexOf("MEMORY-BLOCK") < prompt.indexOf("SEC-IDENTITY"))
        assertTrue("遥测必须排在能力段之前", prompt.indexOf("TELEMETRY-BLOCK") < prompt.indexOf("SEC-IDENTITY"))
        assertTrue(
            "四段顺序固定",
            prompt.indexOf("SEC-IDENTITY") < prompt.indexOf("SEC-CAPABILITY") &&
                prompt.indexOf("SEC-CAPABILITY") < prompt.indexOf("SEC-DISCIPLINE") &&
                prompt.indexOf("SEC-DISCIPLINE") < prompt.indexOf("SEC-BOUNDARY")
        )
    }

    @Test
    fun customPromptOverridesSectionsButKeepsMemoryAndTelemetry() {
        val prompt = AgentSystemPrompt.compose(sections, "MEMORY-BLOCK", "TELEMETRY-BLOCK", "CUSTOM-PROMPT")

        assertTrue(prompt.contains("CUSTOM-PROMPT"))
        assertTrue(prompt.contains("MEMORY-BLOCK"))
        assertTrue(prompt.contains("TELEMETRY-BLOCK"))
        for (section in sections) {
            assertFalse("自定义提示词应整体替换资源段: $section", prompt.contains(section))
        }
    }

    @Test
    fun blankSectionsAndBlankHeadersDoNotLeaveHoles() {
        val prompt = AgentSystemPrompt.compose(listOf("SEC-A", "", "   ", "SEC-B"), "", "", "")

        assertTrue(prompt.contains("SEC-A"))
        assertTrue(prompt.contains("SEC-B"))
        assertFalse("不得出现连续空行", prompt.contains("\n\n\n"))
        assertFalse("不得以空行开头", prompt.startsWith("\n"))
        assertTrue("整体不留首尾空白", prompt == prompt.trim())
    }

    @Test
    fun shippedChinesePromptCarriesAllFourConstraints() {
        val identity = promptSection("values-zh", "agent_prompt_identity")
        val capabilities = promptSection("values-zh", "agent_prompt_capabilities")
        val discipline = promptSection("values-zh", "agent_prompt_discipline")
        val boundaries = promptSection("values-zh", "agent_prompt_boundaries")

        assertTrue("身份段必须点明是执行型 agent", identity.contains("执行任务的 agent"))
        assertTrue("身份段必须否定聊天机器人", identity.contains("不是聊天"))
        assertTrue("能力段必须写明 run_shell", capabilities.contains("run_shell"))
        assertTrue("能力段必须写明 define_tool", capabilities.contains("define_tool"))
        assertTrue("能力段必须写明读写设备状态", capabilities.contains("读写设备状态"))
        assertTrue("纪律段必须要求先规划再执行", discipline.contains("先规划再执行"))
        assertTrue("纪律段必须禁止寒暄", discipline.contains("不寒暄"))
        assertTrue("纪律段必须要求失败说失败", discipline.contains("失败要说失败"))
        assertTrue("纪律段必须要求说明后果", discipline.contains("后果"))
        assertTrue("边界段必须要求审批", boundaries.contains("审批"))
        assertTrue("边界段必须点名不可逆操作", boundaries.contains("不可逆"))
    }

    @Test
    fun shippedEnglishPromptCarriesAllFourConstraints() {
        val identity = promptSection("values", "agent_prompt_identity")
        val capabilities = promptSection("values", "agent_prompt_capabilities")
        val discipline = promptSection("values", "agent_prompt_discipline")
        val boundaries = promptSection("values", "agent_prompt_boundaries")

        assertTrue(identity.contains("agent"))
        assertTrue(identity.contains("executes tasks"))
        assertTrue("身份段必须否定聊天助手", identity.contains("not a chat"))
        assertTrue(capabilities.contains("run_shell"))
        assertTrue(capabilities.contains("define_tool"))
        assertTrue(capabilities.contains("device state"))
        assertTrue(discipline.contains("Plan first, then act"))
        assertTrue(discipline.contains("No greetings"))
        assertTrue(discipline.contains("say it failed"))
        assertTrue(boundaries.contains("irreversible"))
        assertTrue(boundaries.contains("approval"))
    }

    /** 单测的 CWD 是模块目录（wear/），源集资源在 src/main/res 下按相对路径直读。 */
    private fun promptSection(localeDir: String, name: String): String {
        val text = readResource("src/main/res/" + localeDir + "/strings_agent_prompt.xml")
        val pattern = Regex("""<string name="$name">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
        return pattern.find(text)?.groupValues?.get(1)
            ?: error("资源 " + localeDir + "/strings_agent_prompt.xml 缺少 " + name)
    }

    private fun readResource(relative: String): String {
        val candidate = File(relative)
        val moduleScoped = File("wear", relative)
        val file = listOf(candidate, moduleScoped).firstOrNull { it.exists() }
            ?: error("找不到资源文件 " + relative + "（cwd=" + File(".").absolutePath + "）")
        return file.readText()
    }
}
