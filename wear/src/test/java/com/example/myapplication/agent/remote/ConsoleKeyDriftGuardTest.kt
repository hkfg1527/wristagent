package com.example.myapplication.agent.remote

import com.example.myapplication.readProjectFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 网页端的 `SERVER_KEYS` 必须**恰好等于**服务端可写键 + 契约别名。
 *
 * 为什么要这条：网页在提交前会按自己那份清单剔除"服务端不认识"的键。
 * 两份清单一旦漂移，用户点了保存、服务端却什么都没收到 —— 界面上还显示"已保存"。
 * 这是最典型的静默失效，靠手测几乎发现不了。
 */
class ConsoleKeyDriftGuardTest {

    @Test
    fun theWebClientKnowsExactlyTheWritableKeysPlusAliases() {
        val fields = readProjectFile("src/main/java/com/example/myapplication/agent/remote/ConsoleFields.kt")

        // 1) 服务端可写键：从每个 ConsoleField(...) 里取第一个字符串，跳过 readOnly = true 的
        val writable = fields.split("ConsoleField(").drop(1)
            .filterNot { it.take(160).contains("readOnly = true") }
            .mapNotNull { Regex("\"([A-Za-z0-9_]+)\"").find(it)?.groupValues?.get(1) }
            .toSet()

        // 2) 契约别名：只在 ALIASES 块里找 "alias" to "canonical"
        val aliasesBlock = Regex("val ALIASES: Map<String, String> = linkedMapOf\\(([\\s\\S]*?)\\)")
            .find(fields)?.groupValues?.get(1).orEmpty()
        val aliases = Regex("\"([A-Za-z0-9_]+)\" to").findAll(aliasesBlock)
            .map { it.groupValues[1] }.toSet()

        // 3) 网页端清单
        val js = readProjectFile("src/main/assets/console/app.js")
        val serverKeysBlock = Regex("var SERVER_KEYS = \\[([\\s\\S]*?)\\];")
            .find(js)?.groupValues?.get(1)
            ?: error("app.js 里找不到 SERVER_KEYS")
        val jsKeys = Regex("'([A-Za-z0-9_]+)'").findAll(serverKeysBlock)
            .map { it.groupValues[1] }.toSet()

        assertTrue("服务端可写键不该为空", writable.isNotEmpty())
        assertTrue("别名不该为空", aliases.isNotEmpty())

        val expected = (writable + aliases).sorted()
        val actual = jsKeys.sorted()
        assertEquals(
            "网页 SERVER_KEYS 与服务端可写键 + 别名不一致。" +
                "多出来的（网页以为能写、服务端不认）：" + (jsKeys - writable - aliases) +
                "；漏掉的（服务端能写、网页会剔除）：" + ((writable + aliases) - jsKeys),
            expected,
            actual
        )
    }
}
