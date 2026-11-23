package com.example.myapplication.presentation.ui.settingsv2

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myapplication.presentation.ui.settingsv2.style.ArcSettings
import com.example.myapplication.presentation.ui.settingsv2.style.M3CardSettings
import com.example.myapplication.presentation.ui.settingsv2.style.M3ListSettings
import com.example.myapplication.presentation.ui.settingsv2.style.SquareSettings
import com.example.myapplication.presentation.ui.settingsv2.style.TerminalSettings

/**
 * 历史基准宽度，仅供宽度类测试/工具参考；**渲染已改为满宽**。
 *
 * 旧实现用它把内容硬裁成 192dp 矩形，等于放弃圆屏横向面积。现在内容满宽，
 * 靠 SurfaceTransformation / edgeAppearance 贴合圆弧；常量留着只为了让既有宽度守卫有个基准值。
 */
val SettingsV2StandardWidth = 192.dp

/**
 * 换风格入口的测试标识（保留给既有测试/工具）。
 * 实际的入口是「页面风格」那一行；TERMINAL 用底栏 `[ 风格 ]` 字符命令。
 */
const val StyleEntryTag = "style-entry"

/**
 * 设置页 V2 的唯一入口。
 *
 * 职责：①取 [StyleIdentity] 铺页面；②把内容交给对应风格的渲染文件；③承载两类覆盖层
 * （风格选择器、选项列表）。
 *
 * 内容**满宽**，边缘项由各风格自己的 `SurfaceTransformation` / `edgeAppearance` 随滚动贴合圆弧；
 * 换风格收进「页面风格」行，不再有独立底部按钮。
 */

/**
 * 底部内边距：去掉「风格」按钮后不再需要 48dp 让位，只留系统手势区。
 * 内容由此多出约 40dp 竖向面积（圆屏上就是多显示一整项）。
 */
val SettingsV2BottomInset = 8.dp

/**
 * 顶部安全区：圆屏第一行必须往里收，否则会被圆弧切掉。
 *
 * 466px 圆屏上，y=26 处的可用宽度只有 2×√(233²−207²) ≈ 214px；y=56 处约 340px。
 * 24dp（48px）把首行推到 y≥56，整行才进得来。
 */
val SettingsV2SafeInsetTop = 24.dp

/** 底部渐隐高度：把"内容被切断"变成"下面还有"。 */
val SettingsV2BottomFade = 28.dp

@Composable
fun SettingsV2Screen(
    style: SettingsV2Style,
    sections: List<SettingsV2Section> = SettingsV2SampleData.root(),
    modifier: Modifier = Modifier,
    onStyleSelected: ((SettingsV2Style) -> Unit)? = null,
    /**
     * 行被点击。**五套风格共用这一个入口**（此前只有 TERMINAL 接通，另外四套点了没反应，
     * M3_CARD 甚至是 `onClick = {}` 的假按钮 —— 用户实际感受就是"设置页全都用不了"）。
     * 传 null 时不加点击（预览/测试用）。
     */
    onRowClick: ((SettingsV2Row) -> Unit)? = null,
    /**
     * 选项行被选中（点开选项选择器后选定一项）。
     * 参数是 `(row.key, 选中项 value)`，由宿主写回偏好。
     */
    onChoiceSelected: ((String, String) -> Unit)? = null,
    /** 滑块连续回调：行 key + 新值。 */
    onSliderChange: ((String, Float) -> Unit)? = null,
) {
    val identity = style.identity
    var pickerOpen by remember { mutableStateOf(false) }
    var activeChoice by remember { mutableStateOf<SettingsV2Row?>(null) }
    // 选择器/选项列表开着时，返回键只关它 —— 否则一次返回会把整个设置页关掉（真机实测：按返回后直接回到聊天页）
    BackHandler(enabled = pickerOpen || activeChoice != null) {
        when {
            pickerOpen -> pickerOpen = false
            else -> activeChoice = null
        }
    }

    // 行点击先在这里分流：页面风格行开风格选择器，选项行开选项列表，其余转发给宿主。
    val handleRowClick: (SettingsV2Row) -> Unit = { row ->
        when {
            row.key == "style" -> pickerOpen = true
            row.choice != null -> activeChoice = row
            else -> onRowClick?.invoke(row)
        }
    }

    // **满宽**：圆屏中间那一屏的可用弦长就是整宽，定宽 176/192dp 等于主动放弃横向面积。
    // 边缘项靠各风格自己的 SurfaceTransformation / edgeAppearance 随滚动缩小贴合圆弧，
    // 而不是用 clipToBounds 把整页硬裁成矩形（那会把边缘随动的意义整个废掉）。
    // ARC 与其余四套在**形状**上区分（外侧直角贴屏 vs 内侧圆角小卡），不再靠宽度区分。
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(identity.pageBackground),
        contentAlignment = Alignment.TopCenter,
    ) {
        // 风格变了必须**重建**整棵子树：五套的列表/卡片结构完全不同，
        // 只靠参数变化让 Compose 复用旧的 item 结构并不可靠（真机上出现过"改了风格画面不动"）。
        key(style) {
            // 只留竖向安全区：顶部避开圆弧、底部避开系统手势区。
            // **不再 clipToBounds**：TransformingLazyColumn 本来就会把行画到边界之外，
            // 交给官方 SurfaceTransformation / edgeAppearance 按距屏幕中心的比例缩小，
            // 才是"边缘随动"应有的行为；硬裁只会把中间项也压成方框。
            val contentModifier = Modifier
                .fillMaxWidth()
                .fillMaxSize()
                .padding(top = SettingsV2SafeInsetTop, bottom = SettingsV2BottomInset)
            when (style) {
                SettingsV2Style.M3_CARD -> M3CardSettings(sections = sections, modifier = contentModifier, onRowClick = handleRowClick, onSlider = onSliderChange)
                SettingsV2Style.M3_LIST -> M3ListSettings(sections = sections, modifier = contentModifier, onRowClick = handleRowClick, onSlider = onSliderChange)
                SettingsV2Style.ARC -> ArcSettings(sections = sections, modifier = contentModifier, onRowClick = handleRowClick, onSlider = onSliderChange)
                SettingsV2Style.SQUARE -> SquareSettings(sections = sections, modifier = contentModifier, onRowClick = handleRowClick, onSlider = onSliderChange)
                // TERMINAL 把"换风格"收进它自己的底栏（`[ 风格 ]` 与状态段同一行），
                // 不再浮在屏幕右下角当独立控件 —— 真终端的底栏是一整行。
                SettingsV2Style.TERMINAL -> TerminalSettings(
                    sections = sections,
                    modifier = contentModifier,
                    onStylePicker = { pickerOpen = true },
                    onChoice = { row -> activeChoice = row },
                    onToggle = { row -> onRowClick?.invoke(row) },
                )
            }
        }

        // 底部渐隐：把内容下边界的硬切变成"下面还有"。不再有「风格」按钮——
        // 它原先独占 48dp 高的底部区，是页面上最重的视觉焦点，却只是个换皮入口。
        // 换风格现在收进「页面风格」那一行（点行开选择器），主视觉还给设置项本身。
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(SettingsV2BottomFade)
                .background(
                    Brush.verticalGradient(
                        listOf(identity.pageBackground.copy(alpha = 0f), identity.pageBackground),
                    ),
                ),
        )

        // 风格选择器：由「页面风格」行 / TERMINAL 底栏 `[ 风格 ]` 打开，不再有独立底部按钮。
        if (pickerOpen) {
            val pick: (SettingsV2Style) -> Unit = { picked ->
                onStyleSelected?.invoke(picked)
                pickerOpen = false
            }
            if (style == SettingsV2Style.TERMINAL) {
                TerminalStylePicker(current = style, onPick = pick, onDismiss = { pickerOpen = false })
            } else {
                SettingsV2StylePicker(current = style, onPick = pick, onDismiss = { pickerOpen = false })
            }
        }

        // 温度 / 回复风格 / 安全策略 / 特权执行 等可调项：点行 → 选项列表 → 写回偏好。
        activeChoice?.let { row ->
            val choice = row.choice ?: return@let
            OptionPicker(
                title = row.title,
                choice = choice,
                colors = identity,
                onPick = { value ->
                    onChoiceSelected?.invoke(row.key, value)
                    activeChoice = null
                },
                onDismiss = { activeChoice = null },
            )
        }
    }
}
