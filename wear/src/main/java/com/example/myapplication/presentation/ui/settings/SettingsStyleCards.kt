package com.example.myapplication.presentation.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.example.myapplication.presentation.ui.brand.BrandCharacter
import com.example.myapplication.presentation.ui.brand.BrandCharacterSize
import com.example.myapplication.presentation.ui.brand.BrandId
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
    // 卡片档的边缘空间缩放：每张卡按自己离屏心的距离等比缩小并淡出（只缩不放）。
    // 挂在这一层是因为英雄卡与行卡都从这里出去 —— 一处接线，两种卡都有。
    val cardModifier = modifier.fillMaxWidth().cardEdgeZoom()
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
    /** 品牌徽标矢量；给了它就代替 [badge] 里那串缩写。 */
    badgeMark: Int? = null,
    /** 右槽环形指示器进度（0..1）；null 也画轨道 —— 每张卡右边都得有东西压住重心。 */
    ring: Float? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    SettingsM3Card(modifier = modifier, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (badgeMark != null) {
                Spacer(Modifier.width(Wear.Space.Xs))
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(Wear.Radius.Control))
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(badgeMark),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else if (badge != null) {
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
                    // 与品牌身份卡同一条尺子：超过 12 个单位就退一档字号，宁可小一点，
                    // 也不要在型号中间按连字符折成两行。
                    style = if (brandTitleIsCompact(value)) {
                        MaterialTheme.typography.titleMedium
                    } else {
                        MaterialTheme.typography.titleLarge
                    },
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
            Spacer(Modifier.width(Wear.Space.Sm))
            SettingsHeroRing(progress = ring, accent = accent)
        }
    }
}

/** 行卡里的普通行：官方 Card，内容由调用方给。 */
internal val SettingsCardRowShape = RoundedCornerShape(Wear.Radius.Card)

/** 徽标尺寸上限：没有位图时用缩写，宽度受控。 */
internal val SettingsBadgeSize = 26.dp

/**
 * 品牌身份卡：拟人角色在上，标签 / 主数值 / 说明行居中排在下面，整卡吃满内容宽度。
 *
 * 刻意把角色放**上方居中**而不是塞进右侧徽标位：卡片只有 176dp 宽，
 * 角色一旦跟正文并排，长模型名会被挤成两行甚至截断（这个坑踩过一次）。
 * 角色在上、文字在下的竖排结构，让两者都拿到完整宽度。
 */
@Composable
internal fun SettingsBrandCard(
    brand: BrandId,
    label: String,
    value: String,
    accent: Color,
    meta: String? = null,
    /** 右上角环形指示器进度（0..1）；null 也画轨道。 */
    ring: Float? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    SettingsM3Card(modifier = modifier, onClick = onClick) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 圆环贴右上角：角色与文字仍居中，环只压住那一角的空白（用户要的重心）
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                SettingsHeroRing(progress = ring, accent = accent)
            }
            BrandCharacter(
                brand = brand,
                eyeColor = SettingsCardSurface,
                modifier = Modifier.size(BrandCharacterSize.Inline)
            )
            Spacer(Modifier.height(Wear.Space.Sm))
            // 品牌标识与角色是两套资产，页面上各就各位：角色在上，标识贴在标签左侧
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(brand.markRes),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(Wear.Space.Xs))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = value,
                style = if (brandTitleIsCompact(value)) {
                    MaterialTheme.typography.titleMedium
                } else {
                    MaterialTheme.typography.titleLarge
                },
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (meta != null) {
                Spacer(Modifier.height(Wear.Space.Xs))
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * 卡片档的返回卡：**返回控件跟着列表滚**（用户 01:15 拍板选 ②）。
 *
 * 以前返回键挂在 `ScreenScaffold` 的固定槽位里，滚动时永远贴在那儿；
 * 现在它是列表的第一个 item，往下滚就跟着滚出去 —— 想返回要么滚回顶部，
 * 要么用常驻的下拉退出手势（那条手势不看滚动位置）。
 *
 * 点它同样走 [SettingsExitController]，所以退场动画与其它风格一致。
 */
@Composable
internal fun SettingsBackCardRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsM3Card(modifier = modifier, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 主题色 getter 是 @Composable，不能进绘制 lambda
            val chevronColor = MaterialTheme.colorScheme.onSurface
            Canvas(modifier = Modifier.size(12.dp)) {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width * 0.72f, size.height * 0.12f)
                    lineTo(size.width * 0.28f, size.height * 0.5f)
                    lineTo(size.width * 0.72f, size.height * 0.88f)
                }
                drawPath(
                    path = path,
                    color = chevronColor,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 2.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round
                    )
                )
            }
            Spacer(Modifier.width(Wear.Space.Sm))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
