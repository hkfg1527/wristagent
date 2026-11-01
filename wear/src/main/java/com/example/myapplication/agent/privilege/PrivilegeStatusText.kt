package com.example.myapplication.agent.privilege

import com.example.myapplication.R

/** 状态到文案资源的映射：纯函数，便于单测锁定每一档文案。 */
internal object PrivilegeStatusText {

    fun rootRes(available: Boolean): Int = if (available) {
        R.string.settings_privilege_root_available
    } else {
        R.string.settings_privilege_root_unavailable
    }

    fun shizukuInstalledRes(installed: Boolean): Int = if (installed) {
        R.string.settings_privilege_shizuku_installed
    } else {
        R.string.settings_privilege_shizuku_not_installed
    }

    fun shizukuGrantedRes(granted: Boolean): Int = if (granted) {
        R.string.settings_privilege_shizuku_granted
    } else {
        R.string.settings_privilege_shizuku_not_granted
    }
}