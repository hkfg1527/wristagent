package com.example.myapplication.presentation.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.agent.contract.RightButtonAction
import com.example.myapplication.agent.contract.SecurityMode
import com.example.myapplication.presentation.ui.components.Tone
import com.example.myapplication.presentation.ui.components.WearChip
import com.example.myapplication.presentation.ui.components.WearSectionHeader
import com.example.myapplication.presentation.ui.theme.Wear

/**
 * 上下文与长记忆管理：单会话保留轮数与一键清空。
 */
@Composable
fun MemoryManagerContent(
    preferences: AgentPreferences
) {
    val context = LocalContext.current
    var memoryCleared by remember { mutableStateOf(false) }
    var turns by remember { mutableIntStateOf(preferences.maxContextTurns) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(Wear.Space.Xs)
    ) {
        WearSectionHeader(text = "单会话保留轮数")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Wear.Space.Sm)
        ) {
            listOf(4 to "4轮 节能", 10 to "10轮 标准", 30 to "30轮 深度").forEach { (count, label) ->
                WearChip(
                    label = label,
                    selected = turns == count,
                    onClick = {
                        context.vibrateTick()
                        turns = count
                        preferences.maxContextTurns = count
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        WearSectionHeader(text = "记忆清理")

        SettingsActionButton(
            label = if (memoryCleared) "已清空当前记忆与偏好" else "一键清空全部记忆与偏好",
            tone = if (memoryCleared) Tone.MUTED else Tone.DANGER,
            onClick = {
                context.vibrateTick()
                preferences.resetAll()
                memoryCleared = true
            }
        )

        SubPageDoneRow()

        Spacer(modifier = Modifier.height(SubPageBottomReserve))
    }
}

/**
 * 两翼快捷键定义。
 */
@Composable
fun WingShortcutContent(
    currentAction: RightButtonAction,
    onSelect: (RightButtonAction) -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(Wear.Space.Xs)
    ) {
        WearSectionHeader(text = "右侧副键绑定")

        val actions = listOf(
            RightButtonAction.MODEL_SWITCH to "绑定：切换大模型",
            RightButtonAction.OPEN_SETTINGS to "绑定：打开系统设置",
            RightButtonAction.CLEAR_MEMORY to "绑定：一键清空长记忆"
        )

        actions.forEach { (act, label) ->
            val isSelected = currentAction == act
            SettingsChoiceRow(
                title = label,
                value = if (isSelected) "已生效" else null,
                selected = isSelected,
                onClick = {
                    context.vibrateTick()
                    onSelect(act)
                }
            )
        }

        SubPageDoneRow()

        Spacer(modifier = Modifier.height(SubPageBottomReserve))
    }
}

/**
 * AI 安全护栏模式选择。
 */
@Composable
fun SecurityGuardrailContent(
    currentMode: SecurityMode,
    onSelect: (SecurityMode) -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(Wear.Space.Xs)
    ) {
        WearSectionHeader(text = "操作安全策略")

        val modes = listOf(
            Triple(SecurityMode.NOVICE, "严格安全模式", "所有敏感与系统操作均需审批 (3秒倒计时防误触)"),
            Triple(SecurityMode.STANDARD, "标准提示模式", "只读查询自动放行，修改系统配置需滑动确认"),
            Triple(SecurityMode.EXPERT, "极速放行模式", "除致命危险指令外，跳过所有弹窗直接执行")
        )

        modes.forEach { (mode, label, desc) ->
            val isSelected = currentMode == mode
            SettingsChoiceRow(
                title = label,
                subtitle = desc,
                value = if (isSelected) "已激活" else null,
                selected = isSelected,
                onClick = {
                    context.vibrateTick()
                    onSelect(mode)
                }
            )
        }

        SubPageDoneRow()

        Spacer(modifier = Modifier.height(SubPageBottomReserve))
    }
}
