package com.example.myapplication.appearance

import androidx.annotation.StringRes
import com.example.myapplication.R

/**
 * 设置页外观预设：三套造型共用同一份数据、同一套行组件，只换外壳。
 *
 * 放在 appearance 包而不是 ui.settings：偏好仓库（agent 包）、导入导出与控制台字段都要按名读写它，
 * 都不该依赖界面包。默认档固定 [BALLOON]，0.03-beta 的老用户升级后像素不变。
 */
enum class SettingsStyle(
    @StringRes val labelRes: Int,
    @StringRes val summaryRes: Int
) {
    /** 整页一个气球，宽度沿屏幕圆弦收缩。 */
    BALLOON(R.string.settings_style_balloon, R.string.settings_style_balloon_summary),

    /** 文字居中、横线分隔、全部直角，返回键也是方形。 */
    SQUARE(R.string.settings_style_square, R.string.settings_style_square_summary),

    /** 谷歌大卡片：大圆角、每卡独立强调色、右侧圆环指示器。 */
    GOOGLE_CARD(R.string.settings_style_card, R.string.settings_style_card_summary);

    companion object {
        val Default = BALLOON

        /** 存储值 -> 档位；写坏了也不会让设置页变空。 */
        fun fromStorage(raw: String?): SettingsStyle =
            values().firstOrNull { it.name == raw } ?: Default
    }
}
