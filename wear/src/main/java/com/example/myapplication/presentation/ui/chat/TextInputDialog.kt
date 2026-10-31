package com.example.myapplication.presentation.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.myapplication.R
import com.example.myapplication.presentation.TextInputState
import com.example.myapplication.presentation.ui.theme.Wear

/** 文字输入弹窗的浮起距离：与审批文案同源的入场手感。 */
private val TextDialogFloatDistance = 36.dp

/**
 * 自定义文本输入弹窗 (避让输入法 / 返回键只关弹窗)。
 * 内容只有三样东西：输入框、取消、发送。
 */
@Composable
internal fun TextInputDialog(
    draft: TextInputState,
    onChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dialogFocusRequester = remember { FocusRequester() }

    // 返回键只关闭弹窗，绝不退出 Activity；未发送文本保留在状态机中
    BackHandler { onCancel() }

    // 不再自动聚焦：Wear 的软键盘是全屏 Activity，自动唤起会立刻把卡片和按钮顶掉。
    // 先在手表上把"输入框 + 取消/发送"完整露出来，用户点输入框时再唤起键盘。

    // 与审批页同源的入场：从下方浮起 + 淡入 + 轻微放大（不再直接硬切出现）
    val dialogEnter = remember { Animatable(0f) }
    val dialogFloatPx = with(LocalDensity.current) { TextDialogFloatDistance.toPx() }
    LaunchedEffect(Unit) {
        dialogEnter.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 250f))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // 遮罩随入场一起淡入，观感与审批页一致（半透，能看见背后的点阵）
            .background(Wear.Palette.Bg.copy(alpha = 0.9f * dialogEnter.value))
            // 外层承接口感遮罩，内容整体抬到输入法上沿之上
            .imePadding()
            .padding(
                start = Wear.Space.Edge,
                end = Wear.Space.Edge,
                top = Wear.Space.Sm,
                bottom = Wear.Space.Sm
            ),
        contentAlignment = Alignment.Center
    ) {
        val dialogShape = RoundedCornerShape(Wear.Radius.Card)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = dialogFloatPx * (1f - dialogEnter.value)
                    val enterScale = 0.92f + 0.08f * dialogEnter.value
                    scaleX = enterScale
                    scaleY = enterScale
                    alpha = dialogEnter.value
                }
                .clip(dialogShape)
                .background(Wear.Palette.Surface)
                .border(1.dp, Wear.Palette.Border, dialogShape)
                .padding(Wear.Space.Md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 输入框限制最大高度并内部滚动，长文本不会把按钮挤出圆屏。
            BasicTextField(
                value = draft.text,
                onValueChange = onChange,
                textStyle = Wear.TextBody,
                cursorBrush = SolidColor(Wear.Palette.TextPrimary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSubmit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Wear.Size.Touch, max = 120.dp)
                    .focusRequester(dialogFocusRequester)
                    .clip(RoundedCornerShape(Wear.Radius.Chip))
                    .background(Wear.Palette.SurfaceAlt)
                    .padding(Wear.Space.Md)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DialogActionButton(
                    label = stringResource(R.string.wearchat_dialog_cancel),
                    isPrimary = false,
                    modifier = Modifier.weight(1f),
                    onClick = onCancel
                )
                Spacer(Modifier.width(Wear.Space.Sm))
                DialogActionButton(
                    label = stringResource(R.string.wearchat_dialog_send),
                    isPrimary = true,
                    modifier = Modifier.weight(1f),
                    onClick = onSubmit
                )
            }
        }
    }
}

/** 对话框操作按钮：主操作 Accent 实底，次操作 SurfaceAlt；触达高度不小于 44dp。 */
@Composable
private fun DialogActionButton(
    label: String,
    isPrimary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(Wear.Size.Touch)
            .clip(RoundedCornerShape(Wear.Radius.Chip))
            // 主操作与主键同色（Orb 冷银白 + 深字），次操作留空只用文字，视觉上只留一个实心按钮
            .background(if (isPrimary) Wear.Palette.Orb else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = Wear.Space.Sm),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = label,
            style = Wear.TextTitle.copy(
                color = if (isPrimary) Wear.Palette.Bg else Wear.Palette.TextSecondary
            ),
            maxLines = 1
        )
    }
}
