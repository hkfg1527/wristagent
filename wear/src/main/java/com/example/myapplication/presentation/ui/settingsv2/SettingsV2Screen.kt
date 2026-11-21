package com.example.myapplication.presentation.ui.settingsv2

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.example.myapplication.presentation.ui.settingsv2.style.ArcSettings
import com.example.myapplication.presentation.ui.settingsv2.style.M3CardSettings
import com.example.myapplication.presentation.ui.settingsv2.style.M3ListSettings
import com.example.myapplication.presentation.ui.settingsv2.style.SquareSettings
import com.example.myapplication.presentation.ui.settingsv2.style.TerminalSettings

/** 除 ARC 贴边外的四套：卡片/行统一宽度（dp）。 */
val SettingsV2StandardWidth = 176.dp

/**
 * 底部给「风格」按钮让出的高度。
 *
 * 48dp 不是拍的：真机实测按钮（可点击节点）bounds 为 `[177,370][289,466]`，高 **96px = 48dp**，
 * 顶边在 y=370。原值 40dp 让列表底落在 y=386，比按钮顶边低 16px，于是列表最后一行从按钮底下穿出来。
 */
val SettingsV2ContentBottomInset = 48.dp

/** 换风格入口的测试标识（四套是胶囊按钮，TERMINAL 是 `[ 风格 ]` 字符命令，同一个 tag）。 */
const val StyleEntryTag = "style-entry"

/**
 * 设置页 V2 的唯一入口。
 *
 * 职责三件：①按风格取 [StyleIdentity] 铺页面底色；②按 `RowWidthPolicy` 决定列宽
 * （Standard = 176dp 居中，EdgeToEdge = 满宽，只给 ARC）；③把内容交给那一套风格的渲染文件。
 *
 * 这里**不允许**出现"如果风格是 X 就改成什么"的行内分支 —— 那是旧实现变成 2000 行函数的原因。
 *
 * 手表上换风格的唯一入口也在本文件：底部官方 [EdgeButton]（文案「风格」）打开
 * [SettingsV2StylePicker] 覆盖层，选中后通过 [onStyleSelected] 回传给宿主落库。
 */
@Composable
fun SettingsV2Screen(
    style: SettingsV2Style,
    sections: List<SettingsV2Section> = SettingsV2SampleData.root(),
    modifier: Modifier = Modifier,
    onStyleSelected: ((SettingsV2Style) -> Unit)? = null,
) {
    val identity = style.identity
    var pickerOpen by remember { mutableStateOf(false) }
    // 选择器开着时，返回键只关选择器 —— 否则一次返回会把整个设置页关掉（真机实测：按返回后直接回到聊天页）
    BackHandler(enabled = pickerOpen) { pickerOpen = false }

    // 宽度策略：Standard（其余四套）统一 176dp 居中；ARC 贴边满宽（它的辨识度就来自贴边）
    val widthModifier = when (identity.width) {
        RowWidthPolicy.Standard -> Modifier.width(SettingsV2StandardWidth)
        RowWidthPolicy.EdgeToEdge -> Modifier.fillMaxWidth()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(identity.pageBackground),
        contentAlignment = Alignment.TopCenter,
    ) {
        // 风格变了必须**重建**整棵子树：五套的列表/卡片结构完全不同，
        // 只靠参数变化让 Compose 复用旧的 item 结构并不可靠（真机上出现过"改了风格画面不动"）。
        key(style) {
            // 底部留 [SettingsV2ContentBottomInset]：给下缘的「风格」按钮让位。
            // **clipToBounds 必须在 padding 之后**：修饰符按链序生效，放在 padding 之前时
            // 裁剪点仍是整屏高度（真机实测 y=378 处照样有墨迹），放到之后才裁在 y=370。
            // 另外 TransformingLazyColumn 会把行画到自己的边界之外，所以必须裁。
            val contentModifier = widthModifier
                .fillMaxSize()
                .padding(bottom = SettingsV2ContentBottomInset)
                .clipToBounds()
            when (style) {
                SettingsV2Style.M3_CARD -> M3CardSettings(sections = sections, modifier = contentModifier)
                SettingsV2Style.M3_LIST -> M3ListSettings(sections = sections, modifier = contentModifier)
                SettingsV2Style.ARC -> ArcSettings(sections = sections, modifier = contentModifier)
                SettingsV2Style.SQUARE -> SquareSettings(sections = sections, modifier = contentModifier)
                // TERMINAL 把"换风格"收进它自己的底栏（`[ 风格 ]` 与状态段同一行），
                // 不再浮在屏幕右下角当独立控件 —— 真终端的底栏是一整行。
                SettingsV2Style.TERMINAL -> TerminalSettings(
                    sections = sections,
                    modifier = contentModifier,
                    onStylePicker = { pickerOpen = true },
                )
            }
        }

        // 手表上换风格的唯一入口（四套用官方小圆按钮；TERMINAL 的入口在它自己的底栏里）。
        // EdgeButton 的尺寸是内部参数（无公开 size 形参），给内容留 48dp 底部间距由内容避让。
        if (style != SettingsV2Style.TERMINAL) {
            CompactButton(
                onClick = { pickerOpen = true },
                modifier = Modifier.align(Alignment.BottomCenter).testTag(StyleEntryTag),
            ) {
                Text(text = "风格", style = MaterialTheme.typography.labelMedium)
            }
        }

        if (pickerOpen) {
            // TERMINAL 用字符菜单，其余四套仍用原来的面板 —— 满屏等宽绿字里弹出 M3 卡片最出戏
            val pick: (SettingsV2Style) -> Unit = { picked ->
                onStyleSelected?.invoke(picked)
                pickerOpen = false
            }
            if (style == SettingsV2Style.TERMINAL) {
                TerminalStylePicker(
                    current = style,
                    onPick = pick,
                    onDismiss = { pickerOpen = false },
                )
            } else {
                SettingsV2StylePicker(
                    current = style,
                    onPick = pick,
                    onDismiss = { pickerOpen = false },
                )
            }
        }
    }
}
