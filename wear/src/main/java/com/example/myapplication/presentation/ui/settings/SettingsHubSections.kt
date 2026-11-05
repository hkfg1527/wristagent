package com.example.myapplication.presentation.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.example.myapplication.R

/** 中转页内层子页的哨兵下标：等于它表示"只停在中转页本身"。 */
private const val NoInnerPage = -1

/**
 * 提示词中转页：把"系统提示词"与"会话与上下文"两条根页行收成一条。
 *
 * 两条都是"模型行为"同层配置，分开占两行 98dp 的步进不划算。内层子页走 overlay 叠上来，
 * 返回时先关内层再退本页 —— 直接从内层掉回根页会让用户找不到刚才的层级。
 */
@Composable
fun PromptContextContent(
    promptValue: String,
    turnsValue: String,
    onBack: () -> Unit,
    promptPage: @Composable (onDismiss: () -> Unit) -> Unit,
    memoryPage: @Composable (onDismiss: () -> Unit) -> Unit,
) {
    var inner by remember { mutableIntStateOf(NoInnerPage) }
    // 系统返回（右滑）必须与页内返回键一致：先关内层子页，再退中转页。
    // 少了这一行，右滑会直接把 activeDialog 置空，跳过内层，用户会觉得"退得太狠"。
    BackHandler(enabled = inner != NoInnerPage) { inner = NoInnerPage }
    SettingsSubPage(
        title = stringResource(R.string.settings_prompt_context),
        onBack = { if (inner != NoInnerPage) inner = NoInnerPage else onBack() },
        overlay = {
            when (inner) {
                0 -> promptPage { inner = NoInnerPage }
                1 -> memoryPage { inner = NoInnerPage }
            }
        }
    ) {
        row {
            SettingsNavRow(
                title = stringResource(R.string.settings_system_prompt),
                value = promptValue,
                onClick = { inner = 0 }
            )
        }
        row {
            SettingsNavRow(
                title = stringResource(R.string.settings_memory_management),
                value = turnsValue,
                onClick = { inner = 1 }
            )
        }
    }
}

/**
 * 关于中转页：快捷操作、重看引导、特权执行、网络体检四条低频只读项收进一层。
 *
 * 这四条在根页占 4 × 49dp 却几乎不被改动；根页因此从 14 行收到 9 行。
 */
@Composable
fun AboutContent(
    sideKeyValue: String,
    privilegeValue: String,
    netDiagValue: String,
    onBack: () -> Unit,
    onReplayOnboarding: () -> Unit,
    sideKeyPage: @Composable (onDismiss: () -> Unit) -> Unit,
    privilegePage: @Composable (onDismiss: () -> Unit) -> Unit,
    netDiagPage: @Composable (onDismiss: () -> Unit) -> Unit,
) {
    var inner by remember { mutableIntStateOf(NoInnerPage) }
    BackHandler(enabled = inner != NoInnerPage) { inner = NoInnerPage }
    SettingsSubPage(
        title = stringResource(R.string.settings_about),
        onBack = { if (inner != NoInnerPage) inner = NoInnerPage else onBack() },
        overlay = {
            when (inner) {
                0 -> sideKeyPage { inner = NoInnerPage }
                1 -> privilegePage { inner = NoInnerPage }
                2 -> netDiagPage { inner = NoInnerPage }
            }
        }
    ) {
        row {
            SettingsNavRow(
                title = stringResource(R.string.settings_side_key),
                value = sideKeyValue,
                onClick = { inner = 0 }
            )
        }
        row {
            SettingsNavRow(
                title = stringResource(R.string.settings_replay_onboarding),
                onClick = onReplayOnboarding
            )
        }
        row {
            SettingsNavRow(
                title = stringResource(R.string.settings_privilege_title),
                value = privilegeValue,
                onClick = { inner = 1 }
            )
        }
        row {
            SettingsNavRow(
                title = stringResource(R.string.settings_netdiag_title),
                value = netDiagValue,
                onClick = { inner = 2 }
            )
        }
    }
}
