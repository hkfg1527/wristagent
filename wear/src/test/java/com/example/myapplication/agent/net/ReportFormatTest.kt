package com.example.myapplication.agent.net

import com.example.myapplication.R
import org.junit.Assert.assertEquals
import org.junit.Test

/** 「复制诊断报告」的纯文本格式：固定列宽、全 ASCII，用户直接贴给开发者。 */
class ReportFormatTest {

    private fun diag(
        id: DiagnosticId,
        status: DiagnosticStatus,
        detail: String,
    ) = Diagnostic(id = id, status = status, messageRes = 0, detail = detail)

    private fun item(id: RepairPlanId, priority: Int, action: RepairAction) = RepairItem(
        id = id,
        priority = priority,
        titleRes = R.string.settings_netdiag_plan_dns,
        conclusionRes = R.string.settings_netdiag_plan_dns_why,
        guideRes = R.string.settings_netdiag_plan_dns_guide,
        actionRes = R.string.settings_netdiag_action_doh,
        action = action,
    )

    @Test
    fun reportIsStableLineByLine() {
        val report = ReportFormat.toPlainText(
            diagnostics = listOf(
                diag(DiagnosticId.DNS, DiagnosticStatus.OK, "dns 12ms"),
                diag(DiagnosticId.TCP, DiagnosticStatus.FAIL, "connect timeout"),
            ),
            plans = listOf(
                item(RepairPlanId.AUTH_PORTAL, 20, RepairAction.OPEN_CAPTIVE_PORTAL),
                item(RepairPlanId.RETRY_TIMEOUT, 110, RepairAction.RETRY_WITH_BACKOFF),
            ),
            target = "api.deepseek.com",
            generatedAtMillis = 1000L,
        )

        assertEquals(
            listOf(
                "NETDIAG v1",
                "generated=1970-01-01T00:00:01Z target=api.deepseek.com",
                "DNS          OK   dns 12ms",
                "TCP          FAIL connect timeout",
                "plans=AUTH_PORTAL,RETRY_TIMEOUT",
            ),
            report.lines(),
        )
    }

    @Test
    fun reportWithoutPlansSaysNone() {
        val report = ReportFormat.toPlainText(
            diagnostics = listOf(diag(DiagnosticId.MTU, DiagnosticStatus.SKIP, "inconclusive")),
            plans = emptyList(),
            target = "example.com",
            generatedAtMillis = 0L,
        )

        assertEquals(
            listOf(
                "NETDIAG v1",
                "generated=1970-01-01T00:00:00Z target=example.com",
                "MTU          SKIP inconclusive",
                "plans=none",
            ),
            report.lines(),
        )
    }

    @Test
    fun reportKeepsEveryDiagnosticInGivenOrder() {
        val diagnostics = DiagnosticId.entries.map { diag(it, DiagnosticStatus.OK, it.name.lowercase()) }
        val report = ReportFormat.toPlainText(diagnostics, emptyList(), "h", 0L)

        val body = report.lines().drop(2).dropLast(1)
        assertEquals(DiagnosticId.entries.map { it.name }, body.map { it.takeWhile { c -> c != ' ' } })
    }
}
