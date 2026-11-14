package com.example.myapplication.presentation.ui.settings

import androidx.compose.foundation.background
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

    fun run(command: String) {
        if (running) return
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

        section(stringResource(R.string.settings_lux_output))
        row {
            val transcript = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp, max = 150.dp)
                    .background(Wear.Palette.SurfaceAlt, SettingsCardRowShape)
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
