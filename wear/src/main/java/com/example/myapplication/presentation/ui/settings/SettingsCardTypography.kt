package com.example.myapplication.presentation.ui.settings

import androidx.compose.ui.unit.dp
import com.example.myapplication.presentation.ui.theme.Wear

/**
 * 大卡片内部的横向账：卡片宽度 → 留给文字的那一列多宽。
 *
 * 卡片是固定内容轴（[SettingsContentWidth]，176dp），右侧 50dp 圆环与两侧内边距吃掉之后，
 * 文字列只剩 82dp —— 这个数字决定了主数值能放几个字。
 */
internal object SettingsCardMetrics {

    /** 卡片左右内边距。 */
    val SidePadding = 10.dp

    /** 卡片左右内边距（官方 Card 默认 10dp）。 */
    val CardPadding = 10.dp

    /** 左槽徽标（DS 这类缩写）连同间距占的宽度。 */
    val BadgeBlock = 38.dp

    /** 给定卡片宽度，文字列可用宽度（dp）。 */
    fun textColumnDp(cardWidthDp: Float): Float =
        (cardWidthDp - 2f * SidePadding.value - BadgeBlock.value).coerceAtLeast(0f)

    /** displayMedium 下一个半宽单位约 15dp。 */
    const val DisplayUnitDp = 15f

    /**
     * titleLarge 下一个半宽单位约 9.5dp。
     *
     * 之前按 7.5dp 估，把 82dp 算成能放 10 个单位；实机截图里 deepseek-v4-pro 的第一行只装下
     * "deepse" 就折了，说明真实字宽更接近 9.5dp。宁可估紧一点，也不要让它折成 3 行再被截断。
     */
    const val TitleUnitDp = 9.5f

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

    /**
     * 主数值最多几行。
     *
     * 退档后统一给三行：实机量下来 titleLarge 在 82dp 里一行只装得下 6 个拉丁字符
     * （20sp 的字宽约 13dp，不是估算时假设的 9.5dp），两行会把 deepseek-v4-pro 截成
     * "deepse / ek-v4…"。给三行后常见模型名能完整显示，卡片高度按内容自适应。
     */
    fun maxLinesFor(value: String, cardWidthDp: Float): Int {
        if (widthUnits(value) <= SettingsCardMetrics.displayUnitsFor(cardWidthDp)) return 1
        return 3
    }
}

/**
 * 品牌身份卡的主数值是否要退一档字号。
 *
 * 卡片内宽约 144dp：titleLarge（20sp）下一行只装得下 12 个单位，`deepseek-v4-pro` 有 15 个，
 * 一旦放不下 Compose 会在连字符处折行，读起来像拼错的型号。超过 [BrandTitleCompactUnits] 就用
 * titleMedium 让它在同一行里排完 —— 宁可小一档，也不要在型号中间断行。
 */
internal const val BrandTitleCompactUnits = 12

internal fun brandTitleIsCompact(value: String): Boolean = value.length > BrandTitleCompactUnits
