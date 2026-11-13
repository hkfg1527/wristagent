package com.example.myapplication.presentation.ui.settings

import com.example.myapplication.presentation.ui.theme.Wear

/**
 * 大卡片内部的横向账：卡片宽度 → 留给文字的那一列多宽。
 *
 * 卡片是固定内容轴（[SettingsContentWidth]，176dp），右侧 50dp 圆环与两侧内边距吃掉之后，
 * 文字列只剩 82dp —— 这个数字决定了主数值能放几个字。
 */
internal object SettingsCardMetrics {

    /** 卡片左右内边距（Wear.Space.Lg ×2）。 */
    val SidePadding = Wear.Space.Lg

    /** 文字列与圆环之间的间距。 */
    val RingGap = Wear.Space.Md

    /** 环直径。 */
    val RingSize = SettingsCardStyle.RingSize

    /** 给定卡片宽度，文字列可用宽度（dp）。 */
    fun textColumnDp(cardWidthDp: Float): Float =
        (cardWidthDp - 2f * SidePadding.value - RingSize.value - RingGap.value).coerceAtLeast(0f)

    /** displayMedium 下一个半宽单位约 15dp。 */
    const val DisplayUnitDp = 15f

    /** titleLarge 下一个半宽单位约 7.5dp。 */
    const val TitleUnitDp = 7.5f

    /** 该宽度下一行放得下几个半宽单位（大字档）。 */
    fun displayUnitsFor(cardWidthDp: Float): Int =
        (textColumnDp(cardWidthDp) / DisplayUnitDp).toInt().coerceAtLeast(1)

    /** 该宽度下一行放得下几个半宽单位（退一档）。 */
    fun titleUnitsFor(cardWidthDp: Float): Int =
        (textColumnDp(cardWidthDp) / TitleUnitDp).toInt().coerceAtLeast(1)
}

/**
 * 大卡片主数值的字号决策。
 *
 * 宽度按"半宽单位"折算：拉丁字符 1 个单位，CJK 与全角符号 2 个单位 —— 字符个数在中英混排下
 * 完全没有可比性（"密钥未填写" 5 个字符比 "2,278" 宽一倍还多）。
 *
 * 阈值不写死，由 [SettingsCardMetrics] 从卡片宽度算出来：176dp 卡上文字列只有 82dp，
 * 30sp 的 displayMedium 一行只放得下 5 个单位，"0 / 12" 这种就该退一档。
 */
internal object SettingsCardTypography {

    /** 文本折算成半宽单位：CJK / 全角标点按 2 计。 */
    fun widthUnits(value: String): Int = value.trim().sumOf { ch ->
        if (ch.code >= 0x2E80) 2 else 1
    }

    /** 该值是否用 displayMedium（大字档）。 */
    fun usesDisplayStyle(value: String, cardWidthDp: Float): Boolean =
        widthUnits(value) <= SettingsCardMetrics.displayUnitsFor(cardWidthDp)

    /** 主数值最多几行：退一档后仍放不下才给两行。 */
    fun maxLinesFor(value: String, cardWidthDp: Float): Int {
        val units = widthUnits(value)
        if (units <= SettingsCardMetrics.displayUnitsFor(cardWidthDp)) return 1
        return if (units <= SettingsCardMetrics.titleUnitsFor(cardWidthDp)) 1 else 2
    }
}
