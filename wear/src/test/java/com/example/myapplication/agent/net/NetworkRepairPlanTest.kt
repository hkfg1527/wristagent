package com.example.myapplication.agent.net

import com.example.myapplication.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 预案映射的纯逻辑测试：触发条件、组合优先级、root 双分支、不可自动修复项的降级。
 * 断言全部直接对齐产品规格，不迁就实现。
 */
class NetworkRepairPlanTest {

    private fun diag(
        id: DiagnosticId,
        status: DiagnosticStatus,
        vararg flags: DiagnosticFlag,
    ) = Diagnostic(id = id, status = status, messageRes = 0, flags = flags.toSet())

    private fun allOk(): List<Diagnostic> = DiagnosticId.entries.map {
        diag(it, DiagnosticStatus.OK)
    }

    private fun ids(plans: List<RepairItem>) = plans.map { it.id }

    @Test
    fun emptyDiagnosticsProduceNoPlans() {
        assertTrue(NetworkRepairPlan.plan(emptyList(), rootAvailable = false).isEmpty())
    }

    @Test
    fun everyCheckPassingProducesSingleAllOkPlan() {
        val plans = NetworkRepairPlan.plan(allOk(), rootAvailable = false)

        assertEquals(listOf(RepairPlanId.ALL_OK), ids(plans))
        assertEquals(RepairAction.SHOW_FINAL_PROBE, plans.single().action)
        assertTrue(plans.single().autoFixable)
        assertEquals(1, plans[0].priority)
        assertEquals(R.string.settings_netdiag_plan_all_ok, plans.single().titleRes)
        assertEquals(R.string.settings_netdiag_action_probe, plans.single().actionRes)
    }

    @Test
    fun skippedChecksCountAsPassing() {
        val plans = NetworkRepairPlan.plan(
            listOf(diag(DiagnosticId.MTU, DiagnosticStatus.SKIP)),
            rootAvailable = false,
        )

        assertEquals(listOf(RepairPlanId.ALL_OK), ids(plans))
    }

    @Test
    fun htmlPortalPageMapsToCaptivePortalPlan() {
        val plans = NetworkRepairPlan.plan(
            listOf(diag(DiagnosticId.RESPONSE, DiagnosticStatus.FAIL, DiagnosticFlag.PORTAL_HIJACK)),
            rootAvailable = false,
        )

        val portal = plans.first()
        assertEquals(RepairPlanId.AUTH_PORTAL, portal.id)
        assertEquals(RepairAction.OPEN_CAPTIVE_PORTAL, portal.action)
        assertTrue(portal.autoFixable)
        assertEquals(R.string.settings_netdiag_plan_portal, portal.titleRes)
        assertEquals(R.string.settings_netdiag_action_portal, portal.actionRes)
    }

    @Test
    fun http511AlsoMapsToCaptivePortalPlan() {
        val plans = NetworkRepairPlan.plan(
            listOf(diag(DiagnosticId.RESPONSE, DiagnosticStatus.FAIL, DiagnosticFlag.AUTH_REQUIRED_511)),
            rootAvailable = false,
        )

        assertEquals(listOf(RepairPlanId.AUTH_PORTAL), ids(plans))
    }

    @Test
    fun dnsFailureMapsToDohRetest() {
        val plans = NetworkRepairPlan.plan(
            listOf(diag(DiagnosticId.DNS, DiagnosticStatus.FAIL, DiagnosticFlag.DNS_SUSPICIOUS)),
            rootAvailable = false,
        )

        val plan = plans.single()
        assertEquals(RepairPlanId.DNS_POISON, plan.id)
        assertEquals(RepairAction.RETEST_WITH_DOH, plan.action)
        assertTrue(plan.autoFixable)
    }

    @Test
    fun ipv6BlackholeForcesIpv4Retest() {
        val plans = NetworkRepairPlan.plan(
            listOf(
                diag(
                    DiagnosticId.DUAL_STACK,
                    DiagnosticStatus.FAIL,
                    DiagnosticFlag.IPV4_OK,
                    DiagnosticFlag.IPV6_FAIL,
                )
            ),
            rootAvailable = false,
        )

        assertEquals(RepairPlanId.IPV6_BLACKHOLE, plans.single().id)
        assertEquals(RepairAction.RETEST_FORCE_IPV4, plans.single().action)
        assertTrue(plans.single().autoFixable)
    }

    @Test
    fun ipv6OnlyNetworkIsNotAutoFixable() {
        val plans = NetworkRepairPlan.plan(
            listOf(
                diag(
                    DiagnosticId.DUAL_STACK,
                    DiagnosticStatus.WARN,
                    DiagnosticFlag.IPV4_FAIL,
                    DiagnosticFlag.IPV6_OK,
                )
            ),
            rootAvailable = false,
        )

        val plan = plans.single()
        assertEquals(RepairPlanId.IPV6_ONLY, plan.id)
        assertEquals(RepairAction.SUGGEST_HOTSPOT, plan.action)
        assertFalse(plan.autoFixable)
        assertEquals(R.string.settings_netdiag_action_hotspot, plan.actionRes)
    }

    @Test
    fun clockDriftWithRootRunsAutoTimeCommand() {
        val plans = NetworkRepairPlan.plan(
            listOf(diag(DiagnosticId.CLOCK, DiagnosticStatus.WARN, DiagnosticFlag.CLOCK_DRIFT)),
            rootAvailable = true,
        )

        val plan = plans.single()
        assertEquals(RepairPlanId.CLOCK_DRIFT, plan.id)
        assertEquals(RepairAction.SET_AUTO_TIME_VIA_ROOT, plan.action)
        assertTrue(plan.autoFixable)
        assertEquals(R.string.settings_netdiag_action_clock_root, plan.actionRes)
    }

    @Test
    fun clockDriftWithoutRootFallsBackToDateSettings() {
        val plans = NetworkRepairPlan.plan(
            listOf(diag(DiagnosticId.CLOCK, DiagnosticStatus.WARN, DiagnosticFlag.CLOCK_DRIFT)),
            rootAvailable = false,
        )

        val plan = plans.single()
        assertEquals(RepairPlanId.CLOCK_DRIFT, plan.id)
        assertEquals(RepairAction.OPEN_DATE_SETTINGS, plan.action)
        assertEquals(R.string.settings_netdiag_action_clock_manual, plan.actionRes)
    }

    @Test
    fun rootAvailabilityOnlyChangesTheClockBranch() {
        val diagnostics = listOf(
            diag(DiagnosticId.RESPONSE, DiagnosticStatus.FAIL, DiagnosticFlag.PORTAL_HIJACK),
            diag(DiagnosticId.CLOCK, DiagnosticStatus.WARN, DiagnosticFlag.CLOCK_DRIFT),
        )

        val withRoot = NetworkRepairPlan.plan(diagnostics, rootAvailable = true)
        val withoutRoot = NetworkRepairPlan.plan(diagnostics, rootAvailable = false)

        assertEquals(ids(withRoot), ids(withoutRoot))
        val rootActions = withRoot.associate { it.id to it.action }
        val directActions = withoutRoot.associate { it.id to it.action }
        assertEquals(RepairAction.OPEN_CAPTIVE_PORTAL, rootActions[RepairPlanId.AUTH_PORTAL])
        assertEquals(RepairAction.OPEN_CAPTIVE_PORTAL, directActions[RepairPlanId.AUTH_PORTAL])
        assertEquals(RepairAction.SET_AUTO_TIME_VIA_ROOT, rootActions[RepairPlanId.CLOCK_DRIFT])
        assertEquals(RepairAction.OPEN_DATE_SETTINGS, directActions[RepairPlanId.CLOCK_DRIFT])
    }

    @Test
    fun untrustedCertificateIsReportedButNeverAutoFixed() {
        val plans = NetworkRepairPlan.plan(
            listOf(diag(DiagnosticId.TLS, DiagnosticStatus.FAIL, DiagnosticFlag.CERT_UNVERIFIED)),
            rootAvailable = true,
        )

        val plan = plans.single()
        assertEquals(RepairPlanId.TLS_MITM, plan.id)
        assertEquals(RepairAction.SWITCH_NETWORK_REQUIRED, plan.action)
        assertFalse(plan.autoFixable)
        assertEquals(R.string.settings_netdiag_plan_mitm_guide, plan.guideRes)
    }

    @Test
    fun mtuBlackholeIsNotAutoFixed() {
        val plans = NetworkRepairPlan.plan(
            listOf(diag(DiagnosticId.MTU, DiagnosticStatus.FAIL, DiagnosticFlag.MTU_BLACKHOLE)),
            rootAvailable = true,
        )

        val plan = plans.single()
        assertEquals(RepairPlanId.MTU_BLACKHOLE, plan.id)
        assertFalse(plan.autoFixable)
    }

    @Test
    fun connectTimeoutMapsToBackoffRetry() {
        val plans = NetworkRepairPlan.plan(
            listOf(diag(DiagnosticId.TCP, DiagnosticStatus.FAIL, DiagnosticFlag.CONNECT_TIMEOUT)),
            rootAvailable = false,
        )

        val plan = plans.single()
        assertEquals(RepairPlanId.RETRY_TIMEOUT, plan.id)
        assertEquals(RepairAction.RETRY_WITH_BACKOFF, plan.action)
        assertTrue(plan.autoFixable)
    }

    @Test
    fun throttledResponseAlsoMapsToBackoffRetry() {
        val plans = NetworkRepairPlan.plan(
            listOf(diag(DiagnosticId.RESPONSE, DiagnosticStatus.WARN, DiagnosticFlag.RATE_LIMITED)),
            rootAvailable = false,
        )

        assertEquals(listOf(RepairPlanId.RETRY_TIMEOUT), ids(plans))
    }

    @Test
    fun nonJsonBodyMapsToGatewayWhitelistHint() {
        val plans = NetworkRepairPlan.plan(
            listOf(diag(DiagnosticId.RESPONSE, DiagnosticStatus.WARN, DiagnosticFlag.NON_JSON_BODY)),
            rootAvailable = false,
        )

        val plan = plans.single()
        assertEquals(RepairPlanId.WHITELIST_BLOCKED, plan.id)
        assertEquals(RepairAction.SUGGEST_OTHER_BASE_URL, plan.action)
        assertFalse(plan.autoFixable)
    }

    @Test
    fun proxyOnlyMattersWhenTheNetworkIsActuallyBroken() {
        val healthy = NetworkRepairPlan.plan(
            listOf(diag(DiagnosticId.PROXY, DiagnosticStatus.WARN, DiagnosticFlag.PROXY_ACTIVE)) + allOk(),
            rootAvailable = false,
        )
        assertEquals(listOf(RepairPlanId.ALL_OK), ids(healthy))

        val broken = NetworkRepairPlan.plan(
            listOf(
                diag(DiagnosticId.PROXY, DiagnosticStatus.WARN, DiagnosticFlag.PROXY_ACTIVE),
                diag(DiagnosticId.TCP, DiagnosticStatus.FAIL, DiagnosticFlag.CONNECT_REFUSED),
            ),
            rootAvailable = false,
        )
        assertEquals(RepairPlanId.PROXY_MISCONFIG, broken.first().id)
        assertEquals(RepairAction.RETEST_BYPASS_PROXY, broken.first().action)
    }

    @Test
    fun totalOutageSuppressesEveryOtherPlan() {
        val plans = NetworkRepairPlan.plan(
            listOf(
                diag(DiagnosticId.NETWORK_TYPE, DiagnosticStatus.FAIL, DiagnosticFlag.NO_TRANSPORT),
                diag(DiagnosticId.DNS, DiagnosticStatus.FAIL, DiagnosticFlag.DNS_SUSPICIOUS),
                diag(DiagnosticId.TCP, DiagnosticStatus.FAIL, DiagnosticFlag.CONNECT_TIMEOUT),
            ),
            rootAvailable = false,
        )

        assertEquals(listOf(RepairPlanId.NO_NETWORK), ids(plans))
        assertEquals(RepairAction.OPEN_WIFI_SETTINGS, plans.single().action)
        assertTrue(plans.single().autoFixable)
    }

    @Test
    fun coexistingFaultsAreOrderedByBlastRadius() {
        val plans = NetworkRepairPlan.plan(
            listOf(
                diag(DiagnosticId.NETWORK_TYPE, DiagnosticStatus.OK, DiagnosticFlag.METERED),
                diag(DiagnosticId.DNS, DiagnosticStatus.FAIL, DiagnosticFlag.DNS_SUSPICIOUS),
                diag(DiagnosticId.TCP, DiagnosticStatus.FAIL, DiagnosticFlag.CONNECT_TIMEOUT),
                diag(DiagnosticId.TLS, DiagnosticStatus.FAIL, DiagnosticFlag.CERT_UNVERIFIED),
                diag(DiagnosticId.RESPONSE, DiagnosticStatus.FAIL, DiagnosticFlag.PORTAL_HIJACK),
                diag(DiagnosticId.CLOCK, DiagnosticStatus.WARN, DiagnosticFlag.CLOCK_DRIFT),
                diag(DiagnosticId.PROXY, DiagnosticStatus.WARN, DiagnosticFlag.PROXY_ACTIVE),
                diag(
                    DiagnosticId.DUAL_STACK,
                    DiagnosticStatus.FAIL,
                    DiagnosticFlag.IPV4_OK,
                    DiagnosticFlag.IPV6_FAIL,
                ),
                diag(DiagnosticId.MTU, DiagnosticStatus.FAIL, DiagnosticFlag.MTU_BLACKHOLE),
            ),
            rootAvailable = false,
        )

        assertEquals(
            listOf(
                RepairPlanId.AUTH_PORTAL,
                RepairPlanId.PROXY_MISCONFIG,
                RepairPlanId.DNS_POISON,
                RepairPlanId.CLOCK_DRIFT,
                RepairPlanId.TLS_MITM,
                RepairPlanId.IPV6_BLACKHOLE,
                RepairPlanId.MTU_BLACKHOLE,
                RepairPlanId.RETRY_TIMEOUT,
            ),
            ids(plans),
        )
        assertEquals(plans.map { it.priority }.sorted(), plans.map { it.priority })
        assertEquals(plans.map { it.priority }.toSet().size, plans.size)
    }

    @Test
    fun unmappedFailureFallsBackToUnclassifiedPlan() {
        val plans = NetworkRepairPlan.plan(
            listOf(diag(DiagnosticId.TCP, DiagnosticStatus.FAIL)),
            rootAvailable = false,
        )

        val plan = plans.single()
        assertEquals(RepairPlanId.UNCLASSIFIED, plan.id)
        assertEquals(RepairAction.NO_AUTOMATIC_FIX, plan.action)
        assertFalse(plan.autoFixable)
        assertEquals(R.string.settings_netdiag_plan_unknown_guide, plan.guideRes)
    }

    @Test
    fun everyPlanCarriesVisibleTextForAllFields() {
        val plans = NetworkRepairPlan.plan(
            listOf(
                diag(DiagnosticId.RESPONSE, DiagnosticStatus.FAIL, DiagnosticFlag.PORTAL_HIJACK),
                diag(DiagnosticId.DUAL_STACK, DiagnosticStatus.FAIL, DiagnosticFlag.IPV4_OK, DiagnosticFlag.IPV6_FAIL),
                diag(DiagnosticId.MTU, DiagnosticStatus.FAIL, DiagnosticFlag.MTU_BLACKHOLE),
            ),
            rootAvailable = false,
        )

        assertTrue(plans.size >= 3)
        plans.forEach { item ->
            assertTrue("titleRes of ${item.id}", item.titleRes != 0)
            assertTrue("conclusionRes of ${item.id}", item.conclusionRes != 0)
            assertTrue("guideRes of ${item.id}", item.guideRes != 0)
            assertTrue("actionRes of ${item.id}", item.actionRes != 0)
        }
    }
}
