package com.example.myapplication.voice

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Base64

/** 一次 HTTP 往返的结果；error 非空表示传输层失败 */
data class HttpOutcome(val statusCode: Int, val body: String?, val error: String?)

/** 可替换的阻塞式 HTTP 通道：真实实现阻塞在 IO 线程，单测注入假实现，禁止真连网 */
fun interface HttpTransport {
    fun execute(url: String, method: String, jsonBody: String?): HttpOutcome
}

/** 基于 java.net.HttpURLConnection 的最小实现，不引入任何新依赖 */
class JvmHttpTransport(
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 20_000
) : HttpTransport {

    override fun execute(url: String, method: String, jsonBody: String?): HttpOutcome {
        val connection: HttpURLConnection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            return HttpOutcome(0, null, describe(e))
        } catch (e: ClassCastException) {
            return HttpOutcome(0, null, "不支持的请求地址：" + url)
        }

        connection.requestMethod = method
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs
        connection.doInput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.setRequestProperty("Accept", "application/json")

        return try {
            if (jsonBody != null) {
                val payload = jsonBody.toByteArray(Charsets.UTF_8)
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(payload.size)
                connection.outputStream.use { it.write(payload) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            HttpOutcome(code, text, null)
        } catch (e: IOException) {
            HttpOutcome(0, null, describe(e))
        } catch (e: SecurityException) {
            HttpOutcome(0, null, "缺少网络访问权限（" + describe(e) + "）")
        } finally {
            connection.disconnect()
        }
    }

    private fun describe(error: Throwable): String = error.message ?: error.javaClass.simpleName
}

/** access_token 缓存：按 AK 维度隔离，过期前留安全边界 */
class BaiduTokenCache(
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private data class Entry(val apiKey: String, val token: String, val expiresAtMs: Long)

    private var entry: Entry? = null

    fun cached(apiKey: String): String? {
        val current = entry ?: return null
        if (current.apiKey != apiKey) return null
        if (clock() >= current.expiresAtMs) return null
        return current.token
    }

    fun store(apiKey: String, token: String, expiresInSeconds: Long) {
        val ttlMs = expiresInSeconds * 1000L
        val usableMs = (ttlMs - REFRESH_MARGIN_MS).coerceAtLeast(0L)
        entry = Entry(apiKey, token, clock() + usableMs)
    }

    fun invalidate() {
        entry = null
    }

    companion object {
        const val REFRESH_MARGIN_MS: Long = 60_000L
    }
}

/** 百度短语音识别 REST 的请求体构造（参数取自官方文档，见类内 TODO 说明） */
object BaiduAsrRequest {
    fun build(audio: ByteArray, sampleRate: Int, devPid: Int, token: String, cuid: String): String {
        val speech = Base64.getEncoder().encodeToString(audio)
        val builder = StringBuilder(speech.length + 256)
        builder.append('{')
        builder.append("\"format\":\"wav\",")
        builder.append("\"rate\":").append(sampleRate).append(',')
        builder.append("\"channel\":1,")
        builder.append("\"cuid\":\"").append(escape(cuid)).append("\",")
        builder.append("\"dev_pid\":").append(devPid).append(',')
        builder.append("\"token\":\"").append(escape(token)).append("\",")
        builder.append("\"speech\":\"").append(speech).append("\",")
        builder.append("\"len\":").append(audio.size)
        builder.append('}')
        return builder.toString()
    }

    private fun escape(raw: String): String {
        val out = StringBuilder(raw.length + 8)
        for (ch in raw) {
            when (ch) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> out.append(ch)
            }
        }
        return out.toString()
    }
}

/**
 * 百度短语音识别错误码到可读中文的映射。
 *
 * 已核实来源：https://cloud.baidu.com/doc/SPEECH/s/Jlbxdezuf（短语音识别标准版API）
 * TODO(待官方文档确认)：3308/3309/3310/3311/3312/3313/3314/3315/3316/3317 的具体语义未在可查文档中确认，
 * 因此统一落到「未知错误码」分支，保留原始错误码以便排查，不做语义臆测。
 */
object BaiduErrorMessages {
    fun forCode(code: Int): String = when (code) {
        3300 -> "语音请求参数不正确，请重试（错误码 3300）"
        3301 -> "录音质量过差，请靠近麦克风后重新说话（错误码 3301）"
        3302 -> "百度语音鉴权失败，请检查“语音转写”里的语音密钥（API Key / Secret Key），错误码 3302"
        3303 -> "百度语音服务端处理失败，请稍后重试（错误码 3303）"
        3304 -> "请求过于频繁或额度已用尽，请稍后再试（错误码 3304）"
        3305 -> "百度语音服务暂时不可用，请稍后重试（错误码 3305）"
        3307 -> "录音过长，单次识别请控制在 60 秒以内（错误码 3307）"
        else -> "百度语音识别失败（错误码 " + code + "），请稍后重试"
    }

    fun isKnown(code: Int): Boolean = when (code) {
        3300, 3301, 3302, 3303, 3304, 3305, 3307 -> true
        else -> false
    }
}

/** 百度返回体的最小解析：手写正则，避免依赖 org.json 而在 JVM 单测中抛 "not mocked" */
object BaiduResponse {
    fun errNo(body: String): Int? =
        Regex("\"err_no\"\\s*:\\s*(-?\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull()

    fun errMsg(body: String): String? = stringValue(body, "err_msg")

    fun accessToken(body: String): String? = stringValue(body, "access_token")

    fun expiresInSeconds(body: String): Long? =
        Regex("\"expires_in\"\\s*:\\s*(\\d+)").find(body)?.groupValues?.get(1)?.toLongOrNull()

    fun oauthError(body: String): String? =
        stringValue(body, "error_description") ?: stringValue(body, "error")

    fun firstResult(body: String): String? {
        val array = Regex("\"result\"\\s*:\\s*\\[(.*?)\\]", RegexOption.DOT_MATCHES_ALL).find(body) ?: return null
        val first = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"").find(array.groupValues[1]) ?: return null
        return unescape(first.groupValues[1])
    }

    private fun stringValue(body: String, key: String): String? {
        val pattern = Regex("\"" + Regex.escape(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
        val match = pattern.find(body) ?: return null
        return unescape(match.groupValues[1])
    }

    private fun unescape(raw: String): String {
        val out = StringBuilder(raw.length)
        var index = 0
        while (index < raw.length) {
            val ch = raw[index]
            if (ch != '\\' || index == raw.length - 1) {
                out.append(ch)
                index++
                continue
            }
            when (val next = raw[index + 1]) {
                '"', '\\', '/' -> out.append(next)
                'n' -> out.append('\n')
                'r' -> out.append('\r')
                't' -> out.append('\t')
                else -> out.append(next)
            }
            index += 2
        }
        return out.toString()
    }
}

/** 百度短语音识别（REST）实现：token 获取与缓存、请求体构造、错误码映射全部在此收敛 */
class BaiduAsrProvider(
    private val apiKey: String,
    private val secretKey: String,
    private val devPid: Int,
    private val transport: HttpTransport,
    private val tokenCache: BaiduTokenCache = BaiduTokenCache(),
    private val cuid: String = "wear-myapplication",
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AsrProvider {

    override val providerId: AsrProviderId = AsrProviderId.BAIDU

    override suspend fun transcribe(wavBytes: ByteArray, sampleRate: Int): AsrResult {
        if (apiKey.isBlank() || secretKey.isBlank()) {
            return AsrResult.Failure(AsrMessages.NO_BAIDU_KEY)
        }
        if (wavBytes.isEmpty()) {
            return AsrResult.Failure(AsrMessages.EMPTY_AUDIO)
        }

        val tokenOutcome = obtainToken()
        val token = tokenOutcome.getOrNull()
        if (token == null) {
            val error = tokenOutcome.exceptionOrNull()
            val reason = error?.message?.takeIf { it.isNotBlank() } ?: AsrMessages.NO_BAIDU_KEY
            return AsrResult.Failure(reason)
        }
        return callAsr(token, wavBytes, sampleRate)
    }

    private suspend fun obtainToken(): Result<String> {
        tokenCache.cached(apiKey)?.let { return Result.success(it) }

        val url = TOKEN_URL +
            "?grant_type=client_credentials" +
            "&client_id=" + URLEncoder.encode(apiKey, "UTF-8") +
            "&client_secret=" + URLEncoder.encode(secretKey, "UTF-8")

        val outcome = offline { transport.execute(url, "GET", null) }
        val transportError = outcome.error
        if (transportError != null) {
            return Result.failure(BaiduAsrException("百度语音鉴权请求网络失败，请检查网络后重试（" + transportError + "）"))
        }

        val body = outcome.body
        if (outcome.statusCode !in 200..299 || body.isNullOrBlank()) {
            val detail = BaiduResponse.oauthError(body.orEmpty())
            val suffix = if (detail.isNullOrBlank()) "" else "（服务端：" + detail + "）"
            return Result.failure(BaiduAsrException("百度语音鉴权失败，请检查“语音转写”里的语音密钥（API Key / Secret Key）是否正确" + suffix))
        }

        val token = BaiduResponse.accessToken(body)
            ?: return Result.failure(BaiduAsrException("百度语音鉴权失败，返回内容缺少 access_token，请检查密钥"))
        val expiresIn = BaiduResponse.expiresInSeconds(body) ?: DEFAULT_EXPIRES_IN_SECONDS
        tokenCache.store(apiKey, token, expiresIn)
        return Result.success(token)
    }

    private suspend fun callAsr(token: String, wavBytes: ByteArray, sampleRate: Int): AsrResult {
        val payload = BaiduAsrRequest.build(wavBytes, sampleRate, devPid, token, cuid)
        val outcome = offline { transport.execute(ASR_URL, "POST", payload) }

        val transportError = outcome.error
        if (transportError != null) {
            return AsrResult.Failure("百度语音请求网络失败，请检查手表网络后重试（" + transportError + "）")
        }
        val body = outcome.body
        if (outcome.statusCode !in 200..299 || body.isNullOrBlank()) {
            return AsrResult.Failure(
                "百度语音服务返回异常状态码 " + outcome.statusCode + "，请稍后重试",
                outcome.statusCode
            )
        }

        val errNo = BaiduResponse.errNo(body)
            ?: return AsrResult.Failure(AsrMessages.BAIDU_BAD_RESPONSE)
        if (errNo != 0) {
            if (errNo == AUTH_FAILED_CODE) {
                tokenCache.invalidate()
            }
            val detail = BaiduResponse.errMsg(body)
            val mapped = BaiduErrorMessages.forCode(errNo)
            val reason = if (!BaiduErrorMessages.isKnown(errNo) && !detail.isNullOrBlank()) {
                mapped + "（服务端：" + detail + "）"
            } else {
                mapped
            }
            return AsrResult.Failure(reason, errNo)
        }

        val text = BaiduResponse.firstResult(body)?.trim()
        if (text.isNullOrBlank()) {
            return AsrResult.Failure(AsrMessages.BAIDU_EMPTY_RESULT, errNo)
        }
        return AsrResult.Success(text)
    }

    private suspend fun <T> offline(block: () -> T): T = withContext(ioDispatcher) { block() }

    companion object {
        const val TOKEN_URL = "https://aip.baidubce.com/oauth/2.0/token"
        const val ASR_URL = "https://vop.baidu.com/server_api"

        /** oauth 未返回 expires_in 时的保守兜底（官方返回 2592000 秒 = 30 天） */
        const val DEFAULT_EXPIRES_IN_SECONDS = 2_592_000L

        private const val AUTH_FAILED_CODE = 3302
    }
}

/** 鉴权链路失败用异常携带可读原因，避免把空串传给上层 */
class BaiduAsrException(message: String) : Exception(message)
