package com.example.myapplication.agent.privilege

/** 特权层探测快照，UI 只读这一个对象。 */
data class PrivilegeStatus(
    val activeBackend: PrivilegeBackend = PrivilegeBackend.DIRECT,
    val rootAvailable: Boolean = false,
    val shizukuReady: Boolean = false,
    val shizukuInstalled: Boolean = false,
    val lastProbeOutput: String = "",
)
