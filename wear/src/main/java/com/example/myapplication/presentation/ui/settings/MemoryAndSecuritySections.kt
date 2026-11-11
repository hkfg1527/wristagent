package com.example.myapplication.presentation.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.myapplication.R
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.agent.CompressPolicy
import com.example.myapplication.agent.GestureAction
import com.example.myapplication.agent.contract.SecurityMode
import com.example.myapplication.presentation.ui.components.Tone
import kotlin.math.roundToInt

/** 上下文轮数滑杆区间：与网页端 maxContextTurns 的合法区间一致。 */
private const val ContextTurnsMin = 4f
private const val ContextTurnsMax = 60f

/**
 * 上下文与长记忆管理。
 *
 * 三件事同页，因为它们共同决定"下一次请求带多少历史出去"：
 * 轮数预算、压缩阈值、压缩开关。前两项都是本轮新补的表端可写项，
 * 每改一次都能在 [CompressPolicy] 上算出**可观测**的触发点变化（见页面底部的预算回显）。
 */
@Composable
fun MemoryManagerContent(
    preferences: AgentPreferences,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var memoryCleared by remember { mutableStateOf(false) }
    var turns by remember { mutableIntStateOf(preferences.maxContextTurns) }
    var compressThreshold by remember { mutableFloatStateOf(preferences.historyCompressThreshold) }
    var memoryEnabled by remember { mutableStateOf(preferences.memoryEnabled) }
    var autoCompress by remember { mutableStateOf(preferences.autoCompressContext) }

    // 清空会重写全部偏好：本页四处本地状态必须立刻跟真值对齐，
    // 否则屏上还留着刚被清掉的值，用户会以为"清空没生效"。
    fun resyncFromPreferences() {
        turns = preferences.maxContextTurns
        compressThreshold = preferences.historyCompressThreshold
        memoryEnabled = preferences.memoryEnabled
        autoCompress = preferences.autoCompressContext
    }

    SettingsSubPage(
        title = stringResource(R.string.settings_memory_management),
        onBack = onBack
    ) {
        section(stringResource(R.string.settings_turns_header))

        row {
            SettingsSliderRow(
                label = stringResource(R.string.settings_context_turns),
                value = turns.toFloat(),
                valueRange = ContextTurnsMin..ContextTurnsMax,
                steps = (ContextTurnsMax - ContextTurnsMin).toInt() - 1,
                valueText = stringResource(R.string.settings_memory_turns, turns),
                onValueChange = { raw ->
                    val snapped = raw.roundToInt()
                    if (snapped == turns) return@SettingsSliderRow
                    turns = snapped
                    preferences.maxContextTurns = snapped
                    context.vibrateTick()
                }
            )
        }

        row {
            SettingsSliderRow(
                label = stringResource(R.string.settings_compress_threshold),
                value = compressThreshold,
                valueRange = CompressPolicy.MinThreshold..CompressPolicy.MaxThreshold,
                steps = 12,
                valueText = (compressThreshold * 100).roundToInt().toString() + "%",
                onValueChange = { raw ->
                    val snapped = CompressPolicy.clampThreshold(
                        (raw * 20f).roundToInt() / 20f
                    )
                    if (snapped == compressThreshold) return@SettingsSliderRow
                    compressThreshold = snapped
                    preferences.historyCompressThreshold = snapped
                    context.vibrateTick()
                }
            )
        }

        // 预算回显：轮数与阈值组合出"多少条消息开始压缩"这个唯一可观测量，
        // 数字变了就说明设置真的生效了 —— 这是本页对"假开关"的结构性回答。
        row {
            val trigger = CompressPolicy.triggerAt(turns, compressThreshold)
            val budget = CompressPolicy.messageBudget(turns)
            // 状态只落在右端那一个 6dp 点上：压缩关掉时不表态，触发点越过预算才是警告
            SettingsStatusRow(
                title = stringResource(R.string.settings_context_budget),
                value = stringResource(R.string.settings_context_budget_value, trigger, budget),
                statusTone = when {
                    !autoCompress -> null
                    trigger >= budget -> SettingsStatusTone.WARN
                    else -> SettingsStatusTone.OK
                }
            )
        }

        row {
            SettingsSwitchRow(
                label = stringResource(R.string.settings_context_compress),
                secondary = stringResource(R.string.settings_context_compress_secondary),
                checked = autoCompress,
                onCheckedChange = { enabled ->
                    autoCompress = enabled
                    preferences.autoCompressContext = enabled
                }
            )
        }

        row {
            SettingsSwitchRow(
                label = stringResource(R.string.settings_memory_enabled),
                secondary = stringResource(R.string.settings_memory_enabled_secondary),
                checked = memoryEnabled,
                onCheckedChange = { enabled ->
                    memoryEnabled = enabled
                    preferences.memoryEnabled = enabled
                }
            )
        }

        section(stringResource(R.string.settings_web_only_header))

        row {
            SettingsWebOnlyRow(
                form = WebSettingsForm.WEB_ONLY,
                raw = stringResource(R.string.settings_context_window_web)
            )
        }
        row {
            SettingsWebOnlyRow(
                form = WebSettingsForm.WEB_ONLY,
                raw = stringResource(R.string.settings_context_truncation_web)
            )
        }

        section(stringResource(R.string.settings_memory_clear_header))

        row {
            SettingsActionButton(
                label = if (memoryCleared) {
                    stringResource(R.string.settings_memory_cleared)
                } else {
                    stringResource(R.string.settings_memory_clear_all)
                },
                tone = if (memoryCleared) Tone.MUTED else Tone.DANGER,
                onClick = {
                    context.vibrateConfirm()
                    preferences.resetAll()
                    memoryCleared = true
                    resyncFromPreferences()
                }
            )
        }
    }
}

/**
 * 手势与侧键绑定。
 *
 * 四条绑定全部落在 [com.example.myapplication.agent.GestureRuntime] 上，后者只做三件真实的事：
 * 投递"终止当前任务"、切换模型、忘掉已记住的命令。四条绑定各用一组单选行呈现，
 * 选中项就是当前动作；没有任何一条是"拨了没下文"的开关。
 */
@Composable
fun GestureBindingsContent(
    preferences: AgentPreferences,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var bindings by remember { mutableStateOf(preferences.gestureBindings()) }

    fun write(next: com.example.myapplication.agent.GestureBindings) {
        bindings = next
        preferences.longPressAction = next.longPress
        preferences.doubleTapAction = next.doubleTap
        preferences.sideKeyShortAction = next.sideKeyShort
        preferences.sideKeyLongAction = next.sideKeyLong
    }

    SettingsSubPage(
        title = stringResource(R.string.settings_sub_gesture),
        onBack = onBack
    ) {
        GestureBindingSection(
            header = stringResource(R.string.settings_gesture_long_press),
            selected = bindings.longPress,
            onSelect = { context.vibrateTick(); write(bindings.copy(longPress = it)) }
        )
        GestureBindingSection(
            header = stringResource(R.string.settings_gesture_double_tap),
            selected = bindings.doubleTap,
            onSelect = { context.vibrateTick(); write(bindings.copy(doubleTap = it)) }
        )
        GestureBindingSection(
            header = stringResource(R.string.settings_gesture_side_short),
            selected = bindings.sideKeyShort,
            onSelect = { context.vibrateTick(); write(bindings.copy(sideKeyShort = it)) }
        )
        GestureBindingSection(
            header = stringResource(R.string.settings_gesture_side_long),
            selected = bindings.sideKeyLong,
            onSelect = { context.vibrateTick(); write(bindings.copy(sideKeyLong = it)) }
        )

        row { SettingsCaptionRow(text = stringResource(R.string.settings_gesture_hint)) }
    }
}

/** 一条绑定：组标题写明手势，下面四行列出可绑定动作。 */
@Composable
private fun SettingsRows.GestureBindingSection(
    header: String,
    selected: GestureAction,
    onSelect: (GestureAction) -> Unit
) {
    section(header)
    GestureAction.values().forEach { action ->
        row(subItem = true) {
            SettingsSubRadioRow(
                label = gestureActionLabel(action),
                selected = selected == action,
                onSelect = { onSelect(action) }
            )
        }
    }
}

/** 动作的展示文案：只覆盖真实存在执行路径的四个值。 */
@Composable
private fun gestureActionLabel(action: GestureAction): String = stringResource(
    when (action) {
        GestureAction.NONE -> R.string.settings_gesture_none
        GestureAction.STOP_TURN -> R.string.settings_gesture_stop_turn
        GestureAction.SWITCH_MODEL -> R.string.settings_gesture_switch_model
        GestureAction.FORGET_REMEMBERED -> R.string.settings_gesture_forget_remembered
    }
)

/**
 * AI 安全护栏模式选择：三档改单选行，选中态取主色，说明文字作为次行最多两行。
 */
@Composable
fun SecurityGuardrailContent(
    currentMode: SecurityMode,
    onBack: () -> Unit,
    onSelect: (SecurityMode) -> Unit
) {
    val modes = listOf(
        Triple(SecurityMode.NOVICE, R.string.settings_security_novice_title, R.string.settings_security_novice_desc),
        Triple(SecurityMode.STANDARD, R.string.settings_security_standard_title, R.string.settings_security_standard_desc),
        Triple(SecurityMode.EXPERT, R.string.settings_security_expert_title, R.string.settings_security_expert_desc)
    )

    SettingsSubPage(
        title = stringResource(R.string.settings_security_policy),
        onBack = onBack
    ) {
        section(stringResource(R.string.settings_security_policy))

        modes.forEach { (mode, titleRes, descRes) ->
            row(subItem = true) {
                SettingsSubRadioRow(
                    label = stringResource(titleRes),
                    subtitle = stringResource(descRes),
                    selected = currentMode == mode,
                    onSelect = {
                        onSelect(mode)
                    }
                )
            }
        }
    }
}
