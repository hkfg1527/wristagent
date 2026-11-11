package com.example.myapplication.agent.remote

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * 局域网控制台 HTTP 服务端。
 *
 * 端口策略：优先 [preferredPort]，被占用时按 [ConsolePortPolicy] 依次尝试后续端口，真实端口见 [port]；
 * 全部候选端口都不可用时返回 -1，服务不启动，App 照常运行。
 *
 * 线程模型：请求一律交给独立线程池执行，业务侧的阻塞调用（特权探测、ASR、模型拉取）只发生在池线程上。
 * 服务端任何异常都被翻译成结构化 JSON，不向调用栈外抛。
 */
class ConsoleServer(
    private val api: ConsoleApi,
    /** 首选端口：界面拿它与真实 [port] 比对，判定是否发生了端口漂移。 */
    val preferredPort: Int = ConsolePortPolicy.DEFAULT_PORT,
    private val attempts: Int = ConsolePortPolicy.MAX_ATTEMPTS,
) : NanoHTTPD(preferredPort) {

    @Volatile
    var port: Int = -1
        private set

    private var active: NanoHTTPD? = null
    private var pool: ThreadPoolExecutor? = null

    val isRunning: Boolean
        get() = port > 0

    /** 绑定端口并开始接受请求；重复调用不会二次 bind，实际端口见 [port]。 */
    @Synchronized
    override fun start() {
        if (isRunning) return
        val executor = newRequestPool()
        var chosen = -1
        var bound: NanoHTTPD? = null
        for ((index, candidate) in ConsolePortPolicy.candidates(preferredPort, attempts).withIndex()) {
            val httpd: NanoHTTPD = if (index == 0) this else FallbackHttpd(candidate)
            httpd.setAsyncRunner(PooledRunner(executor))
            try {
                if (index == 0) super.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                else httpd.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                chosen = candidate
                bound = httpd
                break
            } catch (error: IOException) {
                Log.w(TAG, "端口 " + candidate + " 绑定失败，转下一个候选", error)
                closeListener(httpd)
            } catch (error: RuntimeException) {
                Log.w(TAG, "端口 " + candidate + " 绑定异常，转下一个候选", error)
                closeListener(httpd)
            }
        }
        if (bound == null) {
            Log.w(TAG, "全部候选端口都不可用，控制台未启动：" + ConsolePortPolicy.candidates(preferredPort, attempts))
            runCatching { executor.shutdownNow() }
            return
        }
        active = bound
        pool = executor
        port = chosen
    }

    /** 停止服务：关闭监听套接字与线程池，端口释放后可以再次 [start]。 */
    @Synchronized
    override fun stop() {
        val httpd = active
        active = null
        port = -1
        pool?.let { executor -> runCatching { executor.shutdownNow() } }
        pool = null
        if (httpd != null) closeListener(httpd)
    }

    /** 本实例自己绑定时必须走 NanoHTTPD.stop()：再调一次本类重载只会原地空转，套接字关不掉。 */
    private fun closeListener(httpd: NanoHTTPD) {
        if (httpd === this) super.stop() else runCatching { httpd.stop() }
    }

    override fun serve(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response = try {
        dispatch(session).toNanoResponse()
    } catch (_: Throwable) {
        errorResponse(500, "internal").toNanoResponse()
    }

    private fun dispatch(session: NanoHTTPD.IHTTPSession): ConsoleResponse {
        val method = session.method?.name ?: "GET"
        val uri = session.uri ?: "/"

        val query = LinkedHashMap<String, String>()
        runCatching {
            session.parameters?.forEach { entry ->
                val key = entry.key
                val value = entry.value?.firstOrNull()
                if (!key.isNullOrEmpty() && value != null) query[key] = value
            }
        }

        val headers = LinkedHashMap<String, String>()
        runCatching {
            session.headers?.forEach { entry ->
                val key = entry.key
                val value = entry.value
                if (!key.isNullOrBlank() && value != null) headers[key.lowercase()] = value
            }
        }

        var body: String? = null
        var bodyBytes = 0
        var tooLarge = false

        val declared = headers["content-length"]?.trim()?.toLongOrNull() ?: -1L
        if (declared > ConsoleApi.MAX_BODY_BYTES) {
            tooLarge = true
        } else if (method == "POST" || method == "PUT" || method == "PATCH") {
            val files = HashMap<String, String>()
            try {
                session.parseBody(files)
            } catch (e: NanoHTTPD.ResponseException) {
                if (e.status?.requestStatus != 413) return errorResponse(400, "bad_request")
                tooLarge = true
            } catch (_: IOException) {
                return errorResponse(400, "bad_request")
            }
            val raw = files["postData"]
            if (raw != null) {
                val file = File(raw)
                if (file.isFile) {
                    try {
                        if (file.length() > ConsoleApi.MAX_BODY_BYTES) {
                            tooLarge = true
                        } else {
                            val bytes = file.readBytes()
                            if (bytes.size > ConsoleApi.MAX_BODY_BYTES) {
                                tooLarge = true
                            } else {
                                bodyBytes = bytes.size
                                body = String(bytes, StandardCharsets.UTF_8)
                            }
                        }
                    } finally {
                        runCatching { file.delete() }
                    }
                } else {
                    val bytes = raw.toByteArray(StandardCharsets.UTF_8)
                    if (bytes.size > ConsoleApi.MAX_BODY_BYTES) {
                        tooLarge = true
                    } else {
                        bodyBytes = bytes.size
                        body = raw
                    }
                }
            }
        }

        // 对端 IP 只用于失败限流分桶，不写进任何响应
        val client = runCatching { session.remoteIpAddress }.getOrNull().orEmpty()
        return api.handle(ConsoleRequest(method, uri, query, headers, body, bodyBytes, tooLarge, client))
    }

    private fun errorResponse(status: Int, error: String): ConsoleResponse =
        ConsoleResponse.json(status, ConsoleJson.write(linkedMapOf<String, Any?>("ok" to false, "error" to error)))

    private fun ConsoleResponse.toNanoResponse(): NanoHTTPD.Response {
        val response = NanoHTTPD.newFixedLengthResponse(
            Statuses.of(status),
            contentType,
            ByteArrayInputStream(body),
            body.size.toLong(),
        )
        response.addHeader("Cache-Control", "no-store")
        return response
    }

    /** 请求执行池；可见是为了让单测断言队列上界，而不是把容量当成实现细节。 */
    fun newRequestPool(): ThreadPoolExecutor = ThreadPoolExecutor(
        0,
        MAX_WORKERS,
        30L,
        TimeUnit.SECONDS,
        ArrayBlockingQueue(QUEUE_CAPACITY),
    ).apply { allowCoreThreadTimeOut(true) }

    /** 投递请求：队列满时返回 false，而不是让 RejectedExecutionException 打死 accept 循环。 */
    fun submitRequest(executor: ThreadPoolExecutor, task: Runnable): Boolean =
        runCatching { executor.execute(task) }.isSuccess

    /** 把 NanoHTTPD 的请求执行托管到自有线程池，并跟踪在途连接以便 stop() 一并关闭。 */
    private inner class PooledRunner(private val executor: ThreadPoolExecutor) : NanoHTTPD.AsyncRunner {

        private val open = Collections.synchronizedList(ArrayList<NanoHTTPD.ClientHandler>())

        override fun exec(code: NanoHTTPD.ClientHandler) {
            // 队列满时丢弃这条连接：RejectedExecutionException 逃出 exec 会连带打死整个 accept 循环
            if (!submitRequest(executor, code)) {
                runCatching { code.close() }
                return
            }
            open.add(code)
        }

        override fun closed(code: NanoHTTPD.ClientHandler) {
            open.remove(code)
        }

        override fun closeAll() {
            open.toList().forEach { handler -> runCatching { handler.close() } }
            open.clear()
        }
    }

    /** 回退端口需要独立套接字，因此用同源派生的临时 NanoHTTPD 实例承载，请求仍回到同一个 [api]。 */
    private inner class FallbackHttpd(listenPort: Int) : NanoHTTPD(listenPort) {
        override fun serve(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response = this@ConsoleServer.serve(session)
    }

    private object Statuses {

        private val cache = HashMap<Int, NanoHTTPD.Response.IStatus>()

        @Synchronized
        fun of(code: Int): NanoHTTPD.Response.IStatus = cache.getOrPut(code) {
            object : NanoHTTPD.Response.IStatus {
                override fun getRequestStatus(): Int = code
                override fun getDescription(): String = code.toString() + " " + reason(code)
            }
        }

        private fun reason(code: Int): String = when (code) {
            200 -> "OK"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            413 -> "Payload Too Large"
            500 -> "Internal Server Error"
            else -> "Status"
        }
    }

    companion object {
        private const val TAG = "ConsoleServer"
        private const val MAX_WORKERS = 8

        /** 排队上界：8 个在跑 + 64 个排队，再多也只能证明有人在刷，不该进内存。 */
        const val QUEUE_CAPACITY = 64

        @Volatile
        private var singleton: ConsoleServer? = null

        /** Activity 重建时可以重复调用：同进程内返回同一实例，不会重复 bind 端口。 */
        @Synchronized
        fun create(context: Context, preferredPort: Int = ConsolePortPolicy.DEFAULT_PORT): ConsoleServer =
            singleton ?: ConsoleServer(
                api = ConsoleBridge.buildApi(context.applicationContext ?: context) { singleton?.port ?: -1 },
                preferredPort = preferredPort,
            ).also { singleton = it }

        @Synchronized
        fun peek(): ConsoleServer? = singleton
    }
}
