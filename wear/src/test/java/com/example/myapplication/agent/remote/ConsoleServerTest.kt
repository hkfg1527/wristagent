package com.example.myapplication.agent.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI

class ConsoleServerTest {

    private val token = "0123456789abcdef0123456789abcdef"

    private fun api(actions: (String) -> String = { "done" }): ConsoleApi = ConsoleApi(
        settings = object : ConsoleSettingsPort {
            override fun snapshot(): Map<String, Any?> = linkedMapOf<String, Any?>("modelName" to "deepseek-chat")
            override fun write(key: String, value: Any): ConsoleWriteResult = ConsoleWriteResult.OK
        },
        capabilities = { emptyMap() },
        actions = actions,
        assets = { "<html>console</html>".toByteArray() },
        token = { token },
        serverPort = { -1 },
        uptime = { 0L },
    )

    /** 固定段内找空闲端口：避开系统临时端口段，避免与测试自身连接的 TIME_WAIT 端口相撞。 */
    private fun freePort(): Int {
        for (candidate in 18080..18120) {
            val socket = runCatching { ServerSocket().apply { bind(InetSocketAddress(candidate)) } }.getOrNull()
            if (socket != null) {
                socket.close()
                return candidate
            }
        }
        return 18990
    }

    private fun acceptsConnection(port: Int): Boolean = try {
        Socket().use { socket -> socket.connect(InetSocketAddress("127.0.0.1", port), 500) }
        true
    } catch (_: IOException) {
        false
    }

    @Test
    fun aChunkedBodyWithoutContentLengthIsRejectedBeforeItIsRead() {
        val port = freePort()
        val server = ConsoleServer(api(), preferredPort = port)
        server.start()
        val bound = server.port
        try {
            // 回归（0.0.0.113 审查，高）：没有 Content-Length 时 NanoHTTPD 会先把整段 body
            // 物化成字符串再交给我们，64K 闸门来不及生效 —— 而且这一步发生在 token 校验之前，
            // 局域网里任何未鉴权主机都能让手表把任意大的 body 读进内存。现在直接 411。
            //
            // 用裸 socket 发**只有头、没有 body** 的请求：这样能证明服务端没在等 body，
            // 而是看到 Transfer-Encoding 就立刻回绝。
            val statusLine = Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", bound), 2000)
                socket.soTimeout = 3000
                socket.getOutputStream().apply {
                    write(
                        ("POST /api/settings HTTP/1.1\r\n" +
                            "Host: 127.0.0.1\r\n" +
                            "Transfer-Encoding: chunked\r\n" +
                            "Content-Type: application/json\r\n\r\n").toByteArray()
                    )
                    flush()
                }
                socket.getInputStream().bufferedReader().readLine().orEmpty()
            }
            assertTrue("必须回 411，实际状态行：" + statusLine, statusLine.contains("411"))
        } finally {
            server.stop()
        }
    }

    private fun http(path: String, port: Int): Pair<Int, String> {
        val connection = URI("http://127.0.0.1:" + port + path).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.requestMethod = "GET"
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            code to (stream?.bufferedReader()?.use { it.readText() } ?: "")
        } finally {
            connection.disconnect()
        }
    }

    /** 发一笔纯文本 POST：body 逐字发出（不设 JSON Content-Type，模拟"正文就是个路径"那种请求）。 */
    private fun post(path: String, body: String, port: Int): Pair<Int, String> {
        val connection = URI("http://127.0.0.1:" + port + path).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "text/plain")
        return try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            code to (stream?.bufferedReader()?.use { it.readText() } ?: "")
        } finally {
            connection.disconnect()
        }
    }


    @Test
    fun servesJsonOverRealSocketAndReleasesPortOnStop() {
        val port = freePort()
        val server = ConsoleServer(api(), preferredPort = port)
        try {
            server.start()
            assertEquals(port, server.port)
            val authorized = http("/api/ping?t=" + token, port)
            assertEquals(200, authorized.first)
            assertTrue("unexpected body: " + authorized.second, authorized.second.contains("wristagent"))
            val denied = http("/api/ping", port)
            assertEquals(401, denied.first)
            assertTrue("unexpected body: " + denied.second, denied.second.contains("unauthorized"))
        } finally {
            server.stop()
        }
        assertEquals(-1, server.port)
        assertFalse(server.isRunning)
        assertFalse("stop 后端口不应再接受连接", acceptsConnection(port))
    }

    @Test
    fun fallsBackToNextPortWhenPreferredIsBusy() {
        ServerSocket(0).use { occupied ->
            val busy = occupied.localPort
            val server = ConsoleServer(api(), preferredPort = busy)
            try {
                server.start()
                val chosen = server.port
                assertTrue("chosen=" + chosen + " busy=" + busy, chosen != busy && chosen in (busy + 1)..(busy + 2))
                assertEquals(200, http("/api/ping?t=" + token, chosen).first)
            } finally {
                server.stop()
            }
        }
    }

    @Test
    fun startIsIdempotentAndKeepsOneListener() {
        val port = freePort()
        val server = ConsoleServer(api(), preferredPort = port)
        try {
            server.start()
            assertEquals(port, server.port)
            server.start()
            assertEquals(port, server.port)
            assertTrue(server.isRunning)
            assertEquals(200, http("/api/ping?t=" + token, port).first)
        } finally {
            server.stop()
        }
        assertEquals(-1, server.port)
    }

    @Test
    fun aPostBodyThatLooksLikeAPathIsNeverTouchedOnDisk() {
        // 回归：曾经把 NanoHTTPD 的 postData 当成"临时文件路径"读了一遍再删掉，
        // 而那个字段其实是**正文文本** —— 于是任何无令牌客户端只要 POST 一个存在的路径，
        // 就能让服务端把该文件读进内存并删除（未鉴权任意文件删除）。
        val victim = java.io.File.createTempFile("console-victim", ".txt")
        victim.writeText("不该被删除")
        val port = freePort()
        val server = ConsoleServer(api(), preferredPort = port)
        try {
            server.start()
            assertEquals(port, server.port)
            val (code, _) = post("/api/state", victim.absolutePath, port)
            assertEquals("无令牌 POST 必须是 401", 401, code)
            assertTrue("正文虽然是一个真实路径，服务端也不许碰它", victim.exists())
            assertEquals("不该被删除", victim.readText())
        } finally {
            server.stop()
            victim.delete()
        }
    }

    @Test
    fun stopDoesNotInterruptAHandlerThatIsAlreadyWriting() {
        // 回归：旧顺序是"先 shutdownNow 再关监听" —— 正在执行的 handler 被直接打断，
        // clear-memory 或一次 40 键批量写会停在一半，prefs 里"半套已写、半套没写"。
        // 现在的保证是：**handler 线程不被中断**（它跑完、写落盘）；
        // 至于响应能不能送到客户端，取决于 NanoHTTPD 关连接的时刻，这里不承诺。
        val port = freePort()
        val finished = java.util.concurrent.atomic.AtomicBoolean(false)
        val slow = api(actions = { name ->
            Thread.sleep(120)
            finished.set(true)
            "done:" + name
        })
        val server = ConsoleServer(slow, preferredPort = port)
        server.start()
        val worker = Thread { runCatching { post("/api/action/probe-privilege?t=" + token, "{}", port) } }
        worker.start()
        Thread.sleep(40)
        server.stop()
        worker.join(3000)
        // 给 handler 一点余量收尾（它可能还在写响应）
        val deadline = System.currentTimeMillis() + 2000
        while (!finished.get() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertTrue("stop() 不该打断已经开始的 handler；它必须跑到自己结束", finished.get())
    }

    @Test
    fun missingStaticFilesStillAnswerJson404() {
        val port = freePort()
        val server = ConsoleServer(api(), preferredPort = port)
        try {
            server.start()
            assertEquals(port, server.port)
            val response = http("/", port)
            assertEquals(200, response.first)
            assertTrue(response.second.contains("console"))
            val unknown = http("/nope", port)
            assertEquals(404, unknown.first)
            assertTrue(unknown.second.contains("not_found"))
        } finally {
            server.stop()
        }
    }
}