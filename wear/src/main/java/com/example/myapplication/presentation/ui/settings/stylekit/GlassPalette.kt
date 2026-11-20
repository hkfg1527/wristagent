package com.example.myapplication.presentation.ui.settings.stylekit

import com.example.myapplication.appearance.SettingsStyle

/**
 * 玻璃档的唯一判定（配色在 `SettingsStyleCards` 的 `GlassCardFill` / `GlassCardBorder`）。
 *
 * 0.0.0.157 体检发现：GLASS 此前只有普通圆角卡，没有任何玻璃特征 —— 这个文件与那两个常量是补上的实现。
 */
object GlassPalette {
    fun isGlass(style: SettingsStyle): Boolean = style == SettingsStyle.GLASS
}