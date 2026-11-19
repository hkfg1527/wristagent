package com.example.myapplication.presentation.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import com.example.myapplication.presentation.ui.theme.Wear

/** 组内行：卡面由外层 [SettingsGroupCard] 统一绘制，本行不再画自己的卡。 */
internal val LocalSettingsGroupChrome = compositionLocalOf { false }

/**
 * 一组设置行 = 一张官方 `Card`。
 *
 * 这里原来是"整页一张手搓闭合超椭圆"（`SettingsBalloonGroup` + `SettingsCurveGeometry`）：
 * 背景路径自己算屏幕圆周的弦长、按高度连续变化，看起来像一块有机的气球。
 * 问题是它**不是任何 Material 组件** —— 圆角半径、描边、色调、涟漪全部是自绘的，
 * 一屏里唯一的曲面又是最大的那块，于是整页读起来"像 M3E 但就是不对"。
 *
 * 现在：外壳交给官方 [Card]（形状 / 颜色 / 抬起态 / 涟漪都是库里的），
 * 分组规则见 [SettingsPageChunking] —— 连续的卡片行拼一张卡，分区标题与卡外动作行各自断开。
 * 这样页面上出现的是"标题 + 卡 + 标题 + 卡"，正是 Wear M3 设置页的骨架。
 */
@Composable
fun SettingsGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 槽位表说 rowShell = PLAIN 的风格（官方列表 / 官方转场）不套卡：
        // 行直接躺在页面上，靠官方控件自己的胶囊轮廓分隔。这一处判断同时服务根页与全部子页。
        if (SettingsStylePolicy.current().rowShell == SettingsRowShell.PLAIN) {
            Column(modifier = Modifier.width(SettingsContentWidth)) {
                CompositionLocalProvider(LocalSettingsGroupChrome provides true) {
                    content()
                }
            }
            return@Column
        }
        // 槽位表说 rowShell 不是"卡"的风格（PLAIN / HAIRLINE_FLAT）就不套 Card：
        // - PLAIN（官方列表/官方转场）：行靠官方控件的胶囊轮廓分隔；
        // - HAIRLINE_FLAT（方角/终端）：行靠行间发丝线分隔，再套卡就是"卡里画线"，两种外壳打架。
        // 全项目只有这一个 SettingsGroupCard 定义（0.0.0.70 用 grep 确认过），根页与子页都走这里。
        val rowShell = SettingsStylePolicy.current().rowShell
        // **只有 CARD_GROUP 才在外层套卡**。CARD_EACH 以前也被套了一层 —— 于是"每行一张卡"
        // 变成了"每行一张卡，装在另一张同圆角的卡里"：两层的圆角与颜色一模一样，肉眼看到的就是
        // 一张大卡里塞着几行（0.0.0.124 的 GOOGLE_CARD 截图就是这样）。
        if (rowShell != SettingsRowShell.CARD_GROUP) {
            Column(modifier = Modifier.width(SettingsContentWidth)) {
                CompositionLocalProvider(LocalSettingsGroupChrome provides true) {
                    content()
                }
            }
            return@Column
        }
        Card(
            onClick = {},
            // 宽度 = 内容基准 176dp = 实测 352px：与卡片档 hero 卡、与"所有卡片同一宽度"的硬约束一致。
            // 行内部的 14dp 槽线因此逐像素对齐，两套风格互切不跳位。
            modifier = Modifier.width(SettingsContentWidth),
            colors = CardDefaults.cardColors(containerColor = SettingsCardSurface),
            // 行的左右内边距由行自己带（[SettingsRowSidePadding]），这里不能再加一层，
            // 否则卡内文字会被推进圆角里（176dp 卡上多 10dp 就是看得见的错位）。
            contentPadding = PaddingValues(
                horizontal = 0.dp,
                // 气泡档自己的节奏：组内上下留 8dp（原来是 4dp，行贴着圆角顶边显得"挤"）。
                // 只有 BALLOON 走 CARD_GROUP，所以这里就是它自己的呼吸，不影响别套风格。
                vertical = if (rowShell == SettingsRowShell.CARD_GROUP) 8.dp else Wear.Space.Xs
            ),
            content = {
                CompositionLocalProvider(LocalSettingsGroupChrome provides true) {
                    content()
                }
            }
        )
    }
}

/** 连续卡片行在列表里的分段：组标题与卡外行把卡片截断。 */
internal data class SettingsPageChunk(
    val indices: List<Int>,
    val grouped: Boolean
)

internal object SettingsPageChunking {
    /**
     * 分段规则：**连续的 [SettingsRowEntryKind.CARD_ROW] 拼成一张卡**，
     * 分区标题（`SECTION`）与卡外动作行（`STANDALONE`）各自占一个不分组的分段。
     *
     * 旧规则是"一页只用一张主气球"，把标题也塞进同一个曲面里 —— 那正是整页一大坨的成因。
     */
    fun chunks(kinds: List<SettingsRowEntryKind>): List<SettingsPageChunk> {
        if (kinds.isEmpty()) return emptyList()
        val chunks = mutableListOf<SettingsPageChunk>()
        var i = 0
        while (i < kinds.size) {
            if (kinds[i] == SettingsRowEntryKind.CARD_ROW) {
                val start = i
                while (i < kinds.size && kinds[i] == SettingsRowEntryKind.CARD_ROW) i++
                chunks += SettingsPageChunk((start until i).toList(), grouped = true)
            } else {
                chunks += SettingsPageChunk(listOf(i), grouped = false)
                i++
            }
        }
        return chunks
    }
}
