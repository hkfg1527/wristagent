package com.example.myapplication.agent.net

import com.example.myapplication.R

/** 预案编号：每条对应一个明确的触发条件。 */
enum class RepairPlanId {
    NO_NETWORK,
    AUTH_PORTAL,
    PROXY_MISCONFIG,
    DNS_POISON,
    CLOCK_DRIFT,
    TLS_MITM,
    IPV6_BLACKHOLE,
    IPV6_ONLY,
    MTU_BLACKHOLE,
    WHITELIST_BLOCKED,
    RETRY_TIMEOUT,
    UNCLASSIFIED,
    ALL_OK,
}

/**
 * 预案能落到的一个具体动作。
 * 自动类动作由 [NetworkRepairer] 真跑；非自动类只给指引。
 */
enum class RepairAction {
    OPEN_WIFI_SETTINGS,
    OPEN_CAPTIVE_PORTAL,
    RETEST_BYPASS_PROXY,
    RETEST_WITH_DOH,
    SET_AUTO_TIME_VIA_ROOT,
    OPEN_DATE_SETTINGS,
    RETEST_FORCE_IPV4,
    RETRY_WITH_BACKOFF,
    SHOW_FINAL_PROBE,
    SUGGEST_HOTSPOT,
    SUGGEST_OTHER_BASE_URL,
    SWITCH_NETWORK_REQUIRED,
    NO_AUTOMATIC_FIX,
    ;

    /** 本页能不能自动执行；false 表示只能给指引。 */
    val isAutomatic: Boolean
        get() = when (this) {
            SUGGEST_HOTSPOT, SUGGEST_OTHER_BASE_URL, SWITCH_NETWORK_REQUIRED, NO_AUTOMATIC_FIX -> false
            else -> true
        }
}

/**
 * 一条预案：触发条件（由 [NetworkRepairPlan] 判定）→ 结论文案 → 自动动作 → 修不好时的指引。
 */
data class RepairItem(
    val id: RepairPlanId,
    val priority: Int,
    val titleRes: Int,
    val conclusionRes: Int,
    val guideRes: Int,
    val actionRes: Int,
    val action: RepairAction,
) {
    val autoFixable: Boolean get() = action.isAutomatic
}

/**
 * 诊断结果 → 预案列表。纯函数：不读系统、不发请求、不碰资源，只做映射与排序。
 *
 * 排序即修复顺序：先让网络能用（无网 → 认证 → 代理），再修解析与时钟，
 * 然后是双栈/MTU 这类通道问题，最后才是重试这类软件层手段。
 */
object NetworkRepairPlan {

    const val PRIORITY_ALL_OK = 1
    const val PRIORITY_NO_NETWORK = 10
    const val PRIORITY_AUTH_PORTAL = 20
    const val PRIORITY_PROXY = 30
    const val PRIORITY_DNS = 40
    const val PRIORITY_CLOCK = 50
    const val PRIORITY_TLS_MITM = 60
    const val PRIORITY_IPV6_BLACKHOLE = 70
    const val PRIORITY_IPV6_ONLY = 80
    const val PRIORITY_MTU = 90
    const val PRIORITY_WHITELIST = 100
    const val PRIORITY_RETRY = 110
    const val PRIORITY_UNCLASSIFIED = 500

    /**
     * @param rootAvailable root 分支只影响时钟修复这一条：有 root 直接改 auto_time，
     * 没 root 降级成跳转日期设置。其余预案与 root 无关。
     */
    fun plan(diagnostics: List<Diagnostic>, rootAvailable: Boolean): List<RepairItem> {
        if (diagnostics.isEmpty()) return emptyList()

        val flags = diagnostics.flatMapTo(mutableSetOf()) { it.flags }
        val anomalies = diagnostics.filter { it.isAnomaly() }

        // 完全没网时其余检查都测不了，只报这一条，避免拿假结论骗用户
        if (DiagnosticFlag.NO_TRANSPORT in flags) {
            return listOf(
                RepairItem(
                    id = RepairPlanId.NO_NETWORK,
                    priority = PRIORITY_NO_NETWORK,
                    titleRes = R.string.settings_netdiag_plan_no_network,
                    conclusionRes = R.string.settings_netdiag_plan_no_network_why,
                    guideRes = R.string.settings_netdiag_plan_no_network_guide,
                    actionRes = R.string.settings_netdiag_action_wifi,
                    action = RepairAction.OPEN_WIFI_SETTINGS,
                )
            )
        }

        val items = mutableListOf<RepairItem>()

        if (DiagnosticFlag.PORTAL_HIJACK in flags || DiagnosticFlag.AUTH_REQUIRED_511 in flags) {
            items += RepairItem(
                id = RepairPlanId.AUTH_PORTAL,
                priority = PRIORITY_AUTH_PORTAL,
                titleRes = R.string.settings_netdiag_plan_portal,
                conclusionRes = R.string.settings_netdiag_plan_portal_why,
                guideRes = R.string.settings_netdiag_plan_portal_guide,
                actionRes = R.string.settings_netdiag_action_portal,
                action = RepairAction.OPEN_CAPTIVE_PORTAL,
            )
        }

        // 代理本身不是故障，只有"设置了代理且网络确实不通"才值得绕过去复测
        if (DiagnosticFlag.PROXY_ACTIVE in flags &&
            anomalies.any { it.id == DiagnosticId.DNS || it.id == DiagnosticId.TCP || it.id == DiagnosticId.RESPONSE }
        ) {
            items += RepairItem(
                id = RepairPlanId.PROXY_MISCONFIG,
                priority = PRIORITY_PROXY,
                titleRes = R.string.settings_netdiag_plan_proxy,
                conclusionRes = R.string.settings_netdiag_plan_proxy_why,
                guideRes = R.string.settings_netdiag_plan_proxy_guide,
                actionRes = R.string.settings_netdiag_action_bypass_proxy,
                action = RepairAction.RETEST_BYPASS_PROXY,
            )
        }

        if (DiagnosticFlag.DNS_SUSPICIOUS in flags ||
            diagnostics.any { it.id == DiagnosticId.DNS && it.status == DiagnosticStatus.FAIL }
        ) {
            items += RepairItem(
                id = RepairPlanId.DNS_POISON,
                priority = PRIORITY_DNS,
                titleRes = R.string.settings_netdiag_plan_dns,
                conclusionRes = R.string.settings_netdiag_plan_dns_why,
                guideRes = R.string.settings_netdiag_plan_dns_guide,
                actionRes = R.string.settings_netdiag_action_doh,
                action = RepairAction.RETEST_WITH_DOH,
            )
        }

        if (DiagnosticFlag.CLOCK_DRIFT in flags) {
            items += if (rootAvailable) {
                RepairItem(
                    id = RepairPlanId.CLOCK_DRIFT,
                    priority = PRIORITY_CLOCK,
                    titleRes = R.string.settings_netdiag_plan_clock,
                    conclusionRes = R.string.settings_netdiag_plan_clock_why,
                    guideRes = R.string.settings_netdiag_plan_clock_guide,
                    actionRes = R.string.settings_netdiag_action_clock_root,
                    action = RepairAction.SET_AUTO_TIME_VIA_ROOT,
                )
            } else {
                RepairItem(
                    id = RepairPlanId.CLOCK_DRIFT,
                    priority = PRIORITY_CLOCK,
                    titleRes = R.string.settings_netdiag_plan_clock,
                    conclusionRes = R.string.settings_netdiag_plan_clock_why,
                    guideRes = R.string.settings_netdiag_plan_clock_guide,
                    actionRes = R.string.settings_netdiag_action_clock_manual,
                    action = RepairAction.OPEN_DATE_SETTINGS,
                )
            }
        }

        // 证书不被信任有两种形态，都按中间人处理：本页绝不提供"继续"按钮
        if (DiagnosticFlag.CERT_UNVERIFIED in flags || DiagnosticFlag.TLS_HANDSHAKE in flags) {
            items += RepairItem(
                id = RepairPlanId.TLS_MITM,
                priority = PRIORITY_TLS_MITM,
                titleRes = R.string.settings_netdiag_plan_mitm,
                conclusionRes = R.string.settings_netdiag_plan_mitm_why,
                guideRes = R.string.settings_netdiag_plan_mitm_guide,
                actionRes = R.string.settings_netdiag_action_switch_network,
                action = RepairAction.SWITCH_NETWORK_REQUIRED,
            )
        }

        if (DiagnosticFlag.IPV4_OK in flags && DiagnosticFlag.IPV6_FAIL in flags) {
            items += RepairItem(
                id = RepairPlanId.IPV6_BLACKHOLE,
                priority = PRIORITY_IPV6_BLACKHOLE,
                titleRes = R.string.settings_netdiag_plan_v6_blackhole,
                conclusionRes = R.string.settings_netdiag_plan_v6_blackhole_why,
                guideRes = R.string.settings_netdiag_plan_v6_blackhole_guide,
                actionRes = R.string.settings_netdiag_action_force_v4,
                action = RepairAction.RETEST_FORCE_IPV4,
            )
        }

        if (DiagnosticFlag.IPV4_FAIL in flags && DiagnosticFlag.IPV6_OK in flags) {
            items += RepairItem(
                id = RepairPlanId.IPV6_ONLY,
                priority = PRIORITY_IPV6_ONLY,
                titleRes = R.string.settings_netdiag_plan_v6_only,
                conclusionRes = R.string.settings_netdiag_plan_v6_only_why,
                guideRes = R.string.settings_netdiag_plan_v6_only_guide,
                actionRes = R.string.settings_netdiag_action_hotspot,
                action = RepairAction.SUGGEST_HOTSPOT,
            )
        }

        if (DiagnosticFlag.MTU_BLACKHOLE in flags) {
            items += RepairItem(
                id = RepairPlanId.MTU_BLACKHOLE,
                priority = PRIORITY_MTU,
                titleRes = R.string.settings_netdiag_plan_mtu,
                conclusionRes = R.string.settings_netdiag_plan_mtu_why,
                guideRes = R.string.settings_netdiag_plan_mtu_guide,
                actionRes = R.string.settings_netdiag_action_hotspot,
                action = RepairAction.SUGGEST_HOTSPOT,
            )
        }

        if (DiagnosticFlag.NON_JSON_BODY in flags) {
            items += RepairItem(
                id = RepairPlanId.WHITELIST_BLOCKED,
                priority = PRIORITY_WHITELIST,
                titleRes = R.string.settings_netdiag_plan_whitelist,
                conclusionRes = R.string.settings_netdiag_plan_whitelist_why,
                guideRes = R.string.settings_netdiag_plan_whitelist_guide,
                actionRes = R.string.settings_netdiag_action_other_url,
                action = RepairAction.SUGGEST_OTHER_BASE_URL,
            )
        }

        if (DiagnosticFlag.CONNECT_TIMEOUT in flags || DiagnosticFlag.RATE_LIMITED in flags) {
            items += RepairItem(
                id = RepairPlanId.RETRY_TIMEOUT,
                priority = PRIORITY_RETRY,
                titleRes = R.string.settings_netdiag_plan_retry,
                conclusionRes = R.string.settings_netdiag_plan_retry_why,
                guideRes = R.string.settings_netdiag_plan_retry_guide,
                actionRes = R.string.settings_netdiag_action_retry,
                action = RepairAction.RETRY_WITH_BACKOFF,
            )
        }

        if (items.isNotEmpty()) return items.sortedBy { it.priority }

        // 一条都没匹配上：要么真的全通，要么异常还没归类，都不能假装没事
        return if (anomalies.isEmpty()) {
            listOf(
                RepairItem(
                    id = RepairPlanId.ALL_OK,
                    priority = PRIORITY_ALL_OK,
                    titleRes = R.string.settings_netdiag_plan_all_ok,
                    conclusionRes = R.string.settings_netdiag_plan_all_ok_why,
                    guideRes = R.string.settings_netdiag_plan_all_ok_guide,
                    actionRes = R.string.settings_netdiag_action_probe,
                    action = RepairAction.SHOW_FINAL_PROBE,
                )
            )
        } else {
            listOf(
                RepairItem(
                    id = RepairPlanId.UNCLASSIFIED,
                    priority = PRIORITY_UNCLASSIFIED,
                    titleRes = R.string.settings_netdiag_plan_unknown,
                    conclusionRes = R.string.settings_netdiag_plan_unknown_why,
                    guideRes = R.string.settings_netdiag_plan_unknown_guide,
                    actionRes = R.string.settings_netdiag_action_none,
                    action = RepairAction.NO_AUTOMATIC_FIX,
                )
            )
        }
    }

    /**
     * 系统代理只是标记、网络类型只描述现状，两者不该单独立案；
     * 其余 WARN / FAIL 都算异常。SKIP 是本机测不了，也不算。
     */
    private fun Diagnostic.isAnomaly(): Boolean = when (status) {
        DiagnosticStatus.OK, DiagnosticStatus.SKIP -> false
        DiagnosticStatus.WARN, DiagnosticStatus.FAIL -> id != DiagnosticId.PROXY && id != DiagnosticId.NETWORK_TYPE
    }
}
