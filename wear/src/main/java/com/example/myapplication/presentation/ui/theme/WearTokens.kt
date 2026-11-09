package com.example.myapplication.presentation.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
        val Surface = Color(0xFF0D1014)
        val SurfaceAlt = Color(0xFF1B212A)

        /** 发丝线：黑底 1.73:1，OLED 上"时有时无"的下限，再暗就等于没画。 */
        val Border = Color(0xFF32363E)
        val TextPrimary = Color(0xFFF5F7FA)

        /** 行取值 / 次要图形 / 曲面时钟：黑底 9.28:1，抬腕一瞬间也要读得清。 */
        val TextSecondary = Color(0xFFA3ADBD)

        /** 最暗一档可读文字：黑底 5.76:1，仍过 WCAG AA 4.5:1；再暗就不许承载文字。 */
        val TextMuted = Color(0xFF7C8798)
        val Accent = Color(0xFF4FC3F7)
        val AccentDim = Color(0xFF0E3A4E)

        /** 强调色的轨道位：等效主色压到 60% 不透明（黑底 5.29:1），用于开关轨道、状态点等静态指示。 */
        val AccentTrack = Color(0xFF3788AD)

        /** 中度风险黄：审批页中风险等级的标题、危险条与滑轨强调色。 */
        val Warning = Color(0xFFF5B942)
        val Danger = Color(0xFFFF6B6B)
        val DangerSurface = Color(0xFF2A1215)
        val Success = Color(0xFF4ADE80)

        /**
         * 主键底色：冷银白（黑底 14.6:1）。
         *
         * 曾经压到 #8C96A6 想让正文独占焦点，真机判定"灰得难看" —— 主键是品牌识别物，
         * 它就该是屏上最亮的东西；层级改由「面」和「留白」承担，不再靠压低主键。
         */
        val Orb = Color(0xFFCDD3DC)
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

    /**
     * 动效令牌：全站只允许三档 + 两个特例。
     *
     * 之前 17 组不同弹簧参数散落在 12 个文件、83 处调用（0.55/280、0.65/260、0.7/280、
     * 0.72/280、0.75/350、0.78/250、0.8/320、0.82/260 …），差值肉眼无法分辨，
     * 却让「关动画」「统一手感」都无从下手。
     */
    object Motion {
        /** 小元素：按钮、图标、副键、行内状态切换。约 120ms 落定，带一次轻微过冲。 */
        fun <T> micro(): SpringSpec<T> = spring(dampingRatio = 0.85f, stiffness = 700f)

        /** 整页 / 浮层进出。220ms，减速曲线，不做弹性。 */
        fun <T> page(): TweenSpec<T> = tween(durationMillis = 220, easing = FastOutSlowInEasing)

        /** 列表逐项错峰：第 n 项的启动延迟。 */
        const val Stagger = 40L

        /** 引导页入场专用，保留 1.1s（它是叙事节奏，不是交互反馈）。 */
        fun <T> intro(): TweenSpec<T> = tween(durationMillis = 1100, easing = FastOutSlowInEasing)
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
