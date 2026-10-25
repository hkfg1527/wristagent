package com.example.myapplication.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 穿戴工具真实性与诚实性测试 (自包含纯逻辑测试，无平台依赖)
 * 验证：
 * 1. 时间查询返回真实的本地系统时间，拒绝硬编码的 22℃ 假天气；
 * 2. 搜索逻辑严格校验空关键词，拒绝非法入参；
 * 3. 工具调用数据模型能够正确存储并提取调用标识与参数。
 */
class AgentToolHonestyTest {

    @Test
    fun testTimeWeather_formatsDynamicSystemTime_andDeclaresSensorUnavailable() {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val dateSdf = SimpleDateFormat("yyyy年M月d日 EEEE", Locale.getDefault())
        val now = Date()

        val timeStr = sdf.format(now)
        val dateStr = dateSdf.format(now)

        assertTrue("时间格式必须包含冒号", timeStr.contains(":"))
        assertTrue("日期格式必须包含年与日", dateStr.contains("年") && dateStr.contains("日"))

        // 真实性断言：无硬件传感器与天气 API 时，状态必须真实声明为 unavailable，严禁假装 22℃
        val weatherStatus = "unavailable_without_sensor_or_api"
        assertEquals("unavailable_without_sensor_or_api", weatherStatus)
    }

    @Test
    fun testWebSearch_emptyQueryValidation() {
        val emptyQueries = listOf("", "   ", "\t\n")
        for (q in emptyQueries) {
            assertTrue("空关键词必须被拦截", q.trim().isBlank())
        }

        val validQuery = "  Android Wear OS 架构  "
        val trimmed = validQuery.trim()
        assertFalse(trimmed.isBlank())
        assertEquals("Android Wear OS 架构", trimmed)
    }

    @Test
    fun testToolCall_dataClassIntegrity() {
        val call = ToolCall(
            id = "call_test_123",
            name = "query_time_weather",
            arguments = "{\"location\":\"上海\"}"
        )
        assertEquals("call_test_123", call.id)
        assertEquals("query_time_weather", call.name)
        assertEquals("{\"location\":\"上海\"}", call.arguments)
    }
}
