package com.example.myapplication.presentation.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.agent.contract.RightButtonAction
import com.example.myapplication.agent.contract.SecurityMode

/**
 * 上下文与长记忆管理 (动态轮数调节与持久偏好)
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
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 上下文滑窗轮数单选 (44dp 大尺寸药丸)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF161A24))
                .border(0.8.dp, Color(0xFF2B3448), RoundedCornerShape(14.dp))
                .padding(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("单会话保留轮数", fontSize = 12.sp, color = TextSecondaryColor)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(4 to "4轮 节能", 10 to "10轮 标准", 30 to "30轮 深度").forEach { (count, label) ->
                        val isSel = turns == count
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSel) Color(0xFF1E293B) else Color(0xFF222B3C))
                                .border(
                                    width = if (isSel) 1.2.dp else 0.6.dp,
                                    color = if (isSel) AccentCyan else Color(0xFF384661),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    context.vibrateTick()
                                    turns = count
                                    preferences.maxContextTurns = count
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) Color.White else Color(0xFFCBD5E1)
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF161A24))
                .border(0.8.dp, Color(0xFF2B3448), RoundedCornerShape(14.dp))
                .padding(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("• 记忆策略：超出设定轮数后自动淘汰早期对话", fontSize = 11.5.sp, color = Color(0xFFCBD5E1))
                Text("• 永久记忆：提示词持久锁定长期人设与指令", fontSize = 11.5.sp, color = Color(0xFFCBD5E1))
                Text("• 会话隔离：支持左滑唤出侧边栏切换会话", fontSize = 11.5.sp, color = Color(0xFFCBD5E1))
            }
        }

        // 清空会话记忆按钮 (44dp 警示操作)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (memoryCleared) Color(0xFF222B3C) else Color(0xFF3B151E))
                .border(0.8.dp, if (memoryCleared) Color(0xFF384661) else Color(0xFFEF4444), RoundedCornerShape(12.dp))
                .clickable {
                    context.vibrateTick()
                    preferences.resetAll()
                    memoryCleared = true
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (memoryCleared) "已清空当前记忆" else "清空全部记忆与偏好",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (memoryCleared) TextSecondaryColor else Color(0xFFF87171)
            )
        }
    }
}

/**
 * 两翼快捷键定义
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val actions = listOf(
            RightButtonAction.MODEL_SWITCH to "切换大模型",
            RightButtonAction.OPEN_SETTINGS to "打开系统设置",
            RightButtonAction.CLEAR_MEMORY to "一键清空长记忆"
        )

        actions.forEach { (act, label) ->
            val isSelected = currentAction == act
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) Color(0xFF1E293B) else Color(0xFF161A24))
                    .border(
                        width = if (isSelected) 1.2.dp else 0.8.dp,
                        color = if (isSelected) AccentCyan else Color(0xFF2C3548),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable {
                        context.vibrateTick()
                        onSelect(act)
                    }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else Color(0xFFCBD5E1)
                    )
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(AccentCyanDark)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("已绑定", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

/**
 * AI 安全护栏模式选择
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val modes = listOf(
            Triple(SecurityMode.NOVICE, "严格确认模式", "所有敏感与系统操作均需审批，高危操作锁定 3 秒防误触"),
            Triple(SecurityMode.STANDARD, "标准提示模式", "只读查询自动放行，涉及修改系统配置需滑动确认"),
            Triple(SecurityMode.EXPERT, "自动执行模式", "除致命危险指令外，跳过确认弹窗直接执行")
        )

        modes.forEach { (mode, label, desc) ->
            val isSelected = currentMode == mode
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) Color(0xFF1E293B) else Color(0xFF161A24))
                    .border(
                        width = if (isSelected) 1.2.dp else 0.8.dp,
                        color = if (isSelected) AccentCyan else Color(0xFF2C3548),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable {
                        context.vibrateTick()
                        onSelect(mode)
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else Color(0xFFCBD5E1)
                        )
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(AccentCyanDark)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("生效中", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                    Text(
                        text = desc,
                        fontSize = 11.5.sp,
                        color = TextSecondaryColor,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}
