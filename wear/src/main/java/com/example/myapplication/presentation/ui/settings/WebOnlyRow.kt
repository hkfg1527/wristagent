package com.example.myapplication.presentation.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.example.myapplication.R
import com.example.myapplication.presentation.ui.theme.Wear

/** 「项名：指引」拆成两栏后的内容：项名在上，去向指引在下。 */
internal data class WebOnlyRowParts(val label: String, val guidance: String)

/**
 * 拆开「项名：指引」式的既有文案。
 *
 * 中文用全角冒号、英文用半角冒号加空格；压在一句里只能当说明文字读，看不出它是"某一项设置"。
 * 找不到分隔符时整句当项名、指引回落到通用文案 —— 宁可少一栏，也不吞掉屏上已有的字。
 */
internal fun webOnlyRowParts(raw: String, fallbackGuidance: String): WebOnlyRowParts {
    val text = raw.trim()
    val cut = listOf("：", ": ")
        .map { text.indexOf(it) }
        .filter { it > 0 }
        .minOrNull()
        ?: return WebOnlyRowParts(label = text, guidance = fallbackGuidance)
    val label = text.substring(0, cut).trim()
    val guidance = text.substring(cut).trimStart('：', ':', ' ').trim()
    return WebOnlyRowParts(
        label = label.ifEmpty { text },
        guidance = guidance.ifEmpty { fallbackGuidance }
    )
}

/** 一行在手表上到底能不能点。 */
internal enum class RowInteractivity { INTERACTIVE, READ_ONLY }

/**
 * 一行的可交互性：只有表端真能改的形态才可点。
 *
 * 只读回显与网页专属两形态在手表上没有写入路径，行上既没有 clickable 也没有箭头；
 * 这条判定是 [SettingsWebOnlyRow] 的入口契约，挡住"把可写形态画成一行只读回显"。
 */
internal fun rowInteractivityOf(form: WebSettingsForm): RowInteractivity = when (form) {
    WebSettingsForm.READ_ONLY, WebSettingsForm.WEB_ONLY -> RowInteractivity.READ_ONLY
    WebSettingsForm.SWITCH, WebSettingsForm.TRI_STATE, WebSettingsForm.SLIDER,
    WebSettingsForm.TEXT_ENTRY, WebSettingsForm.SELECT, WebSettingsForm.ACTION ->
        RowInteractivity.INTERACTIVE
}

/**
 * 网页专属行的唯一呈现：项名 + "去手机网页改"的指引，两行铺满内容宽度。
 *
 * 不走 [SettingsInfoRow]：它把取值挤进最宽 96dp 的窄栏，九字项名（压缩阈值与截断策略）会被截成省略号。
 * 也不走 [SettingsCaptionRow]：说明文字读不出一条设置的身份，用户不知道自己在看哪一项。
 * 本行没有 onClick 入参、也不挂 clickable —— 不可点是结构上的事实，不是把回调写成空 lambda。
 */
@Composable
internal fun SettingsWebOnlyRow(form: WebSettingsForm, raw: String) {
    require(rowInteractivityOf(form) == RowInteractivity.READ_ONLY) {
        "$form 在表端可写，必须用开关 / 单选 / 滑轨 / 按钮行，不能走只读行入口"
    }
    val parts = webOnlyRowParts(raw, stringResource(R.string.settings_appearance_web_only))
    // 左槽对齐：项名与全页所有行标签落在同一条左槽线上（旧版整行居中，读起来没有左边缘）
    Column(
        modifier = Modifier
            .widthIn(max = SettingsContentWidth)
            .fillMaxWidth()
            .heightIn(min = Wear.Size.Touch)
            .padding(vertical = Wear.Space.Sm),
        verticalArrangement = Arrangement.spacedBy(Wear.Space.Xs)
    ) {
        BasicText(
            text = parts.label,
            style = SettingsRowLabelStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        BasicText(
            text = parts.guidance,
            style = SettingsRowValueStyle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        // 行内标记：手表端改不了的字段必须当场说明，不能让用户点进去才发现没有控件。
        BasicText(
            text = if (form == WebSettingsForm.WEB_ONLY) "仅网页端可改" else "只读",
            style = Wear.TextCaption.copy(color = Wear.Palette.TextMuted),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
