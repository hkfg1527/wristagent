package com.example.myapplication.presentation.ui.brand

import androidx.compose.ui.graphics.Color
import com.example.myapplication.R

/**
 * 当前接入的是哪一家模型供应商。
 *
 * 识别只吃两个输入：端点的 host（权威）与模型名（兜底，用户把自建网关挂在 `/v1` 之类路径上时
 * 模型名往往仍带着厂商标）。两者都不中就是 [UNKNOWN] —— 自建/中转端点走这套统一标识，
 * 不再出现"没配图标就一片空白"的占位。
 *
 * [accent] 是这家品牌的识别色，只用于徽标与角色着色；应用自身的调色板（Palette）另有 14 色上限，
 * 品牌色不进去、也不参与主题换肤 —— 它是被"引用"的外部身份，不是我们的设计令牌。
 */
internal enum class BrandId(
    val accent: Color,
    /** 徽标矢量：[brand_*_mark] 一律单色白路径，使用时按 [accent] 上色。 */
    val markRes: Int,
    private val hosts: List<String>,
    private val models: List<String>
) {
    DEEPSEEK(
        accent = Color(0xFF4D6BFE), markRes = R.drawable.brand_deepseek_mark,
        hosts = listOf("deepseek"), models = listOf("deepseek")
    ),
    OPENAI(
        accent = Color(0xFF10A37F), markRes = R.drawable.brand_openai_mark,
        hosts = listOf("openai", "azure.com"), models = listOf("gpt", "o1-", "o3-", "o4-")
    ),
    ANTHROPIC(
        accent = Color(0xFFD97757), markRes = R.drawable.brand_anthropic_mark,
        hosts = listOf("anthropic", "claude"), models = listOf("claude")
    ),
    GEMINI(
        accent = Color(0xFF8AB4F8), markRes = R.drawable.brand_gemini_mark,
        hosts = listOf("generativelanguage", "gemini", "googleapis", "google"), models = listOf("gemini", "gemma")
    ),
    KIMI(
        accent = Color(0xFF8E7CFF), markRes = R.drawable.brand_kimi_mark,
        hosts = listOf("moonshot", "kimi"), models = listOf("moonshot", "kimi")
    ),
    QWEN(
        accent = Color(0xFF33B1FF), markRes = R.drawable.brand_qwen_mark,
        hosts = listOf("dashscope", "aliyuncs", "qwen"), models = listOf("qwen", "qwq")
    ),
    OLLAMA(
        accent = Color(0xFFB9C2CF), markRes = R.drawable.brand_ollama_mark,
        hosts = listOf("ollama", "11434"), models = listOf("ollama")
    ),
    MISTRAL(
        accent = Color(0xFFFF7000), markRes = R.drawable.brand_mistral_mark,
        hosts = listOf("mistral"), models = listOf("mistral", "codestral", "mixtral")
    ),
    GROK(
        accent = Color(0xFFE8ECF2), markRes = R.drawable.brand_grok_mark,
        hosts = listOf("x.ai", "xai"), models = listOf("grok")
    ),
    LLAMA(
        accent = Color(0xFF0866FF), markRes = R.drawable.brand_llama_mark,
        hosts = listOf("llama", "meta.ai"), models = listOf("llama", "codellama")
    ),
    UNKNOWN(
        accent = Color(0xFF7E8899), markRes = R.drawable.brand_unknown_mark,
        hosts = emptyList(), models = emptyList()
    );

    companion object {
        /**
         * 端点 → 品牌。host 优先，模型名兜底；都不中返回 [UNKNOWN]。
         *
         * host 判断用"包含"而不是"后缀相等"：自建网关常把原厂 host 挂在路径里
         * （`https://gw.internal/proxy/api.deepseek.com`），这种也要认出来。
         */
        fun fromEndpoint(baseUrl: String, modelName: String = ""): BrandId {
            val haystack = baseUrl.lowercase()
            if (haystack.isNotBlank()) {
                for (brand in entries) {
                    if (brand.hosts.any { haystack.contains(it) }) return brand
                }
            }
            val model = modelName.lowercase()
            if (model.isNotBlank()) {
                for (brand in entries) {
                    if (brand.models.any { model.contains(it) }) return brand
                }
            }
            return UNKNOWN
        }
    }
}
