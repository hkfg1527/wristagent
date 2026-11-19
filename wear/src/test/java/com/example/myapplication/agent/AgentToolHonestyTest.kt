package com.example.myapplication.agent

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.test.core.app.ApplicationProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 工具"诚实性"测试：**必须调用真实工具入口**，而不是在测试里自己把结论抄一遍。
 *
 * 这里原来三条是自证式的（自己 `new Date()` 再断言格式、自己写一个常量再断言它等于自己、
 * 断言数据类存了构造参数）—— 一条都没碰被测代码，永远绿。0.0.0.90 / 0.0.0.100 审查两次点名。
 * 现在改成走 [AgentToolRegistry.executeTool]。
 *
 * 必须跑 Robolectric：工具层用 `org.json` 解析参数，纯 JVM 下它是桩
 * （`isReturnDefaultValues = true`），`optString` 返回 null 会直接把工具打成 NPE ——
 * 那测的就不是诚实性，而是"桩坏没坏"。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AgentToolHonestyTest {

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun registry(): AgentToolRegistry =
        AgentToolRegistry(context, AgentPreferences(FakeSharedPreferences()))

    @Test
    fun queryTimeWeatherReportsRealLocalTimeAndDeclaresWeatherUnavailable() = runBlocking {
        val result = registry().executeTool(
            ToolCall(id = "c1", name = "query_time_weather", arguments = "{\"location\":\"上海\"}")
        )
        assertFalse("工具不许报错：" + result.output, result.isError)
        val output = result.output

        // 时间必须与"现在"对得上（同一分钟），而不是某个写死的字符串
        val expected = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        assertTrue("输出里必须出现当前本地时间 $expected，实际：" + output, output.contains(expected))
        assertTrue("必须带时区：" + output, output.contains("timezone") || output.contains("TimeZone"))
        // 诚实性：没有传感器/天气 API 时必须如实声明，不许假装 22℃
        assertTrue(
            "天气必须如实声明 unavailable，实际：" + output,
            output.contains("unavailable_without_sensor_or_api")
        )
        // 诚实性：不许凭空给出温度。别用"输出里没有 22"这种判据 —— 当前时间正好 22:00 时会误报，
        // 直接钉"没有 temperature / 天气字段"。
        assertFalse("不许凭空给出温度：" + output, output.contains("temperature"))
        assertFalse("不许凭空给出天气状况：" + output, output.contains("\"weather\""))
    }

    @Test
    fun webSearchRejectsBlankQueriesThroughTheRealGate() = runBlocking {
        val registry = registry()
        for (blank in listOf("", "   ", "\t\n")) {
            val result = registry.executeTool(
                ToolCall(id = "c2", name = "web_search", arguments = "{\"query\":\"" + blank + "\"}")
            )
            assertTrue(
                "空关键词必须被真实入口挡下（query=[" + blank + "]）：" + result.output,
                result.output.contains("搜索关键词不可为空") || result.isError
            )
        }
        // 反方向：非空关键词不该走到"关键词不可为空"
        val ok = registry.executeTool(
            ToolCall(id = "c3", name = "web_search", arguments = "{\"query\":\"Android Wear OS\"}")
        )
        assertFalse("非空关键词不该被当成空：" + ok.output, ok.output.contains("搜索关键词不可为空"))
    }
}
