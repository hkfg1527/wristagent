package com.example.myapplication.presentation

/**
 * 纯逻辑：右副键模型循环（只在 cachedRemoteModels 清单内循环）
 */
object ModelCycle {
    fun parse(cached: String?): List<String> = (cached ?: "")
        .split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

    fun next(current: String?, cached: String?): String? {
        val models = parse(cached)
        if (models.isEmpty()) return null
        val index = models.indexOf(current?.trim())
        return models[(index + 1) % models.size]
    }
}
