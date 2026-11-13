package com.example.myapplication.presentation.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.example.myapplication.presentation.ui.theme.Wear

/** 大卡片档的间距与圆角：圆角仍取卡片令牌，间距 8dp。 */
internal object SettingsCardStyle {
    /** 卡片圆角，与 Google Health 实测的 24dp 一致。 */
    val Radius = Wear.Radius.Card

    /** 卡间距。 */
    val Gap = Wear.Space.Sm
}

/**
 * 大卡片档的卡片底色：Pixel Watch 2 上 Google Health 步数卡实测值 #2A313C。
 *
 * 刻意不进 Palette：调色板上限 14 个 token，这枚颜色只服务这一套风格。
 */
internal val SettingsCardSurface = Color(0xFF2A313C)

/**
 * 官方 M3E 卡片：可点用 [Card]，纯展示用 [CardDefaults] 配套的形状与配色。
 *
 * 外形、内边距、涟漪、按下态全部交给官方组件 —— 这套风格的目标是"看起来就是谷歌的"，
 * 所以不再自己 clip + background 画一张假卡。
 */
@Composable
internal fun SettingsM3Card(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardModifier = modifier.fillMaxWidth()
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = cardModifier,
            colors = CardDefaults.cardColors(containerColor = SettingsCardSurface),
            content = content
        )
    } else {
        // M3E 1.7 没有公开的 NonClickableCard：用无副作用的 Card 顶替，外形与配色完全一致
        Card(
            onClick = {},
            modifier = cardModifier,
            colors = CardDefaults.cardColors(containerColor = SettingsCardSurface),
            content = content
        )
    }
}

/**
 * 大卡片页首卡：标签 → 主数值 → 说明行，徽标（如 DS）在数值左侧单独成块。
 *
 * 刻意不再画进度环：环压在文字右侧时，长模型名会撞到环上。信息层级改由
 * 「accent 标签 + 大数值 + 次要说明」承担，说明行里放真正有用的东西
 * （上下文轮数、温度、端口、就绪状态）。
 */
@Composable
internal fun SettingsHeroCard(
    label: String,
    value: String,
    accent: Color,
    meta: String? = null,
    badge: String? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    SettingsM3Card(modifier = modifier, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (badge != null) {
                Spacer(Modifier.width(Wear.Space.Xs))
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Wear.Radius.Control))
                        .background(accent.copy(alpha = 0.16f))
                        .padding(horizontal = Wear.Space.Sm, vertical = 2.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (meta != null) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = Wear.Palette.TextMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** 行卡里的普通行：官方 Card，内容由调用方给。 */
internal val SettingsCardRowShape = RoundedCornerShape(Wear.Radius.Card)

/** 徽标尺寸上限：没有位图时用缩写，宽度受控。 */
internal val SettingsBadgeSize = 26.dp
