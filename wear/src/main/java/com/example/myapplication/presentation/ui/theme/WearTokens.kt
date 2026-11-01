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
        val Border = Color(0xFF232A35)
        val TextPrimary = Color(0xFFF5F7FA)
        val TextSecondary = Color(0xFF8B95A5)
        val TextMuted = Color(0xFF5F6B7A)
        val Accent = Color(0xFF4FC3F7)
        val AccentDim = Color(0xFF0E3A4E)
        /** 中度风险黄：审批页中风险等级的标题、危险条与滑轨强调色。 */
        val Warning = Color(0xFFF5B942)
        /** 重度风险黄红：审批页高风险等级的强调色，红之前先亮黄。 */
        val Critical = Color(0xFFFB6B4B)
        val Danger = Color(0xFFFF6B6B)
        val DangerSurface = Color(0xFF2A1215)
        val Success = Color(0xFF4ADE80)

        /** 主键冷银白：IDLE 单体键与审批胶囊底条共用，让审批态是同一颗键的延续，而不是另起一条深色胶囊。 */
        val Orb = Color(0xFFE8EDF5)
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
        val StatusBlock = 8.dp
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
