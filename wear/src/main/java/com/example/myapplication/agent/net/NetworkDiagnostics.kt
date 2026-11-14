package com.example.myapplication.agent.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.myapplication.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.ConnectException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/** 九项检查的固定顺序与标识。 */
enum class DiagnosticId {
    DNS,
    TCP,
    TLS,
    RESPONSE,
    CLOCK,
    PROXY,
    DUAL_STACK,
    MTU,
    NETWORK_TYPE,
}

/** 单项检查的结论档位。SKIP 表示本机条件下测不了，不算异常。 */
enum class DiagnosticStatus { OK, WARN, FAIL, SKIP }

/** 机器可判定的标记：预案只按标记与档位匹配，不猜文案。 */
enum class DiagnosticFlag {
    NO_TRANSPORT,
    METERED,
    NO_IPV6_ADDRESS,
    DNS_SUSPICIOUS,
    CONNECT_OK,
    CONNECT_REFUSED,
    CONNECT_TIMEOUT,
    CERT_UNVERIFIED,
    TLS_HANDSHAKE,
    PORTAL_HIJACK,
    AUTH_REQUIRED_511,
    NON_JSON_BODY,
    RATE_LIMITED,
    CLOCK_DRIFT,
    CLOCK_UNKNOWN,
    PROXY_ACTIVE,
    IPV4_OK,
    IPV4_FAIL,
    IPV6_OK,
    IPV6_FAIL,
    MTU_BLACKHOLE,
    MTU_INCONCLUSIVE,
}

/**
 * 单项检查结果。
 *
 * @param messageRes 一句话结论的字符串资源，文案全部落在 strings_settings.xml。
 * @param formatArgs 该文案的占位符参数（统一用字符串，槽位一律 %1$s 起）。
 * @param flags 供预案匹配的机器标记。
 * @param detail 纯技术细节（地址、耗时、异常类名），只进「复制诊断报告」，不含中文。
 */
data class Diagnostic(
    val id: DiagnosticId,
    val status: DiagnosticStatus,
    val messageRes: Int,
    val formatArgs: List<String> = emptyList(),
    val flags: Set<DiagnosticFlag> = emptySet(),
    val detail: String = "",
)

/** 响应体归类：校园网未认证的关键判据。 */
enum class ResponseClass { JSON_OK, REDIRECT, HTML, NETWORK_AUTH_REQUIRED, NON_JSON }

/**
 * 响应归类规则：先看状态码这种硬信号，再看正文形态，最后才信 Content-Type。
 *
 * 正文优先于 Content-Type，因为劫持页与被改写过的网关响应经常沿用 application/json。
 */
internal object ResponseClassifier {

    const val NETWORK_AUTH_REQUIRED_STATUS = 511

    fun classify(status: Int, contentType: String?, body: String): ResponseClass {
        if (status == NETWORK_AUTH_REQUIRED_STATUS) return ResponseClass.NETWORK_AUTH_REQUIRED
        if (status in 300..399) return ResponseClass.REDIRECT

        val head = body.trimStart()
        val type = contentType?.lowercase().orEmpty()

        val htmlBody = head.startsWith("<") &&
            (head.contains("<html", ignoreCase = true) || head.contains("<!doctype", ignoreCase = true))
        val htmlType = head.isEmpty() && type.contains("html")
        if (htmlBody || htmlType) return ResponseClass.HTML

        val jsonBody = head.startsWith("{") || head.startsWith("[")
        val jsonType = head.isEmpty() && type.contains("json")
        if (jsonBody || jsonType) return ResponseClass.JSON_OK

        return ResponseClass.NON_JSON
    }
}

/** 从接口地址里取出主机名；解析不出就返回 null，调用方按"无目标"处理。 */
internal object NetworkTarget {
    fun hostOf(baseUrl: String): String? {
        val trimmed = baseUrl.trim()
        if (trimmed.isEmpty()) return null
        val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        return try {
            URI(withScheme).host?.trim('[', ']')?.lowercase()?.takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            null
        }
    }
}

/** 「复制诊断报告」的纯文本组装：全 ASCII，便于直接贴给开发者。 */
internal object ReportFormat {

    private const val HEADER = "NETDIAG v1"
    private const val ID_WIDTH = 12
    private const val STATUS_WIDTH = 4

    fun toPlainText(
        diagnostics: List<Diagnostic>,
        plans: List<RepairItem>,
        target: String,
        generatedAtMillis: Long,
    ): String = buildString {
        appendLine(HEADER)
        appendLine("generated=${Instant.ofEpochMilli(generatedAtMillis)} target=$target")
        diagnostics.forEach { diagnostic ->
            val detail = diagnostic.detail.replace('\n', ' ').trim()
            appendLine(formatRow(diagnostic, detail))
        }
        append("plans=")
        append(if (plans.isEmpty()) "none" else plans.joinToString(",") { it.id.name })
    }

    private fun formatRow(diagnostic: Diagnostic, detail: String): String =
        "${diagnostic.id.name.padEnd(ID_WIDTH)} ${diagnostic.status.name.padEnd(STATUS_WIDTH)} $detail".trimEnd()
}

/** 一次 TCP 连接尝试的结果。 */
internal data class TcpAttempt(
    val ok: Boolean,
    val elapsedMs: Long,
    val failure: Throwable? = null,
)

/** 一次 HTTPS 请求尝试的结果；重定向不跟随，否则会丢掉认证页这一关键信号。 */
internal data class HttpAttempt(
    val ok: Boolean,
    val code: Int,
    val elapsedMs: Long,
    val contentType: String? = null,
    val dateHeader: String? = null,
    val bodyPrefix: String = "",
    val failure: Throwable? = null,
)

/**
 * 九项体检的真实执行体。
 *
 * 全部走系统默认网络栈（HttpURLConnection / Socket / InetAddress），不引入新依赖；
 * 每种失败都收敛成 [Diagnostic]，绝不把异常抛给界面。
 */
/**
 * 体检的两档探测超时（毫秒）。
 *
 * 用户只调一个旋钮（net_diag_timeout_ms），这里把它摊成两档：TCP 用旋钮本身，
 * 双栈探测比单栈快一档（−500ms，下限 500），因为两条连接是并发发起的。
 */
data class DiagnosticTimeouts(
    val tcpMs: Int = NetworkDiagnostics.TCP_TIMEOUT_MS,
    val stackMs: Int = NetworkDiagnostics.STACK_TIMEOUT_MS,
) {
    companion object {
        const val MIN_MS = 1_000
        const val MAX_MS = 10_000

        fun of(preferenceMs: Int): DiagnosticTimeouts {
            val tcp = preferenceMs.coerceIn(MIN_MS, MAX_MS)
            return DiagnosticTimeouts(tcpMs = tcp, stackMs = (tcp - 500).coerceAtLeast(500))
        }
    }
}

class NetworkDiagnostics(
    private val context: Context,
    private val baseUrl: () -> String,
    private val clock: () -> Long = System::currentTimeMillis,
    private val timeouts: DiagnosticTimeouts = DiagnosticTimeouts(),
) {

    suspend fun run(): List<Diagnostic> = withContext(Dispatchers.IO) {
        val base = baseUrl().trim()
        val host = NetworkTarget.hostOf(base)
        val transport = transportFacts()

        if (!transport.connected) {
            return@withContext listOf(
                networkDiagnostic(transport),
                skipped(DiagnosticId.DNS, R.string.settings_netdiag_dns_fail, "no-transport"),
                skipped(DiagnosticId.TCP, R.string.settings_netdiag_tcp_fail, "no-transport"),
                skipped(DiagnosticId.TLS, R.string.settings_netdiag_tls_fail, "no-transport"),
                skipped(DiagnosticId.RESPONSE, R.string.settings_netdiag_response_fail, "no-transport"),
                skipped(DiagnosticId.CLOCK, R.string.settings_netdiag_clock_unknown),
                proxyDiagnostic(host),
                skipped(DiagnosticId.DUAL_STACK, R.string.settings_netdiag_dual_both_fail, "no-transport"),
                skipped(DiagnosticId.MTU, R.string.settings_netdiag_mtu_unknown, "no-transport"),
            )
        }

        val dns = dnsCheck(host)
        val tcp = tcpCheck(host)
        val tls = tlsCheck(base)
        val response = responseCheck(base)
        val clockDiagnostic = clockCheck(response.attempt)

        listOf(
            dns.diagnostic,
            tcp,
            tls,
            response.diagnostic,
            clockDiagnostic,
            proxyDiagnostic(host),
            dualStackCheck(dns.ipv4, dns.ipv6, host),
            mtuCheck(base),
            networkDiagnostic(transport),
        ).sortedBy { it.id.ordinal }
    }

    // ---------------- 单项检查 ----------------

    private class DnsCheck(
        val diagnostic: Diagnostic,
        val ipv4: Inet4Address?,
        val ipv6: Inet6Address?,
    )

    private fun dnsCheck(host: String?): DnsCheck {
        if (host == null) {
            return DnsCheck(
                diagnostic = skipped(DiagnosticId.DNS, R.string.settings_netdiag_dns_fail, "no-base-url"),
                ipv4 = null,
                ipv6 = null,
            )
        }
        val started = System.nanoTime()
        val addresses = try {
            InetAddress.getAllByName(host).toList()
        } catch (t: Throwable) {
            val ms = elapsedMs(started)
            return DnsCheck(
                diagnostic = Diagnostic(
                    id = DiagnosticId.DNS,
                    status = DiagnosticStatus.FAIL,
                    messageRes = R.string.settings_netdiag_dns_fail,
                    formatArgs = listOf(t.simpleName()),
                    flags = setOf(DiagnosticFlag.DNS_SUSPICIOUS),
                    detail = "dns ${ms}ms err=${t.simpleName()}",
                ),
                ipv4 = null,
                ipv6 = null,
            )
        }
        val ms = elapsedMs(started)
        val v4 = addresses.filterIsInstance<Inet4Address>()
        val v6 = addresses.filterIsInstance<Inet6Address>()
        // 公网域名被解析到回环/通配地址，是 DNS 被改写最硬的证据
        val suspicious = addresses.isEmpty() ||
            addresses.all { it.isLoopbackAddress || it.isAnyLocalAddress || it.isLinkLocalAddress }
        val detail = "dns ${ms}ms v4=${v4.map { it.hostAddress }} v6=${v6.map { it.hostAddress }}"
        return DnsCheck(
            diagnostic = Diagnostic(
                id = DiagnosticId.DNS,
                status = if (suspicious) DiagnosticStatus.WARN else DiagnosticStatus.OK,
                messageRes = if (suspicious) {
                    R.string.settings_netdiag_dns_suspicious
                } else {
                    R.string.settings_netdiag_dns_ok
                },
                formatArgs = listOf("${ms}ms"),
                flags = if (suspicious) setOf(DiagnosticFlag.DNS_SUSPICIOUS) else emptySet(),
                detail = detail,
            ),
            ipv4 = v4.firstOrNull(),
            ipv6 = v6.firstOrNull(),
        )
    }

    private suspend fun tcpCheck(host: String?): Diagnostic {
        if (host == null) {
            return skipped(DiagnosticId.TCP, R.string.settings_netdiag_tcp_fail, "no-base-url")
        }
        val attempt = connectTcp(host, HTTPS_PORT, timeouts.tcpMs)
        val detail = "tcp ${attempt.elapsedMs}ms ${attempt.failure?.simpleName() ?: "ok"}"
        return when {
            attempt.ok -> Diagnostic(
                id = DiagnosticId.TCP,
                status = DiagnosticStatus.OK,
                messageRes = R.string.settings_netdiag_tcp_ok,
                formatArgs = listOf("${attempt.elapsedMs}ms"),
                flags = setOf(DiagnosticFlag.CONNECT_OK),
                detail = detail,
            )
            attempt.failure is SocketTimeoutException -> Diagnostic(
                id = DiagnosticId.TCP,
                status = DiagnosticStatus.FAIL,
                messageRes = R.string.settings_netdiag_tcp_timeout,
                flags = setOf(DiagnosticFlag.CONNECT_TIMEOUT),
                detail = detail,
            )
            attempt.failure is ConnectException -> Diagnostic(
                id = DiagnosticId.TCP,
                status = DiagnosticStatus.FAIL,
                messageRes = R.string.settings_netdiag_tcp_refused,
                flags = setOf(DiagnosticFlag.CONNECT_REFUSED),
                detail = detail,
            )
            else -> Diagnostic(
                id = DiagnosticId.TCP,
                status = DiagnosticStatus.FAIL,
                messageRes = R.string.settings_netdiag_tcp_fail,
                formatArgs = listOf(attempt.failure?.simpleName() ?: "unknown"),
                detail = detail,
            )
        }
    }

    private suspend fun tlsCheck(base: String): Diagnostic {
        if (base.isEmpty()) {
            return skipped(DiagnosticId.TLS, R.string.settings_netdiag_tls_fail, "no-base-url")
        }
        val attempt = httpsAttempt(base, method = "HEAD", useSystemProxy = true)
        val detail = "tls ${attempt.elapsedMs}ms ${attempt.failure?.simpleName() ?: "ok"}"
        if (attempt.ok) {
            return Diagnostic(
                id = DiagnosticId.TLS,
                status = DiagnosticStatus.OK,
                messageRes = R.string.settings_netdiag_tls_ok,
                formatArgs = listOf("${attempt.elapsedMs}ms"),
                detail = detail,
            )
        }
        val chain = attempt.failure?.causeChain().orEmpty()
        return when {
            chain.any { it is SSLPeerUnverifiedException } -> Diagnostic(
                id = DiagnosticId.TLS,
                status = DiagnosticStatus.FAIL,
                messageRes = R.string.settings_netdiag_tls_cert,
                flags = setOf(DiagnosticFlag.CERT_UNVERIFIED),
                detail = detail,
            )
            chain.any { it is SSLHandshakeException } -> Diagnostic(
                id = DiagnosticId.TLS,
                status = DiagnosticStatus.FAIL,
                messageRes = R.string.settings_netdiag_tls_handshake,
                formatArgs = listOf(attempt.failure?.simpleName() ?: "unknown"),
                flags = setOf(DiagnosticFlag.TLS_HANDSHAKE),
                detail = detail,
            )
            else -> Diagnostic(
                id = DiagnosticId.TLS,
                status = DiagnosticStatus.FAIL,
                messageRes = R.string.settings_netdiag_tls_fail,
                formatArgs = listOf(attempt.failure?.simpleName() ?: "unknown"),
                detail = detail,
            )
        }
    }

    private class ResponseCheck(val diagnostic: Diagnostic, val attempt: HttpAttempt)

    private suspend fun responseCheck(base: String): ResponseCheck {
        if (base.isEmpty()) {
            return ResponseCheck(
                skipped(DiagnosticId.RESPONSE, R.string.settings_netdiag_response_fail, "no-base-url"),
                HttpAttempt(ok = false, code = 0, elapsedMs = 0),
            )
        }
        val attempt = httpsAttempt(base, method = "GET", useSystemProxy = true)
        val detail = "http ${attempt.code} ${attempt.elapsedMs}ms class=" +
            ResponseClassifier.classify(attempt.code, attempt.contentType, attempt.bodyPrefix).name
        if (!attempt.ok) {
            return ResponseCheck(
                Diagnostic(
                    id = DiagnosticId.RESPONSE,
                    status = DiagnosticStatus.FAIL,
                    messageRes = R.string.settings_netdiag_response_fail,
                    formatArgs = listOf(attempt.failure?.simpleName() ?: "unknown"),
                    detail = detail,
                ),
                attempt,
            )
        }
        val code = attempt.code.toString()
        val rateLimited = attempt.code == RATE_LIMIT_STATUS
        val diagnostic = when (ResponseClassifier.classify(attempt.code, attempt.contentType, attempt.bodyPrefix)) {
            ResponseClass.JSON_OK -> Diagnostic(
                id = DiagnosticId.RESPONSE,
                status = if (rateLimited) DiagnosticStatus.WARN else DiagnosticStatus.OK,
                messageRes = R.string.settings_netdiag_response_json_ok,
                formatArgs = listOf(code),
                flags = if (rateLimited) setOf(DiagnosticFlag.RATE_LIMITED) else emptySet(),
                detail = detail,
            )
            ResponseClass.REDIRECT -> Diagnostic(
                id = DiagnosticId.RESPONSE,
                status = DiagnosticStatus.FAIL,
                messageRes = R.string.settings_netdiag_response_redirect,
                formatArgs = listOf(code),
                flags = setOf(DiagnosticFlag.PORTAL_HIJACK),
                detail = detail,
            )
            ResponseClass.HTML -> Diagnostic(
                id = DiagnosticId.RESPONSE,
                status = DiagnosticStatus.FAIL,
                messageRes = R.string.settings_netdiag_response_html,
                flags = setOf(DiagnosticFlag.PORTAL_HIJACK),
                detail = detail,
            )
            ResponseClass.NETWORK_AUTH_REQUIRED -> Diagnostic(
                id = DiagnosticId.RESPONSE,
                status = DiagnosticStatus.FAIL,
                messageRes = R.string.settings_netdiag_response_auth_required,
                flags = setOf(DiagnosticFlag.AUTH_REQUIRED_511),
                detail = detail,
            )
            ResponseClass.NON_JSON -> Diagnostic(
                id = DiagnosticId.RESPONSE,
                status = DiagnosticStatus.WARN,
                messageRes = R.string.settings_netdiag_response_non_json,
                formatArgs = listOf(code),
                flags = if (rateLimited) {
                    setOf(DiagnosticFlag.NON_JSON_BODY, DiagnosticFlag.RATE_LIMITED)
                } else {
                    setOf(DiagnosticFlag.NON_JSON_BODY)
                },
                detail = detail,
            )
        }
        return ResponseCheck(diagnostic, attempt)
    }

    /**
     * 时钟偏差：拿响应头 Date 与本机时间比。
     * 时钟偏差是 TLS 校验失败最常见的真因，所以单独成项而不是并进 TLS。
     */
    private fun clockCheck(attempt: HttpAttempt): Diagnostic {
        val header = attempt.dateHeader?.takeIf { it.isNotBlank() }
            ?: return skipped(
                DiagnosticId.CLOCK,
                R.string.settings_netdiag_clock_unknown,
                detail = "clock no-date-header",
                flag = DiagnosticFlag.CLOCK_UNKNOWN,
            )
        val serverMillis = try {
            ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
        } catch (_: Throwable) {
            return skipped(
                DiagnosticId.CLOCK,
                R.string.settings_netdiag_clock_unknown,
                detail = "clock unparsable-date",
                flag = DiagnosticFlag.CLOCK_UNKNOWN,
            )
        }
        val skewSeconds = (serverMillis - clock()) / 1000
        val drifted = kotlin.math.abs(skewSeconds) > CLOCK_SKEW_LIMIT_SECONDS
        return Diagnostic(
            id = DiagnosticId.CLOCK,
            status = if (drifted) DiagnosticStatus.WARN else DiagnosticStatus.OK,
            messageRes = if (drifted) {
                R.string.settings_netdiag_clock_drift
            } else {
                R.string.settings_netdiag_clock_ok
            },
            formatArgs = listOf(skewSeconds.toString()),
            flags = if (drifted) setOf(DiagnosticFlag.CLOCK_DRIFT) else emptySet(),
            detail = "clock skew=${skewSeconds}s",
        )
    }

    private fun proxyDiagnostic(host: String?): Diagnostic {
        val selectorProxy = try {
            host?.let { ProxySelector.getDefault()?.select(URI("https://$it/"))?.firstOrNull() }
        } catch (_: Throwable) {
            null
        }
        val selectorActive = selectorProxy != null && selectorProxy.type() != Proxy.Type.DIRECT
        val propertyHost = System.getProperty("https.proxyHost") ?: System.getProperty("http.proxyHost")
        val active = selectorActive || !propertyHost.isNullOrBlank()
        val description = when {
            selectorActive -> "proxy selector=${selectorProxy?.type()} ${selectorProxy?.address()}"
            !propertyHost.isNullOrBlank() -> "proxy property=$propertyHost"
            else -> "proxy none"
        }
        return Diagnostic(
            id = DiagnosticId.PROXY,
            status = if (active) DiagnosticStatus.WARN else DiagnosticStatus.OK,
            messageRes = if (active) R.string.settings_netdiag_proxy_active else R.string.settings_netdiag_proxy_none,
            formatArgs = if (active) listOf(description) else emptyList(),
            flags = if (active) setOf(DiagnosticFlag.PROXY_ACTIVE) else emptySet(),
            detail = description,
        )
    }

    /** 双栈：分别用 A 记录与 AAAA 记录各连一次，区分 v6 黑洞与 IPv6-only。 */
    private suspend fun dualStackCheck(ipv4: Inet4Address?, ipv6: Inet6Address?, host: String?): Diagnostic {
        if (host == null || (ipv4 == null && ipv6 == null)) {
            return skipped(DiagnosticId.DUAL_STACK, R.string.settings_netdiag_dual_both_fail, "no-address")
        }
        val v4 = ipv4?.let { connectTcp(host, HTTPS_PORT, timeouts.stackMs, address = it) }
        val v6 = ipv6?.let { connectTcp(host, HTTPS_PORT, timeouts.stackMs, address = it) }
        val detail = "dual v4=${v4?.ok ?: "none"} v6=${v6?.ok ?: "none"}"
        return when {
            ipv4 != null && ipv6 != null && v4?.ok == true && v6?.ok == true -> Diagnostic(
                id = DiagnosticId.DUAL_STACK,
                status = DiagnosticStatus.OK,
                messageRes = R.string.settings_netdiag_dual_ok,
                flags = setOf(DiagnosticFlag.IPV4_OK, DiagnosticFlag.IPV6_OK),
                detail = detail,
            )
            ipv4 != null && ipv6 != null && v4?.ok == true -> Diagnostic(
                id = DiagnosticId.DUAL_STACK,
                status = DiagnosticStatus.FAIL,
                messageRes = R.string.settings_netdiag_dual_v6_blackhole,
                flags = setOf(DiagnosticFlag.IPV4_OK, DiagnosticFlag.IPV6_FAIL),
                detail = detail,
            )
            ipv4 != null && ipv6 != null && v6?.ok == true -> Diagnostic(
                id = DiagnosticId.DUAL_STACK,
                status = DiagnosticStatus.WARN,
                messageRes = R.string.settings_netdiag_dual_ipv6_only,
                flags = setOf(DiagnosticFlag.IPV4_FAIL, DiagnosticFlag.IPV6_OK),
                detail = detail,
            )
            ipv4 != null && ipv6 == null && v4?.ok == true -> Diagnostic(
                id = DiagnosticId.DUAL_STACK,
                status = DiagnosticStatus.OK,
                messageRes = R.string.settings_netdiag_dual_no_v6,
                flags = setOf(DiagnosticFlag.IPV4_OK, DiagnosticFlag.NO_IPV6_ADDRESS),
                detail = detail,
            )
            else -> Diagnostic(
                id = DiagnosticId.DUAL_STACK,
                status = DiagnosticStatus.FAIL,
                messageRes = R.string.settings_netdiag_dual_both_fail,
                flags = setOf(DiagnosticFlag.IPV4_FAIL, DiagnosticFlag.IPV6_FAIL),
                detail = detail,
            )
        }
    }

    /**
     * MTU 黑洞：同一接口分别发 ~1400B 与 ~1200B 的定长请求体，
     * 大包被丢、小包通过就说明路径 MTU 小于本机 MSS。尽力而为，判不了就 SKIP。
     */
    private suspend fun mtuCheck(base: String): Diagnostic {
        if (base.isEmpty()) {
            return skipped(DiagnosticId.MTU, R.string.settings_netdiag_mtu_unknown, "no-base-url")
        }
        val large = httpsAttempt(base, method = "POST", useSystemProxy = true, bodyBytes = MTU_LARGE_BYTES)
        val small = httpsAttempt(base, method = "POST", useSystemProxy = true, bodyBytes = MTU_SMALL_BYTES)
        val detail = "mtu large=${large.ok} small=${small.ok}"
        return when {
            large.ok && small.ok -> Diagnostic(
                id = DiagnosticId.MTU,
                status = DiagnosticStatus.OK,
                messageRes = R.string.settings_netdiag_mtu_ok,
                detail = detail,
            )
            !large.ok && small.ok -> Diagnostic(
                id = DiagnosticId.MTU,
                status = DiagnosticStatus.FAIL,
                messageRes = R.string.settings_netdiag_mtu_blackhole,
                flags = setOf(DiagnosticFlag.MTU_BLACKHOLE),
                detail = detail,
            )
            else -> Diagnostic(
                id = DiagnosticId.MTU,
                status = DiagnosticStatus.SKIP,
                messageRes = R.string.settings_netdiag_mtu_unknown,
                flags = setOf(DiagnosticFlag.MTU_INCONCLUSIVE),
                detail = detail,
            )
        }
    }

    private fun networkDiagnostic(facts: TransportFacts): Diagnostic {
        if (!facts.connected) {
            return Diagnostic(
                id = DiagnosticId.NETWORK_TYPE,
                status = DiagnosticStatus.FAIL,
                messageRes = R.string.settings_netdiag_net_none,
                flags = setOf(DiagnosticFlag.NO_TRANSPORT),
                detail = "net none",
            )
        }
        val transportName = context.getString(
            if (facts.wifi) R.string.settings_netdiag_net_wifi else R.string.settings_netdiag_net_cellular
        )
        val meteredName = context.getString(
            if (facts.metered) R.string.settings_netdiag_net_metered else R.string.settings_netdiag_net_unmetered
        )
        return Diagnostic(
            id = DiagnosticId.NETWORK_TYPE,
            status = DiagnosticStatus.OK,
            messageRes = R.string.settings_netdiag_net_line,
            formatArgs = listOf(transportName, meteredName),
            flags = if (facts.metered) setOf(DiagnosticFlag.METERED) else emptySet(),
            detail = "net transport=${if (facts.wifi) "wifi" else "cellular"} metered=${facts.metered} " +
                "validated=${facts.validated}",
        )
    }

    // ---------------- 平台封装 ----------------

    private class TransportFacts(
        val connected: Boolean,
        val wifi: Boolean,
        val cellular: Boolean,
        val metered: Boolean,
        val validated: Boolean,
    )

    private fun transportFacts(): TransportFacts {
        val manager = context.getSystemService(ConnectivityManager::class.java)
            ?: return TransportFacts(false, false, false, true, false)
        val active = manager.activeNetwork
        if (active == null) return TransportFacts(false, false, false, true, false)
        val capabilities = manager.getNetworkCapabilities(active)
            ?: return TransportFacts(false, false, false, true, false)
        return TransportFacts(
            connected = true,
            wifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
            cellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
            metered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
        )
    }

    /** 一次 TCP 连接。指定 [address] 时按该地址直连，用于 IPv4 / IPv6 与 DoH 复测。 */
    internal suspend fun connectTcp(
        host: String,
        port: Int = HTTPS_PORT,
        timeoutMs: Int = TCP_TIMEOUT_MS,
        address: InetAddress? = null,
    ): TcpAttempt = withContext(Dispatchers.IO) {
        val started = System.nanoTime()
        var socket: Socket? = null
        try {
            val target = if (address != null) InetSocketAddress(address, port) else InetSocketAddress(host, port)
            socket = Socket()
            socket.connect(target, timeoutMs)
            TcpAttempt(ok = true, elapsedMs = elapsedMs(started))
        } catch (t: Throwable) {
            TcpAttempt(ok = false, elapsedMs = elapsedMs(started), failure = t)
        } finally {
            try {
                socket?.close()
            } catch (_: Throwable) {
            }
        }
    }

    /** 一次 HTTPS 请求。重定向不跟随：认证页的 302 本身就是结论。 */
    internal suspend fun httpsAttempt(
        url: String,
        method: String = "GET",
        useSystemProxy: Boolean = true,
        bodyBytes: Int = 0,
        connectTimeoutMs: Int = HTTP_CONNECT_TIMEOUT_MS,
        readTimeoutMs: Int = HTTP_READ_TIMEOUT_MS,
    ): HttpAttempt = withContext(Dispatchers.IO) {
        val started = System.nanoTime()
        var connection: HttpsURLConnection? = null
        try {
            val opened = if (useSystemProxy) {
                URL(url).openConnection()
            } else {
                URL(url).openConnection(Proxy.NO_PROXY)
            }
            val conn = opened as HttpsURLConnection
            connection = conn
            conn.requestMethod = method
            conn.instanceFollowRedirects = false
            conn.useCaches = false
            conn.connectTimeout = connectTimeoutMs
            conn.readTimeout = readTimeoutMs
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "WristAgent-NetDiag")
            if (bodyBytes > 0) {
                conn.doOutput = true
                conn.setFixedLengthStreamingMode(bodyBytes)
                conn.outputStream.use { it.write(ByteArray(bodyBytes)) }
            }
            val code = conn.responseCode
            val stream = if (code >= 400) conn.errorStream else conn.inputStream
            HttpAttempt(
                ok = true,
                code = code,
                elapsedMs = elapsedMs(started),
                contentType = conn.contentType,
                dateHeader = conn.getHeaderField("Date"),
                bodyPrefix = readPrefix(stream),
            )
        } catch (t: Throwable) {
            HttpAttempt(ok = false, code = 0, elapsedMs = elapsedMs(started), failure = t)
        } finally {
            try {
                connection?.disconnect()
            } catch (_: Throwable) {
            }
        }
    }

    /** DoH 解析：绕开系统 DNS，用来判断"系统解析坏了但网络本身通"。 */
    internal suspend fun dohResolve(host: String): List<String> = withContext(Dispatchers.IO) {
        val attempt = httpsAttempt(
            url = "$DOH_ENDPOINT?name=$host&type=A",
            method = "GET",
            useSystemProxy = false,
            readTimeoutMs = DOH_TIMEOUT_MS,
            connectTimeoutMs = DOH_TIMEOUT_MS,
        )
        if (!attempt.ok) return@withContext emptyList()
        DohAnswerPattern.findAll(attempt.bodyPrefix).map { it.groupValues[1] }.toList()
    }

    private fun readPrefix(stream: InputStream?, cap: Int = BODY_PREFIX_BYTES): String {
        if (stream == null) return ""
        return try {
            val buffer = ByteArray(cap)
            var read = 0
            while (read < cap) {
                val n = stream.read(buffer, read, cap - read)
                if (n <= 0) break
                read += n
            }
            String(buffer, 0, read, Charsets.UTF_8)
        } catch (_: Throwable) {
            ""
        } finally {
            try {
                stream.close()
            } catch (_: Throwable) {
            }
        }
    }

    private fun skipped(
        id: DiagnosticId,
        messageRes: Int,
        detail: String = "",
        flag: DiagnosticFlag? = null,
    ) = Diagnostic(
        id = id,
        status = DiagnosticStatus.SKIP,
        messageRes = messageRes,
        detail = detail,
        flags = flag?.let { setOf(it) } ?: emptySet(),
    )

    private fun elapsedMs(startedNanos: Long): Long = (System.nanoTime() - startedNanos) / 1_000_000

    private fun Throwable.causeChain(): List<Throwable> {
        val chain = mutableListOf<Throwable>()
        var current: Throwable? = this
        while (current != null && chain.size < MAX_CAUSE_DEPTH) {
            chain += current
            current = current.cause
        }
        return chain
    }

    private fun Throwable.simpleName(): String = this.javaClass.simpleName

    companion object {
        const val HTTPS_PORT = 443
        const val TCP_TIMEOUT_MS = 3_000
        const val STACK_TIMEOUT_MS = 2_500
        const val HTTP_CONNECT_TIMEOUT_MS = 4_000
        const val HTTP_READ_TIMEOUT_MS = 4_000
        const val DOH_TIMEOUT_MS = 5_000
        const val BODY_PREFIX_BYTES = 512
        const val MTU_LARGE_BYTES = 1_400
        const val MTU_SMALL_BYTES = 1_200
        const val RATE_LIMIT_STATUS = 429
        const val CLOCK_SKEW_LIMIT_SECONDS = 300
        const val MAX_CAUSE_DEPTH = 8
        const val DOH_ENDPOINT = "https://1.1.1.1/dns-query"

        private val DohAnswerPattern = Regex("\"data\"\\s*:\\s*\"([0-9a-fA-F.:]+)\"")
    }
}

/** 最近一次体检的结论，只有「有没有异常 + 何时」两个信息，够列表行显示。 */
data class DiagnosisSummary(val issues: Int, val ranAtMillis: Long) {
    val hasRun: Boolean get() = ranAtMillis > 0L
}

/** 最近一次结论的持久化：设置页那一行要在体检页关掉后仍然显示上次结果。 */
class NetworkDiagnosisStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(issues: Int, ranAtMillis: Long) {
        prefs.edit().putInt(KEY_ISSUES, issues).putLong(KEY_RAN_AT, ranAtMillis).apply()
    }

    /** 从未体检时返回 null。 */
    fun lastSummary(): DiagnosisSummary? {
        val ranAt = prefs.getLong(KEY_RAN_AT, 0L)
        if (ranAt <= 0L) return null
        return DiagnosisSummary(issues = prefs.getInt(KEY_ISSUES, 0), ranAtMillis = ranAt)
    }

    private companion object {
        const val PREFS_NAME = "network_diagnostics"
        const val KEY_ISSUES = "last_issues"
        const val KEY_RAN_AT = "last_ran_at"
    }
}
