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