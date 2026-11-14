package com.example.myapplication.presentation.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.myapplication.R
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.presentation.ui.components.Tone
import com.example.myapplication.agent.SandboxLevel
import com.example.myapplication.agent.SubAgentPolicy
import com.example.myapplication.agent.ToolApprovalPolicy
import com.example.myapplication.presentation.ui.theme.Wear

/**
 * 工具集细粒度放行与 MCP 协议探针：六个内置工具改用官方 SwitchButton，
 * 点击语义、写入的偏好键与旧版逐条一致。
 */
@Composable
fun ToolsMcpContent(
    preferences: AgentPreferences,
    isMcpEnabled: Boolean,
    mcpEndpoint: String,
    onBack: () -> Unit,
    onToggleMcp: (enabled: Boolean, endpoint: String) -> Unit,
    animationsEnabled: Boolean = true,
    onToggleAnimations: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    var toolDeviceStatus by remember { mutableStateOf(preferences.toolDeviceStatusEnabled) }
    var toolVibrate by remember { mutableStateOf(preferences.toolVibrateEnabled) }
    var toolMatrixDensity by remember { mutableStateOf(preferences.toolMatrixDensityEnabled) }
    var toolPowerMode by remember { mutableStateOf(preferences.toolPowerModeEnabled) }
    var toolWeather by remember { mutableStateOf(preferences.toolWeatherEnabled) }
    var toolWebSearch by remember { mutableStateOf(preferences.toolWebSearchEnabled) }
    var providerManagedSearch by remember { mutableStateOf(preferences.providerManagedSearchEnabled) }
    var subAgentEnabled by remember { mutableStateOf(preferences.subAgentEnabled) }
    // 审批策略与沙箱等级：一个决定"什么时候问人"，一个决定"能碰多远"，互不覆盖
    var approvalPolicy by remember { mutableStateOf(preferences.toolApprovalPolicy) }
    var sandboxLevel by remember { mutableStateOf(preferences.sandboxLevel) }
    // 审批放行清单：条数必须可见（上限满了新的"记住"会被静默拒绝），撤销必须是显式二次确认
    var rememberedCount by remember { mutableIntStateOf(preferences.rememberedShellCommandCount) }
    var forgetArmed by remember { mutableStateOf(false) }
    // 子代理只在专家模式生效：判定交给 SubAgentPolicy 这个唯一真源
    // （enabled = true 即问"本模式下开关有没有意义"），其余模式下这一行退化成只读提示
    val subAgentEffective = SubAgentPolicy.isEffective(preferences.securityMode, enabled = true)

    SettingsSubPage(
        title = stringResource(R.string.settings_tools_and_mcp),
        heroLabel = stringResource(R.string.settings_tools_and_mcp),
        onBack = onBack,
        hero = {
            val toolSwitches = listOf(
                toolDeviceStatus,
                toolVibrate,
                toolMatrixDensity,
                toolPowerMode,
                toolWeather,
                toolWebSearch
            )
            val enabled = toolSwitches.count { it }
            SettingsHeroCard(
                label = stringResource(R.string.settings_tools_and_mcp),
                value = if (enabled == toolSwitches.size) {
                    stringResource(R.string.settings_tools_all_enabled)
                } else {
                    stringResource(R.string.settings_tools_enabled_count, enabled)
                },
                accent = Wear.CardTools,
                meta = if (isMcpEnabled) mcpEndpoint else null,
                badge = "MCP"
            )
        }
    ) {
        section(stringResource(R.string.settings_tools_release_header))

        row {
            SettingsSwitchRow(
                label = stringResource(R.string.settings_tool_device_status),
                secondary = "get_device_status",
                checked = toolDeviceStatus,
                onCheckedChange = { enabled ->
                    toolDeviceStatus = enabled
                    preferences.toolDeviceStatusEnabled = enabled
                }
            )
        }

        row {
            SettingsSwitchRow(
                label = stringResource(R.string.settings_tool_vibrate),
                secondary = "vibrate_device",
                checked = toolVibrate,
                onCheckedChange = { enabled ->
                    toolVibrate = enabled
                    preferences.toolVibrateEnabled = enabled
                }
            )
        }

        row {
            SettingsSwitchRow(
                label = stringResource(R.string.settings_tool_matrix_density),
                secondary = "adjust_matrix_density",
                checked = toolMatrixDensity,
                onCheckedChange = { enabled ->
                    toolMatrixDensity = enabled
                    preferences.toolMatrixDensityEnabled = enabled
                }
            )
        }

        row {
            SettingsSwitchRow(
                label = stringResource(R.string.settings_tool_power_mode),
                secondary = "set_system_power_mode",
                checked = toolPowerMode,
                onCheckedChange = { enabled ->
                    toolPowerMode = enabled
                    preferences.toolPowerModeEnabled = enabled
                }
            )
        }

        row {
            SettingsSwitchRow(
                label = stringResource(R.string.settings_tool_time_weather),
                secondary = "query_time_weather",
                checked = toolWeather,
                onCheckedChange = { enabled ->
                    toolWeather = enabled
                    preferences.toolWeatherEnabled = enabled
                }
            )
        }

        row {
            SettingsSwitchRow(
                label = stringResource(R.string.settings_tool_web_search),
                secondary = "web_search",
                checked = toolWebSearch,
                onCheckedChange = { enabled ->
                    toolWebSearch = enabled
                    preferences.toolWebSearchEnabled = enabled
                }
            )
        }

        // 子代理模式：非专家模式下列表里连工具都不会出现，设置面也没有可拨的开关
        row {
            if (subAgentEffective) {
                SettingsSwitchRow(
                    label = stringResource(R.string.settings_sub_agent),
                    secondary = stringResource(R.string.settings_sub_agent_secondary),
                    checked = subAgentEnabled,
                    onCheckedChange = { enabled ->
                        subAgentEnabled = enabled
                        preferences.subAgentEnabled = enabled
                    }
                )
            } else {
                SettingsInfoRow(
                    title = stringResource(R.string.settings_sub_agent),
                    subtitle = stringResource(R.string.settings_sub_agent_requires_expert),
                    subtitleBelowRow = true
                )
            }
        }

        // ===== 审批策略与沙箱等级：本轮补全的两档"工具能做什么"=====
        section(stringResource(R.string.settings_approval_policy))

        row {
            // 取值中性：本页只留一个状态点（沙箱等级），选项的好坏由下面列表的选中态表达
            SettingsStatusRow(
                title = stringResource(R.string.settings_approval_policy),
                value = approvalPolicyLabel(approvalPolicy)
            )
        }

        ToolApprovalPolicy.values().forEach { policy ->
            row(subItem = true) {
                SettingsSubRadioRow(
                    label = approvalPolicyLabel(policy),
                    selected = approvalPolicy == policy,
                    // 从不确认 = 危险选项：Danger 只染选中那个圆点
                    dangerSelected = policy == ToolApprovalPolicy.NEVER,
                    onSelect = {
                        approvalPolicy = policy
                        preferences.toolApprovalPolicy = policy
                    }
                )
            }
        }

        section(stringResource(R.string.settings_sandbox_level))

        row {
            // 本页唯一的状态点：工具能碰多远，是最该被一眼看到的量
            SettingsStatusRow(
                title = stringResource(R.string.settings_sandbox_level),
                value = sandboxLevelLabel(sandboxLevel),
                statusTone = if (sandboxLevel == SandboxLevel.READ_ONLY) {
                    SettingsStatusTone.OK
                } else {
                    SettingsStatusTone.WARN
                }
            )
        }

        SandboxLevel.values().forEach { level ->
            row(subItem = true) {
                SettingsSubRadioRow(
                    label = sandboxLevelLabel(level),
                    selected = sandboxLevel == level,
                    // 全权限 = 危险选项：Danger 只染选中那个圆点
                    dangerSelected = level == SandboxLevel.FULL,
                    onSelect = {
                        sandboxLevel = level
                        preferences.sandboxLevel = level
                    }
                )
            }
        }

        // ===== 审批放行清单：条数可见 + 可撤销 =====
        section(stringResource(R.string.settings_remembered_header))

        row {
            // 满额本身就有下一条说明行兜住，取值不再上色
            SettingsStatusRow(
                title = stringResource(R.string.settings_remembered_title),
                value = stringResource(
                    R.string.settings_remembered_count,
                    rememberedCount,
                    AgentPreferences.MaxRememberedShellCommands
                )
            )
        }

        if (rememberedCount >= AgentPreferences.MaxRememberedShellCommands) {
            row { SettingsCaptionRow(text = stringResource(R.string.settings_remembered_full)) }
        }

        standaloneRow {
            SettingsActionButton(
                label = if (forgetArmed) {
                    stringResource(R.string.settings_remembered_forget_confirm)
                } else {
                    stringResource(R.string.settings_remembered_forget)
                },
                tone = if (rememberedCount == 0) Tone.MUTED else Tone.DANGER,
                enabled = rememberedCount > 0,
                onClick = {
                    if (!forgetArmed) {
                        // 第一次点只上膛：撤销放行记录是破坏性动作，必须两次
                        forgetArmed = true
                    } else {
                        context.vibrateConfirm()
                        preferences.forgetRememberedShellCommands()
                        rememberedCount = preferences.rememberedShellCommandCount
                        forgetArmed = false
                    }
                }
            )
        }

        section(stringResource(R.string.settings_web_tools_header))

        row {
            SettingsSwitchRow(
                label = stringResource(R.string.settings_tools_provider_search),
                secondary = stringResource(R.string.settings_tools_provider_search_secondary),
                subtitleBelowRow = true,
                checked = providerManagedSearch,
                onCheckedChange = { enabled ->
                    providerManagedSearch = enabled
                    preferences.providerManagedSearchEnabled = enabled
                }
            )
        }

        section(stringResource(R.string.settings_mcp_protocol))

        row {
            SettingsSwitchRow(
                label = stringResource(R.string.settings_mcp_probe),
                checked = isMcpEnabled,
                onCheckedChange = { enabled ->
                    onToggleMcp(enabled, mcpEndpoint)
                }
            )
        }

        row { SettingsCaptionRow(text = mcpEndpoint, mono = true) }

        // 界面动效：这一页是所有"功能开关"的归口，语义上比推理参数页更贴
        section(stringResource(R.string.settings_ui_and_beta_header))

        row {
            SettingsSwitchRow(
                label = stringResource(R.string.settings_animations),
                secondary = stringResource(R.string.settings_animations_secondary),
                checked = animationsEnabled,
                onCheckedChange = onToggleAnimations
            )
        }
    }
}

/** 审批策略三档的展示文案。 */
@Composable
internal fun approvalPolicyLabel(policy: ToolApprovalPolicy): String = stringResource(
    when (policy) {
        ToolApprovalPolicy.ALWAYS -> R.string.settings_approval_always
        ToolApprovalPolicy.RISKY -> R.string.settings_approval_risky
        ToolApprovalPolicy.NEVER -> R.string.settings_approval_never
    }
)

/** 沙箱等级三档的展示文案。 */
@Composable
internal fun sandboxLevelLabel(level: SandboxLevel): String = stringResource(
    when (level) {
        SandboxLevel.READ_ONLY -> R.string.settings_sandbox_read_only
        SandboxLevel.WORKSPACE -> R.string.settings_sandbox_workspace
        SandboxLevel.FULL -> R.string.settings_sandbox_full
    }
)
