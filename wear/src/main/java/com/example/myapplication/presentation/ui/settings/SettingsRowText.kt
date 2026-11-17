package com.example.myapplication.presentation.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.appearance.SettingsStyle
import com.example.myapplication.presentation.ui.components.Tone
import com.example.myapplication.presentation.ui.theme.Wear
import com.example.myapplication.presentation.ui.theme.accentRailOf

/**
 * 行的排版令牌 + 可进入行（导航行）+ 行尾状态色块。
 *
 * 从 `SettingsShared.kt` 拆出来：这几件东西只回答"设置页的文字长什么样"，
 * 与卡片外壳、滑杆、开关的几何都无关。
 */
/**
 * 可进入行：标签左槽 + 取值右槽，没有容器也没有分隔线。
 *
 * 取值只在宽屏显示（见 [settingsRowShowsValue]），窄屏上整行只剩一行标签 ——
 * 根页与子页是同一份实现，左右槽线因此全站一致。
 *
 * 行尾标记二选一：有状态时是一块 [Wear.Size.StatusBlock] 的色块（状态文案转成无障碍描述，
 * 不再和大段文字抢注意力），没有状态时什么都不放。
 */
@Composable
fun SettingsNavRow(
    title: String,
    onClick: () -> Unit,
    value: String? = null,
    badge: Pair<String, Tone>? = null,
    modifier: Modifier = Modifier
) {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    SettingsSlotRow(
        title = title,
        value = if (value != null && settingsRowShowsValue(screenWidthDp)) value else null,
        modifier = modifier,
        onClick = onClick,
        trailing = if (badge == null) {
            null
        } else {
            { SettingsStatusBlock(description = badge.first, tone = badge.second) }
        }
    )
}

/**
 * 全页字号只许三档：组标题 12sp / 行文本 15sp / 唯一焦点 18sp。
 *
 * 层级靠**字重与位置**，不靠继续堆字号：行标签与行取值同为 15sp，
 * 二者的差别是颜色（主色 vs 次色）与槽位（左 vs 右），不是大小。
 */
/** 终端档把三档正文全部换成等宽：这是它唯一的外壳，其余两套靠形状区分。 */
private val terminalMono: FontFamily
    @Composable get() = if (SettingsStylePolicy.usesMonospace) FontFamily.Monospace else FontFamily.Default

/** 组标题档：12sp / TextMuted / Medium / 字距 +0.5sp。 */
internal val SettingsSectionStyle: TextStyle
    @Composable get() = Wear.TextCaption.copy(
        color = Wear.Palette.TextMuted,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp,
        fontFamily = terminalMono
    )

/** 行标签档：15sp / TextPrimary / Normal。 */
internal val SettingsRowLabelStyle: TextStyle
    @Composable get() = Wear.TextBody.copy(fontWeight = FontWeight.Normal, fontFamily = terminalMono)

/** 行取值档：15sp / TextSecondary / Normal，右槽对齐写在样式里（BasicText 没有 textAlign 参数）。 */
internal val SettingsRowValueStyle: TextStyle
    @Composable get() = Wear.TextRowValue.copy(
        fontWeight = FontWeight.Normal,
        textAlign = TextAlign.End,
        fontFamily = terminalMono
    )

/** 焦点档：18sp / TextPrimary / Medium，全页只给当前模型名和子页标题。 */
internal val SettingsFocusStyle: TextStyle
    @Composable get() = Wear.TextRowTitle.copy(fontWeight = FontWeight.Medium)

/** 子项行的左槽线右移量：字号不变，只把标签推后 12dp，值槽线不动。 */
val SettingsSubItemIndent: Dp = SettingsDesignMath.SubItemIndentDp.dp

/** 行尾状态色块：一块纯色就够表达状态，文案走无障碍描述。 */
@Composable
private fun SettingsStatusBlock(description: String, tone: Tone) {
    Box(
        modifier = Modifier
            .size(Wear.Size.StatusBlock)
            .clip(CircleShape)
            .background(tone.statusBlockColor(Wear.Accent))
            .semantics { contentDescription = description }
    )
}

/**
 * [Tone] 到令牌色的唯一映射，避免状态色散落在各处。
 *
 * 强调色那一档读不了投影 —— [Wear.AccentRail] 是 @Composable 的，这里是零 Compose 依赖的纯映射，
 * 所以由调用方在组合作用域把当前强调色传进来，轨道位系数仍只由 [accentRailOf] 定义。
 */
private fun Tone.statusBlockColor(accent: Color): Color = when (this) {
    // 静态属性不该是焦点：主色压到 60% 的轨道位（黑底 5.28:1），不再满饱和抢页面标题
    Tone.ACCENT -> accentRailOf(accent)
    Tone.DANGER -> Wear.Palette.Danger
    Tone.MUTED -> Wear.Palette.TextMuted
}
