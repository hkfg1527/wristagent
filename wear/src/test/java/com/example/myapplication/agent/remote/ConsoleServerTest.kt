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

    private fun api(): ConsoleApi = ConsoleApi(
        settings = object : ConsoleSettingsPort {
            override fun snapshot(): Map<String, Any?> = linkedMapOf<String, Any?>("modelName" to "deepseek-chat")
            override fun write(key: String, value: Any): ConsoleWriteResult = ConsoleWriteResult.OK
        },
        capabilities = { emptyMap() },
        actions = { "done" },
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