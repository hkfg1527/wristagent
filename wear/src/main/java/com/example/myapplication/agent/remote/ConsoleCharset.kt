package com.example.myapplication.agent.remote

/**
 * NanoHTTPD 解 body 时只看 Content-Type 里的 charset，缺省是 US-ASCII：
 * 网页/脚本发来的中文会被逐字节替换成 U+FFFD，落库后不可逆（实测「中文探针-ABC」写成 12 个 U+FFFD）。
 * 这里负责把缺省 charset 补成 UTF-8。
 */
internal object ConsoleCharset {

    private const val UTF8_PARAM = "; charset=utf-8"

    /**
     * 缺 charset 的 Content-Type 补成 utf-8。
     *
     * 已写明 charset（哪怕是 GBK）原样返回——那是调用方明确的意图，不该被覆盖；
     * multipart 不动——它的 boundary 解析依赖原始头，追加参数没有收益。
     */
    fun withUtf8(contentType: String?): String? {
        if (contentType == null) return null
        val trimmed = contentType.trim()
        if (trimmed.isEmpty()) return contentType
        if (trimmed.contains("charset=", ignoreCase = true)) return contentType
        if (trimmed.contains("multipart/", ignoreCase = true)) return contentType
        return trimmed + UTF8_PARAM
    }
}
