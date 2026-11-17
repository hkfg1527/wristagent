package com.example.myapplication.presentation.ui.settings

import com.example.myapplication.appearance.SettingsStyle

/**
 * 设置页的**风格槽位表**：每套风格用 8 个槽位把自己讲清楚。
 *
 * 为什么要有它：2026-11-18 体检发现 `WearAppearance.style ==` 散在 10 处、`style ==` 共 12 处，
 * 于是同一页里"一会儿 M3E、一会儿基础"——用户原话"大杂烩，愁得要死"。
 * 现在渲染代码**只读这张表**，不再自己写 `if (style == ...)`；
 * 新增一套风格 = 在 [SettingsStylePolicy.specOf] 里补一行，而不是去十个地方加 if。
 *
 * 槽位定义见 `docs/UI-STYLE-PLAN.md` §2。
 */

/** 槽位 1：页面骨架。 */
enum class SettingsPageShell {
    /** 官方 `ScreenScaffold` + 曲面时钟 + 右缘滚动指示器。 */
    SCREEN_SCAFFOLD,

    /** 纯列表（时钟与指示器由页面自己叠层放）。 */
    PLAIN_LIST
}

/** 槽位 2：组标题。 */
enum class SettingsGroupHeader {
    /** 中性 12sp 文字 + 沟里一根强调色短横。 */
    ACCENT_MARK,

    /** 居中、无装饰。 */
    CENTERED,

    /** 等宽 + `> ` 前缀。 */
    CHEVRON_PREFIX
}

/** 槽位 3：行外壳。 */
enum class SettingsRowShell {
    /** 一组行拼一张官方 `Card`。 */
    CARD_GROUP,

    /** 每行之间一根发丝线，无卡面。 */
    HAIRLINE_FLAT,

    /** 只有字，什么都没有。 */
    PLAIN
}

/** 槽位 4：开关控件。 */
enum class SettingsToggleControl {
    /** 手搓 44×26dp 胶囊（可精确控宽，但非官方件）。 */
    COMPACT_SWITCH,

    /** 官方 `SwitchButton`。 */
    OFFICIAL_SWITCH_BUTTON
}

/** 槽位 5：滑杆控件。 */
enum class SettingsSliderControl {
    /** 自绘 6dp 细轨 + 18dp 圆点。 */
    THIN_TRACK,

    /** 官方 `Slider(segmented = true)`。 */
    OFFICIAL_SEGMENTED
}

/** 槽位 6：单选控件。 */
enum class SettingsRadioControl {
    /** 行尾手绘圆点。 */
    DOT,

    /** 官方 `RadioButton`（整行胶囊）。 */
    OFFICIAL_RADIO_BUTTON
}

/** 槽位 7：返回控件。 */
enum class SettingsBackControl {
    /** 官方贴底 `EdgeButton`，固定在屏幕上。 */
    EDGE_BUTTON,

    /** 列表首项，跟着列表滚。 */
    LIST_ROW,

    /** 页面顶部的固定返回行。 */
    TOP_BAR
}

/** 槽位 8：字体族。 */
enum class SettingsTypeface {
    DEFAULT,
    MONOSPACE
}

/** 一套风格的完整声明。 */
data class SettingsStyleSpec(
    val shell: SettingsPageShell,
    val header: SettingsGroupHeader,
    val rowShell: SettingsRowShell,
    val toggle: SettingsToggleControl,
    val slider: SettingsSliderControl,
    val radio: SettingsRadioControl,
    val back: SettingsBackControl,
    val typeface: SettingsTypeface
)

object SettingsStylePolicy {

    /** 每套风格一行，8 个槽位全部给值（缺一个编译期就过不了）。 */
    fun specOf(style: SettingsStyle): SettingsStyleSpec = when (style) {
        // 气泡：整页一块连续曲面，行外壳是"一组一张卡"，其余控件仍是手搓件（待收口）
        SettingsStyle.BALLOON -> SettingsStyleSpec(
            shell = SettingsPageShell.PLAIN_LIST,
            header = SettingsGroupHeader.ACCENT_MARK,
            rowShell = SettingsRowShell.CARD_GROUP,
            toggle = SettingsToggleControl.COMPACT_SWITCH,
            slider = SettingsSliderControl.THIN_TRACK,
            radio = SettingsRadioControl.DOT,
            back = SettingsBackControl.EDGE_BUTTON,
            typeface = SettingsTypeface.DEFAULT
        )
        // 方角：全直角、横线分隔、标题居中
        SettingsStyle.SQUARE -> SettingsStyleSpec(
            shell = SettingsPageShell.PLAIN_LIST,
            header = SettingsGroupHeader.CENTERED,
            rowShell = SettingsRowShell.HAIRLINE_FLAT,
            toggle = SettingsToggleControl.COMPACT_SWITCH,
            slider = SettingsSliderControl.THIN_TRACK,
            radio = SettingsRadioControl.DOT,
            back = SettingsBackControl.EDGE_BUTTON,
            typeface = SettingsTypeface.DEFAULT
        )
        // 大卡片：M3E 基准档 —— 骨架走官方 ScreenScaffold，返回进列表首项
        SettingsStyle.GOOGLE_CARD -> SettingsStyleSpec(
            shell = SettingsPageShell.SCREEN_SCAFFOLD,
            header = SettingsGroupHeader.ACCENT_MARK,
            rowShell = SettingsRowShell.CARD_GROUP,
            toggle = SettingsToggleControl.COMPACT_SWITCH,
            slider = SettingsSliderControl.THIN_TRACK,
            radio = SettingsRadioControl.DOT,
            back = SettingsBackControl.LIST_ROW,
            typeface = SettingsTypeface.DEFAULT
        )
        // 终端：无壳、等宽、`> ` 前缀
        SettingsStyle.TERMINAL -> SettingsStyleSpec(
            shell = SettingsPageShell.PLAIN_LIST,
            header = SettingsGroupHeader.CHEVRON_PREFIX,
            rowShell = SettingsRowShell.PLAIN,
            toggle = SettingsToggleControl.COMPACT_SWITCH,
            slider = SettingsSliderControl.THIN_TRACK,
            radio = SettingsRadioControl.DOT,
            back = SettingsBackControl.EDGE_BUTTON,
            typeface = SettingsTypeface.MONOSPACE
        )
    }

    /** 当前生效的风格声明。渲染侧只读它。 */
    fun current(): SettingsStyleSpec = specOf(WearAppearance.style)

    /** 便捷判定：返回控件是否进列表（子页与根页共用一句，不再各写一个 `==`）。 */
    val backInList: Boolean get() = current().back == SettingsBackControl.LIST_ROW

    /** 便捷判定：字体是否等宽。 */
    val usesMonospace: Boolean get() = current().typeface == SettingsTypeface.MONOSPACE
}
