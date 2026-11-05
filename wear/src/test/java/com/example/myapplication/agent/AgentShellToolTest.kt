package com.example.myapplication.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 特权工具的审批门控：run_shell 与自写工具一律走审批页，define_tool 同样必须审批，
 * 只有元数据类工具（list_tools / remove_tool）才回到通用分级闸门。
 */
class AgentShellToolTest {

    private val selfDefinedNames = setOf("disk_usage", "kill_app")

    @Test
    fun runShellAndSelfDefinedToolsArePrivileged() {
        assertTrue(ShellPolicy.isPrivilegedTool(ShellPolicy.TOOL_RUN_SHELL, emptySet()))
        assertTrue(ShellPolicy.isPrivilegedTool("disk_usage", selfDefinedNames))
        assertFalse(ShellPolicy.isPrivilegedTool("get_device_status", selfDefinedNames))
        assertFalse(ShellPolicy.isPrivilegedTool(ShellPolicy.TOOL_DEFINE_TOOL, selfDefinedNames))
    }

    @Test
    fun onlyMetadataToolsEscapeForcedApproval() {
        assertTrue(ShellPolicy.requiresApprovalAlways(ShellPolicy.TOOL_RUN_SHELL, emptySet()))
        assertTrue(ShellPolicy.requiresApprovalAlways(ShellPolicy.TOOL_DEFINE_TOOL, emptySet()))
        assertTrue(ShellPolicy.requiresApprovalAlways("kill_app", selfDefinedNames))

        assertFalse(ShellPolicy.requiresApprovalAlways(ShellPolicy.TOOL_LIST_TOOLS, selfDefinedNames))
        assertFalse(ShellPolicy.requiresApprovalAlways(ShellPolicy.TOOL_REMOVE_TOOL, selfDefinedNames))
        assertFalse(ShellPolicy.requiresApprovalAlways("vibrate_device", selfDefinedNames))
    }

    @Test
    fun rememberedCommandStillSkipsOnlyThroughAllowlist() {
        val destructive = "rm -rf /system"
        assertEquals(
            ApprovalGate.Decision.REQUIRE_APPROVAL,
            ShellPolicy.gate(ShellPolicy.dangerOf(destructive), remembered = true)
        )
        assertEquals(
            ApprovalGate.Decision.AUTO_ALLOW,
            ShellPolicy.gate(ShellPolicy.dangerOf("getprop ro.product.model"), remembered = true)
        )
    }

    @Test
    fun malformedArgumentsAreRejectedBeforeExecution() {
        assertNotNull("空命令不得进入特权层", ShellPolicy.validate("  \n  "))
        assertEquals("命令超长必须拒绝", true, ShellPolicy.validate("a".repeat(ShellPolicy.MAX_COMMAND_LENGTH + 1)) != null)
        assertEquals(null, ShellPolicy.validate("getprop ro.product.model"))
    }

    @Test
    fun timeoutIsClampedIntoExecutableRange() {
        assertEquals(ShellPolicy.DEFAULT_TIMEOUT_MS, ShellPolicy.clampTimeout(null))
        assertEquals(ShellPolicy.MIN_TIMEOUT_MS, ShellPolicy.clampTimeout(0L))
        assertEquals(ShellPolicy.MIN_TIMEOUT_MS, ShellPolicy.clampTimeout(-5L))
        assertEquals(5_000L, ShellPolicy.clampTimeout(5_000L))
        assertEquals(ShellPolicy.MAX_TIMEOUT_MS, ShellPolicy.clampTimeout(999_999L))
    }
}
