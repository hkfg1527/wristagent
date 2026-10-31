package com.example.myapplication.agent

import com.example.myapplication.agent.contract.RiskAssessment
import com.example.myapplication.agent.contract.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AI 生成的三段审批说明解析器单元测试 (TDD 严格红绿循环)
 * 覆盖：标准 JSON、markdown 围栏、前后废话、单引号、中文字引号、缺字段、非法输入、超长截断、提示词与启发式回退
 */
class RiskNarratorTest {

    // ---------- parse：正常路径 ----------

    @Test
    fun parse_standardJson_returnsThreeFields() {
        val raw = """{"what":"读取电量信息","risk":"low","worst":"没有任何副作用"}"""
        val narration = RiskNarrator.parse(raw)
        assertEquals("读取电量信息", narration?.whatItDoes)
        assertEquals("没有任何副作用", narration?.worstCase)
        assertEquals(RiskLevel.LOW, narration?.level)
    }

    @Test
    fun parse_markdownFence_isTolerated() {
        val raw = """
            ```json
            {"what":"清空应用缓存","risk":"medium","worst":"聊天记录被清除"}
            ```
        """.trimIndent()
        val narration = RiskNarrator.parse(raw)
        assertEquals("清空应用缓存", narration?.whatItDoes)
        assertEquals(RiskLevel.MEDIUM, narration?.level)
    }

    @Test
    fun parse_chatterAroundJson_isTolerated() {
        val raw = "好的，以下是结果：{\"what\":\"删除临时文件\",\"risk\":\"high\",\"worst\":\"误删用户资料\"} 希望对你有帮助。"
        val narration = RiskNarrator.parse(raw)
        assertEquals("删除临时文件", narration?.whatItDoes)
        assertEquals("误删用户资料", narration?.worstCase)
        assertEquals(RiskLevel.HIGH, narration?.level)
    }

    @Test
    fun parse_singleQuotes_isTolerated() {
        val raw = "{'what':'修改系统设置','risk':'high','worst':'设置无法还原'}"
        val narration = RiskNarrator.parse(raw)
        assertEquals("修改系统设置", narration?.whatItDoes)
        assertEquals("设置无法还原", narration?.worstCase)
        assertEquals(RiskLevel.HIGH, narration?.level)
    }

    @Test
    fun parse_chineseQuotes_isTolerated() {
        val raw = "{“what”:“发送短信”,“risk”:“critical”,“worst”:“产生真实话费”}"
        val narration = RiskNarrator.parse(raw)
        assertEquals("发送短信", narration?.whatItDoes)
        assertEquals("产生真实话费", narration?.worstCase)
        assertEquals(RiskLevel.CRITICAL, narration?.level)
    }

    // ---------- parse：缺字段与异常值 ----------

    @Test
    fun parse_missingWhat_yieldsEmptyStringButKeepsOthers() {
        val raw = """{"risk":"low","worst":"无影响"}"""
        val narration = RiskNarrator.parse(raw)
        assertEquals("", narration?.whatItDoes)
        assertEquals("无影响", narration?.worstCase)
        assertEquals(RiskLevel.LOW, narration?.level)
    }

    @Test
    fun parse_missingRisk_yieldsMedium() {
        val raw = """{"what":"查询天气","worst":"没有副作用"}"""
        val narration = RiskNarrator.parse(raw)
        assertEquals("查询天气", narration?.whatItDoes)
        assertEquals(RiskLevel.MEDIUM, narration?.level)
    }

    @Test
    fun parse_unknownRiskWord_yieldsMedium() {
        val raw = """{"what":"查询天气","risk":"待定","worst":"没有副作用"}"""
        assertEquals(RiskLevel.MEDIUM, RiskNarrator.parse(raw)?.level)
    }

    @Test
    fun parse_riskCritical_isRecognized() {
        val raw = """{"what":"格式化分区","risk":"critical","worst":"设备彻底无法开机"}"""
        assertEquals(RiskLevel.CRITICAL, RiskNarrator.parse(raw)?.level)
    }

    @Test
    fun parse_illegalText_returnsNull() {
        assertNull(RiskNarrator.parse("抱歉，我无法判断这条命令的风险。"))
    }

    @Test
    fun parse_blankOrBracesOnly_returnsNull() {
        assertNull(RiskNarrator.parse(""))
        assertNull(RiskNarrator.parse("   "))
        assertNull(RiskNarrator.parse("{{{"))
    }

    // ---------- parse：长度约束 ----------

    @Test
    fun parse_overlongText_isTruncatedTo40CharsWithEllipsis() {
        val longWhat = "这是一条非常非常长的说明".repeat(6) // 72 字
        val longWorst = "最坏情况下会彻底损坏系统并且无法恢复".repeat(4)
        val raw = """{"what":"$longWhat","risk":"low","worst":"$longWorst"}"""
        val narration = RiskNarrator.parse(raw)!!
        assertEquals(40, narration.whatItDoes.length)
        assertTrue(narration.whatItDoes.endsWith("…"))
        assertEquals(40, narration.worstCase.length)
        assertTrue(narration.worstCase.endsWith("…"))
    }

    // ---------- buildPrompt ----------

    @Test
    fun buildPrompt_containsToolNameActionAndJsonContract() {
        val prompt = RiskNarrator.buildPrompt("shell_execute", "执行 rm -rf /sdcard/tmp")
        assertTrue(prompt.contains("shell_execute"))
        assertTrue(prompt.contains("rm -rf /sdcard/tmp"))
        assertTrue(prompt.contains("what"))
        assertTrue(prompt.contains("risk"))
        assertTrue(prompt.contains("worst"))
        assertTrue(prompt.contains("json", ignoreCase = true))
    }

    @Test
    fun buildPrompt_carriesWatchBrevityConstraint() {
        val prompt = RiskNarrator.buildPrompt("set_alarm", "设置早上 7 点闹钟")
        assertTrue(prompt.contains("手表"))
        assertTrue(prompt.contains("40"))
    }

    // ---------- fallback：启发式回退 ----------

    @Test
    fun fallback_hardBlocked_mapsToCritical() {
        val risk = RiskAssessment(isHardBlocked = true, isHighRisk = true, riskSummary = "致命指令：破坏系统数据")
        val narration = RiskNarrator.fallback("shell_execute", "执行 rm -rf /", risk)
        assertEquals(RiskLevel.CRITICAL, narration.level)
        assertEquals("执行 rm -rf /", narration.whatItDoes)
        assertEquals("致命指令：破坏系统数据", narration.worstCase)
    }

    @Test
    fun fallback_highRisk_mapsToHigh() {
        val risk = RiskAssessment(isHighRisk = true, riskSummary = "可能影响系统稳定性")
        val narration = RiskNarrator.fallback("shell_execute", "执行 pm uninstall", risk)
        assertEquals(RiskLevel.HIGH, narration.level)
        assertEquals("可能影响系统稳定性", narration.worstCase)
    }

    @Test
    fun fallback_plainRisk_mapsToMediumWithNonEmptyCopy() {
        val risk = RiskAssessment()
        val narration = RiskNarrator.fallback("query_time_weather", "执行 [query_time_weather]", risk)
        assertEquals(RiskLevel.MEDIUM, narration.level)
        assertEquals("执行 [query_time_weather]", narration.whatItDoes)
        assertTrue(narration.worstCase.isNotBlank())
        assertTrue(narration.worstCase.contains("回滚"))
    }

    @Test
    fun fallback_overlongActionText_isTruncated() {
        val longAction = "执行一条特别长的动作说明".repeat(8)
        val narration = RiskNarrator.fallback("shell_execute", longAction, RiskAssessment(isHighRisk = true))
        assertEquals(40, narration.whatItDoes.length)
        assertTrue(narration.whatItDoes.endsWith("…"))
    }
}
