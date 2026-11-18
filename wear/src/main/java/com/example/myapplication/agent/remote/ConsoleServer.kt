package com.example.myapplication.agent.remote

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
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
                if (index == 0) super.start(IDLE_READ_TIMEOUT_MS, false)
                else httpd.start(IDLE_READ_TIMEOUT_MS, false)
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
        // 顺序很重要：先关监听（不再收新请求），再优雅关池子，最后才打断。
        // 旧顺序是"先 shutdownNow 再关监听" —— 已经开始的 handler 会被直接打断，
        // clear-memory 或一次 40 键批量写会停在一半，prefs 里"半套已写、半套没写"。
        // 注意这套保证只覆盖"handler 跑完并落盘"：响应能否送到客户端取决于 NanoHTTPD 关连接的时机。
        if (httpd != null) closeListener(httpd)
        pool?.let { executor ->
            runCatching { executor.shutdown() }
            runCatching { executor.awaitTermination(STOP_GRACE_MS, TimeUnit.MILLISECONDS) }
            runCatching { executor.shutdownNow() }
        }
        pool = null
    }

    /** 本实例自己绑定时必须走 NanoHTTPD.stop()：再调一次本类重载只会原地空转，套接字关不掉。 */
    private fun closeListener(httpd: NanoHTTPD) {
        if (httpd === this) super.stop() else runCatching { httpd.stop() }
    }

    override fun serve(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response = try {
        dispatch(session).toNanoResponse()
    } catch (failure: Throwable) {
        // 500 必须留痕：以前这里零日志，真缺陷在真机上完全没有证据（只有客户端拿到 internal）
        Log.w(TAG, "serve 失败：" + (session.method?.name ?: "?") + " " + (session.uri ?: "?"), failure)
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
            val restoreHeaders = forceUtf8Charset(session)
            try {
                session.parseBody(files)
            } catch (e: NanoHTTPD.ResponseException) {
                if (e.status?.requestStatus != 413) return errorResponse(400, "bad_request")
                tooLarge = true
            } catch (_: IOException) {
                return errorResponse(400, "bad_request")
            } finally {
                restoreHeaders()
            }
            // NanoHTTPD 的 postData 是**正文文本**（只有 PUT 才落临时文件并给路径，而上游
            // parseBody 的 saveTmpFile 分支我们不接受）。曾经这里把正文当路径去 File(raw).isFile
            // 读一遍再 delete —— 于是任何无令牌客户端只要 POST 一个真实路径，就能让服务端
            // 把该文件读进内存并删除：未鉴权任意文件删除，且发生在鉴权之前。
            val raw = files["postData"]
            if (raw != null) {
                val bytes = raw.toByteArray(StandardCharsets.UTF_8)
                if (bytes.size > ConsoleApi.MAX_BODY_BYTES) {
                    tooLarge = true
                } else {
                    bodyBytes = bytes.size
                    body = raw
                }
            }
        }

        // 对端 IP 只用于失败限流分桶，不写进任何响应
        val client = runCatching { session.remoteIpAddress }.getOrNull().orEmpty()
        return api.handle(ConsoleRequest(method, uri, query, headers, body, bodyBytes, tooLarge, client))
    }

    /**
     * NanoHTTPD 按 Content-Type 里的 charset 解 body，缺省 US-ASCII —— 中文会被换成 U+FFFD 再落库，不可逆。
     * 提交前补 charset=utf-8，解析完立刻还原，避免下游读到的请求头被改动。
     */
    private fun forceUtf8Charset(session: NanoHTTPD.IHTTPSession): () -> Unit {
        val headers = runCatching { session.headers }.getOrNull() ?: return {}
        val original = headers["content-type"] ?: return {}
        val patched = ConsoleCharset.withUtf8(original) ?: return {}
        if (patched == original) return {}
        val mutable = headers as? MutableMap<String, String> ?: return {}
        return try {
            mutable["content-type"] = patched
            { mutable["content-type"] = original }
        } catch (_: RuntimeException) {
            {}
        }
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
        // 一问一答就关连接：NanoHTTPD 把「一个 keep-alive 连接」当成一个 worker 占着，
        // 而浏览器会保留空闲连接；服务端读超时关掉它、客户端还不知道的那一瞬间发的请求会石沉大海
        // （POST 不重试）—— 实测网页保存读到 9 秒超时报「未保存」。关掉 keep-alive 没有这个竞态，
        // 代价只是局域网里多一次 TCP 握手。
        response.closeConnection(true)
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

        /**
         * 同时在跑的连接数上限。
         *
         * 为什么是 32 而不是 8：NanoHTTPD 一个 TCP 连接就占一个 worker，直到对端关闭或读超时。
         * 浏览器对同一 host 会开 4~8 条并发连接，两个标签页就顶满 8 个 worker；
         * 2026-11-18 实测 12 笔并发 POST 被拉成 5.3s / 10.3s / 15.4s / 20.4s / 25.4s（每笔正好卡在 5s 读超时上），
         * 网页端的 9s 超时就会报「未保存」，而其实写已经落盘了。
         */
        private const val MAX_WORKERS = 32

        /** 空闲连接读超时：对端不发新请求就尽快把 worker 还回池子，别让空转连接继续占位。 */
        private const val IDLE_READ_TIMEOUT_MS = 2000

        /**
         * stop() 时给在跑的请求留多久收尾。
         *
         * 200ms 是"够写完一次 prefs.apply()"与"不把 onDestroy 卡住"之间的折中：
         * 超过就 shutdownNow 打断，宁可丢这一笔，也不让主线程无限等。
         */
        private const val STOP_GRACE_MS = 200L

        /** 排队上界：32 个在跑 + 64 个排队，再多也只能证明有人在刷，不该进内存。 */
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
