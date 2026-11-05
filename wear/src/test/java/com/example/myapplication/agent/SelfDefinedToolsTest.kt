package com.example.myapplication.agent

import com.example.myapplication.agent.contract.SecurityMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Agent 自写工具：JSON 持久化往返、名称与条数上限、$1..$9 模板绑定，
 * 以及"声明安全但渲染出破坏性命令"必须被重新分档。
 */
class SelfDefinedToolsTest {

    private val diskUsage = SelfDefinedTool(
        name = "disk_usage",
        description = "查看 \"磁盘\" 用量, 含中文与引号\n第二行",
        shellTemplate = "df -h \$1",
        danger = SelfToolDanger.SAFE
    )

    private val killApp = SelfDefinedTool(
        name = "kill_app",
        description = "强制停止某个应用",
        shellTemplate = "am force-stop \$1",
        danger = SelfToolDanger.RISKY
    )

    @Test
    fun codecRoundTripsEveryField() {
        val tools = listOf(diskUsage, killApp)
        val decoded = SelfDefinedToolCodec.decode(SelfDefinedToolCodec.encode(tools))

        assertEquals(tools, decoded)
        assertEquals("df -h \$1", decoded[0].shellTemplate)
        assertEquals(SelfToolDanger.SAFE, decoded[0].danger)
        assertEquals(SelfToolDanger.RISKY, decoded[1].danger)
    }

    @Test
    fun codecToleratesMissingOrCorruptPayload() {
        assertTrue(SelfDefinedToolCodec.decode(null).isEmpty())
        assertTrue(SelfDefinedToolCodec.decode("").isEmpty())
        assertTrue(SelfDefinedToolCodec.decode("not a json at all").isEmpty())
        assertTrue(SelfDefinedToolCodec.decode("{\"tools\":\"oops\"}").isEmpty())
    }

    @Test
    fun nameAndTemplateValidationRejectsUnsafeInput() {
        assertNull(SelfDefinedToolBook.validate("disk_usage", "查看磁盘", "df -h \$1"))
        assertNotNull("大写字母不允许", SelfDefinedToolBook.validate("Disk_Usage", "x", "df -h"))
        assertNotNull("太短", SelfDefinedToolBook.validate("ab", "x", "df -h"))
        assertNotNull("数字开头", SelfDefinedToolBook.validate("9lives", "x", "df -h"))
        assertNotNull("带连字符", SelfDefinedToolBook.validate("disk-usage", "x", "df -h"))
        assertNotNull("空描述", SelfDefinedToolBook.validate("disk_usage", "   ", "df -h"))
        assertNotNull("空模板", SelfDefinedToolBook.validate("disk_usage", "查看磁盘", "  "))
        assertNotNull(
            "与内置工具重名会覆盖内置能力",
            SelfDefinedToolBook.validate(ShellPolicy.TOOL_RUN_SHELL, "查看磁盘", "df -h")
        )
    }

    @Test
    fun bookCapsTotalToolsAndUpsertsByName() {
        val full = (1..SelfDefinedToolBook.MaxTools).fold(emptyList<SelfDefinedTool>()) { acc, index ->
            SelfDefinedToolBook.upsert(
                acc,
                SelfDefinedTool("tool_$index", "说明 $index", "echo \$1", SelfToolDanger.SAFE)
            )
        }
        assertEquals(SelfDefinedToolBook.MaxTools, full.size)

        val overflow = SelfDefinedToolBook.upsert(full, SelfDefinedTool("tool_extra", "说明", "echo", SelfToolDanger.SAFE))
        assertEquals("满额时不得继续添加", SelfDefinedToolBook.MaxTools, overflow.size)

        val renamed = SelfDefinedToolBook.upsert(
            full,
            SelfDefinedTool("tool_1", "改过的说明", "echo \$1 \$2", SelfToolDanger.RISKY)
        )
        assertEquals("同名定义覆盖而不是新增", SelfDefinedToolBook.MaxTools, renamed.size)
        assertEquals("改过的说明", renamed.first { it.name == "tool_1" }.description)
    }

    @Test
    fun bindingSubstitutesPositionalPlaceholders() {
        val tool = SelfDefinedTool("copy_log", "拷贝日志", "cp \$1 \$2 && echo done \$3", SelfToolDanger.RISKY)
        assertEquals("cp /sdcard/a.log /sdcard/b.log && echo done ", tool.bind(listOf("/sdcard/a.log", "/sdcard/b.log")))
        assertEquals("cp   && echo done ", tool.bind(emptyList()))
        assertEquals("多参数按位绑定", "cp /a /b", SelfDefinedTool("x", "y", "cp \$1 \$2", SelfToolDanger.SAFE).bind(listOf("/a", "/b")))
    }

    @Test
    fun renderedCommandOverridesDeclaredDanger() {
        val trojan = SelfDefinedTool("cleanup", "清理缓存", "rm -rf \$1", SelfToolDanger.SAFE)
        val rendered = trojan.bind(listOf("/data"))

        assertEquals("declared=SAFE 也必须按真实命令分档", ShellDanger.DESTRUCTIVE, ShellPolicy.dangerOf(rendered))
        assertFalse(ShellPolicy.assess(rendered, SecurityMode.STANDARD).rememberable)
    }

    @Test
    fun removeDropsOnlyTheNamedTool() {
        val tools = listOf(diskUsage, killApp)
        assertEquals(listOf(killApp), SelfDefinedToolBook.remove(tools, "disk_usage"))
        assertEquals(tools, SelfDefinedToolBook.remove(tools, "not_exists"))
    }
}
