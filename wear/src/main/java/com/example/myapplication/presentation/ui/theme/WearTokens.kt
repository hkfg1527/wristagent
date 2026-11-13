package com.example.myapplication.presentation.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import com.example.myapplication.presentation.ui.settings.AppearanceBootstrap
import com.example.myapplication.presentation.ui.settings.WearAppearance

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

        /** 默认蓝下的配套面（手挑值）：页面读投影 [Wear.AccentSurface]，这枚只作默认档定义。 */
        val AccentDim = Color(0xFF0E3A4E)

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
         * 组卡圆角：16dp。
         *
         * 与 [Card] 分开是因为它们表达的东西不同：
         * - [Card] 是**独立面板**（对话框、抽屉），四周都是空的，24dp 才站得住；
         * - [Group] 是**成组列表里的一张卡**（多个 42~48dp 的行拼成一张），
         *   四周紧邻其它卡与组标题，24dp 对这么矮的行来说太大，读起来像按钮。
         */
        val Group = 16.dp

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

        /** 微交互：按下反馈、行内淡入淡出、遮罩显隐的快速档。140ms。 */
        fun <T> quick(): TweenSpec<T> = tween(durationMillis = 140, easing = FastOutSlowInEasing)

        /** 过渡档：屏幕切换遮罩、卡片入场。180ms。 */
        fun <T> base(): TweenSpec<T> = tween(durationMillis = 180, easing = FastOutSlowInEasing)

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
        /**
         * 图标按钮与 chip 的触摸基准尺寸：**刻意取 44dp，低于无障碍建议的 48dp，不是漏改**。
         *
         * Android 无障碍指引要求触摸目标 ≥48dp，但在 233dp 圆屏上 44dp 是有账可算的取舍：
         * [com.example.myapplication.presentation.ui.OnboardingGeometry.FooterHeightDp] 与
         * SkipWidthDp 直接由本令牌推导，页脚矩形（136×44dp，上沿 y=48dp）下沿最远角点半径
         * 只有 114.40dp，到 116.5dp 的圆周只剩 **2.10dp** 余量；抬到 48dp 会同时把页脚变成
         * 140×48dp，角点半径 118.81dp > 116.5dp，页脚撑出圆屏、最远那个角被裁掉。
         * 而列表行本来就是 48dp（SettingsSpacingMath.MinRowHeightDp），44dp 只用在图标按钮与
         * chip 这类自带圆形背景、视觉直径小于行高的小目标上。
         *
         * 所以改这个值不是"补上 4dp 的无障碍欠账"，而是重排引导页几何：先改圆屏预算，再改这里。
         */
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
     * 运行时强调色：默认是令牌 [Palette.Accent]，可由主题（[LocalAccentColor]）或外观设置覆盖。
     *
     * 覆盖是"同一个 token 的运行时投影"，不是第 15 个色值：色板仍然只有 [Palette] 里那 14 个，
     * PaletteAuditTest 数的常量一个没多。
     */
    val Accent: Color
        @Composable @ReadOnlyComposable
        get() = resolveAccent()

    /**
     * 强调色的配套面：用户输入气泡底、徽标 ACCENT 底、引导页右翼、主题的 primaryDim / primaryContainer。
     *
     * 这是 [Accent] 这个 token 的**运行时投影**，不是第 15 个色值 —— [Palette] 里仍然恰好 14 个色值，
     * PaletteAuditTest 数的常量一个没多；换色时它跟着 [Accent] 一起变，页面因此不再读
     * [Palette.AccentDim]（那只是默认蓝下一次手挑的结果）。算法与系数见 [accentSurfaceOf]。
     */
    val AccentSurface: Color
        @Composable @ReadOnlyComposable
        get() = accentSurfaceOf(resolveAccent())

    /**
     * 强调色的轨道位：开关选中轨道、主键静息档高光、滑轨未选段。
     *
     * 同样是 [Accent] 的运行时投影（[Palette] 仍是 14 个色值），算法与系数见 [accentRailOf]。
     */
    val AccentRail: Color
        @Composable @ReadOnlyComposable
        get() = accentRailOf(resolveAccent())

    /**
     * 大标题/数字档：官方 displayMedium。
     *
     * 这里的 TextStyle 只负责"官方字号 + 令牌色"的组合，字号本身来自 [MaterialTheme.typography]，
     * 再乘以当前字号档（[LocalFontScale] -> [WearAppearance.fontScale]）。
     */
    val TextDisplay: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.displayMedium.withWearFontScale()
            .withTokenColor(Palette.TextPrimary)

    /** 页面标题档：官方 titleLarge。 */
    val TextDisplayTitle: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.titleLarge.withWearFontScale()
            .withTokenColor(Palette.TextPrimary)

    /** 标题档：官方 titleMedium。 */
    val TextTitle: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.titleMedium.withWearFontScale()
            .withTokenColor(Palette.TextPrimary)

    /** 正文档：官方 bodyMedium。 */
    val TextBody: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.bodyMedium.withWearFontScale()
            .withTokenColor(Palette.TextPrimary)

    /** 说明档：官方 bodySmall。 */
    val TextCaption: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.bodySmall.withWearFontScale()
            .withTokenColor(Palette.TextSecondary)

    /** 设置行标题档：上一档正文 + 主色（黑底 19.57:1），层级由字号而非"整体压暗"承担。 */
    val TextRowTitle: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.bodyLarge.withWearFontScale()
            .withTokenColor(Palette.TextPrimary)

    /** 设置行取值档：正文档 + 次色（黑底 6.94:1），小字也必须过 AA。 */
    val TextRowValue: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.bodyMedium.withWearFontScale()
            .withTokenColor(Palette.TextSecondary)

    /** 等宽档：官方 bodySmall 换等宽字族，用于端点、密钥掩码与探针输出。 */
    val TextMono: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            .withWearFontScale()
            .withTokenColor(Palette.TextSecondary)

    /**
     * 大卡片档每张卡自己的强调色。
     *
     * 取值来自 Pixel Watch 2 上 Google Health 主页的实测像素（见 docs/DESIGN-GOOGLE-HEALTH.md）：
     * 卡片色不是主题强调色派生的，谷歌给每种度量固定一种色相，换主题色不该把步数卡变绿。
     * 调色板上限 14 个 token，这四枚只服务单套风格，因此留在投影里而不进 Palette。
     */
    val CardModel: Color
        @Composable @ReadOnlyComposable
        get() = CardAccentBlue

    val CardTools: Color
        @Composable @ReadOnlyComposable
        get() = CardAccentPeriwinkle

    val CardVoice: Color
        @Composable @ReadOnlyComposable
        get() = CardAccentMint

    val CardSystem: Color
        @Composable @ReadOnlyComposable
        get() = CardAccentSlate
}

/** Google Health 实测强调色：步数卡进度环与 chip。 */
private val CardAccentBlue = Color(0xFF7FCFFF)

/** Google Health 实测强调色：第二档度量（爬楼层数）。 */
private val CardAccentPeriwinkle = Color(0xFFA8C7FA)

/** Google Health 实测强调色：第三档度量（锻炼天数）。 */
private val CardAccentMint = Color(0xFFC4EED0)

/** Google Health 的次级文字色，用作第四张卡（系统）的强调色。 */
private val CardAccentSlate = Color(0xFFBDCBE2)

/**
 * 主题内可覆盖的强调色；null = 没有主题覆盖，读 [WearAppearance] 的运行时值。
 *
 * 用 [compositionLocalOf]（动态）而不是 static：强调色换掉时，读过它的组合必须重跑，
 * 静态 local 不做失效传播，换色会停在旧像素上。
 */
val LocalAccentColor = compositionLocalOf<Color?> { null }

/** 主题内可覆盖的字号缩放；null = 读 [WearAppearance] 的运行时值。 */
val LocalFontScale = compositionLocalOf<Float?> { null }

/**
 * 当前强调色：主题覆盖优先，其次是外观设置的运行时值（默认即令牌 [Wear.Palette.Accent]）。
 *
 * 首次读取时把持久化值灌进内存（进程内只做一次），因此冷启动的聊天页也拿得到上次的选择。
 */
@Composable
@ReadOnlyComposable
internal fun resolveAccent(): Color {
    AppearanceBootstrap.ensure(LocalContext.current)
    return LocalAccentColor.current ?: WearAppearance.accent
}

/**
 * 强调色配套面（气泡 / 徽标 / 右翼 / primaryDim）的唯一算法：Bg 与强调色按 0.425 混合。
 *
 * **系数是按 Oklab 反推的，不是按 sRGB。** Compose 1.10.4 的 `lerp(Color, Color, Float)`
 * 走 Oklab 插值（不是逐通道线性），所以「按 sRGB 算出来该是 0.28」在真机上会落到 #061D27 ——
 * 比旧的 AccentDim #0E3A4E 暗一大截，气泡和纯黑页面的区分度会掉。
 * 0.425 在 Oklab 下落回 #123A4C，与旧值只差几个色阶。
 *
 * 抽成纯函数是为了让没有组合运行时的调用点（主键边缘高光的 toneOf）复用同一份系数，
 * 而不是各自记一个浮点数。
 */
internal fun accentSurfaceOf(accent: Color): Color = lerp(Wear.Palette.Bg, accent, 0.425f)

/**
 * 强调色轨道位的唯一算法：Bg 与强调色按 0.765 混合，理由同 [accentSurfaceOf]。
 *
 * 0.765 在 Oklab 下落回 #3588AD，与旧的 AccentTrack #3788AD 几乎一致。
 */
internal fun accentRailOf(accent: Color): Color = lerp(Wear.Palette.Bg, accent, 0.765f)

/** 当前字号缩放，取法与 [resolveAccent] 一致。 */
@Composable
@ReadOnlyComposable
internal fun resolveFontScale(): Float {
    AppearanceBootstrap.ensure(LocalContext.current)
    return LocalFontScale.current ?: WearAppearance.fontScale
}

/**
 * 按当前字号档整体缩放一档字号：只乘系数，不新增档位常量。
 *
 * 行高同步缩放，否则放大中文正文会被原行高裁掉；两处都只在"数值已定"时才乘
 * （[androidx.compose.ui.unit.TextUnit.Unspecified] 的 value 是 NaN，参与算术会抛异常）。
 */
@Composable
@ReadOnlyComposable
private fun TextStyle.withWearFontScale(): TextStyle {
    val scale = resolveFontScale()
    if (scale == 1f) return this
    return copy(
        fontSize = if (fontSize.value.isFinite()) fontSize * scale else fontSize,
        lineHeight = if (lineHeight.value.isFinite()) lineHeight * scale else lineHeight
    )
}

/** 把令牌色贴到官方字号上：字号只从 typography 来，颜色只从 Palette 来。 */
private fun TextStyle.withTokenColor(color: Color): TextStyle =
    if (this.color == color) this else copy(color = color)
