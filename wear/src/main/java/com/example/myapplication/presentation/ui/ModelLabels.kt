package com.example.myapplication.presentation.ui

object ModelLabels {
    /** deepseek-chat -> Chat；openai/gpt-4o -> 4o；acme-2.5-flash -> 2.5 Flash */
    fun label(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return ""
        val leaf = trimmed.substringAfterLast('/').trim()
        if (leaf.isEmpty()) return ""
        val tokens = leaf.split('-').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return ""
        val kept = if (tokens.size > 1 && tokens.first().all { it.isLetter() }) tokens.drop(1) else tokens
        return kept.joinToString(" ") { token -> token.replaceFirstChar { it.uppercaseChar() } }
    }

    fun isEmpty(models: List<String>?): Boolean = models.isNullOrEmpty()

    fun rows(models: List<String>?, perRow: Int = 2): List<List<String>> {
        val items = models.orEmpty()
        if (perRow <= 0 || items.isEmpty()) return emptyList()
        return items.chunked(perRow)
    }
}
