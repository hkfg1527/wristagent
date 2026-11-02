package com.example.myapplication.agent.net

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.example.myapplication.agent.privilege.PrivilegeBackend
import com.example.myapplication.agent.privilege.PrivilegeManager
import kotlinx.coroutines.delay
import java.net.Inet4Address
import java.net.InetAddress

/** 单条预案的执行结果。NEEDS_MANUAL 表示本页修不了，只能给指引。 */
enum class RepairOutcome { SUCCEEDED, FAILED, NEEDS_MANUAL }

data class RepairResult(
    val planId: RepairPlanId,
    val outcome: RepairOutcome,
    val detail: String,
)

/**
 * 预案执行器：只跑「可自动修复」的动作，逐条返回成功 / 失败 / 需人工。
 *
 * 所有系统调用与网络调用都收敛成 [RepairResult]：这里绝不抛异常，
 * 也不做任何降权尝试 —— 没 root 就走跳转设置的降级路径。
 */
class NetworkRepairer(
    private val context: Context,
    private val diagnostics: NetworkDiagnostics,
    private val privilege: PrivilegeManager,
    private val baseUrl: () -> String,
) {

    suspend fun runAll(items: List<RepairItem>): List<RepairResult> {
        val results = mutableListOf<RepairResult>()
        items.forEach { results += execute(it) }
        return results
    }

    private suspend fun execute(item: RepairItem): RepairResult {
        if (!item.action.isAutomatic) {
            return RepairResult(item.id, RepairOutcome.NEEDS_MANUAL, "no-automatic-fix")
        }
        return try {
            when (item.action) {
                RepairAction.OPEN_WIFI_SETTINGS -> open(item.id, Settings.ACTION_WIFI_SETTINGS)
                RepairAction.OPEN_DATE_SETTINGS -> open(item.id, Settings.ACTION_DATE_SETTINGS)
                RepairAction.OPEN_CAPTIVE_PORTAL -> openPortal(item.id)
                RepairAction.RETEST_BYPASS_PROXY -> retestBypassProxy(item.id)
                RepairAction.RETEST_WITH_DOH -> retestWithDoh(item.id)
                RepairAction.RETEST_FORCE_IPV4 -> retestForceIpv4(item.id)
                RepairAction.RETRY_WITH_BACKOFF -> retryWithBackoff(item.id)
                RepairAction.SET_AUTO_TIME_VIA_ROOT -> enableAutoTime(item.id)
                RepairAction.SHOW_FINAL_PROBE -> finalProbe(item.id)
                RepairAction.SUGGEST_HOTSPOT,
                RepairAction.SUGGEST_OTHER_BASE_URL,
                RepairAction.SWITCH_NETWORK_REQUIRED,
                RepairAction.NO_AUTOMATIC_FIX,
                -> RepairResult(item.id, RepairOutcome.NEEDS_MANUAL, "no-automatic-fix")
            }
        } catch (t: Throwable) {
            RepairResult(item.id, RepairOutcome.FAILED, t.javaClass.simpleName)
        }
    }

    private fun open(id: RepairPlanId, action: String): RepairResult {
        val launched = launch(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return RepairResult(id, if (launched) RepairOutcome.SUCCEEDED else RepairOutcome.NEEDS_MANUAL, action)
    }

    private fun openPortal(id: RepairPlanId): RepairResult {
        val base = baseUrl().trim()
        if (base.isEmpty()) return RepairResult(id, RepairOutcome.NEEDS_MANUAL, "no-base-url")
        val url = if (base.contains("://")) base else "http://$base"
        val launched = launch(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return RepairResult(id, if (launched) RepairOutcome.SUCCEEDED else RepairOutcome.NEEDS_MANUAL, url)
    }

    private fun launch(intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: Throwable) {
        false
    }

    private suspend fun retestBypassProxy(id: RepairPlanId): RepairResult {
        val base = baseUrl().trim()
        if (base.isEmpty()) return RepairResult(id, RepairOutcome.FAILED, "no-base-url")
        val attempt = diagnostics.httpsAttempt(url = base, method = "GET", useSystemProxy = false)
        return if (attempt.ok) {
            RepairResult(id, RepairOutcome.SUCCEEDED, "direct http=${attempt.code} ${attempt.elapsedMs}ms")
        } else {
            RepairResult(id, RepairOutcome.FAILED, "direct ${attempt.failure?.javaClass?.simpleName}")
        }
    }

    private suspend fun retestWithDoh(id: RepairPlanId): RepairResult {
        val host = NetworkTarget.hostOf(baseUrl())
            ?: return RepairResult(id, RepairOutcome.FAILED, "no-host")
        val answers = diagnostics.dohResolve(host)
        if (answers.isEmpty()) return RepairResult(id, RepairOutcome.FAILED, "doh-empty")
        val reachable = answers.any { address ->
            try {
                diagnostics.connectTcp(host, NetworkDiagnostics.HTTPS_PORT, NetworkDiagnostics.TCP_TIMEOUT_MS, InetAddress.getByName(address)).ok
            } catch (_: Throwable) {
                false
            }
        }
        val detail = "doh=${answers.joinToString(",")} reachable=$reachable"
        return RepairResult(id, if (reachable) RepairOutcome.SUCCEEDED else RepairOutcome.FAILED, detail)
    }

    private suspend fun retestForceIpv4(id: RepairPlanId): RepairResult {
        val host = NetworkTarget.hostOf(baseUrl()) ?: return RepairResult(id, RepairOutcome.FAILED, "no-host")
        val ipv4 = try {
            InetAddress.getAllByName(host).filterIsInstance<Inet4Address>().firstOrNull()
        } catch (_: Throwable) {
            null
        } ?: return RepairResult(id, RepairOutcome.FAILED, "no-a-record")
        val attempt = diagnostics.connectTcp(host, NetworkDiagnostics.HTTPS_PORT, NetworkDiagnostics.TCP_TIMEOUT_MS, ipv4)
        return RepairResult(
            id,
            if (attempt.ok) RepairOutcome.SUCCEEDED else RepairOutcome.FAILED,
            "ipv4=${ipv4.hostAddress} ok=${attempt.ok} ${attempt.elapsedMs}ms",
        )
    }

    /** 指数退避：250ms、500ms 各重试一次，把每次耗时都记下来。 */
    private suspend fun retryWithBackoff(id: RepairPlanId): RepairResult {
        val base = baseUrl().trim()
        if (base.isEmpty()) return RepairResult(id, RepairOutcome.FAILED, "no-base-url")
        val timings = mutableListOf<String>()
        var delayMs = FIRST_BACKOFF_MS
        repeat(MAX_RETRY) { index ->
            val attempt = diagnostics.httpsAttempt(url = base, method = "GET")
            timings += "try${index + 1}=${attempt.elapsedMs}ms"
            if (attempt.ok) {
                return RepairResult(id, RepairOutcome.SUCCEEDED, timings.joinToString(" "))
            }
            delay(delayMs)
            delayMs *= 2
        }
        return RepairResult(id, RepairOutcome.FAILED, timings.joinToString(" "))
    }

    private suspend fun enableAutoTime(id: RepairPlanId): RepairResult {
        if (!privilege.status.value.rootAvailable) {
            return RepairResult(id, RepairOutcome.NEEDS_MANUAL, "no-root")
        }
        val result = privilege.shellFor(PrivilegeBackend.ROOT).run(AUTO_TIME_COMMAND)
        val detail = result.stdout.ifBlank { result.stderr }.ifBlank { "exit=${result.exitCode}" }
        return RepairResult(id, if (result.isSuccess) RepairOutcome.SUCCEEDED else RepairOutcome.FAILED, detail)
    }

    private suspend fun finalProbe(id: RepairPlanId): RepairResult {
        val base = baseUrl().trim()
        if (base.isEmpty()) return RepairResult(id, RepairOutcome.FAILED, "no-base-url")
        val attempt = diagnostics.httpsAttempt(url = base, method = "GET")
        return RepairResult(
            id,
            if (attempt.ok) RepairOutcome.SUCCEEDED else RepairOutcome.FAILED,
            "http=${attempt.code} ${attempt.elapsedMs}ms",
        )
    }

    private companion object {
        const val AUTO_TIME_COMMAND = "settings put global auto_time 1"
        const val FIRST_BACKOFF_MS = 250L
        const val MAX_RETRY = 2
    }
}
