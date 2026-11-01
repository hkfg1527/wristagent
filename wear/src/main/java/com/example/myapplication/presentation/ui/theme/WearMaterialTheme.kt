package com.example.myapplication.presentation.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Shapes
import androidx.wear.compose.material3.Typography

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
 * 字号的唯一权威：官方 M3E Wear 阶梯原样下发，不覆盖任何一档。
 *
 * 之前主题只注入 colorScheme，官方组件吃库默认字号、页面又用自造的 Wear.Text*，
 * 于是同一屏上并存两套排版权威 —— 这正是"排版散装"的来源。这里把官方阶梯定为唯一来源，
 * [Wear.TextDisplay] 等令牌只是对它的映射。
 */
val WearTypography: Typography = Typography()

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

/** 设置页的 M3 主题壳：把令牌配色、官方字号阶梯与两档圆角一并灌进 [MaterialTheme]。 */
@Composable
fun WearMaterialTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WearMaterialColorScheme,
        typography = WearTypography,
        shapes = WearShapes,
        content = content
    )
}
