package com.example.myapplication.agent.contract

import android.content.Context
import android.content.SharedPreferences

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
