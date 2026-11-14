package com.example.myapplication.presentation.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import com.example.myapplication.R
import com.example.myapplication.agent.net.Diagnostic
import com.example.myapplication.agent.net.DiagnosticId
import com.example.myapplication.agent.net.DiagnosticStatus
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.agent.net.DiagnosticTimeouts
import com.example.myapplication.agent.net.NetworkDiagnosisStore
import com.example.myapplication.agent.net.NetworkDiagnostics
import com.example.myapplication.agent.net.NetworkRepairPlan
import com.example.myapplication.agent.net.NetworkRepairer
import com.example.myapplication.agent.net.NetworkTarget
import com.example.myapplication.agent.net.RepairItem
import com.example.myapplication.agent.net.RepairOutcome
import com.example.myapplication.agent.net.RepairPlanId
import com.example.myapplication.agent.net.RepairResult
import com.example.myapplication.agent.net.ReportFormat
import com.example.myapplication.agent.privilege.PrivilegeManager
import com.example.myapplication.presentation.ui.components.Tone
import com.example.myapplication.presentation.ui.theme.Wear
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 网络体检页：九项检查逐条给结论，下面按优先级摆预案，三个按钮分别对应
 * 重新体检 / 一键修复 / 复制诊断报告。
 *
 * 进场即自动体检一次：用户点进来就是要看结论，不该再点一下。
 */
@Composable
fun NetworkDiagnosticsContent(
    privilegeManager: PrivilegeManager,
    baseUrl: () -> String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val privilegeStatus by privilegeManager.status.collectAsState()

    val target = remember { baseUrl().trim() }
    val preferences = remember { AgentPreferences(context.applicationContext) }
    val diagnostics = remember {
        NetworkDiagnostics(
            context.applicationContext,
            baseUrl,
            timeouts = DiagnosticTimeouts.of(preferences.netDiagTimeoutMs)
        )
    }
    val store = remember { NetworkDiagnosisStore(context.applicationContext) }

    var checks by remember { mutableStateOf<List<Diagnostic>>(emptyList()) }
    var plans by remember { mutableStateOf<List<RepairItem>>(emptyList()) }
    var results by remember { mutableStateOf<List<RepairResult>>(emptyList()) }
    var running by remember { mutableStateOf(false) }
    // 体检任务的句柄：连点时取消上一次并重开，而不是把按钮禁用掉当作"已受理"
    var checkJob by remember { mutableStateOf<Job?>(null) }
    var copied by remember { mutableStateOf(false) }
    var generatedAt by remember { mutableStateOf(0L) }

    val rootAvailable = privilegeStatus.rootAvailable

    suspend fun runCheck() {
        running = true
        results = emptyList()
        copied = false
        val report = diagnostics.run()
        val generated = System.currentTimeMillis()
        // 现读一次特权状态：进场那一刻的 root 探测可能还没回来
        val decided = NetworkRepairPlan.plan(report, privilegeManager.status.value.rootAvailable)
        checks = report
        plans = decided
        generatedAt = generated
        store.save(
            issues = decided.count { it.id != RepairPlanId.ALL_OK },
            ranAtMillis = generated,
        )
        running = false
    }

    LaunchedEffect(Unit) { runCheck() }

    SettingsSubPage(
        title = stringResource(R.string.settings_netdiag_title),
        heroLabel = stringResource(R.string.settings_netdiag_title),
        onBack = onBack,
        hero = {
            val issues = plans.count { it.id != RepairPlanId.ALL_OK }
            SettingsHeroCard(
                label = stringResource(R.string.settings_netdiag_title),
                value = when {
                    running -> stringResource(R.string.settings_netdiag_running)
                    checks.isEmpty() -> stringResource(R.string.settings_netdiag_value_never)
                    issues == 0 -> stringResource(R.string.settings_netdiag_value_ok)
                    else -> stringResource(R.string.settings_netdiag_value_issues, issues)
                },
                accent = Wear.CardTools,
                badge = "NET"
            )
        }
    ) {
        section(stringResource(R.string.settings_netdiag_checks_header))
        row { SettingsCaptionRow(text = stringResource(R.string.settings_netdiag_subtitle)) }

        DiagnosticId.entries.forEach { id ->
            val check = checks.firstOrNull { it.id == id }
            row {
                SettingsInfoRow(
                    title = statusSymbol(check?.status) + " " + stringResource(checkTitleRes(id)),
                    value = ""
                )
            }
            if (check != null) {
                row {
                    SettingsCaptionRow(
                        text = stringResource(check.messageRes, *check.formatArgs.toTypedArray())
                    )
                }
            }
        }

        section(stringResource(R.string.settings_netdiag_plans_header))
        if (plans.isEmpty()) {
            row { SettingsCaptionRow(text = stringResource(R.string.settings_netdiag_no_plans)) }
        }
        if (!rootAvailable) {
            row { SettingsCaptionRow(text = stringResource(R.string.settings_netdiag_root_off)) }
        }
        plans.forEach { item ->
            row { SettingsInfoRow(title = stringResource(item.titleRes), value = stringResource(item.actionRes)) }
            row { SettingsCaptionRow(text = stringResource(item.conclusionRes)) }
            if (!item.autoFixable) {
                row { SettingsCaptionRow(text = stringResource(item.guideRes)) }
            }
        }

        standaloneRow {
            SettingsActionButton(
                label = stringResource(R.string.settings_netdiag_recheck),
                // 不禁用：进页会自动体检一次，禁用会把这几秒里的点击静默吞掉
                enabled = true,
                onClick = {
                    context.vibrateTick()
                    checkJob?.cancel()
                    checkJob = scope.launch { runCheck() }
                }
            )
        }
        standaloneRow {
            SettingsActionButton(
                label = stringResource(R.string.settings_netdiag_repair),
                enabled = !running && plans.any { it.autoFixable },
                tone = Tone.ACCENT,
                onClick = {
                    context.vibrateTick()
                    scope.launch {
                        running = true
                        results = NetworkRepairer(
                            context.applicationContext,
                            diagnostics,
                            privilegeManager,
                            baseUrl,
                        ).runAll(plans)
                        running = false
                    }
                }
            )
        }
        // 反馈块紧贴按钮：按钮在页面最底部，结果/进行中若追加在它上方（旧位置），
        // 用户点完看到的仍是按钮本身 —— 真机上表现为"点了没反应"。
        if (running) {
            row { SettingsCaptionRow(text = stringResource(R.string.settings_netdiag_running)) }
        }
        if (results.isNotEmpty()) {
            results.forEach { result ->
                row {
                    SettingsCaptionRow(
                        text = stringResource(
                            when (result.outcome) {
                                RepairOutcome.SUCCEEDED -> R.string.settings_netdiag_repair_ok
                                RepairOutcome.FAILED -> R.string.settings_netdiag_repair_fail
                                RepairOutcome.NEEDS_MANUAL -> R.string.settings_netdiag_repair_manual
                            },
                            planLabel(plans, result),
                        )
                    )
                }
                if (result.detail.isNotBlank()) {
                    // 探针明细是机器串：折行会从词中间劈开，单行 + 尾部省略号更可读
                    row { SettingsCaptionRow(text = result.detail, mono = true, maxLines = 1) }
                }
            }
        }
        standaloneRow {
            SettingsActionButton(
                label = stringResource(R.string.settings_netdiag_copy),
                enabled = !running && checks.isNotEmpty(),
                onClick = {
                    context.vibrateTick()
                    clipboard.setText(
                        AnnotatedString(
                            ReportFormat.toPlainText(
                                diagnostics = checks,
                                plans = plans,
                                target = NetworkTarget.hostOf(target) ?: "unknown",
                                generatedAtMillis = generatedAt,
                            )
                        )
                    )
                    copied = true
                }
            )
        }
        if (copied) {
            row { SettingsCaptionRow(text = stringResource(R.string.settings_netdiag_copied)) }
        }
    }
}

@Composable
private fun planLabel(plans: List<RepairItem>, result: RepairResult): String =
    plans.firstOrNull { it.id == result.planId }?.let { stringResource(it.titleRes) } ?: result.planId.name

/** ✅ 通过 / ⚠️ 可疑 / ❌ 失败 / ⏭ 跳过；符号不属于文案，不进资源。 */
private fun statusSymbol(status: DiagnosticStatus?): String = when (status) {
    DiagnosticStatus.OK -> "✅"
    DiagnosticStatus.WARN -> "⚠️"
    DiagnosticStatus.FAIL -> "❌"
    DiagnosticStatus.SKIP -> "⏭"
    null -> "…"
}

private fun checkTitleRes(id: DiagnosticId): Int = when (id) {
    DiagnosticId.DNS -> R.string.settings_netdiag_check_dns
    DiagnosticId.TCP -> R.string.settings_netdiag_check_tcp
    DiagnosticId.TLS -> R.string.settings_netdiag_check_tls
    DiagnosticId.RESPONSE -> R.string.settings_netdiag_check_response
    DiagnosticId.CLOCK -> R.string.settings_netdiag_check_clock
    DiagnosticId.PROXY -> R.string.settings_netdiag_check_proxy
    DiagnosticId.DUAL_STACK -> R.string.settings_netdiag_check_dualstack
    DiagnosticId.MTU -> R.string.settings_netdiag_check_mtu
    DiagnosticId.NETWORK_TYPE -> R.string.settings_netdiag_check_network
}
