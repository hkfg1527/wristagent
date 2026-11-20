package com.example.myapplication.presentation.ui.settingsv2

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 设置页 V2 的五套风格（见 `docs/SETTINGS-REBUILD-V2.md`）。
 *
 * 与旧实现（`ui/settings/SettingsStyle`，10 套）**完全独立**：旧枚举继续服务其它页面，
 * 新设置页只认这一个枚举。这样做的原因是旧实现里十套风格共用一套渲染分支，
 * 一处改动影响十套、并且容纳不下"贴边卡"这类与全局宽度约束冲突的形状。
 *
 * 每套的"身份"集中写在 [StyleIdentity] 里：圆角、宽度策略、配色。渲染文件只读这些参数，
 * 互相之间不 import —— 加一套或删一套不影响其它四套。
 */
enum class SettingsV2Style(val label: String, val summary: String, val identity: StyleIdentity) {

    /** 谷歌健康（Google Health）那种大卡片：一屏 1~2 张，卡内标题 + 副标题 + 控件。 */
    M3_CARD(
        label = "大卡片",
        summary = "官方卡片，一屏一到两张，信息成组",
        identity = StyleIdentity(
            cornerRadius = 26.dp,
            width = RowWidthPolicy.Standard,
            pageBackground = Color(0xFF000000),
            surface = Color(0xFF1F2429),
            titleColor = Color(0xFFF2F4F7),
            subtitleColor = Color(0xFF9AA4AF),
            rowMinHeight = 140.dp,
            dividers = false,
            centeredText = false
        )
    ),

    /** 官方 M3 列表：无卡片面，左标题右值，行间发丝线。 */
    M3_LIST(
        label = "官方列表",
        summary = "官方行样式，密度最高",
        identity = StyleIdentity(
            cornerRadius = 0.dp,
            width = RowWidthPolicy.Standard,
            pageBackground = Color(0xFF000000),
            surface = Color(0x00000000),
            titleColor = Color(0xFFF2F4F7),
            subtitleColor = Color(0xFF9AA4AF),
            rowMinHeight = 48.dp,
            dividers = true,
            centeredText = false
        )

    ),

    /** 华为手表式贴边卡：外侧贴屏（0 圆角），内侧留圆角。 */
    ARC(
        label = "贴边卡",
        summary = "卡片外侧贴屏，内侧圆角",
        identity = StyleIdentity(
            cornerRadius = 28.dp,
            width = RowWidthPolicy.EdgeToEdge,
            pageBackground = Color(0xFF000000),
            surface = Color(0xFF2A313C),
            titleColor = Color(0xFFF2F4F7),
            subtitleColor = Color(0xFF9AA4AF),
            rowMinHeight = 56.dp,
            dividers = false,
            centeredText = false
        )

    ),

    /** 方角 + 发丝线：最克制的一套。 */
    SQUARE(
        label = "方角",
        summary = "零圆角，一行一根发丝线",
        identity = StyleIdentity(
            cornerRadius = 0.dp,
            width = RowWidthPolicy.Standard,
            pageBackground = Color(0xFF000000),
            surface = Color(0x00000000),
            titleColor = Color(0xFFF2F4F7),
            subtitleColor = Color(0xFF9AA4AF),
            rowMinHeight = 48.dp,
            dividers = true,
            centeredText = true
        )

    ),

    /** 终端：等宽绿字 + `> ` 前缀（这个 App 是 agent，这套最贴主题）。 */
    TERMINAL(
        label = "终端",
        summary = "近黑底、等宽绿字、行首 >",
        identity = StyleIdentity(
            cornerRadius = 0.dp,
            width = RowWidthPolicy.Standard,
            pageBackground = Color(0xFF0B0F0C),
            surface = Color(0x00000000),
            titleColor = Color(0xFF3BFF9A),
            subtitleColor = Color(0xFF1E7A4E),
            rowMinHeight = 40.dp,
            dividers = false,
            centeredText = false
        )
    ),
    ;

    companion object {
        val Default: SettingsV2Style = M3_CARD
    }
}

/**
 * 行容器的宽度策略。
 *
 * 旧实现把 `width(176.dp)` 硬编码在页面层，结果"贴边卡"永远做不出来
 * （行内 `fillMaxWidth()` 只能填满 176dp）。新体系里宽度由风格决定。
 */
enum class RowWidthPolicy {
    /** 定宽 176dp：圆屏两侧留白，九成风格用这个。 */
    Standard,

    /** 贴边：占满可用宽度（圆屏会自己裁切外侧），只有 ARC 用。 */
    EdgeToEdge,
}

/** 一套风格的全部"身份"参数；渲染文件只读它，不再各自判断风格。 */
data class StyleIdentity(
    val cornerRadius: Dp,
    val width: RowWidthPolicy,
    val pageBackground: Color,
    val surface: Color,
    val titleColor: Color,
    val subtitleColor: Color,
    /** 行/卡的最小高度：一屏能放几条，由它决定。 */
    val rowMinHeight: Dp,
    /** 行间是否画 1px 分隔线（有卡面的风格不画）。 */
    val dividers: Boolean,
    /** 行内文字对齐：圆屏官方建议标题居中时用 CenterHorizontally。 */
    val centeredText: Boolean,
)
