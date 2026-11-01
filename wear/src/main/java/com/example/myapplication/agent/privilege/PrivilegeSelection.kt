package com.example.myapplication.agent.privilege

/** 后端选择与状态组装：纯函数，不碰系统 API。 */
internal object PrivilegeSelection {

    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    /** 优先级固定为 ROOT > SHIZUKU > DIRECT：root 不需要额外守护进程，可靠性最高。 */
    fun selectBackend(rootAvailable: Boolean, shizukuReady: Boolean): PrivilegeBackend = when {
        rootAvailable -> PrivilegeBackend.ROOT
        shizukuReady -> PrivilegeBackend.SHIZUKU
        else -> PrivilegeBackend.DIRECT
    }

    /**
     * 组装给 UI 的快照。
     * 这里会把"没安装却报告就绪"这种脏输入降级：探测逻辑分散在 binder 回调、进程探测与包管理器里，
     * 任何一处出错都不该让界面显示"未安装但已授权"这种自相矛盾的状态。
     */
    fun status(
        rootAvailable: Boolean,
        shizukuInstalled: Boolean,
        shizukuReady: Boolean,
    ): PrivilegeStatus {
        val effectiveReady = shizukuReady && shizukuInstalled
        return PrivilegeStatus(
            activeBackend = selectBackend(rootAvailable, effectiveReady),
            rootAvailable = rootAvailable,
            shizukuReady = effectiveReady,
            shizukuInstalled = shizukuInstalled,
        )
    }
}