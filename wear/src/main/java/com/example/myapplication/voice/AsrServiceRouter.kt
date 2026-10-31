package com.example.myapplication.voice

/** 路由结果：要么选中某个服务，要么给出不可用原因 */
sealed interface AsrRoute {
    data class UseProvider(val providerId: AsrProviderId, val note: String? = null) : AsrRoute
    data class Unavailable(val reason: String) : AsrRoute
}

/** 语音服务选择与降级：纯函数，便于穷举单测 */
object AsrServiceRouter {

    /**
     * 1. 总开关关闭 -> 不可用（说明如何开启）
     * 2. 选了百度且密钥齐全 -> 直接用百度
     * 3. 选了百度但密钥缺失 -> 系统识别可用则降级并说明，否则报缺少密钥
     * 4. 选了系统 -> 有引擎则用，无引擎则报未安装
     */
    fun resolve(settings: AsrSettings, systemRecognizerAvailable: Boolean): AsrRoute {
        if (!settings.enabled) {
            return AsrRoute.Unavailable(AsrMessages.DISABLED)
        }
        return when (settings.providerId) {
            AsrProviderId.BAIDU -> when {
                settings.hasBaiduCredentials -> AsrRoute.UseProvider(AsrProviderId.BAIDU)
                systemRecognizerAvailable -> AsrRoute.UseProvider(AsrProviderId.SYSTEM, AsrMessages.BAIDU_FALLBACK_NOTE)
                else -> AsrRoute.Unavailable(AsrMessages.NO_BAIDU_KEY)
            }
            AsrProviderId.SYSTEM -> if (systemRecognizerAvailable) {
                AsrRoute.UseProvider(AsrProviderId.SYSTEM)
            } else {
                AsrRoute.Unavailable(AsrMessages.NO_ENGINE)
            }
        }
    }
}
