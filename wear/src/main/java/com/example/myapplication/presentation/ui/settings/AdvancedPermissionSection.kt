package com.example.myapplication.presentation.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.myapplication.R
import com.example.myapplication.agent.privilege.PrivilegeManager
import com.example.myapplication.agent.privilege.PrivilegeStatusText
import kotlinx.coroutines.launch

/**
 * 特权执行：显示 Root / Shizuku 的探测结果，提供 Shizuku 授权入口与一次真实探针。
 *
 * 设计原则：这一页**只读状态 + 触发探测**，任何系统调用失败都变成一行文案，绝不在这里抛异常 ——
 * 未 root 且未安装 Shizuku 的手表是常态，那一屏必须看起来是"功能说明"而不是"报错"。
 */
@Composable
fun AdvancedPermissionContent(
    manager: PrivilegeManager,
    onBack: () -> Unit,
) {
    val status by manager.status.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var probing by remember { mutableStateOf(false) }

    SettingsSubPage(
        title = stringResource(R.string.settings_privilege_title),
        onBack = onBack
    ) {
        section(stringResource(R.string.settings_privilege_title))

        row {
            SettingsCaptionRow(
                text = stringResource(R.string.settings_privilege_backend, status.activeBackend.name),
                mono = true
            )
        }

        row {
            SettingsInfoRow(
                title = stringResource(R.string.settings_privilege_root_label),
                value = stringResource(PrivilegeStatusText.rootRes(status.rootAvailable))
            )
        }

        row {
            SettingsInfoRow(
                title = stringResource(R.string.settings_privilege_shizuku_label),
                value = stringResource(PrivilegeStatusText.shizukuInstalledRes(status.shizukuInstalled))
            )
        }

        row {
            SettingsInfoRow(
                title = stringResource(R.string.settings_privilege_shizuku_permission_label),
                value = stringResource(PrivilegeStatusText.shizukuGrantedRes(status.shizukuReady))
            )
        }

        row {
            SettingsCaptionRow(
                text = status.lastProbeOutput.ifBlank { stringResource(R.string.settings_privilege_probe_empty) },
                mono = true
            )
        }

        row {
            SettingsActionButton(
                label = stringResource(R.string.settings_privilege_request_shizuku),
                enabled = status.shizukuInstalled,
                onClick = {
                    val activity = context.findActivity()
                    if (activity != null) manager.requestShizukuPermission(activity, SHIZUKU_REQUEST_CODE)
                }
            )
        }

        row {
            SettingsActionButton(
                label = stringResource(
                    if (probing) R.string.settings_privilege_probe_running else R.string.settings_privilege_probe
                ),
                enabled = !probing,
                onClick = {
                    probing = true
                    scope.launch {
                        runCatching { manager.probe() }
                        manager.refreshAsync()
                        probing = false
                    }
                }
            )
        }
    }
}

/** Shizuku 授权请求码：全局唯一即可，回调在 PrivilegeManager 里按 code 匹配。 */
private const val SHIZUKU_REQUEST_CODE = 4101

/** 从 Context 里找 Activity：requestPermission 必须由 Activity 发起。 */
private fun android.content.Context.findActivity(): android.app.Activity? {
    var current = this
    while (current is android.content.ContextWrapper) {
        if (current is android.app.Activity) return current
        current = current.baseContext
    }
    return null
}
