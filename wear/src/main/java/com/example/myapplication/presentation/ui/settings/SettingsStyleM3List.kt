package com.example.myapplication.presentation.ui.settings

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SplitRadioButton
import androidx.wear.compose.material3.SplitSwitchButton
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.example.myapplication.R

/**
 * 第五套设置风格：**官方列表档**。不画任何外壳（不 clip、不 background、不自拼滑杆开关），
 * 一行就是一个官方组件，列表与边缘形变全走官方 M3E 实现。
 *
 * ## 核对过的组件（签名原文，不是凭记忆）
 *
 * 依据：`D:\gradle-home\caches\modules-2\files-2.1\androidx.wear.compose\compose-material3\1.5.6\`
 * `…\compose-material3-1.5.6-sources.jar`、`…\compose-foundation\1.5.6\…-sources.jar`、
 * `…\compose-material3-1.5.6-samples-sources.jar`，解包后逐个读 .kt 文件。
 *
 * `ScreenScaffold.kt` —— 列表 + 贴底按钮的重载（`edgeButton` 是第二个位置参数，不可省）：
 * ```
 * public fun ScreenScaffold(
 *     scrollState: TransformingLazyColumnState,
 *     edgeButton: @Composable BoxScope.() -> Unit,
 *     modifier: Modifier = Modifier,
 *     contentPadding: PaddingValues = ScreenScaffoldDefaults.contentPadding,
 *     timeText: (@Composable () -> Unit)? = null,
 *     scrollIndicator: (@Composable BoxScope.() -> Unit)? = { ScrollIndicator(scrollState) },
 *     edgeButtonSpacing: Dp = ScreenScaffoldDefaults.EdgeButtonSpacing,
 *     overscrollEffect: OverscrollEffect? = rememberOverscrollEffect(),
 *     content: @Composable BoxScope.(PaddingValues) -> Unit,
 * )
 * ```
 * `Button.kt` —— 返回行用带 `label` 槽的那个重载（`transformation` 就在这一版里）：
 * ```
 * public fun Button(
 *     onClick: () -> Unit,
 *     modifier: Modifier = Modifier,
 *     onLongClick: (() -> Unit)? = null,
 *     onLongClickLabel: String? = null,
 *     secondaryLabel: (@Composable RowScope.() -> Unit)? = null,
 *     icon: (@Composable BoxScope.() -> Unit)? = null,
 *     enabled: Boolean = true,
 *     shape: Shape = ButtonDefaults.shape,
 *     colors: ButtonColors = ButtonDefaults.buttonColors(),
 *     border: BorderStroke? = null,
 *     contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
 *     interactionSource: MutableInteractionSource? = null,
 *     transformation: SurfaceTransformation? = null,
 *     label: @Composable RowScope.() -> Unit,
 * )
 * ```
 * `ListHeader.kt` —— 页标题：
 * ```
 * public fun ListHeader(
 *     modifier: Modifier = Modifier,
 *     backgroundColor: Color = Color.Transparent,
 *     contentColor: Color = ListHeaderDefaults.contentColor,
 *     contentPadding: PaddingValues = ListHeaderDefaults.ContentPadding,
 *     transformation: SurfaceTransformation? = null,
 *     content: @Composable RowScope.() -> Unit,
 * )
 * ```
 * `EdgeButton.kt` —— 贴底兜底：
 * ```
 * public fun EdgeButton(
 *     onClick: () -> Unit,
 *     modifier: Modifier = Modifier,
 *     buttonSize: EdgeButtonSize = EdgeButtonSize.Small,
 *     enabled: Boolean = true,
 *     colors: ButtonColors = ButtonDefaults.buttonColors(),
 *     border: BorderStroke? = null,
 *     interactionSource: MutableInteractionSource? = null,
 *     content: @Composable RowScope.() -> Unit,
 * )
 * ```
 * `SwitchButton.kt` —— 开关行（前两个参数是**开关那半边**的，行体点击是 `onContainerClick`）：
 * ```
 * public fun SplitSwitchButton(
 *     checked: Boolean,
 *     onCheckedChange: (Boolean) -> Unit,
 *     toggleContentDescription: String?,
 *     onContainerClick: () -> Unit,
 *     modifier: Modifier = Modifier,
 *     enabled: Boolean = true,
 *     shape: Shape = SwitchButtonDefaults.splitSwitchButtonShape,
 *     colors: SplitSwitchButtonColors = SwitchButtonDefaults.splitSwitchButtonColors(),
 *     toggleInteractionSource: MutableInteractionSource? = null,
 *     containerInteractionSource: MutableInteractionSource? = null,
 *     transformation: SurfaceTransformation? = null,
 *     containerClickLabel: String? = null,
 *     contentPadding: PaddingValues = SwitchButtonDefaults.ContentPadding,
 *     secondaryLabel: @Composable (RowScope.() -> Unit)? = null,
 *     label: @Composable RowScope.() -> Unit,
 * )
 * ```
 * `RadioButton.kt` —— 单选行：
 * ```
 * public fun SplitRadioButton(
 *     selected: Boolean,
 *     onSelectionClick: () -> Unit,
 *     selectionContentDescription: String?,
 *     onContainerClick: () -> Unit,
 *     modifier: Modifier = Modifier,
 *     enabled: Boolean = true,
 *     shape: Shape = RadioButtonDefaults.splitRadioButtonShape,
 *     colors: SplitRadioButtonColors = RadioButtonDefaults.splitRadioButtonColors(),
 *     selectionInteractionSource: MutableInteractionSource? = null,
 *     containerInteractionSource: MutableInteractionSource? = null,
 *     transformation: SurfaceTransformation? = null,
 *     containerClickLabel: String? = null,
 *     contentPadding: PaddingValues = RadioButtonDefaults.ContentPadding,
 *     secondaryLabel: @Composable (RowScope.() -> Unit)? = null,
 *     label: @Composable RowScope.() -> Unit,
 * )
 * ```
 * `lazy/TransformationSpec.kt` + `lazy/ResponsiveTransformationSpec.kt` + `SurfaceTransformation.kt`
 * —— 列表项形变，`SurfaceTransformation` 是**项作用域**的扩展，只能在 `item {}` 里调：
 * ```
 * public fun Modifier.transformedHeight(
 *     scope: TransformingLazyColumnItemScope,
 *     transformationSpec: TransformationSpec,
 * ): Modifier
 *
 * @Composable public fun rememberTransformationSpec(): TransformationSpec
 *
 * public fun TransformingLazyColumnItemScope.SurfaceTransformation(
 *     spec: TransformationSpec
 * ): SurfaceTransformation
 * ```
 * `compose-foundation-1.5.6-sources.jar` 的 `lazy/TransformingLazyColumn.kt` +
 * `lazy/TransformingLazyColumnState.kt`（后者：`class TransformingLazyColumnState : ScrollableState`，
 * 所以 EdgeButton 能直接把它交给 `Modifier.scrollable`）：
 * ```
 * public fun TransformingLazyColumn(
 *     modifier: Modifier = Modifier,
 *     state: TransformingLazyColumnState = rememberTransformingLazyColumnState(),
 *     contentPadding: PaddingValues = PaddingValues(),
 *     verticalArrangement: Arrangement.Vertical =
 *         Arrangement.spacedBy(space = 4.dp, alignment = Alignment.Top),
 *     horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
 *     flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
 *     userScrollEnabled: Boolean = true,
 *     rotaryScrollableBehavior: RotaryScrollableBehavior? = RotaryScrollableDefaults.behavior(state),
 *     overscrollEffect: OverscrollEffect? = rememberOverscrollEffect(),
 *     content: TransformingLazyColumnScope.() -> Unit,
 * )
 * ```
 * 官方样本 `samples/ScaffoldSample.kt`（`ScaffoldWithTLCEdgeButtonSample`）给出了 EdgeButton 的接法，
 * `samples/TransformingLazyColumnSample.kt` 给出了 item 形变的接法：
 * `Modifier.fillMaxWidth().transformedHeight(this, spec)` + `transformation = SurfaceTransformation(spec)`。
 *
 * 本项目实际依赖 1.7.1，缓存里没有 1.7.1 的 sources，于是解开
 * `…\compose-material3\1.7.1\…\compose-material3-1.7.1.aar` 的 `classes.jar` 用 javap 复核：
 * 上述 `SplitSwitchButton` / `SplitRadioButton` / `SurfaceTransformation` / `transformedHeight` /
 * `ScreenScaffold(TransformingLazyColumnState, edgeButton, …)` 与
 * `TransformingLazyColumnState implements ScrollableState` 均与 1.5.6 源码逐参数一致；
 * 这些组件在 1.5.6 源码里都没有 `@ExperimentalWearMaterialApi`，因此不需要 opt-in。
 *
 * ## 官方没有的组件
 *
 * **`SplitToggleButton` 不存在**（1.5.6 源码、1.7.1 字节码一致）：官方拆分布局只有
 * `SplitSwitchButton`（`SwitchButton.kt`）、`SplitCheckboxButton`（`CheckboxButton.kt`）、
 * `SplitRadioButton`（`RadioButton.kt`）。所以开关行用 `SplitSwitchButton`：它本身就是
 * "整行 + 右侧开关"，不必退到 `TextToggleButton`（那是整宽胶囊，会吞掉行尾槽位）。
 */
private val SettingsM3ListItemGap = 4.dp

/**
 * 列表档页面骨架：官方脚手架 + 官方 M3E 列表。
 *
 * 结构（顺序即渲染顺序）：
 * 1. `ScreenScaffold` 负责 ScrollIndicator 的显隐与 EdgeButton 的贴底宿主；
 * 2. `TransformingLazyColumn` 承载列表，项间距吃官方默认值；
 * 3. 列表首项 = 返回行（官方 [Button]），带官方 item 形变；
 * 4. 次项 = 页标题（官方 [ListHeader]）；
 * 5. 其余内容由 [content] 提供，整块作为一项进入列表；
 * 6. 底部 [EdgeButton] 兜底：滚到末端时它长出来，不必再滚回顶部才能返回。
 *
 * `AppScaffold` 说明：官方样本把 `TimeText` 交给每个 app 唯一的 `AppScaffold` 托管，而本工程没有
 * `AppScaffold`（聊天页是自绘壳）；`ScreenScaffold` 收到 `timeText` 只会把它登记进
 * `LocalScaffoldState`，真正绘制的是 `AppScaffold` —— 这一点在 `SettingsShared.kt` 里已实证并留了注释。
 * 所以这里与其余四套风格一致：`timeText` 不传，官方 [TimeText] 自己放进内容层 TopCenter，
 * `contentPadding` 顶部让出同一段钟带（`RootContentTopPadding`），左右用 `settingsCardScreenInset()`。
 */
@Composable
internal fun SettingsM3ListPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val backLabel = stringResource(R.string.settings_back)
    val screenInset = settingsCardScreenInset()

    ScreenScaffold(
        scrollState = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = screenInset,
            end = screenInset,
            top = RootContentTopPadding
        ),
        edgeButton = {
            EdgeButton(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    // 官方样本：贴底按钮必须自己接滚动，否则从按钮上起手滑不动列表
                    .scrollable(
                        state = listState,
                        orientation = Orientation.Vertical,
                        reverseDirection = true,
                        overscrollEffect = rememberOverscrollEffect()
                    )
            ) {
                Text(text = backLabel)
            }
        }
    ) { scaffoldPadding ->
        TransformingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // 必须吃脚手架给的 padding：圆屏的上下被钟带与边缘按钮挤过，自己算会漏
            contentPadding = scaffoldPadding
        ) {
            // 首项 = 返回行：官方 Button + 官方项形变（滚到边缘时缩小、淡出）
            item(key = "settings-m3-list-back") {
                Button(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec)
                ) {
                    Text(text = backLabel)
                }
            }

            // 次项 = 页标题：官方 ListHeader，自带 heading 语义
            item(key = "settings-m3-list-title") {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec)
                ) {
                    Text(text = title)
                }
            }

            // 其余内容：签名是 `@Composable () -> Unit`，拿不到 item 作用域，
            // 所以整块作为一项进入列表，项内按官方项间距（4dp）往下排。
            item(key = "settings-m3-list-content") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(SettingsM3ListItemGap)
                ) {
                    content()
                }
            }
        }

        // 本工程没有 AppScaffold：时间由页面自己画，位置与其余四套风格一致
        TimeText(modifier = Modifier.align(Alignment.TopCenter))
    }
}

/**
 * 开关行：整行就是官方 [SplitSwitchButton]，左标签区、右开关区都可点。
 *
 * [secondary] 进官方副标槽位（官方 `Labels` 把副标画在主标下方；传 null 时那一行不画也不占高度）。
 */
@Composable
internal fun SettingsM3ListSwitchRow(
    label: String,
    secondary: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SplitSwitchButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        // 右侧开关自带 toggleable 语义（读屏播报开/关），不需要额外 contentDescription
        toggleContentDescription = null,
        // 行体点击 = 切换：官方拆分布局里两边改的是同一个值
        onContainerClick = { onCheckedChange(!checked) },
        modifier = Modifier.fillMaxWidth(),
        secondaryLabel = settingsM3ListSecondarySlot(secondary),
        label = { Text(text = label) }
    )
}

/**
 * 单选行：整行就是官方 [SplitRadioButton]，右侧是官方单选控件，选中态由组件自己画。
 *
 * 点行与点控件走同一个 [onSelect]：拆分布局里两者语义一致，调用方只需要一个回调。
 */
@Composable
internal fun SettingsM3ListRadioRow(
    label: String,
    secondary: String?,
    selected: Boolean,
    onSelect: () -> Unit
) {
    SplitRadioButton(
        selected = selected,
        onSelectionClick = onSelect,
        // 名称由行内 label 提供；控件自己已带 Role.RadioButton
        selectionContentDescription = null,
        onContainerClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        secondaryLabel = settingsM3ListSecondarySlot(secondary),
        label = { Text(text = label) }
    )
}

/**
 * 官方副标槽位：`secondaryLabel` 本身就是可空的 composable lambda，null 即"没有这一行"。
 *
 * 单独抽成函数是为了让字面量的期望类型由返回类型钉死 —— 在 `if/else` 里直接写
 * lambda 字面量会和代码块混淆。
 */
private fun settingsM3ListSecondarySlot(
    secondary: String?
): (@Composable RowScope.() -> Unit)? {
    if (secondary == null) return null
    return { Text(text = secondary) }
}
