package com.example.myapplication.presentation.ui.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

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

/** 设置页的 M3 主题壳：只负责把令牌配色灌进 [MaterialTheme]，不改动字号与形状。 */
@Composable
fun WearMaterialTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = WearMaterialColorScheme, content = content)
}
