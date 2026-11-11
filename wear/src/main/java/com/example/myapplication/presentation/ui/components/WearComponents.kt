package com.example.myapplication.presentation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.myapplication.presentation.ui.theme.Wear

/** 语义色调，供 [WearBadge] 与 [WearBanner] 使用。 */
enum class Tone { ACCENT, DANGER, MUTED }

/** 卡片容器：Surface 实底 + 1dp 描边 + 圆角，可选整卡点击。 */
@Composable
fun WearCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(Wear.Radius.Card)
    val clickable = onClick != null
    val background = if (clickable && pressed) Wear.Palette.SurfaceAlt else Wear.Palette.Surface

    Column(
        modifier = modifier
            .then(if (clickable) Modifier.heightIn(min = Wear.Size.Touch) else Modifier)
            .clip(shape)
            .background(background)
            .border(1.dp, Wear.Palette.Border, shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(Wear.Space.Md),
        content = content,
    )
}

/** 分组标题：Caption 级小标题，上下各留 Sm 间距。 */
@Composable
fun WearSectionHeader(text: String, modifier: Modifier = Modifier) {
    BasicText(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Wear.Space.Sm),
        style = Wear.TextCaption.copy(textAlign = TextAlign.Left),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 单行列表项：标题 + 可选右对齐值 + 可选 Canvas 折线箭头，整行可点。 */
@Composable
fun WearRow(
    title: String,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val highlighted = onClick != null && pressed

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Wear.Size.Touch)
            .clip(RoundedCornerShape(Wear.Radius.Control))
            .background(if (highlighted) Wear.Palette.SurfaceAlt else Color.Transparent)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = Wear.Space.Md, vertical = Wear.Space.Sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Wear.Space.Sm),
    ) {
        BasicText(
            text = title,
            modifier = Modifier.weight(1f),
            style = Wear.TextBody,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (value != null) {
            BasicText(
                text = value,
                modifier = Modifier.widthIn(max = 200.dp),
                style = Wear.TextCaption.copy(textAlign = TextAlign.End),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showChevron) {
            WearChevron()
        }
    }
}

/** 右向折线箭头，用于可进入的列表项。 */
@Composable
private fun WearChevron(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(Wear.Space.Md)) {
        val path = Path().apply {
            moveTo(size.width * 0.3f, size.height * 0.2f)
            lineTo(size.width * 0.7f, size.height * 0.5f)
            lineTo(size.width * 0.3f, size.height * 0.8f)
        }
        drawPath(
            path = path,
            color = Wear.Palette.TextMuted,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/** 小徽标：固定高度的小圆角标签，色调由 [Tone] 决定；[horizontalPadding] 供窄徽章收紧内边距。 */
@Composable
fun WearBadge(
    text: String,
    tone: Tone,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = Wear.Space.Sm,
) {
    val shape = RoundedCornerShape(Wear.Space.Xs)
    val container = when (tone) {
        Tone.ACCENT -> Wear.Palette.AccentDim
        Tone.DANGER -> Wear.Palette.DangerSurface
        Tone.MUTED -> Wear.Palette.SurfaceAlt
    }
    val content = when (tone) {
        Tone.ACCENT -> Wear.Accent
        Tone.DANGER -> Wear.Palette.Danger
        Tone.MUTED -> Wear.Palette.TextSecondary
    }

    Box(
        modifier = modifier
            .height(Wear.Size.BadgeHeight)
            .clip(shape)
            .background(container)
            .padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style = Wear.TextCaption.copy(color = content),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 单条提示条：底 + 描边 + 按 [Tone] 上色的说明文字，最多两行。 */
@Composable
fun WearBanner(text: String, tone: Tone, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Wear.Radius.Control)
    val container = if (tone == Tone.DANGER) Wear.Palette.DangerSurface else Wear.Palette.Surface
    val content = when (tone) {
        Tone.ACCENT -> Wear.Accent
        Tone.DANGER -> Wear.Palette.Danger
        Tone.MUTED -> Wear.Palette.TextSecondary
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(container)
            .border(1.dp, Wear.Palette.Border, shape)
            .padding(horizontal = Wear.Space.Md, vertical = Wear.Space.Sm),
    ) {
        BasicText(
            text = text,
            style = Wear.TextCaption.copy(color = content),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 1dp 分隔线，左右内缩 Sm。 */
@Composable
fun WearDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Wear.Space.Sm)
            .height(1.dp)
            .background(Wear.Palette.Border),
    )
}
