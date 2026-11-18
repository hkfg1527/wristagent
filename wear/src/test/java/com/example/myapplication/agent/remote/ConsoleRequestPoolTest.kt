package com.example.myapplication.agent.remote

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.ThreadPoolExecutor

/** 请求执行池的两条硬约束：队列必须有界，队列满时必须丢弃而不是抛异常打死 accept 循环。 */
class ConsoleRequestPoolTest {

    private val server = ConsoleServer(
        ConsoleApi(
            settings = object : ConsoleSettingsPort {
                override fun snapshot(): Map<String, Any?> = emptyMap()
                override fun write(key: String, value: Any): ConsoleWriteResult = ConsoleWriteResult.OK
            },
            capabilities = { emptyMap() },
            actions = { "" },
            assets = { null },
            token = { "1234" },
            serverPort = { -1 },
            uptime = { 0L },
        )
    )

    private fun newPool(): ThreadPoolExecutor = server.newRequestPool()

    @Test
    fun poolCanAbsorbABrowsersParallelConnections() {
        val executor = newPool()
        try {
            // 浏览器对一个 host 会开 4~8 条并发连接，两个标签页就 16 条；再加页面里并发的几个请求。
            // 池子小于这数就会把"空闲 keep-alive 连接"排到真请求前面 —— 真机上表现为保存 5~25 秒才回。
            assertTrue(
                "请求池至少要能同时容纳 24 条连接（当前最大 " + executor.maximumPoolSize + "）",
                executor.maximumPoolSize >= 24
            )
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun requestQueueHasABound() {
        val executor = newPool()
        try {
            val remaining = executor.queue.remainingCapacity()
            assertTrue("请求队列必须有上界（当前剩余容量 " + remaining + "），否则洪泛会吃光堆", remaining <= 256)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun queueFullDropsTheRequestInsteadOfThrowing() {
        val executor = newPool()
        val gate = CountDownLatch(1)
        try {
            var rejected = false
            var submitted = 0
            while (submitted < 600 && !rejected) {
                rejected = !server.submitRequest(
                    executor,
                    object : Runnable {
                        override fun run() {
                            runCatching { gate.await(2, TimeUnit.SECONDS) }
                        }
                    },
                )
                submitted += 1
            }
            assertTrue(
                "队列满时必须返回 false 并丢弃连接，而不是无限排队（已投递 " + submitted + " 个任务）",
                rejected
            )
        } finally {
            gate.countDown()
            executor.shutdownNow()
        }
    }
}
