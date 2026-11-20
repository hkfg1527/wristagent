package com.example.myapplication.presentation.ui.settingsv2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.example.myapplication.presentation.ui.settingsv2.style.M3CardSettings

/**
 * 设置页 V2 的唯一入口。
 *
 * 职责只有两件：①按风格取 [StyleIdentity] 铺页面底色；②把内容交给那一套风格的渲染文件。
 * 这里**不允许**出现任何"如果风格是 X 就改成什么"的行内分支 —— 那是旧实现变成 2000 行函数的原因。
 *
 * 五套风格的落地顺序见 `docs/SETTINGS-REBUILD-V2.md`：
 * R1 大卡片（本文件已可用）→ R2 官方列表 → R3 贴边卡 → R4 方角 + 终端 → R5 切换入口、删旧实现。
 */
@Composable
fun SettingsV2Screen(
    style: SettingsV2Style,
    sections: List<SettingsV2Section> = SettingsV2SampleData.root(),
    modifier: Modifier = Modifier,
) {
    val identity = style.identity
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(identity.pageBackground),
        contentAlignment = Alignment.TopCenter,
    ) {
        when (style) {
            SettingsV2Style.M3_CARD -> M3CardSettings(sections = sections)
            // R2~R4 逐个补齐；在此之前明确显示"这套还没做"，而不是假装它是别的风格。
            else -> Text(
                text = style.label + "：渲染待实现（R2~R4）",
                color = Color(0xFF9AA4AF),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
