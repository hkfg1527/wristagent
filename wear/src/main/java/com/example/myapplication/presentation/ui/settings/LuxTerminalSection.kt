package com.example.myapplication.presentation.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.MaterialTheme
import com.example.myapplication.R
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.presentation.ui.components.Tone
import com.example.myapplication.agent.ShellPolicy
import com.example.myapplication.agent.privilege.DirectShell
import com.example.myapplication.agent.privilege.ShellResult
import com.example.myapplication.presentation.ui.theme.Wear
import kotlinx.coroutines.launch

/**
 * Lux 终端：一条命令、一次输出、一份最近的转录。
 *
 * 边界写在 [LuxTerminalPolicy] 的注释里：没有 PTY、没有 ANSI、没有交互式 stdin。
 * 命令仍然过 [ShellPolicy.validate] 的同一道闸门 —— 那道闸门是挡模型的，不是挡主人的，
 * 但共用一份规则能省掉"终端里能干、工具里不能干"的割裂。
 */
@Composable
fun LuxTerminalContent(
    preferences: AgentPreferences,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<String>>(emptyList()) }
    var running by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var lastCommand by remember { mutableStateOf("") }
    var history by remember { mutableStateOf<List<String>>(emptyList()) }
    var saved by remember { mutableStateOf(LuxTerminalPolicy.savedOf(preferences.luxSavedCommands)) }

    fun run(command: String) {
        if (running) return
        history = LuxTerminalPolicy.appendHistory(history, command)
        val rejection = ShellPolicy.validate(command)
        if (rejection != null) {
            entries = LuxTerminalPolicy.trimEntries(entries + "❯ " + command + "\n被拒：" + rejection)
            context.vibrateConfirm()
            return
        }
        running = true
        scope.launch {
            val shell = DirectShell(preferences.shellCommandTimeoutMs)
            val result = shell.run(command)
            entries = LuxTerminalPolicy.trimEntries(
                entries + LuxTerminalPolicy.renderEntry(
                    command = command,
                    result = result,
                    cap = preferences.toolResultMaxChars
                )
            )
            running = false
            if (result.isSuccess) context.vibrateTick() else context.vibrateConfirm()
        }
    }

    SettingsSubPage(
        title = stringResource(R.string.settings_lux_terminal),
        heroLabel = stringResource(R.string.settings_lux_terminal),
        onBack = onBack,
        overlay = {
            if (editing) {
                SettingsTextDialog(
                    title = stringResource(R.string.settings_lux_input_title),
                    initial = lastCommand,
                    placeholder = stringResource(R.string.settings_lux_input_placeholder),
                    onValueChange = { raw ->
                        editing = false
                        LuxTerminalPolicy.parseCommand(raw)?.let { command ->
                            lastCommand = command
                            run(command)
                        }
                    },
                    onDismiss = { editing = false }
                )
            }
        },
        hero = {
            SettingsHeroCard(
                label = stringResource(R.string.settings_lux_terminal),
                value = entries.size.toString() + " / " + LuxTerminalPolicy.MAX_ENTRIES,
                accent = Wear.CardTools,
                ring = (entries.size.toFloat() / LuxTerminalPolicy.MAX_ENTRIES).coerceIn(0f, 1f),
                meta = stringResource(R.string.settings_lux_meta, preferences.shellCommandTimeoutMs / 1000)
            )
        }
    ) {
        section(stringResource(R.string.settings_lux_input))
        row {
            SettingsTextEntryRow(
                label = stringResource(R.string.settings_lux_free_input),
                value = lastCommand,
                onClick = { editing = true }
            )
        }

        // 我的快捷位：用户自己攒的常用命令。行永远注册，内容在渲染时读状态（DSL 的收集期规则）。
        section(stringResource(R.string.settings_lux_saved))
        row {
            val current = saved
            val lastRun = history.lastOrNull()
            SettingsNavRow(
                title = if (lastRun == null) {
                    stringResource(R.string.settings_lux_saved_hint)
                } else {
                    stringResource(R.string.settings_lux_save_last, lastRun)
                },
                value = if (lastRun == null) null else stringResource(R.string.settings_lux_save_action),
                onClick = {
                    val command = history.lastOrNull() ?: return@SettingsNavRow
                    val next = LuxTerminalPolicy.withSaved(preferences.luxSavedCommands, command)
                    preferences.luxSavedCommands = next
                    saved = LuxTerminalPolicy.savedOf(next)
                    context.vibrateConfirm()
                }
            )
        }
        // 已保存的快捷位：用**一个 standaloneRow 内部渲染整个列表**。
        //
        // 这样绕开了设置页 DSL 的"收集期"约束：standaloneRow 的内容在渲染时求值，
        // 所以保存/删除后立刻反映到界面上，不需要重新进入本页。
        standaloneRow {
            // 读 Compose 状态而不是偏好本身：SharedPreferences 的写入不会触发重组，
            // 只有状态变量才会。状态在进入页面时用偏好初始化，之后每次增删都同步更新。
            val list = saved
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                list.forEach { command ->
                    SettingsNavRow(
                        title = command,
                        value = stringResource(R.string.settings_lux_delete),
                        onClick = {
                            preferences.luxSavedCommands =
                                LuxTerminalPolicy.withoutSaved(preferences.luxSavedCommands, command)
                            saved = LuxTerminalPolicy.savedOf(preferences.luxSavedCommands)
                            context.vibrateTick()
                        }
                    )
                }
            }
        }
        section(stringResource(R.string.settings_lux_quick))
        LuxTerminalPolicy.QuickCommands.forEach { command ->
            row {
                SettingsNavRow(
                    title = command,
                    value = if (running) "…" else null,
                    onClick = { run(command) }
                )
            }
        }

        // 历史：点一条就再跑一次；与快捷命令分开，因为这是"你自己敲过的"。
        //
        // 注意：**不能用 if 把行整个拿掉**。设置页的行在"收集期"注册（SettingsSubPage 调 registry.rows()），
        // 那里读到的状态是首帧的；后续状态变化只会让已注册的行重绘，不会补注册新行。
        // 所以这里永远注册一整组：有历史就列出来，没有就给一行说明。
        section(stringResource(R.string.settings_lux_history))
        // 行必须"总是注册"，而**行里的文字要在渲染时读状态**：
        // 设置页的行在收集期注册，收集期读到的 history 是首帧的值；
        // 只有把读取放进行内容的 lambda，它才会随状态重绘。所以这里只登记一行，
        // 文案由 lastOrNull() 在渲染时决定（空态给提示文字）。
        row {
            val lastRun = history.lastOrNull()
            SettingsNavRow(
                title = lastRun ?: stringResource(R.string.settings_lux_history_empty),
                value = if (lastRun == null) null else stringResource(R.string.settings_lux_rerun),
                onClick = { lastRun?.let { lastCommand = it; run(it) } }
            )
        }
        section(stringResource(R.string.settings_lux_output))
        // 同上：按钮永远注册，用 enabled 表达"能不能点"，而不是把按钮拿掉。
        standaloneRow {
            SettingsActionButton(
                label = stringResource(R.string.settings_lux_clear),
                enabled = entries.isNotEmpty(),
                onClick = { entries = emptyList(); context.vibrateConfirm() },
                tone = Tone.DANGER
            )
        }
        row {
            val transcript = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp, max = 150.dp)
                    .background(Wear.Palette.SurfaceAlt, settingsCardRowShape())
                    .padding(Wear.Space.Sm)
                    .verticalScroll(transcript)
            ) {
                val text = if (entries.isEmpty()) {
                    stringResource(R.string.settings_lux_empty)
                } else {
                    entries.joinToString("\n\n")
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = Wear.Palette.TextMuted
                )
            }
        }
    }
}
