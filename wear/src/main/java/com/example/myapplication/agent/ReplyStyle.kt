package com.example.myapplication.agent

import android.content.Context
import com.example.myapplication.R

/** 回答风格：只影响系统提示词的表达约束，不改变模型与工具权限。 */
enum class ReplyStyle(val storageKey: String) {
    CONCISE("CONCISE"),
    BALANCED("BALANCED"),
    DETAILED("DETAILED");

    companion object {
        const val DEFAULT_STORAGE = "BALANCED"

        fun fromStored(raw: String?): ReplyStyle =
            values().firstOrNull { it.storageKey == raw } ?: BALANCED
    }
}

object ReplyStylePolicy {
    /** 追加进系统提示词的表达约束；与用户自定义提示词并列，不覆盖它。 */
    fun instruction(context: Context, style: ReplyStyle): String {
        val res = when (style) {
            ReplyStyle.CONCISE -> R.string.agent_reply_style_concise
            ReplyStyle.BALANCED -> R.string.agent_reply_style_balanced
            ReplyStyle.DETAILED -> R.string.agent_reply_style_detailed
        }
        return context.getString(res)
    }
}
