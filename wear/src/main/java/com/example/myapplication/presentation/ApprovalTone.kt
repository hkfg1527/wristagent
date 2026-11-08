package com.example.myapplication.presentation

import androidx.compose.ui.graphics.Color
import com.example.myapplication.agent.contract.RiskLevel
import com.example.myapplication.presentation.ui.theme.Wear

/**
 * 审批强调色与标题：随 AI 风险等级变化 —— 低风险绿、中度黄、重度黄红、致命红。
 * 标题、危险条点亮段与滑块着色共用同一套映射，避免各处重复写等级到颜色的分支。
 */
internal object ApprovalTone {
    fun accentOf(level: RiskLevel): Color = when (level) {
        RiskLevel.LOW -> Wear.Palette.Success
        RiskLevel.MEDIUM -> Wear.Palette.Warning
        RiskLevel.HIGH -> Wear.Palette.Danger
        RiskLevel.CRITICAL -> Wear.Palette.Danger
    }

}
