package com.example.myapplication.agent

import com.example.myapplication.readProjectFile
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 沙箱闸门的**接线**守卫：两条工具执行路径都必须真的调用 [SandboxGate]。
 *
 * 为什么只能扫源码：`AgentDispatcher` 的主循环与子代理路径都是私有方法，
 * 构造它需要网关、注册表、审批回调一大串外部依赖；而这里要钉的恰恰是"有没有接上这一行"。
 * 0.0.0.90 审查发现：`executeSubAgentTool` 根本没调 `SandboxGate.blockMessage` ——
 * 沙箱等级 READ_ONLY 时，子代理照样能跑 RISKY/DESTRUCTIVE 命令，等级形同不存在。
 *
 * 切片纪律（0.0.0.91 的教训）：先用锚点自证，再用长度上限兜住"找不到就返回整串"的退化。
 */
class SandboxWiringGuardTest {

    private val source = readProjectFile("src/main/java/com/example/myapplication/agent/AgentDispatcher.kt")

    @Test
    fun theMainLoopConsultsTheSandboxGate() {
        val body = slice("private suspend fun executeInterleavedApiLoop(", "private suspend fun executeSubAgentTool(")
        assertTrue("主循环必须调用 SandboxGate.blockMessage", body.contains("SandboxGate.blockMessage("))
        assertTrue("主循环必须把挡下的理由回给模型", body.contains("sandboxBlock"))
    }

    @Test
    fun theSubAgentPathAlsoConsultsTheSandboxGate() {
        val body = slice("private suspend fun executeSubAgentTool(", "private fun rejectedToolResult(")
        assertTrue(
            "子代理路径必须与主循环用同一道沙箱闸门（0.0.0.92 修）",
            body.contains("SandboxGate.blockMessage(preferences.sandboxLevel, ShellPolicy.dangerOf(")
        )
        // 顺序也要对：沙箱判定必须在审批闸门之前 —— 等级不允许时不该弹审批页（问人也无用）
        val sandboxIndex = body.indexOf("SandboxGate.blockMessage(")
        val approvalIndex = body.indexOf("ApprovalGate.Decision.REQUIRE_APPROVAL")
        assertTrue("沙箱判定必须在审批之前（sandbox=$sandboxIndex approval=$approvalIndex）", sandboxIndex in 0 until approvalIndex)
    }

    private fun slice(startAnchor: String, endAnchor: String): String {
        assertTrue("起始锚点缺失：$startAnchor", source.contains(startAnchor))
        assertTrue("结束锚点缺失：$endAnchor", source.contains(endAnchor))
        val start = source.indexOf(startAnchor)
        val end = source.indexOf(endAnchor)
        assertTrue("锚点顺序异常：$startAnchor 应在 $endAnchor 之前", end > start)
        val body = source.substring(start, end)
        // 上界放宽到 90%：主循环那个函数本身就有 ~40k 字符（全文 62k），
        // 这条只用来兜住"结束锚点缺失 → 一路切到文件尾"的退化，不是精确尺寸断言。
        assertTrue("切片异常（长度 ${body.length} / 全文 ${source.length}）", body.length < source.length * 9 / 10)
        return body
    }
}
