package com.example.myapplication.agent

import com.example.myapplication.agent.contract.SecurityMode
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 子代理模式的纯逻辑约束：只有「专家模式 + 开关打开」才生效，非专家模式下整条工具都不出现，
 * 且子代理里不允许再开子代理。
 */
class SubAgentPolicyTest {

    private fun registryTool(name: String) = ToolDefinition(
        name = name,
        description = "内置工具",
        parametersSchema = JSONObject()
    )

    @Test
    fun expertModeWithSwitchEnabledIsEffective() {
        assertTrue(SubAgentPolicy.isEffective(SecurityMode.EXPERT, true))
    }

    @Test
    fun expertModeWithSwitchDisabledIsNotEffective() {
        assertFalse(SubAgentPolicy.isEffective(SecurityMode.EXPERT, false))
    }

    @Test
    fun standardModeIgnoresTheSwitch() {
        assertFalse("开关可留但不生效", SubAgentPolicy.isEffective(SecurityMode.STANDARD, true))
        assertFalse(SubAgentPolicy.isEffective(SecurityMode.STANDARD, false))
    }

    @Test
    fun noviceModeIgnoresTheSwitch() {
        assertFalse("开关可留但不生效", SubAgentPolicy.isEffective(SecurityMode.NOVICE, true))
        assertFalse(SubAgentPolicy.isEffective(SecurityMode.NOVICE, false))
    }

    @Test
    fun depthAllowsOnlyTheFirstLevel() {
        assertEquals(1, SubAgentPolicy.MAX_DEPTH)
        assertTrue("主循环在 depth 0，可以下钻一层", SubAgentPolicy.depthAllowed(0))
        assertFalse("子代理在 depth 1，不能再开子代理", SubAgentPolicy.depthAllowed(1))
        assertFalse(SubAgentPolicy.depthAllowed(2))
    }

    @Test
    fun nonExpertToolListHasNoRunSubagent() {
        val base = listOf(registryTool("get_device_status"), registryTool("web_search"))
        for (mode in listOf(SecurityMode.STANDARD, SecurityMode.NOVICE)) {
            val names = (base + SubAgentPolicy.toolDefinitions(mode, true)).map { it.name }
            assertEquals("$mode 下工具清单必须保持原样", base.map { it.name }, names)
            assertFalse("$mode 下 run_subagent 必须整条不出现", SubAgentPolicy.TOOL_NAME in names)
        }
        assertFalse(
            "专家模式但开关关闭时同样不出现",
            SubAgentPolicy.TOOL_NAME in SubAgentPolicy.toolDefinitions(SecurityMode.EXPERT, false).map { it.name }
        )
    }

    @Test
    fun expertWithSwitchExposesRunSubagentExactlyOnce() {
        val defs = SubAgentPolicy.toolDefinitions(SecurityMode.EXPERT, true)
        assertEquals(1, defs.size)
        assertEquals(SubAgentPolicy.TOOL_NAME, defs.single().name)
        val merged = listOf(registryTool("get_device_status")) + defs
        assertEquals(
            listOf("get_device_status", SubAgentPolicy.TOOL_NAME),
            merged.map { it.name }
        )
    }
}
