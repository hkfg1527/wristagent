package com.example.myapplication.presentation.ui.settings

/**
 * 大卡片主数值的字号决策。
 *
 * 卡片内容轴固定（176dp 减去 50dp 圆环与 12dp 间距后约 110dp）：30sp 的数值塞得下 7 个半宽字符，
 * 再长就只剩 "dee…"。因此短数值顶满 displayMedium，长文字退到 titleLarge 并允许两行 ——
 * 与谷歌一致：数字是主角，模型名这类文本不该被截成三个字母。
 *
 * 宽度按"半宽字符"折算：拉丁字符 1 个单位，CJK 与全角符号 2 个单位 —— 字符个数在中英混排下
 * 完全没有可比性（"密钥未填写" 5 个字符比 "2,278" 宽一倍还多）。
 */
internal object SettingsCardTypography {

    /** 一行放得下 displayMedium 的最大半宽单位数。 */
    const val DisplayMaxUnits = 7

    /** 文本折算成半宽单位：CJK / 全角标点按 2 计。 */
    fun widthUnits(value: String): Int = value.trim().sumOf { ch ->
        if (ch.code >= 0x2E80) 2 else 1
    }

    /** 该用大字号还是退一档。 */
    fun usesDisplayStyle(value: String): Boolean = widthUnits(value) <= DisplayMaxUnits

    /** 主数值最多几行。 */
    fun maxLinesFor(value: String): Int = if (usesDisplayStyle(value)) 1 else 2
}
