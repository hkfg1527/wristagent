package com.example.myapplication.presentation.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.myapplication.R
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.presentation.ui.components.Tone

/**
 * 配置导入导出：整份可移植设置的 JSON 快照。
 *
 * 两个动作各自落到一件真事上：
 * - 导出 = 把 [SettingsTransferCodec.export] 的文本放进系统剪贴板（可以被任何 App 粘走）；
 * - 导入 = 解析剪贴板里的文本，逐项校验后写回偏好；解析失败时**一个字都不写**。
 *
 * 结果行用语义色表态：成功 Success、失败 Danger —— 这是"有颜色"但不乱刷的用法。
 */
@Composable
fun SettingsTransferContent(
    preferences: AgentPreferences,
    onBack: () -> Unit,
    onApplied: () -> Unit = {}
) {
    val context = LocalContext.current
    // 外观两项住在独立偏好文件里，快照必须同时带它们走，否则导出再导入等于换了个外观
    val appearance = remember(context) { AppearanceStore.of(context) }
    var pasteOpen by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var statusOk by remember { mutableStateOf(true) }

    SettingsSubPage(
        title = stringResource(R.string.settings_sub_transfer),
        onBack = onBack,
        overlay = {
            if (pasteOpen) {
                SettingsTextDialog(
                    title = stringResource(R.string.settings_transfer_dialog_title),
                    initial = "",
                    placeholder = stringResource(R.string.settings_transfer_placeholder),
                    onValueChange = { raw ->
                        when (val parsed = SettingsTransferCodec.parse(raw)) {
                            is TransferResult.Ok -> {
                                val applied =
                                    SettingsTransferBridge.apply(preferences, parsed.entries, appearance)
                                onApplied()
                                statusOk = true
                                status = context.getString(R.string.settings_transfer_imported, applied)
                            }
                            is TransferResult.Invalid -> {
                                statusOk = false
                                status = context.getString(R.string.settings_transfer_invalid)
                            }
                        }
                        pasteOpen = false
                    },
                    onDismiss = { pasteOpen = false }
                )
            }
        }
    ) {
        section(stringResource(R.string.settings_transfer_header))

        standaloneRow {
            SettingsActionButton(
                label = stringResource(R.string.settings_transfer_export),
                tone = Tone.ACCENT,
                onClick = {
                    context.vibrateConfirm()
                    val snapshot = SettingsTransferCodec.export(
                        SettingsTransferBridge.snapshot(preferences, appearance)
                    )
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(ClipData.newPlainText("settings", snapshot))
                    statusOk = true
                    status = context.getString(R.string.settings_transfer_exported)
                }
            )
        }

        // 从剪贴板直接读：真实路径是"先复制、再点这一行"，不是让用户手打一段 JSON
        standaloneRow {
            SettingsActionButton(
                label = stringResource(R.string.settings_transfer_import),
                tone = Tone.MUTED,
                onClick = {
                    context.vibrateTick()
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    val raw = clipboard?.primaryClip
                        ?.takeIf { it.itemCount > 0 }
                        ?.getItemAt(0)
                        ?.coerceToText(context)
                        ?.toString()
                    if (raw.isNullOrBlank()) {
                        pasteOpen = true
                    } else {
                        when (val parsed = SettingsTransferCodec.parse(raw)) {
                            is TransferResult.Ok -> {
                                val applied =
                                    SettingsTransferBridge.apply(preferences, parsed.entries, appearance)
                                onApplied()
                                statusOk = true
                                status = context.getString(R.string.settings_transfer_imported, applied)
                            }
                            is TransferResult.Invalid -> {
                                pasteOpen = true
                            }
                        }
                    }
                }
            )
        }

        status?.let { text ->
            row {
                // 导入导出结果：成败只由右端状态点表态，正文保持中性
                SettingsStatusRow(
                    title = stringResource(R.string.settings_transfer_header),
                    value = text,
                    statusTone = if (statusOk) SettingsStatusTone.OK else SettingsStatusTone.WARN
                )
            }
        }

        row { SettingsCaptionRow(text = stringResource(R.string.settings_transfer_hint)) }
    }
}
