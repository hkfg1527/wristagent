package com.example.myapplication.presentation.ui.settingsv2

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

/** 选择器覆盖层的测试标识。 */
internal const val StylePickerTag = "style-picker"

/** 选择器里每一项的测试标识前缀。 */
internal const val StylePickerItemTagPrefix = "style-picker-"

/**
 * 风格选择器（覆盖层）：手表上换风格的唯一入口。
 *
 * 在这之前，V2 换风格只能靠网页控制台改偏好 —— 屏幕上没有入口，等于"功能存在但用户到不了"。
 * 这里给一个自绘的覆盖层而不是新页面：五套列表结构不同，做成覆盖层可以**不侵入**五套渲染文件。
 */
@Composable
fun SettingsV2StylePicker(
    current: SettingsV2Style,
    onPick: (SettingsV2Style) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            // 必须全不透明：0xF2(95%) 时底层页面的文字会透上来（真机在 TERMINAL 下能看到 ghost 的 > 温度）
            .background(Color(0xFF000000))
            .testTag(StylePickerTag)
            // 点空白处关闭：覆盖层自己是唯一的点击目标，不做拦截的话背景会被下层吃掉
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        // 五套 + 标题在 233dp 高的圆屏上放不下，必须可滚动（否则底部两套点不到）
        Column(
            modifier = Modifier
                // 176dp：与四套 Standard 列宽一致；再宽会在圆屏右侧被裁（实测 200dp 时右侧被切）
                .width(176.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "页面风格",
                color = Color(0xFFF2F4F7),
                style = MaterialTheme.typography.labelMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
            )
            SettingsV2Style.values().forEach { style ->
                val selected = style == current
                // 官方 `Card` 而不是"background + clickable"手搓表面：
                // ①按压反馈、涟漪、语义（读屏念成按钮）都是官方给的；
                // ②目标里写着"禁止手搓仿真件"，这里之前是最后几处之一。
                Card(
                    onClick = { onPick(style) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(StylePickerItemTagPrefix + style.name),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) Color(0xFF2A313C) else Color(0xFF16191E),
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = if (selected) "● " + style.label else style.label,
                        color = if (selected) Color(0xFFFFFFFF) else Color(0xFFE6EAEF),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = style.summary,
                        color = Color(0xFF9AA4AF),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
