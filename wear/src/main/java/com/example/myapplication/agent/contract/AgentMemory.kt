package com.example.myapplication.agent.contract

import android.content.Context
import android.content.SharedPreferences

/**
 * 长记忆条目的规则（纯 Kotlin，零 Android / 零 org.json 依赖）。
 *
 * 解析、归一化、去重与上限只允许有一份：写入方（remember_fact）、条数统计与系统提示词注入
 * 都走这里，否则「记了 3 条、提示词里只出现 1 条」这类自相矛盾无法复现也无法修。
 */
object MemoryFactBook {

    /** 单条记忆的长度上限：手表上下文极小，一条超长记忆等于把长度预算吃掉大半。 */
    const val MAX_FACT_LENGTH = 120

    enum class Status { ADDED, DUPLICATE, FULL, INVALID }

    /**
     * 一次写入的结局：[status] 决定回执，[notes] 是**应当落盘**的内容（没写进去时等于原内容），
     * [count] 是这次之后的条目数。
     */
    data class WriteResult(val status: Status, val fact: String, val notes: String, val count: Int)

    /**
     * 条目视图：一行一条，空白行丢掉。
     *
     * 只认换行这一个分隔符，是因为 [normalize] 已经把条目内部的空白（含换行）折成空格：
     * 写入侧不可能造出带换行的单条，读取侧按行切就永远切不出半条。
     * 若这里再认分号，一条「先看日志；再看电池」会被写成一条、读成两条，条数与注入内容自相矛盾。
     */
    fun facts(notes: String): List<String> = notes.lines().map { normalize(it) }.filter { it.isNotEmpty() }

    /** 归一化：折叠空白并去掉首尾空格 —— 同一条记忆不该因为空格差异变成两条。 */
    fun normalize(raw: String): String = raw.trim().replace(Regex("\\s+"), " ")

    fun render(facts: List<String>): String = facts.joinToString("\n")

    /**
     * 写入判定：空值 / 超长 → INVALID，命中已有条目 → DUPLICATE，清单已满 → FULL，
     * 其余才真的追加。重复与超限都不改动已有清单。
     */
    fun add(notes: String, raw: String, maxFacts: Int): WriteResult {
        val fact = normalize(raw)
        val current = facts(notes)
        val keep = { status: Status -> WriteResult(status, fact, render(current), current.size) }
        return when {
            fact.isEmpty() || fact.length > MAX_FACT_LENGTH -> keep(Status.INVALID)
            current.any { key(it) == key(fact) } -> keep(Status.DUPLICATE)
            current.size >= maxFacts -> keep(Status.FULL)
            else -> {
                val next = current + fact
                WriteResult(Status.ADDED, fact, render(next), next.size)
            }
        }
    }

    /** 近似重复的判据：忽略大小写、空白与标点，只比字母数字 —— 「喜欢 中文回复。」与「喜欢中文回复」是同一条。 */
    private fun key(text: String): String = normalize(text).lowercase().filter { it.isLetterOrDigit() }
}

/**
 * 手表专属长记忆持久化仓储 (即使多轮上下文被滚动修剪截断，此记忆槽永远锁死在 System 最顶层)
 */
class AgentMemory(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("wear_agent_memory", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_LONG_TERM_NOTES = "long_term_notes"
        private const val KEY_SHORTCUT_INTENTS = "shortcut_intents"
    }

    /** 核心长时记忆：用户偏好、习惯与固定设定 */
    var longTermNotes: String
        get() = prefs.getString(KEY_LONG_TERM_NOTES, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LONG_TERM_NOTES, value).apply()

    /** 手表快捷意图配置 */
    var shortcutIntents: String
        get() = prefs.getString(KEY_SHORTCUT_INTENTS, "总结手表电量状态;搜索今日热点科技资讯;切换省电") ?: ""
        set(value) = prefs.edit().putString(KEY_SHORTCUT_INTENTS, value).apply()

    /** 条目视图：条数统计与写入方读的是同一条解析规则（[MemoryFactBook.facts]）。 */
    fun facts(): List<String> = MemoryFactBook.facts(longTermNotes)

    /**
     * 长期记忆的唯一写入方：归一化 → 去重 → 上限 → 落盘。
     * 只有真正新增时才写盘；重复与超限保持原清单不变。
     */
    fun rememberFact(raw: String, maxFacts: Int): MemoryFactBook.WriteResult {
        val result = MemoryFactBook.add(longTermNotes, raw, maxFacts)
        if (result.status == MemoryFactBook.Status.ADDED) longTermNotes = result.notes
        return result
    }

    /**
     * 生成注入 System 提示词顶部的固定记忆块
     */
    fun buildSystemMemoryHeader(): String {
        val sb = StringBuilder()
        sb.append("【宿主平台与长记忆核心约束】\n")
        sb.append("- 宿主设备: Android Wear OS 智能手表 (480x480 纯圆表盘)，屏幕极小且功耗高度敏感。\n")
        sb.append("- 输出长度预算 (Length Budget): 单次回复正文必须精炼在高信息密度的 150~250 字以内，杜绝客套寒暄，分点最多 3 点。\n")
        if (longTermNotes.isNotBlank()) {
            sb.append("- 持久用户偏好 (长记忆): ").append(longTermNotes).append("\n")
        }
        sb.append("- 手表常驻快捷能力: ").append(shortcutIntents).append("\n")
        return sb.toString()
    }
}
