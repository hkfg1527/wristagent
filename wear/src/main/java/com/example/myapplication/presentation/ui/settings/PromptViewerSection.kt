package com.example.myapplication.presentation.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.R
import com.example.myapplication.presentation.ui.theme.Wear

/** 预设角色正文：与旧版内联文本逐字一致，属于业务数据而非界面文案。 */
private const val PromptMinimal =
    "你是运行在 Android Wear OS 智能手表上的 AI 助手。支持工具调用，回答简短精准，适配手表快速浏览。"
private const val PromptArchitect =
    "你是资深系统工程架构师，回答严谨客观、具备高信息密度与技术深度，杜绝虚假与客套。"
private const val PromptSecretary =
    "你是贴身随行腕上助理，提供精准时间、日程与日常信息整理，语言温和凝练。"

/** 正文可视高度上限：再高就会把下面的预设角色挤出圆屏。 */
private val PromptBodyMaxHeight = 112.dp

/**
 * 正文分段：空行分段，段内换行原样保留。
 *
 * 首行不做缩进（不塞全角空格）：手表屏宽只够 10 个汉字左右，缩进两格等于白扔两字。
 * 纯函数，段落规则可以直接单测。
 */
internal fun promptParagraphs(text: String): List<String> = text
    .split(Regex("\n[ \t]*\n"))
    .map { it.trim('\n', ' ', '\t') }
    .filter { it.isNotBlank() }

/** 正文档：15sp / TextSecondary / 行高 22sp —— 与全站行文本同档，只把行高放开以便整段阅读。 */
private val PromptBodyStyle: TextStyle
    @Composable get() = Wear.TextRowValue.copy(
        lineHeight = SettingsDesignMath.BodyLineHeightSp.sp
    )

/**
 * 系统提示词查看与编辑。
 *
 * 版面（旧版是一大段裸文本，读起来像日志）：
 * - 顶部一行「系统提示词」+ 右侧管理入口（Accent），点整行进入编辑对话框；
 * - 正文 15sp / TextSecondary / 22sp 行高，段间距 8dp，首行不缩进；
 * - 正文超过 [PromptBodyMaxHeight] 时，顶/底各压一条 12dp 的 Bg→透明 渐隐，
 *   明确告诉用户"还有内容"；不画滚动条（手表上滚动条既占宽又看不清）。
 */
@Composable
fun PromptViewerContent(
    customPrompt: String,
    onBack: () -> Unit,
    onSavePrompt: (String) -> Unit
) {
    var editorOpen by remember { mutableStateOf(false) }
    // 预设名参与选中比较，先取本地化文案再进比较，避免在非组合上下文里读资源。
    val presetMinimal = stringResource(R.string.settings_preset_minimal)
    val presetArchitect = stringResource(R.string.settings_preset_architect)
    val presetSecretary = stringResource(R.string.settings_preset_secretary)
    val presetClear = stringResource(R.string.settings_preset_clear_test)

    val rolePresets = listOf(
        presetMinimal to PromptMinimal,
        presetArchitect to PromptArchitect,
        presetSecretary to PromptSecretary
    )
    // 选中态由当前正文精确反推：点哪一行就亮哪一行，正文被手改过就一行都不亮。
    // 用 contains 猜会在正文里恰好出现"架构师"时误判；更糟的是极简助手正文永远匹配不上，
    // 而空正文（初始态 / 预置默认）反倒把极简助手点亮 —— 屏上的选中项与真值完全相反。
    val selectedPreset = rolePresets.firstOrNull { it.second == customPrompt }?.first

    // 空正文 = 运行时走内置默认：正文区显示"预置默认"这个事实，而不是把某条预设冒充成当前正文
    val bodyText = if (customPrompt.isBlank()) {
        stringResource(R.string.settings_prompt_default)
    } else {
        customPrompt
    }

    SettingsSubPage(
        title = stringResource(R.string.settings_system_prompt),
        onBack = onBack,
        overlay = {
            if (editorOpen) {
                SettingsTextDialog(
                    title = stringResource(R.string.settings_system_prompt),
                    initial = customPrompt,
                    placeholder = stringResource(R.string.settings_prompt_placeholder),
                    onValueChange = { value -> onSavePrompt(value) },
                    onDismiss = { editorOpen = false }
                )
            }
        }
    ) {
        row {
            PromptHeaderRow(onEdit = { editorOpen = true })
        }

        row {
            PromptBody(text = bodyText)
        }

        section(stringResource(R.string.settings_prompt_presets))

        // 清空行是把正文写空的动作，不是一种"当前取值"：正文为空时它是初始态，不该高亮成已选
        (rolePresets + (presetClear to "")).forEach { (label, prompt) ->
            row {
                SettingsRadioRow(
                    label = label,
                    selected = selectedPreset == label,
                    onSelect = { onSavePrompt(prompt) }
                )
            }
        }

        // 角色卡 / 提示词库 / 开场白是多记录列表与长文本：表端只读，并把去向写在行里
        section(stringResource(R.string.settings_web_only_header))

        row {
            SettingsWebOnlyRow(
                form = WebSettingsForm.WEB_ONLY,
                raw = stringResource(R.string.settings_role_cards_web)
            )
        }
        row {
            SettingsWebOnlyRow(
                form = WebSettingsForm.WEB_ONLY,
                raw = stringResource(R.string.settings_prompt_library_web)
            )
        }
        row {
            SettingsWebOnlyRow(
                form = WebSettingsForm.WEB_ONLY,
                raw = stringResource(R.string.settings_opening_line_web)
            )
        }
    }
}

/** 正文头部：标签左槽 + 管理入口右槽（Accent），整行可点。 */
@Composable
private fun PromptHeaderRow(onEdit: () -> Unit) {
    Row(
        modifier = Modifier
            .widthIn(max = SettingsContentWidth)
            .fillMaxWidth()
            .heightIn(min = SettingsSpacingMath.MinRowHeightDp.dp)
            .clickable(role = Role.Button, onClick = onEdit),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = stringResource(R.string.settings_system_prompt),
            modifier = Modifier.weight(1f),
            style = SettingsRowLabelStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        // 强调色只留给"选中态"：入口靠右槽位置 + 整行可点表达可进入，不再染强调色
        BasicText(
            text = stringResource(R.string.settings_providers_actions),
            style = SettingsRowValueStyle.copy(color = Wear.Palette.TextPrimary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 正文区：段落 15sp / 22sp 行高，超出可视高度时顶部与底部各压一条 12dp 渐隐。
 *
 * 渐隐只在这一侧真的还有内容时出现（[androidx.compose.foundation.ScrollState.canScrollBackward]
 * / canScrollForward），没有遮挡就不画 —— 遮罩不是装饰。
 */
@Composable
private fun PromptBody(text: String) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val fadePx = with(density) { SettingsDesignMath.FadeDp.dp.toPx() }
    val showTopFade = scroll.value > 0
    val showBottomFade = scroll.canScrollForward

    Box(
        modifier = Modifier
            .widthIn(max = SettingsContentWidth)
            .fillMaxWidth()
            .heightIn(max = PromptBodyMaxHeight)
            .drawWithContent {
                drawContent()
                if (showTopFade) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Wear.Palette.Bg, Color.Transparent),
                            startY = 0f,
                            endY = fadePx
                        ),
                        size = Size(size.width, fadePx)
                    )
                }
                if (showBottomFade) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Wear.Palette.Bg),
                            startY = size.height - fadePx,
                            endY = size.height
                        ),
                        topLeft = Offset(0f, size.height - fadePx),
                        size = Size(size.width, fadePx)
                    )
                }
            }
            .verticalScroll(scroll)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(SettingsDesignMath.ParagraphGapDp.dp)
        ) {
            promptParagraphs(text).forEach { paragraph ->
                BasicText(text = paragraph, style = PromptBodyStyle)
            }
        }
    }
}
