package com.example.myapplication.presentation.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Wear 设计令牌：颜色、圆角、间距、尺寸与字号阶梯的唯一来源，UI 层只从这里取值。
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
        val Danger = Color(0xFFFF6B6B)
        val DangerSurface = Color(0xFF2A1215)
        val Success = Color(0xFF4ADE80)
    }

    /** 圆角令牌。 */
    object Radius {
        val Chip = 10.dp
        val Card = 16.dp
        val Sheet = 24.dp
    }

    /** 间距令牌。 */
    object Space {
        val Xs = 4.dp
        val Sm = 8.dp
        val Md = 12.dp
        val Lg = 16.dp
        val Xl = 24.dp
        val Edge = 20.dp
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
    }

    // 字号阶梯：最小 12sp，禁止再出现更小字号
    val TextDisplay = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary)
    val TextTitle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Palette.TextPrimary)
    val TextBody = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        color = Palette.TextPrimary,
        lineHeight = 19.sp,
    )
    val TextCaption = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        color = Palette.TextSecondary,
        lineHeight = 16.sp,
    )
    val TextMono = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = Palette.TextSecondary)
}
