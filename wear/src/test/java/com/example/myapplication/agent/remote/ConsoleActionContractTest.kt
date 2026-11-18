package com.example.myapplication.agent.remote

import androidx.test.core.app.ApplicationProvider
import com.example.myapplication.readProjectFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 网页按钮与服务端 action 的契约。
 *
 * 0.0.0.90 审查发现：供应商档案那张卡的「测试连接」按钮打的是 `connection-test`，
 * 而服务端 `ACTION_NAMES` 里根本没有这个名字 —— 于是点一次红一次（404 not_found）。
 * 这类缺陷不会让任何单测变红，因为两边各自都"自洽"：网页以为自己发了个合法 action，
 * 服务端以为自己收到个不认识的 action。只有把两份清单摆在一起比才会暴露。
 *
 * 所以这里直接扫网页源码里的 `actionName: '...'`，与服务端 `ACTION_NAMES` 双向对齐。
 */
class ConsoleActionContractTest {

    private val appJs = readProjectFile("src/main/assets/console/app.js")

    private val webActions: Set<String> =
        Regex("""actionName:\s*'([a-z][a-z-]*)'""").findAll(appJs).map { it.groupValues[1] }.toSet()

    @Test
    fun theWebConsoleOnlyCallsActionsTheServerKnows() {
        assertTrue("没扫到任何 actionName，正则已失效（app.js 可能改了写法）", webActions.isNotEmpty())
        val unknown = webActions - ConsoleApi.ACTION_NAMES.toSet()
        assertEquals("网页调了服务端没有的 action（会恒 404）：", emptySet<String>(), unknown)
    }

    @Test
    fun everyServerActionIsReachableFromTheWebConsole() {
        val unused = ConsoleApi.ACTION_NAMES.toSet() - webActions
        assertEquals("服务端登记了网页根本不会调用的 action（要么删，要么接线）：", emptySet<String>(), unused)
    }

    @Test
    fun theTestConnectionButtonMapsToARealAction() {
        assertTrue(
            "供应商档案的「测试连接」必须指向 connection-test",
            appJs.contains("id: 'providerProfiles'") && appJs.contains("actionName: 'connection-test'")
        )
        assertTrue("服务端必须登记 connection-test", "connection-test" in ConsoleApi.ACTION_NAMES)
    }
}
