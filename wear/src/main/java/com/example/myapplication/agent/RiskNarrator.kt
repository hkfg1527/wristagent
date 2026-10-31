package com.example.myapplication.agent

import com.example.myapplication.agent.contract.RiskAssessment
import com.example.myapplication.agent.contract.RiskLevel

/** AI 生成的三段审批说明：做什么 / 最坏情况 / 风险等级。 */
data class RiskNarration(
    val whatItDoes: String,
    val worstCase: String,
    val level: RiskLevel
)

/**
 * 审批文案生成器：提示词构造、模型输出容错解析、失败启发式回退。
 * 纯 Kotlin 实现，零 Android 依赖，可被 JVM 单测直接覆盖。
 */
object RiskNarrator {

    /** 单个字段的汉字上限，超出按字符截断并补省略号。 */
    const val MAX_FIELD_LENGTH = 40

    private const val ELLIPSIS = "…"
    private const val DEFAULT_WORST_CASE = "可能导致系统设置被意外更改，且无法自动回滚"

    private val WHAT_PATTERN = Regex("\"(?:whatItDoes|what)\"\\s*:\\s*\"([^\"]*)\"")
    private val WORST_PATTERN = Regex("\"(?:worstCase|worst)\"\\s*:\\s*\"([^\"]*)\"")
    private val RISK_PATTERN = Regex("\"(?:aiRiskLevel|risk)\"\\s*:\\s*\"?([A-Za-z\u4e00-\u9fa5]+)")

    /**
     * 生成给模型的紧凑提示：只要一行 JSON，不要解释也不要 markdown。
     */
    fun buildPrompt(toolName: String, actionText: String): String = buildString {
        append("你是智能手表上的安全审批助手。工具：")
        append(toolName)
        append("；动作：")
        append(actionText)
        append("。请用中文给出三段说明，每项不超过 $MAX_FIELD_LENGTH 个汉字：")
        append("这条命令会做什么(what)、AI 判定的风险等级(risk)、最坏情况(worst)。")
        append("用户是在手表上审批，文案必须极短，不要标点堆叠。")
        append("只输出一行 JSON，不要解释、不要 markdown、不要代码块：")
        append("{\"what\":\"这条命令会做什么\",\"risk\":\"low|medium|high|critical\",\"worst\":\"最坏情况\"}")
    }

    /**
     * 容错解析模型输出：围栏、前后废话、单引号/中文引号均可；缺字段留空串，risk 不可识别记 MEDIUM，
     * 完全找不到任何字段则返回 null 交给调用方回退。
     */
    fun parse(raw: String): RiskNarration? {
        if (raw.isBlank()) return null
        val text = normalize(raw)
        val what = WHAT_PATTERN.find(text)?.groupValues?.get(1)
        val worst = WORST_PATTERN.find(text)?.groupValues?.get(1)
        val risk = RISK_PATTERN.find(text)?.groupValues?.get(1)
        if (what == null && worst == null && risk == null) return null
        return RiskNarration(
            whatItDoes = shorten(what.orEmpty()),
            worstCase = shorten(worst.orEmpty()),
            level = toRiskLevel(risk)
        )
    }

    /**
     * 网络或解析失败时的启发式兜底，保证审批页永远有文案可展示。
     */
    fun fallback(toolName: String, actionText: String, risk: RiskAssessment): RiskNarration {
        val level = when {
            risk.isHardBlocked -> RiskLevel.CRITICAL
            risk.isHighRisk -> RiskLevel.HIGH
            else -> RiskLevel.MEDIUM
        }
        val what = shorten(actionText.trim().ifBlank { "执行 $toolName" })
        val worst = shorten(risk.riskSummary.trim().ifBlank { DEFAULT_WORST_CASE })
        return RiskNarration(whatItDoes = what, worstCase = worst, level = level)
    }

    /** 需在外部静默失败：这里只做纯文本规整，不抛异常。 */
    private fun normalize(raw: String): String = raw
        .replace("```json", "", ignoreCase = true)
        .replace("```JSON", "")
        .replace("```", "")
        .replace('\u201c', '"')   // “
        .replace('\u201d', '"')   // ”
        .replace('\u2018', '"')   // ‘
        .replace('\u2019', '"')   // ’
        .replace('`', '"')
        .replace('\'', '"')
        .replace('\uff1a', ':')   // ：
        .replace("\\\"", "\"")

    private fun shorten(value: String, limit: Int = MAX_FIELD_LENGTH): String {
        val clean = value.replace('\n', ' ').replace('\r', ' ').trim()
        return if (clean.length <= limit) clean else clean.take(limit - 1) + ELLIPSIS
    }

    private fun toRiskLevel(raw: String?): RiskLevel {
        val value = raw?.trim()?.lowercase() ?: return RiskLevel.MEDIUM
        return when {
            value.contains("critical") || value.contains("致命") || value.contains("极高") -> RiskLevel.CRITICAL
            value.contains("high") || value.contains("高") -> RiskLevel.HIGH
            value.contains("medium") || value.contains("moderate") || value.contains("中") -> RiskLevel.MEDIUM
            value.contains("low") || value.contains("低") -> RiskLevel.LOW
            else -> RiskLevel.MEDIUM
        }
    }
}
