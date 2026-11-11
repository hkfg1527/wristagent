package com.example.myapplication.presentation.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Shapes
import androidx.wear.compose.material3.Typography
import androidx.compose.ui.unit.sp

/**
 * Wear Material3 配色：每个槽位都映射自 [Wear.Palette] 令牌。
 *
 * 组件显式传色时以传参为准；未传的槽位由此处兜底，保证官方 M3 组件不会漏出库自带色板，
 * 也就不会出现"令牌之外的颜色"。
 */
val WearMaterialColorScheme: ColorScheme = ColorScheme().copy(
    primary = Wear.Palette.Accent,
    primaryDim = Wear.Palette.AccentDim,
    primaryContainer = Wear.Palette.AccentDim,
    onPrimary = Wear.Palette.Bg,
    onPrimaryContainer = Wear.Palette.Accent,
    secondary = Wear.Palette.TextSecondary,
    secondaryDim = Wear.Palette.SurfaceAlt,
    secondaryContainer = Wear.Palette.SurfaceAlt,
    onSecondary = Wear.Palette.Bg,
    onSecondaryContainer = Wear.Palette.TextPrimary,
    tertiary = Wear.Palette.TextMuted,
    tertiaryDim = Wear.Palette.SurfaceAlt,
    tertiaryContainer = Wear.Palette.SurfaceAlt,
    onTertiary = Wear.Palette.Bg,
    onTertiaryContainer = Wear.Palette.TextPrimary,
    surfaceContainerLow = Wear.Palette.Surface,
    surfaceContainer = Wear.Palette.Surface,
    surfaceContainerHigh = Wear.Palette.SurfaceAlt,
    onSurface = Wear.Palette.TextPrimary,
    onSurfaceVariant = Wear.Palette.TextSecondary,
    outline = Wear.Palette.Border,
    outlineVariant = Wear.Palette.Border,
    background = Wear.Palette.Bg,
    onBackground = Wear.Palette.TextPrimary,
    error = Wear.Palette.Danger,
    errorDim = Wear.Palette.DangerSurface,
    errorContainer = Wear.Palette.DangerSurface,
    onError = Wear.Palette.Bg,
    onErrorContainer = Wear.Palette.Danger,
)

/**
 * 字号的唯一权威：在官方 M3E Wear 阶梯上显式声明四档。
 *
 * 官方默认阶梯 12 / 14 / 16 / 18 / 30 的相邻差只有 16.7% / 14.3% / 12.5%，
 * 低于"相邻层级字号差 >= 20%"的排版判据，读起来是糊在一起的一片。
 * 这里把页面实际用到的四档拉成 12 / 15 / 18 / 24（差 25% / 20% / 33%），
 * 第五档 displayMedium 取官方 30（与 24 差 25%）。
 * 行高按 1.25~1.33 显式给出，避免只放大字号后中文被行高裁掉。
 *
 * [Wear.TextDisplay] 等令牌只是对这套阶梯的映射，页面不得再自带 sp。
 */
val WearTypography: Typography = Typography().let { official ->
    official.copy(
        bodySmall = official.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
        bodyMedium = official.bodyMedium.copy(fontSize = 15.sp, lineHeight = 20.sp),
        bodyLarge = official.bodyLarge.copy(fontSize = 18.sp, lineHeight = 24.sp),
        titleMedium = official.titleMedium.copy(fontSize = 18.sp, lineHeight = 24.sp),
        titleLarge = official.titleLarge.copy(fontSize = 24.sp, lineHeight = 30.sp)
    )
}

/**
 * 形状的唯一权威：官方五档全部收敛到两档圆角。
 *
 * extraSmall / small / medium → 控件圆角（[Wear.Radius.Control]）
 * large / extraLarge → 卡片圆角（[Wear.Radius.Card]）
 */
val WearShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(Wear.Radius.Control),
    small = RoundedCornerShape(Wear.Radius.Control),
    medium = RoundedCornerShape(Wear.Radius.Control),
    large = RoundedCornerShape(Wear.Radius.Card),
    extraLarge = RoundedCornerShape(Wear.Radius.Card)
)

/**
 * 把运行时强调色换上主色槽位，其余槽位仍是令牌映射。
 *
 * 官方 M3 组件（滑轨、开关、按钮、分段开关）拿到的 primary 因此跟随用户选择的强调色；
 * 外观设置只能换"一个"颜色，不能长出第二套配色体系。
 *
 * primaryDim / primaryContainer 也必须跟着重算：它们是强调色的**配套面**，用页面同一份投影
 * （[accentSurfaceOf] 的系数），否则用这两个槽位的官方组件会停在默认档的 AccentDim 上不跟随。
 */
fun ColorScheme.withAccent(accent: Color): ColorScheme {
    val surface = accentSurfaceOf(accent)
    return if (primary == accent && primaryDim == surface && primaryContainer == surface) {
        this
    } else {
        copy(
            primary = accent,
            onPrimaryContainer = accent,
            primaryDim = surface,
            primaryContainer = surface
        )
    }
}

/**
 * 设置页的 M3 主题壳：把令牌配色、官方字号阶梯与两档圆角一并灌进 [MaterialTheme]，
 * 并把运行时强调色 / 字号档作为可覆盖点下发（[LocalAccentColor] / [LocalFontScale]）。
 *
 * 这两个 local 是"主题内的覆盖点"：没有主题包裹的地方（主界面、引导页）读到的仍是
 * [WearAppearance] 的运行时值，所以外观设置在整台表上都生效，而不是只在设置页里生效。
 */
@Composable
fun WearMaterialTheme(content: @Composable () -> Unit) {
    val accent = resolveAccent()
    val fontScale = resolveFontScale()
    CompositionLocalProvider(
        LocalAccentColor provides accent,
        LocalFontScale provides fontScale
    ) {
        MaterialTheme(
            colorScheme = remember(accent) { WearMaterialColorScheme.withAccent(accent) },
            typography = WearTypography,
            shapes = WearShapes,
            content = content
        )
    }
}
