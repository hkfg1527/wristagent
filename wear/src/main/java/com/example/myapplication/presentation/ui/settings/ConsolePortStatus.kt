package com.example.myapplication.presentation.ui.settings

/**
 * 网页控制台端口的展示判定（纯 Kotlin，无 Android / Compose 依赖）。
 *
 * 为什么需要它：首选端口被占用时 [com.example.myapplication.agent.remote.ConsoleServer]
 * 会按 [com.example.myapplication.agent.remote.ConsolePortPolicy] 漂到 8081 / 8082，
 * 而配置里只有首选端口 —— 界面若回显首选端口，就等于报了一个并不存在的端口。
 *
 * 判定只看两个数字：真实端口（ConsoleServer.port）与首选端口（ConsoleServer.preferredPort）。
 * 文案不进这里：字符串在 strings_settings.xml，本文件只产出"该说哪一句"。
 */
enum class ConsolePortStatus {

    /** 服务没在跑：ConsoleServer 此时把 port 记成 -1，界面显示「未运行」，不给假端口。 */
    NOT_RUNNING,

    /** 真实端口 == 首选端口：常规回显。 */
    STABLE,

    /** 真实端口 != 首选端口：首选端口被占用，界面必须点明。 */
    DRIFTED;

    companion object {

        /** 端口判定。非正数不是合法端口，一律按"未运行"处理。 */
        fun of(preferredPort: Int, actualPort: Int): ConsolePortStatus = when {
            actualPort <= 0 -> NOT_RUNNING
            actualPort == preferredPort -> STABLE
            else -> DRIFTED
        }
    }
}
