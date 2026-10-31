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
import androidx.compose.ui.res.stringResource
import com.example.myapplication.R
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.agent.contract.RightButtonAction
import com.example.myapplication.agent.contract.SecurityMode
import com.example.myapplication.presentation.ui.components.Tone
import com.example.myapplication.presentation.ui.components.WearChip
import com.example.myapplication.presentation.ui.components.WearRow
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
        WearSectionHeader(text = stringResource(R.string.settings_turns_header))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Wear.Space.Sm)
        ) {
            listOf(4 to stringResource(R.string.settings_turns_4), 10 to stringResource(R.string.settings_turns_10), 30 to stringResource(R.string.settings_turns_30)).forEach { (count, label) ->
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

        WearSectionHeader(text = stringResource(R.string.settings_memory_clear_header))

        SettingsActionButton(
            label = if (memoryCleared) stringResource(R.string.settings_memory_cleared) else stringResource(R.string.settings_memory_clear_all),
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
 * 两翼副键说明。
 * 横向手势已被 Wear 系统占用，右副键固定为打开会话抽屉，不再提供可绑定项。
 */
@Composable
fun WingShortcutContent(
    currentAction: RightButtonAction,
    onSelect: (RightButtonAction) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(Wear.Space.Xs)
    ) {
        WearSectionHeader(text = stringResource(R.string.settings_side_key_header))

        WearRow(
            title = stringResource(R.string.settings_side_key),
            value = stringResource(R.string.settings_side_key_value)
        )

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
        WearSectionHeader(text = stringResource(R.string.settings_security_policy))

        val modes = listOf(
            Triple(SecurityMode.NOVICE, stringResource(R.string.settings_security_novice_title), stringResource(R.string.settings_security_novice_desc)),
            Triple(SecurityMode.STANDARD, stringResource(R.string.settings_security_standard_title), stringResource(R.string.settings_security_standard_desc)),
            Triple(SecurityMode.EXPERT, stringResource(R.string.settings_security_expert_title), stringResource(R.string.settings_security_expert_desc))
        )

        modes.forEach { (mode, label, desc) ->
            val isSelected = currentMode == mode
            SettingsChoiceRow(
                title = label,
                subtitle = desc,
                value = if (isSelected) stringResource(R.string.settings_activated) else null,
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
