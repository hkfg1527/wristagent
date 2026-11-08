package com.example.myapplication.presentation.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme

/**
 * Wear 设计令牌：颜色、圆角、间距、尺寸的唯一来源。
 *
 * 排版不在这里定义 —— 字号阶梯的唯一权威是 [MaterialTheme.typography]（由 [WearMaterialTheme]
 * 注入官方 M3E [androidx.wear.compose.material3.Typography]）。下面的 Text* 只是"官方槽位 + 令牌色"
 * 的映射，不再持有任何 sp 数值。
 */
object Wear {
    /** 颜色令牌。 */
    object Palette {
        val Bg = Color(0xFF000000)
        val Surface = Color(0xFF10141A)
        val SurfaceAlt = Color(0xFF171C24)

        /** 发丝线：黑底 1.73:1，OLED 上"时有时无"的下限，再暗就等于没画。 */
        val Border = Color(0xFF2E3644)
        val TextPrimary = Color(0xFFF5F7FA)

        /** 行取值：黑底 6.94:1，12sp 小字也过 WCAG AA。 */
        val TextSecondary = Color(0xFF8B95A5)

        /** 旧的行取值色：黑底只有 3.87:1，低于 AA，已不许承载任何文字。 */
        val TextMuted = Color(0xFF5F6B7A)

        /** 次要图形（行尾箭头、胶囊秒数）：黑底 5.04:1，比正文暗一档但仍过 AA。 */
        val TextDim = Color(0xFF737D8C)
        val Accent = Color(0xFF4FC3F7)
        val AccentDim = Color(0xFF0E3A4E)

        /** 强调色的"轨道位"：等效主色压到 60% 不透明（黑底 5.29:1），用于开关轨道、状态点等静态指示。 */
        val AccentTrack = Color(0xFF3788AD)
        /** 中度风险黄：审批页中风险等级的标题、危险条与滑轨强调色。 */
        val Warning = Color(0xFFF5B942)
        /** 重度风险黄红：审批页高风险等级的强调色，红之前先亮黄。 */
        val Critical = Color(0xFFFB6B4B)
        val Danger = Color(0xFFFF6B6B)
        val DangerSurface = Color(0xFF2A1215)
        val Success = Color(0xFF4ADE80)

        /** 主键冷银白：IDLE 单体键与审批胶囊底条共用，让审批态是同一颗键的延续，而不是另起一条深色胶囊。 */
        val Orb = Color(0xFFCDD3DC)

        /** 曲面时钟：比行标题暗一档（黑底 9.79:1），时钟不再和主键抢第二个焦点。 */
        val Clock = Color(0xFFA8B2C0)
    }

    /**
     * 圆角令牌：只保留两档。
     * - [Control] 控件（行、按钮、滑轨、开关）
     * - [Card] 卡片（对话框、浮层）
     *
     * 实际下发给组件的是 [WearShapes]，它把官方五档全部收敛到这两档上，
     * 于是任何官方组件拿到的圆角都只会是其中之一。
     */
    object Radius {
        val Control = 12.dp
        val Card = 24.dp

        /**
         * 全圆例外：取值远大于任何胶囊高度，任何尺寸下渲染结果都是"两端半圆"。
         * 尺寸可变的胶囊（思考小胶囊）用它，而不是在页面里各写一份"高度一半"。
         */
        val Pill = 999.dp
    }

    /** 间距令牌：只允许 4 的倍数，取值集合固定为 4 / 8 / 12 / 16 / 24。 */
    object Space {
        val Xs = 4.dp
        val Sm = 8.dp
        val Md = 12.dp
        val Lg = 16.dp
        val Xl = 24.dp
    }

    /** 尺寸令牌。 */
    object Size {
        val Touch = 44.dp
        val ChipHeight = 32.dp
        val BadgeHeight = 20.dp
        val Orb = 52.dp
        val TopContentInset = 34.dp
        /** 底栏保留区：主键 52dp + 底距 12dp + 12dp 余量，尽量把高度让给正文 */
        val BottomBarReserve = 76.dp
        /** 行尾状态色块：只用一块颜色表示状态，不和大段文字抢注意力 */
        val StatusBlock = 6.dp

        /** 行尾箭头图形盒：12dp 在 14sp 正文旁边偏重，收到 10dp。 */
        val ChevronGlyph = 10.dp

        /**
         * 设置页底部可达高度：44dp = 底部 28dp 渐变遮罩 + 16dp 余量。
         *
         * 不能用聊天页的 [BottomBarReserve] 76dp —— 设置页没有主键，76dp 会把最后一行永久挡在半空。
         */
        val SettingsBottomInset = 44.dp
        /**
         * 列表末端可达高度（bottom padding），**恒等于** [BottomBarReserve]。
         *
         * 真表 192dp 的实测版面：时钟带 0~34dp、主键 128~180dp、底距 12dp，可读区只有 34~128dp。
         * bottom padding 取 76dp 时正文底边的锚点 = 192 − 76 = 116dp，正好落在主键上沿上方 12dp；
         * 旧值 88dp 会把底边锚到 104dp，比主键再高 24dp，等于白扔一行高度。
         * 这个预留**固定不变**，不随按钮收起/展开改变 —— 否则正文会被顶动。
         */
        val ListCenterReach = BottomBarReserve
    }

    /**
     * 大标题/数字档：官方 displayMedium。
     *
     * 这里的 TextStyle 只负责"官方字号 + 令牌色"的组合，字号本身来自 [MaterialTheme.typography]。
     */
    val TextDisplay: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.displayMedium.withTokenColor(Palette.TextPrimary)

    /** 页面标题档：官方 titleLarge。 */
    val TextDisplayTitle: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.titleLarge.withTokenColor(Palette.TextPrimary)

    /** 标题档：官方 titleMedium。 */
    val TextTitle: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.titleMedium.withTokenColor(Palette.TextPrimary)

    /** 正文档：官方 bodyMedium。 */
    val TextBody: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.bodyMedium.withTokenColor(Palette.TextPrimary)

    /** 说明档：官方 bodySmall。 */
    val TextCaption: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.bodySmall.withTokenColor(Palette.TextSecondary)

    /** 设置行标题档：上一档正文 + 主色（黑底 19.57:1），层级由字号而非"整体压暗"承担。 */
    val TextRowTitle: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.bodyLarge.withTokenColor(Palette.TextPrimary)

    /** 设置行取值档：正文档 + 次色（黑底 6.94:1），小字也必须过 AA。 */
    val TextRowValue: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.bodyMedium.withTokenColor(Palette.TextSecondary)

    /** 等宽档：官方 bodySmall 换等宽字族，用于端点、密钥掩码与探针输出。 */
    val TextMono: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            color = Palette.TextSecondary
        )
}

/** 把令牌色贴到官方字号上：字号只从 typography 来，颜色只从 Palette 来。 */
private fun TextStyle.withTokenColor(color: Color): TextStyle =
    if (this.color == color) this else copy(color = color)
